package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.common.result.PageResult;
import com.zwinsight.finance.domain.BizFundAnnualBudget;
import com.zwinsight.finance.domain.BizFundMonthlyPlan;
import com.zwinsight.finance.domain.BizFundRollingForecast;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.mapper.BizFundAnnualBudgetMapper;
import com.zwinsight.finance.mapper.BizFundMonthlyPlanMapper;
import com.zwinsight.finance.mapper.BizFundRollingForecastMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 资金计划三层联动服务（年度预算 + 月度计划 + 滚动预测）
 * <p>对标广联达 PMCore「先计划后支付」：
 * 1) 年度预算：年初编制，项目或公司维度；
 * 2) 月度计划：预计收支 + 月末实际回填（actual 数据源为真实单据聚合，非手填）；
 * 3) 滚动预测：预计付款 = 已审批未付付款申请（按付款日期落月聚合），
 *    预计收款 = 月度计划（APPROVED），净缺口 = 付款 - 收款。</p>
 */
@Service
@RequiredArgsConstructor
public class FundPlanService {

    private final BizFundAnnualBudgetMapper annualBudgetMapper;
    private final BizFundMonthlyPlanMapper monthlyPlanMapper;
    private final BizFundRollingForecastMapper rollingForecastMapper;
    private final BizPaymentApplyMapper paymentApplyMapper;
    private final BizPaymentReceivedMapper paymentReceivedMapper;

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
     * 保存月度计划（项目+年月唯一；存在即更新，不存在即新增）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveMonthlyPlan(BizFundMonthlyPlan plan) {
        validateMonthlyPlan(plan);
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
        } else {
            plan.setStatus("DRAFT");
            plan.setActualIncome(BigDecimal.ZERO);
            plan.setActualExpense(BigDecimal.ZERO);
            monthlyPlanMapper.insert(plan);
        }
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
     * 生成滚动预测快照（未来 N 个月，按月粒度）：
     * 预计付款 = 已审批未付付款申请按付款日期落月聚合；
     * 预计收款 = 月度计划 APPROVED 的 income_plan（未回填实际部分）；
     * 净缺口 = 付款 - 收款；风险等级按缺口与付款规模判定。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<BizFundRollingForecast> generateRollingForecast(Long projectId, int months) {
        if (months < 1 || months > 12) {
            throw new BusinessException(400, "预测月数需在1-12之间");
        }
        LocalDate today = LocalDate.now();
        YearMonth current = YearMonth.from(today);

        List<BizFundRollingForecast> result = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth ym = current.plusMonths(i);
            String monthKey = ym.format(MONTH_FMT);

            // 预计付款：已审批付款申请（当月到期）
            BigDecimal payments = BigDecimal.ZERO;
            List<BizPaymentApply> applies = paymentApplyMapper.selectList(
                    new LambdaQueryWrapper<BizPaymentApply>()
                            .eq(BizPaymentApply::getStatus, "APPROVED")
                            .eq(projectId != null, BizPaymentApply::getProjectId, projectId)
                            .isNotNull(BizPaymentApply::getPaymentDate)
                            .ge(BizPaymentApply::getPaymentDate, ym.atDay(1))
                            .le(BizPaymentApply::getPaymentDate, ym.atEndOfMonth()));
            for (BizPaymentApply a : applies) {
                payments = payments.add(a.getPaymentAmount());
            }

            // 预计收款：月度计划（APPROVED）的收款计划
            BigDecimal receipts = BigDecimal.ZERO;
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

            BigDecimal netGap = payments.subtract(receipts);
            BizFundRollingForecast forecast = new BizFundRollingForecast();
            forecast.setProjectId(projectId);
            forecast.setForecastMonth(monthKey);
            forecast.setExpectedReceipts(receipts);
            forecast.setExpectedPayments(payments);
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

    // ==================== 私有方法 ====================

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
