package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizFundAnnualBudget;
import com.zwinsight.finance.domain.BizFundMonthlyPlan;
import com.zwinsight.finance.domain.BizFundPlanDetail;
import com.zwinsight.finance.domain.BizFundRollingForecast;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.domain.BizReceivable;
import com.zwinsight.finance.mapper.BizFundAnnualBudgetMapper;
import com.zwinsight.finance.mapper.BizFundMonthlyPlanMapper;
import com.zwinsight.finance.mapper.BizFundPlanDetailMapper;
import com.zwinsight.finance.mapper.BizFundRollingForecastMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.finance.mapper.BizReceivableMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 资金计划三层联动服务（年度预算 + 月度计划 + 滚动预测）
 * <p>对标广联达 PMCore「先计划后支付」：
 * 1) 年度预算：年初编制，项目或公司维度；
 * 2) 月度计划：预计收支 + 科目明细（V2026_58）+ 月末实际回填（actual 为真实单据聚合，非手填）；
 * 3) 滚动预测（V2026_56/57 数据源重做，V2026_63 补逾期口径）：
 *    预计付款 = 已审批且<b>未支付</b>（status=APPROVED 且 pay_status≠PAID）的付款申请按付款日期落月，
 *    <b>其中已逾期未付（payment_date 早于当月月初）全额计入当月</b>并单列 overdue_unpaid 构成；
 *    预计收款 = 应收台账 OPEN 余额按到期日落月（无台账时回退月度计划 income_plan）；
 *    净缺口 = 付款 − 收款。快照由 {@code FundForecastTask} 每日 01:15 自动刷新。</p>
 * <p><b>旧版语义纠正</b>：本类原注释称“预计付款 = 已审批未付”，但当时系统无支付执行态
 * （审批通过即视为已付），“已批未付”状态并不存在；V2026_56 引入 pay_status 后该口径才真实成立。</p>
 * <p><b>逾期口径补正（V2026_63）</b>：原实现只统计 payment_date 落在未来窗口内的单据，
 * 已过计划付款日却仍未支付的款项会从预测中完全消失——线上实测 16 条/5850 万逾期款
 * 被漏计，当月缺口显示为盈余（LOW），而真实待付缺口为 5590 万（应 HIGH）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundPlanService {

    private final BizFundAnnualBudgetMapper annualBudgetMapper;
    private final BizFundMonthlyPlanMapper monthlyPlanMapper;
    private final BizFundPlanDetailMapper planDetailMapper;
    private final BizFundRollingForecastMapper rollingForecastMapper;
    private final BizPaymentApplyMapper paymentApplyMapper;
    private final BizPaymentReceivedMapper paymentReceivedMapper;
    private final BizReceivableMapper receivableMapper;
    private final com.zwinsight.finance.mapper.BizBankFlowMapper bankFlowMapper;
    private final FundCategoryService fundCategoryService;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    // ==================== 年度预算 ====================

    /**
     * 保存年度预算（项目+年度唯一）
     */
    public void saveAnnualBudget(BizFundAnnualBudget budget) {
        if (budget.getBudgetYear() == null || budget.getBudgetYear() < 2000 || budget.getBudgetYear() > 2100) {
            throw new BusinessException(400, "预算年度不合法");
        }
        if (budget.getIncomePlan() == null || budget.getIncomePlan().signum() < 0
                || budget.getExpensePlan() == null || budget.getExpensePlan().signum() < 0) {
            throw new BusinessException(400, "收支计划金额不能为负");
        }
        LambdaQueryWrapper<BizFundAnnualBudget> unique = new LambdaQueryWrapper<BizFundAnnualBudget>()
                .eq(BizFundAnnualBudget::getBudgetYear, budget.getBudgetYear());
        if (budget.getProjectId() != null) {
            unique.eq(BizFundAnnualBudget::getProjectId, budget.getProjectId());
        } else {
            unique.isNull(BizFundAnnualBudget::getProjectId);
        }
        if (annualBudgetMapper.selectCount(unique) > 0) {
            throw new BusinessException(400, budget.getBudgetYear() + "年度已存在预算（项目维度唯一）");
        }
        budget.setStatus("DRAFT");
        annualBudgetMapper.insert(budget);
    }

    /**
     * 分页查询年度预算
     */
    public PageResult<BizFundAnnualBudget> pageAnnualBudget(int page, int size, Integer budgetYear, Long projectId) {
        Page<BizFundAnnualBudget> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizFundAnnualBudget> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(budgetYear != null, BizFundAnnualBudget::getBudgetYear, budgetYear)
                .eq(projectId != null, BizFundAnnualBudget::getProjectId, projectId)
                .orderByDesc(BizFundAnnualBudget::getBudgetYear);
        return PageResult.of(annualBudgetMapper.selectPage(pageParam, wrapper));
    }

    // ==================== 月度计划 ====================

    /**
     * 保存月度计划（项目+年月唯一；存在即更新，不存在即新增）；
     * 携带 details 时级联保存科目明细（V2026_58，先校验后落库，不静默丢弃）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMonthlyPlan(BizFundMonthlyPlan plan) {
        validateMonthlyPlan(plan);
        validatePlanDetails(plan);
        LambdaQueryWrapper<BizFundMonthlyPlan> unique = new LambdaQueryWrapper<BizFundMonthlyPlan>()
                .eq(BizFundMonthlyPlan::getPlanYear, plan.getPlanYear())
                .eq(BizFundMonthlyPlan::getPlanMonth, plan.getPlanMonth());
        if (plan.getProjectId() != null) {
            unique.eq(BizFundMonthlyPlan::getProjectId, plan.getProjectId());
        } else {
            unique.isNull(BizFundMonthlyPlan::getProjectId);
        }
        BizFundMonthlyPlan existing = monthlyPlanMapper.selectOne(unique);
        if (existing != null) {
            existing.setIncomePlan(plan.getIncomePlan());
            existing.setExpensePlan(plan.getExpensePlan());
            existing.setRemark(plan.getRemark());
            monthlyPlanMapper.updateById(existing);
            if (plan.getDetails() != null) {
                replacePlanDetails(existing.getId(), plan.getDetails());
            }
        } else {
            plan.setStatus("DRAFT");
            plan.setActualIncome(BigDecimal.ZERO);
            plan.setActualExpense(BigDecimal.ZERO);
            monthlyPlanMapper.insert(plan);
            if (plan.getDetails() != null) {
                replacePlanDetails(plan.getId(), plan.getDetails());
            }
        }
    }

    /**
     * 查询某月度计划的科目明细（V2026_58）
     */
    public List<BizFundPlanDetail> listPlanDetails(Long planId) {
        if (planId == null) {
            throw new BusinessException(400, "计划ID不能为空");
        }
        return planDetailMapper.selectList(new LambdaQueryWrapper<BizFundPlanDetail>()
                .eq(BizFundPlanDetail::getPlanId, planId)
                .orderByAsc(BizFundPlanDetail::getDirection)
                .orderByAsc(BizFundPlanDetail::getId));
    }

    /**
     * 分页查询月度计划
     */
    public PageResult<BizFundMonthlyPlan> pageMonthlyPlan(int page, int size, Integer planYear, Long projectId) {
        Page<BizFundMonthlyPlan> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizFundMonthlyPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(planYear != null, BizFundMonthlyPlan::getPlanYear, planYear)
                .eq(projectId != null, BizFundMonthlyPlan::getProjectId, projectId)
                .orderByDesc(BizFundMonthlyPlan::getPlanYear)
                .orderByAsc(BizFundMonthlyPlan::getPlanMonth);
        return PageResult.of(monthlyPlanMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 回填月度实际收支（真实单据聚合，非手填）：
     * 实际收款 = 当月回款登记总额（回款日期落月）；
     * 实际付款 = 当月审批通过的付款申请总额（付款日期落月，付款口径与 total_expense 一致）
     * <p>注：实际付款仍按<b>审批口径</b>统计（与 total_expense 同源），不按 pay_status 现金口径，
     * 保证月度计划回填与项目支出账、R7 审计基线一致。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void fillActual(int year, int month, Long projectId) {
        BizFundMonthlyPlan plan = requireMonthlyPlan(year, month, projectId);
        YearMonth ym = YearMonth.of(year, month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        // 实际收款：回款登记（回款日期落月，APPROVED）
        BigDecimal actualIncome = BigDecimal.ZERO;
        List<BizPaymentReceived> received = paymentReceivedMapper.selectList(
                new LambdaQueryWrapper<BizPaymentReceived>()
                        .eq(BizPaymentReceived::getStatus, "APPROVED")
                        .eq(projectId != null, BizPaymentReceived::getProjectId, projectId)
                        .ge(BizPaymentReceived::getReceiveDate, start)
                        .le(BizPaymentReceived::getReceiveDate, end));
        for (BizPaymentReceived r : received) {
            actualIncome = actualIncome.add(r.getReceiveAmount());
        }

        // 实际付款：审批通过的付款申请（付款日期在当月）
        BigDecimal actualExpense = BigDecimal.ZERO;
        List<BizPaymentApply> applied = paymentApplyMapper.selectList(
                new LambdaQueryWrapper<BizPaymentApply>()
                        .eq(BizPaymentApply::getStatus, "APPROVED")
                        .eq(projectId != null, BizPaymentApply::getProjectId, projectId)
                        .isNotNull(BizPaymentApply::getPaymentDate)
                        .ge(BizPaymentApply::getPaymentDate, start)
                        .le(BizPaymentApply::getPaymentDate, end));
        for (BizPaymentApply a : applied) {
            actualExpense = actualExpense.add(a.getPaymentAmount());
        }

        plan.setActualIncome(actualIncome);
        plan.setActualExpense(actualExpense);
        monthlyPlanMapper.updateById(plan);
    }

    // ==================== 滚动预测 ====================

    /**
     * 生成滚动预测快照（未来 N 个月，按月粒度；V2026_56/57 数据源重做，V2026_63 补逾期口径）：
     * <p>预计付款 = 已审批且<b>未支付</b>（pay_status≠PAID，现金口径）的付款申请按付款日期落月聚合，
     * <b>并将「已逾期未付」（payment_date 早于当月月初且仍未支付）全额计入当月</b>；</p>
     * <p>预计收款 = 应收台账（biz_receivable OPEN）按到期日落月聚合（真实应收驱动）；
     * 无台账记录的项目维度仍用月度计划（APPROVED）income_plan 兜底；</p>
     * <p>净缺口 = 付款 - 收款；风险等级按缺口与付款规模判定（逾期计入后自然修正）。</p>
     * <p><b>为何必须计入逾期</b>：原口径只统计未来窗口，已过计划付款日却仍未支付的单据
     * 会从预测中完全消失。线上实测（2026-09-23）：16 条 APPROVED+UNPAID 合计 5850 万，
     * payment_date 全在过去，导致当月 net_gap 显示 -260 万（LOW），而真实待付缺口为 5590 万（应 HIGH）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public List<BizFundRollingForecast> generateRollingForecast(Long projectId, int months) {
        if (months < 1 || months > 12) {
            throw new BusinessException(400, "预测月数需在1-12之间");
        }
        LocalDate today = LocalDate.now();
        YearMonth current = YearMonth.from(today);

        // 收款侧数据源：应收台账 OPEN 余额按到期日落月（一次性加载，避免逐月查库）
        Map<String, BigDecimal> receivableByMonth = sumOpenReceivableByDueMonth(projectId);

        // 付款侧数据源：一次性加载「已审批 + 未支付 + 付款日不晚于预测窗口末」的全部单据，
        // 再在内存按付款日落月（原实现每月查一次库，months 次查询）。
        // 注意下界故意不设：payment_date 已过的未付款属「已逾期未付」，必须计入当月（V2026_63）。
        LocalDate windowEnd = current.plusMonths(months - 1L).atEndOfMonth();
        List<BizPaymentApply> pendingApplies = paymentApplyMapper.selectList(
                new LambdaQueryWrapper<BizPaymentApply>()
                        .eq(BizPaymentApply::getStatus, "APPROVED")
                        .ne(BizPaymentApply::getPayStatus, BizPaymentApply.PAY_STATUS_PAID)
                        .eq(projectId != null, BizPaymentApply::getProjectId, projectId)
                        .isNotNull(BizPaymentApply::getPaymentDate)
                        .le(BizPaymentApply::getPaymentDate, windowEnd));

        Map<String, BigDecimal> paymentsByMonth = new HashMap<>();
        BigDecimal overdueUnpaid = BigDecimal.ZERO;
        LocalDate currentMonthStart = current.atDay(1);
        // 已勾稽合计（按付款申请分组）：部分支付场景下只计「剩余未付额」，
        // 否则 100 万申请已付 60 万仍按 100 万计入，重复夸大 40 万资金压力（V2026_64）
        Map<Long, BigDecimal> matchedByApply = bankFlowMapper.matchedAmountByPaymentApply();
        for (BizPaymentApply a : pendingApplies) {
            LocalDate payDate = a.getPaymentDate();
            if (payDate == null) {
                // 无计划付款日的已审批单据无法落月，不计入预测（源头由付款日必填校验保证）
                continue;
            }
            BigDecimal amount = remainingUnpaid(a, matchedByApply);
            if (amount.signum() <= 0) {
                // 已足额勾稽（或异常负值）：无剩余付款压力，不计入预测
                continue;
            }
            if (payDate.isBefore(currentMonthStart)) {
                // 已逾期未付：归集到当月（不向后续月份摊开，避免同一笔重复计入）
                overdueUnpaid = overdueUnpaid.add(amount);
            } else {
                paymentsByMonth.merge(YearMonth.from(payDate).format(MONTH_FMT), amount, BigDecimal::add);
            }
        }
        if (overdueUnpaid.signum() > 0) {
            log.warn("滚动预测发现已逾期未付款项，已计入当月预计付款: projectId={}, overdueUnpaid={}",
                    projectId, overdueUnpaid);
        }

        List<BizFundRollingForecast> result = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth ym = current.plusMonths(i);
            String monthKey = ym.format(MONTH_FMT);

            // 预计付款 = 当月计划内未付 + （仅当月）已逾期未付——现金口径，已付部分不再计入未来流出
            BigDecimal monthOverdue = (i == 0) ? overdueUnpaid : BigDecimal.ZERO;
            BigDecimal payments = paymentsByMonth.getOrDefault(monthKey, BigDecimal.ZERO).add(monthOverdue);

            // 预计收款：应收台账优先；台账无任何记录时回退月度计划 income_plan（兜底，不重复叠加）
            BigDecimal receipts = receivableByMonth.getOrDefault(monthKey, BigDecimal.ZERO);
            if (receivableByMonth.isEmpty()) {
                BizFundMonthlyPlan plan = monthlyPlanMapper.selectOne(
                        new LambdaQueryWrapper<BizFundMonthlyPlan>()
                                .eq(BizFundMonthlyPlan::getPlanYear, ym.getYear())
                                .eq(BizFundMonthlyPlan::getPlanMonth, ym.getMonthValue())
                                .eq(BizFundMonthlyPlan::getStatus, "APPROVED")
                                .eq(projectId != null, BizFundMonthlyPlan::getProjectId, projectId)
                                .isNull(projectId == null, BizFundMonthlyPlan::getProjectId));
                if (plan != null && plan.getIncomePlan() != null) {
                    receipts = plan.getIncomePlan();
                }
            }

            BigDecimal netGap = payments.subtract(receipts);
            BizFundRollingForecast forecast = new BizFundRollingForecast();
            forecast.setProjectId(projectId);
            forecast.setForecastMonth(monthKey);
            forecast.setExpectedReceipts(receipts);
            forecast.setExpectedPayments(payments);
            // 构成项（已包含在 expectedPayments 内，不可相加）：供前端区分“计划内 vs 逾期堆积”
            forecast.setOverdueUnpaid(monthOverdue);
            forecast.setNetGap(netGap);
            forecast.setRiskLevel(evaluateRisk(payments, netGap));
            forecast.setSnapshotDate(today);
            result.add(forecast);

            // 覆盖式写入快照（同月同项目旧快照作废后插新）
            rollingForecastMapper.delete(new LambdaQueryWrapper<BizFundRollingForecast>()
                    .eq(BizFundRollingForecast::getForecastMonth, monthKey)
                    .eq(projectId != null, BizFundRollingForecast::getProjectId, projectId)
                    .isNull(projectId == null, BizFundRollingForecast::getProjectId));
            rollingForecastMapper.insert(forecast);
        }
        return result;
    }

    /**
     * 付款申请关联计划校验（先计划后支付）：
     * 付款日期所在月份存在 APPROVED 月度计划且累计已审批付款 ≤ 计划付款额时放行
     */
    public void validatePaymentAgainstPlan(Long projectId, LocalDate paymentDate, BigDecimal amount) {
        if (paymentDate == null) {
            return; // 未定付款日期的申请不做计划校验（审批前补填日期时再校验）
        }
        YearMonth ym = YearMonth.from(paymentDate);
        BizFundMonthlyPlan plan = monthlyPlanMapper.selectOne(
                new LambdaQueryWrapper<BizFundMonthlyPlan>()
                        .eq(BizFundMonthlyPlan::getPlanYear, ym.getYear())
                        .eq(BizFundMonthlyPlan::getPlanMonth, ym.getMonthValue())
                        .eq(BizFundMonthlyPlan::getStatus, "APPROVED")
                        .eq(BizFundMonthlyPlan::getProjectId, projectId));
        if (plan == null || plan.getExpensePlan() == null) {
            return; // 无计划月份不硬拦截（WARN 模式），由预算硬控制兜底
        }
        // 已有该月已审批付款总额
        BigDecimal approved = BigDecimal.ZERO;
        List<BizPaymentApply> applies = paymentApplyMapper.selectList(
                new LambdaQueryWrapper<BizPaymentApply>()
                        .eq(BizPaymentApply::getProjectId, projectId)
                        .eq(BizPaymentApply::getStatus, "APPROVED")
                        .isNotNull(BizPaymentApply::getPaymentDate)
                        .ge(BizPaymentApply::getPaymentDate, ym.atDay(1))
                        .le(BizPaymentApply::getPaymentDate, ym.atEndOfMonth()));
        for (BizPaymentApply a : applies) {
            approved = approved.add(a.getPaymentAmount());
        }
        if (approved.add(amount).compareTo(plan.getExpensePlan()) > 0) {
            throw new BusinessException(400, "超出当月资金计划：月度计划付款 "
                    + plan.getExpensePlan() + "，已审批 " + approved + "，本次申请 " + amount);
        }
    }

    /**
     * 分页查询滚动预测快照（按月倒序）
     */
    public PageResult<BizFundRollingForecast> pageRollingForecast(int page, int size, Long projectId) {
        Page<BizFundRollingForecast> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<BizFundRollingForecast> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(projectId != null, BizFundRollingForecast::getProjectId, projectId)
                .isNull(projectId == null, BizFundRollingForecast::getProjectId)
                .orderByDesc(BizFundRollingForecast::getForecastMonth);
        return PageResult.of(rollingForecastMapper.selectPage(pageParam, wrapper));
    }

    /**
     * 待支付大额支出 TOP（驾驶舱 V1 §9.3）：已审批且未支付的付款申请，
     * 付款日期在 今天+days 之前（<b>含已逾期部分</b>），按支出科目（paymentCategory）聚合降序。
     * <p><b>为何含逾期</b>（V2026_63）：原口径下界为 today，已逾期的待付款被排除——
     * 线上实测该接口返回空数组，而库内实际有 16 条/5850 万待付款，老板看到的“无大额支出”是错的。
     * 返回中另给 overdueAmount 字段如实区分“其中已逾期”金额，不混为一个数。</p>
     * <p>无科目编码的单据归入 "UNCATEGORIZED"（如实呈现分类缺失，不隐藏）。</p>
     *
     * @param projectId 项目ID（可选，空则公司整体）
     * @param days      未来天数（1-365）
     * @return [{categoryCode, categoryName, amount, count, overdueAmount}]，按金额降序
     */
    public List<Map<String, Object>> futureExpenseTop(Long projectId, int days) {
        if (days < 1 || days > 365) {
            throw new BusinessException(400, "预测天数需在1-365之间");
        }
        LocalDate today = LocalDate.now();
        List<BizPaymentApply> applies = paymentApplyMapper.selectList(
                new LambdaQueryWrapper<BizPaymentApply>()
                        .eq(BizPaymentApply::getStatus, "APPROVED")
                        .ne(BizPaymentApply::getPayStatus, BizPaymentApply.PAY_STATUS_PAID)
                        .eq(projectId != null, BizPaymentApply::getProjectId, projectId)
                        .isNotNull(BizPaymentApply::getPaymentDate)
                        // 不再限定下界：payment_date 已过的未付款仍属待支付义务，必须计入
                        .le(BizPaymentApply::getPaymentDate, today.plusDays(days)));
        Map<String, BigDecimal> byCategory = new HashMap<>();
        Map<String, Integer> countByCategory = new HashMap<>();
        Map<String, BigDecimal> overdueByCategory = new HashMap<>();
        // 部分支付只计剩余未付额（与滚动预测同口径，V2026_64）
        Map<Long, BigDecimal> matchedByApply = bankFlowMapper.matchedAmountByPaymentApply();
        for (BizPaymentApply a : applies) {
            BigDecimal amount = remainingUnpaid(a, matchedByApply);
            if (amount.signum() <= 0) {
                continue;
            }
            String code = a.getPaymentCategory() != null && !a.getPaymentCategory().isBlank()
                    ? a.getPaymentCategory() : "UNCATEGORIZED";
            byCategory.merge(code, amount, BigDecimal::add);
            countByCategory.merge(code, 1, Integer::sum);
            LocalDate payDate = a.getPaymentDate();
            if (payDate != null && payDate.isBefore(today)) {
                overdueByCategory.merge(code, amount, BigDecimal::add);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : byCategory.entrySet()) {
            Map<String, Object> row = new HashMap<>();
            row.put("categoryCode", entry.getKey());
            row.put("categoryName", resolveCategoryName(entry.getKey()));
            row.put("amount", entry.getValue());
            row.put("count", countByCategory.getOrDefault(entry.getKey(), 0));
            // 构成项（已包含在 amount 内）：其中已逾期的金额
            row.put("overdueAmount", overdueByCategory.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            result.add(row);
        }
        result.sort((x, y) -> ((BigDecimal) y.get("amount")).compareTo((BigDecimal) x.get("amount")));
        return result;
    }

    /**
     * 科目编码→名称（UNCATEGORIZED 固定文案；科目已删除/不存在时回退编码本身，不中断聚合）
     */
    private String resolveCategoryName(String code) {
        if ("UNCATEGORIZED".equals(code)) {
            return "未分类";
        }
        try {
            var category = fundCategoryService.getByCode(code, "EXPENSE");
            return category != null && category.getName() != null ? category.getName() : code;
        } catch (BusinessException e) {
            return code;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 单笔付款申请的「剩余未付额」= payment_amount − 已勾稽合计（下限 0）。
     * <p>资金压力只算尚未流出的部分：部分支付（PARTIAL_PAID）时已付部分不得重复计入。
     * 已勾稽合计取自 {@code BizBankFlowMapper.matchedAmountByPaymentApply()}（与
     * BankFlowService.sumMatchedAmount 同口径，并供 DashboardService 复用）。</p>
     */
    private BigDecimal remainingUnpaid(BizPaymentApply apply, Map<Long, BigDecimal> matchedByApply) {
        BigDecimal amount = apply.getPaymentAmount() != null ? apply.getPaymentAmount() : BigDecimal.ZERO;
        // null 保护：无勾稽记录时 Mapper 返回空 Map，但防御异常/测试桩返回 null
        BigDecimal matched = matchedByApply == null ? BigDecimal.ZERO
                : matchedByApply.getOrDefault(apply.getId(), BigDecimal.ZERO);
        return amount.subtract(matched).max(BigDecimal.ZERO);
    }

    /**
     * 校验科目明细（V2026_58）：科目有效且方向匹配、金额为正、同方向科目不重复、
     * 各方向合计与主表总额一致（容差 0.01，不一致抛异常不静默）。
     * details 为 null 时跳过（明细可选，存量计划向后兼容）；空列表视为清空明细。
     */
    private void validatePlanDetails(BizFundMonthlyPlan plan) {
        List<BizFundPlanDetail> details = plan.getDetails();
        if (details == null) {
            return;
        }
        Map<String, BigDecimal> sums = new HashMap<>();
        Map<String, String> seen = new HashMap<>();
        for (BizFundPlanDetail d : details) {
            if (d.getDirection() == null
                    || !(BizFundPlanDetail.DIRECTION_INCOME.equals(d.getDirection())
                    || BizFundPlanDetail.DIRECTION_EXPENSE.equals(d.getDirection()))) {
                throw new BusinessException(400, "明细方向不合法，需为 INCOME 或 EXPENSE");
            }
            if (d.getAmount() == null || d.getAmount().signum() <= 0) {
                throw new BusinessException(400, "明细金额必须大于0，科目：" + d.getCategoryCode());
            }
            // 科目有效性 + 方向匹配（getByCode 内部校验不存在/方向不符抛异常）
            String expectDirection = BizFundPlanDetail.DIRECTION_INCOME.equals(d.getDirection())
                    ? "INCOME" : "EXPENSE";
            fundCategoryService.getByCode(d.getCategoryCode(), expectDirection);
            String dupKey = d.getDirection() + ":" + d.getCategoryCode();
            if (seen.put(dupKey, d.getCategoryCode()) != null) {
                throw new BusinessException(400, "同方向科目重复：" + d.getCategoryCode());
            }
            sums.merge(d.getDirection(), d.getAmount(), BigDecimal::add);
        }
        BigDecimal tolerance = new BigDecimal("0.01");
        assertSumMatches(sums.get(BizFundPlanDetail.DIRECTION_INCOME), plan.getIncomePlan(), tolerance, "收款");
        assertSumMatches(sums.get(BizFundPlanDetail.DIRECTION_EXPENSE), plan.getExpensePlan(), tolerance, "付款");
    }

    private void assertSumMatches(BigDecimal detailSum, BigDecimal planTotal, BigDecimal tolerance, String label) {
        BigDecimal sum = detailSum != null ? detailSum : BigDecimal.ZERO;
        BigDecimal total = planTotal != null ? planTotal : BigDecimal.ZERO;
        // 该方向无任何明细时不校验（允许仅拆单侧，如只拆付款不拆收款）
        if (detailSum == null) {
            return;
        }
        if (sum.subtract(total).abs().compareTo(tolerance) > 0) {
            throw new BusinessException(400, label + "明细合计 " + sum + " 与计划总额 " + total + " 不一致");
        }
    }

    /**
     * 替换式保存明细（先逻辑删除旧明细再插入，与滚动快照覆盖式写入惯例一致）
     */
    private void replacePlanDetails(Long planId, List<BizFundPlanDetail> details) {
        planDetailMapper.delete(new LambdaQueryWrapper<BizFundPlanDetail>()
                .eq(BizFundPlanDetail::getPlanId, planId));
        for (BizFundPlanDetail d : details) {
            d.setId(null);
            d.setPlanId(planId);
            planDetailMapper.insert(d);
        }
    }

    /**
     * 应收台账 OPEN 余额按到期日落月汇总（滚动预测收款侧数据源，V2026_57）
     */
    private Map<String, BigDecimal> sumOpenReceivableByDueMonth(Long projectId) {
        List<BizReceivable> opens = receivableMapper.selectList(new LambdaQueryWrapper<BizReceivable>()
                .eq(projectId != null, BizReceivable::getProjectId, projectId)
                .eq(BizReceivable::getStatus, BizReceivable.STATUS_OPEN));
        Map<String, BigDecimal> byMonth = new HashMap<>();
        for (BizReceivable r : opens) {
            if (r.getDueDate() == null) {
                continue;
            }
            BigDecimal balance = (r.getReceivableAmount() != null ? r.getReceivableAmount() : BigDecimal.ZERO)
                    .subtract(r.getWrittenOffAmount() != null ? r.getWrittenOffAmount() : BigDecimal.ZERO);
            if (balance.signum() <= 0) {
                continue;
            }
            byMonth.merge(YearMonth.from(r.getDueDate()).format(MONTH_FMT), balance, BigDecimal::add);
        }
        return byMonth;
    }

    private void validateMonthlyPlan(BizFundMonthlyPlan plan) {
        if (plan.getPlanYear() == null || plan.getPlanYear() < 2000 || plan.getPlanYear() > 2100) {
            throw new BusinessException(400, "计划年度不合法");
        }
        if (plan.getPlanMonth() == null || plan.getPlanMonth() < 1 || plan.getPlanMonth() > 12) {
            throw new BusinessException(400, "计划月份不合法（1-12）");
        }
        if (plan.getIncomePlan() == null || plan.getIncomePlan().signum() < 0
                || plan.getExpensePlan() == null || plan.getExpensePlan().signum() < 0) {
            throw new BusinessException(400, "计划金额不能为负");
        }
    }

    private BizFundMonthlyPlan requireMonthlyPlan(int year, int month, Long projectId) {
        LambdaQueryWrapper<BizFundMonthlyPlan> wrapper = new LambdaQueryWrapper<BizFundMonthlyPlan>()
                .eq(BizFundMonthlyPlan::getPlanYear, year)
                .eq(BizFundMonthlyPlan::getPlanMonth, month);
        if (projectId != null) {
            wrapper.eq(BizFundMonthlyPlan::getProjectId, projectId);
        } else {
            wrapper.isNull(BizFundMonthlyPlan::getProjectId);
        }
        BizFundMonthlyPlan plan = monthlyPlanMapper.selectOne(wrapper);
        if (plan == null) {
            throw new BusinessException(404, year + "-" + month + " 月度计划不存在，请先编制");
        }
        return plan;
    }

    /**
     * 风险等级判定：
     * 净缺口 > 0 且 收款计划覆盖 < 50% → HIGH；
     * 净缺口 > 0 → MEDIUM；否则 LOW
     */
    private String evaluateRisk(BigDecimal payments, BigDecimal netGap) {
        if (netGap.signum() <= 0) {
            return "LOW";
        }
        if (payments.signum() > 0
                && (payments.subtract(netGap)).divide(payments, 4, java.math.RoundingMode.HALF_UP)
                        .compareTo(new BigDecimal("0.50")) < 0) {
            return "HIGH";
        }
        return "MEDIUM";
    }
}
