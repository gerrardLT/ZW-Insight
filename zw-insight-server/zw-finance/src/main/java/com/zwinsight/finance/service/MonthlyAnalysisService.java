package com.zwinsight.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizMonthlyOperationAnalysis;
import com.zwinsight.finance.mapper.BizMonthlyOperationAnalysisMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 月度经营分析表服务（V2026_67，资金流转流程 §9「每月必须形成一个项目经营表」）
 * <p>产出 10 类费用 × 6 列矩阵：预算 / 本月发生 / 累计发生 / 累计支付 / 应付未付 / 预计最终。
 * 由 {@code MonthlyAnalysisTask} 每月 1 日 04:00 生成上月（错开 03:30 的利润快照任务），
 * 也可经 Controller 手工生成；同项目同月重跑为<b>覆盖更新</b>（按唯一键 upsert，幂等）。</p>
 *
 * <p><b>口径纪律（三处如实为空，不伪造）</b>：</p>
 * <ol>
 *   <li><b>本月发生</b> = 本月末累计发生 − 上月末累计发生（取本表上月行）。首次生成无上月行时
 *       为 null 且 {@code occurredBasis=NO_BASELINE}。<i>为何不用单据按月聚合</i>：CBS 流水表
 *       {@code biz_cost_account_txn} 当前 0 行；四类结算单中仅 purchase/final 有 settlement_date
 *       且 purchase 的该列全为 NULL；labor/subcontract 结算单无该列；用 created_at 冒充业务日期
 *       会把补录单据落错月份。详见迁移脚本头注释。</li>
 *   <li><b>累计支付 / 应付未付</b>：仅直接费四类（人工/材料/机械/分包）有权威来源——对应合同的
 *       {@code cumulative_paid}（付款审批回写口径，落 {@code paidBasis=APPROVAL_WRITEBACK}）。
 *       间接费六类走报销与其他费用付款，付款申请只到 OTHER_EXPENSE 粒度、无法按十类细分，
 *       故为 null 且 {@code paidBasis=NO_PAYMENT_SOURCE}。</li>
 *   <li><b>未归类行</b>：CBS 账户子类名不含任何关键词时单列 UNCLASSIFIED（仅在有此类账户时写行），
 *       保证「各类合计 = CBS actual 总额」可对账；不静默并入管理费等任意类。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MonthlyAnalysisService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /** §9 十类的中文名与固定行序（LinkedHashMap 保证表格行序稳定） */
    private static final Map<String, String> CATEGORY_NAMES = buildCategoryNames();

    /** 直接费四类：累计支付有合同 cumulative_paid 作为权威来源 */
    private static final Set<String> DIRECT_CATEGORIES =
            Set.of(BizMonthlyOperationAnalysis.CAT_LABOR, BizMonthlyOperationAnalysis.CAT_MATERIAL,
                    BizMonthlyOperationAnalysis.CAT_MACHINE, BizMonthlyOperationAnalysis.CAT_SUBCONTRACT);

    private final BizMonthlyOperationAnalysisMapper analysisMapper;

    private static Map<String, String> buildCategoryNames() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(BizMonthlyOperationAnalysis.CAT_LABOR, "人工");
        m.put(BizMonthlyOperationAnalysis.CAT_MATERIAL, "材料");
        m.put(BizMonthlyOperationAnalysis.CAT_MACHINE, "机械");
        m.put(BizMonthlyOperationAnalysis.CAT_SUBCONTRACT, "分包");
        m.put(BizMonthlyOperationAnalysis.CAT_MEASURE, "措施");
        m.put(BizMonthlyOperationAnalysis.CAT_ADMIN, "管理");
        m.put(BizMonthlyOperationAnalysis.CAT_ENTERTAIN, "招待");
        m.put(BizMonthlyOperationAnalysis.CAT_TRAVEL_VEHICLE, "差旅车辆");
        m.put(BizMonthlyOperationAnalysis.CAT_PROFESSIONAL, "专业服务");
        m.put(BizMonthlyOperationAnalysis.CAT_TAX, "财税");
        return m;
    }

    /**
     * 生成（或覆盖更新）某项目某月的经营分析表。
     *
     * @param projectId 项目ID（必填，本表按项目粒度）
     * @param month     分析月份 yyyy-MM（不得晚于当月：未来月份无发生额可言）
     * @return {projectId, month, inserted, updated, rows, occurredNullCategories, skippedUnclassified}
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generate(Long projectId, String month) {
        if (projectId == null) {
            throw new BusinessException(400, "项目ID不能为空");
        }
        YearMonth ym = parseMonth(month);
        if (ym.isAfter(YearMonth.now())) {
            throw new BusinessException(400, "不能生成未来月份的经营分析表：" + month);
        }

        // 1. CBS 按十类聚合（预算 / 累计发生 / 预计最终 / 账户数）
        Map<String, Map<String, Object>> cbsByCategory = new HashMap<>();
        List<Map<String, Object>> cbsRows = analysisMapper.sumCbsByCategory(projectId);
        if (cbsRows != null) {
            for (Map<String, Object> row : cbsRows) {
                cbsByCategory.put(String.valueOf(row.get("categoryCode")), row);
            }
        }

        // 2. 直接费四类的累计支付（合同 cumulative_paid，审批口径）
        Map<String, BigDecimal> paidByCategory = new HashMap<>();
        List<Map<String, Object>> paidRows = analysisMapper.sumContractPaidByCategory(projectId);
        if (paidRows != null) {
            for (Map<String, Object> row : paidRows) {
                paidByCategory.put(String.valueOf(row.get("categoryCode")), toDecimal(row.get("cumulativePaid")));
            }
        }

        // 3. 上月行的累计发生（本月发生 = 本月累计 − 上月累计）
        String lastMonth = ym.minusMonths(1).format(MONTH_FMT);
        Map<String, BigDecimal> lastOccurred = new HashMap<>();
        List<Map<String, Object>> lastRows = analysisMapper.selectLastMonthOccurred(projectId, lastMonth);
        if (lastRows != null) {
            for (Map<String, Object> row : lastRows) {
                lastOccurred.put(String.valueOf(row.get("categoryCode")), toDecimal(row.get("cumulativeOccurred")));
            }
        }

        // 4. 既有行索引（覆盖更新而非重复插入，唯一键 tenant+project+month+category）
        Map<String, BizMonthlyOperationAnalysis> existing = new HashMap<>();
        List<BizMonthlyOperationAnalysis> existingRows = analysisMapper.selectList(
                new LambdaQueryWrapper<BizMonthlyOperationAnalysis>()
                        .eq(BizMonthlyOperationAnalysis::getProjectId, projectId)
                        .eq(BizMonthlyOperationAnalysis::getAnalysisMonth, month));
        if (existingRows != null) {
            for (BizMonthlyOperationAnalysis r : existingRows) {
                existing.put(r.getCategoryCode(), r);
            }
        }

        int inserted = 0;
        int updated = 0;
        List<String> occurredNullCategories = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 5. 十类恒定生成（无账户的类金额为 0、accountCount=0，标 NO_DATA_SOURCE）
        for (Map.Entry<String, String> entry : CATEGORY_NAMES.entrySet()) {
            boolean wrote = upsertRow(projectId, month, entry.getKey(), entry.getValue(),
                    cbsByCategory.get(entry.getKey()), paidByCategory, lastOccurred, existing, now, true);
            if (wrote) {
                if (existing.containsKey(entry.getKey())) {
                    updated++;
                } else {
                    inserted++;
                }
            }
            if (cbsByCategory.get(entry.getKey()) == null) {
                occurredNullCategories.add(entry.getValue());
            }
        }

        // 6. 未归类行：仅在确有未归类账户时写入（否则表格多一行恒 0 的噪音）
        Map<String, Object> unclassified = cbsByCategory.get(BizMonthlyOperationAnalysis.CAT_UNCLASSIFIED);
        boolean skippedUnclassified = true;
        if (unclassified != null && toInt(unclassified.get("accountCount")) > 0) {
            upsertRow(projectId, month, BizMonthlyOperationAnalysis.CAT_UNCLASSIFIED, "未归类",
                    unclassified, paidByCategory, lastOccurred, existing, now, false);
            skippedUnclassified = false;
            if (existing.containsKey(BizMonthlyOperationAnalysis.CAT_UNCLASSIFIED)) {
                updated++;
            } else {
                inserted++;
            }
        }

        Map<String, Object> report = new HashMap<>();
        report.put("projectId", projectId);
        report.put("month", month);
        report.put("inserted", inserted);
        report.put("updated", updated);
        report.put("rows", inserted + updated);
        report.put("occurredNullCategories", occurredNullCategories);
        report.put("skippedUnclassified", skippedUnclassified);
        log.info("月度经营分析表生成完成：projectId={}, month={}, 新增 {} 行/更新 {} 行，未归类行{}",
                projectId, month, inserted, updated, skippedUnclassified ? "跳过（无未归类账户）" : "已写入");
        return report;
    }

    /**
     * 写入（或更新）一行。
     *
     * @param alwaysWrite 十类为 true（无账户也写 0 行，保证表格结构完整）；未归类为 false（调用方已判空）
     * @return 是否实际写库
     */
    private boolean upsertRow(Long projectId, String month, String code, String name,
                              Map<String, Object> cbs, Map<String, BigDecimal> paidByCategory,
                              Map<String, BigDecimal> lastOccurred,
                              Map<String, BizMonthlyOperationAnalysis> existing,
                              LocalDateTime now, boolean alwaysWrite) {
        int accountCount = cbs == null ? 0 : toInt(cbs.get("accountCount"));
        if (!alwaysWrite && accountCount == 0) {
            return false;
        }
        BigDecimal budget = cbs == null ? BigDecimal.ZERO : toDecimal(cbs.get("budgetAmount"));
        BigDecimal occurred = cbs == null ? BigDecimal.ZERO : toDecimal(cbs.get("cumulativeOccurred"));
        BigDecimal forecast = cbs == null ? BigDecimal.ZERO : toDecimal(cbs.get("forecastFinal"));

        // 累计支付：仅直接费四类有权威来源；其余为 null（不拿 0 冒充「已付清」）
        boolean hasPaidSource = DIRECT_CATEGORIES.contains(code);
        BigDecimal paid = hasPaidSource ? paidByCategory.getOrDefault(code, BigDecimal.ZERO) : null;
        BigDecimal payable = paid == null ? null : occurred.subtract(paid);

        // 本月发生：需上月行做基期；无 CBS 账户的类标 NO_DATA_SOURCE
        BigDecimal currentMonth;
        String occurredBasis;
        if (accountCount == 0) {
            currentMonth = null;
            occurredBasis = BizMonthlyOperationAnalysis.OCCURRED_NO_DATA_SOURCE;
        } else if (lastOccurred.containsKey(code)) {
            currentMonth = occurred.subtract(lastOccurred.get(code));
            occurredBasis = BizMonthlyOperationAnalysis.OCCURRED_VS_LAST_MONTH;
        } else {
            currentMonth = null;
            occurredBasis = BizMonthlyOperationAnalysis.OCCURRED_NO_BASELINE;
        }

        BizMonthlyOperationAnalysis row = existing.get(code);
        boolean isNew = row == null;
        if (isNew) {
            row = new BizMonthlyOperationAnalysis();
            row.setProjectId(projectId);
            row.setAnalysisMonth(month);
            row.setCategoryCode(code);
        }
        row.setCategoryName(name);
        row.setBudgetAmount(budget);
        row.setCurrentMonthOccurred(currentMonth);
        row.setCumulativeOccurred(occurred);
        row.setCumulativePaid(paid);
        row.setPayableOutstanding(payable);
        row.setForecastFinal(forecast);
        row.setOccurredBasis(occurredBasis);
        row.setPaidBasis(hasPaidSource
                ? BizMonthlyOperationAnalysis.PAID_APPROVAL_WRITEBACK
                : BizMonthlyOperationAnalysis.PAID_NO_PAYMENT_SOURCE);
        row.setAccountCount(accountCount);
        row.setGeneratedAt(now);
        if (isNew) {
            analysisMapper.insert(row);
        } else {
            analysisMapper.updateById(row);
        }
        return true;
    }

    /**
     * 生成全部已建 CBS 账户项目的某月分析表（定时任务入口）。
     * <p>单项目失败不中断其余项目，但失败必须计入返回值并 WARN，<b>不静默跳过</b>。</p>
     */
    public Map<String, Object> generateAll(String month) {
        parseMonth(month);
        List<Long> projectIds = analysisMapper.selectProjectIdsWithAccounts();
        int ok = 0;
        List<String> failed = new ArrayList<>();
        if (projectIds != null) {
            for (Long projectId : projectIds) {
                try {
                    generate(projectId, month);
                    ok++;
                } catch (RuntimeException e) {
                    failed.add(projectId + ":" + e.getMessage());
                    log.error("月度经营分析表生成失败，projectId={}, month={}", projectId, month, e);
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("month", month);
        result.put("projectCount", projectIds == null ? 0 : projectIds.size());
        result.put("successCount", ok);
        result.put("failedProjects", failed);
        if (projectIds == null || projectIds.isEmpty()) {
            log.warn("无已建 CBS 成本账户的项目，月度经营分析表本轮无数据可生成（month={}）", month);
        }
        return result;
    }

    /**
     * 查询某项目某月的分析表（矩阵 + 合计 + 口径说明）。
     *
     * @return {projectId, month, rows, totals, occurredNullCategories, paidNullCategories,
     *          generatedAt, notes}
     */
    public Map<String, Object> query(Long projectId, String month) {
        if (projectId == null) {
            throw new BusinessException(400, "项目ID不能为空");
        }
        parseMonth(month);
        List<BizMonthlyOperationAnalysis> rows = analysisMapper.selectList(
                new LambdaQueryWrapper<BizMonthlyOperationAnalysis>()
                        .eq(BizMonthlyOperationAnalysis::getProjectId, projectId)
                        .eq(BizMonthlyOperationAnalysis::getAnalysisMonth, month));

        // 按 §9 固定行序排列（未归类殿后），未生成的类不出现在结果里（前端据空态提示先生成）
        List<String> order = new ArrayList<>(CATEGORY_NAMES.keySet());
        order.add(BizMonthlyOperationAnalysis.CAT_UNCLASSIFIED);
        Map<String, BizMonthlyOperationAnalysis> byCode = new HashMap<>();
        if (rows != null) {
            for (BizMonthlyOperationAnalysis r : rows) {
                byCode.put(r.getCategoryCode(), r);
            }
        }
        List<BizMonthlyOperationAnalysis> ordered = new ArrayList<>();
        for (String code : order) {
            BizMonthlyOperationAnalysis r = byCode.get(code);
            if (r != null) {
                ordered.add(r);
            }
        }

        // 合计：null 项不参与求和，但必须告知哪些类为空（否则合计会被误读为全量）
        BigDecimal budgetTotal = BigDecimal.ZERO;
        BigDecimal occurredTotal = BigDecimal.ZERO;
        BigDecimal forecastTotal = BigDecimal.ZERO;
        BigDecimal currentMonthTotal = BigDecimal.ZERO;
        BigDecimal paidTotal = BigDecimal.ZERO;
        // 应付未付只能在「有付款数据源的四类」范围内合计（间接费六类 paid 恒为 null，
        // 若混入全量 occurred 会把未付款的间接费当成应付未付，口径就错了）
        BigDecimal directOccurredTotal = BigDecimal.ZERO;
        boolean currentMonthComplete = !ordered.isEmpty();
        List<String> occurredNullCategories = new ArrayList<>();
        List<String> paidNullCategories = new ArrayList<>();
        LocalDateTime generatedAt = null;
        for (BizMonthlyOperationAnalysis r : ordered) {
            budgetTotal = budgetTotal.add(nvl(r.getBudgetAmount()));
            occurredTotal = occurredTotal.add(nvl(r.getCumulativeOccurred()));
            forecastTotal = forecastTotal.add(nvl(r.getForecastFinal()));
            if (r.getCurrentMonthOccurred() == null) {
                currentMonthComplete = false;
                occurredNullCategories.add(r.getCategoryName());
            } else {
                currentMonthTotal = currentMonthTotal.add(r.getCurrentMonthOccurred());
            }
            if (r.getCumulativePaid() == null) {
                paidNullCategories.add(r.getCategoryName());
            } else {
                paidTotal = paidTotal.add(r.getCumulativePaid());
                directOccurredTotal = directOccurredTotal.add(nvl(r.getCumulativeOccurred()));
            }
            if (r.getGeneratedAt() != null
                    && (generatedAt == null || r.getGeneratedAt().isAfter(generatedAt))) {
                generatedAt = r.getGeneratedAt();
            }
        }

        Map<String, Object> totals = new HashMap<>();
        totals.put("budgetAmount", budgetTotal);
        totals.put("cumulativeOccurred", occurredTotal);
        totals.put("forecastFinal", forecastTotal);
        // 本月发生合计：行为空时给 null（无数据不等于 0）；否则为已知项之和（同时给出口径提示）
        totals.put("currentMonthOccurred", ordered.isEmpty() ? null : currentMonthTotal);
        totals.put("currentMonthComplete", currentMonthComplete);
        // 累计支付/应付未付合计仅覆盖直接费四类，口径随行附送，不笼统称“合计”
        totals.put("cumulativePaid", paidTotal);
        totals.put("directOccurredTotal", directOccurredTotal);
        totals.put("payableOutstanding", ordered.isEmpty() ? null : directOccurredTotal.subtract(paidTotal));
        totals.put("paidScope", "DIRECT_FOUR_CATEGORIES");

        List<String> notes = new ArrayList<>();
        if (!currentMonthComplete) {
            notes.add("「本月发生」合计仅含 " + (ordered.size() - occurredNullCategories.size())
                    + "/" + ordered.size() + " 类；无值的类：" + String.join("、", occurredNullCategories)
                    + "（首次生成无上月基期，或该类未建 CBS 账户）");
        }
        if (!paidNullCategories.isEmpty()) {
            notes.add("「累计支付/应付未付」仅直接费四类（人工/材料/机械/分包）有合同 cumulative_paid 权威来源，"
                    + "合计也仅覆盖这四类；间接费无按类细分的付款数据源，故为空而非 0："
                    + String.join("、", paidNullCategories));
        }
        if (ordered.isEmpty()) {
            notes.add("该项目该月尚未生成分析表，请先执行生成（定时任务每月 1 日 04:00 自动生成上月）");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("projectId", projectId);
        result.put("month", month);
        result.put("rows", ordered);
        result.put("totals", totals);
        result.put("occurredNullCategories", occurredNullCategories);
        result.put("paidNullCategories", paidNullCategories);
        result.put("generatedAt", generatedAt);
        result.put("notes", notes);
        return result;
    }

    /** 某项目已生成的最新月份（无数据时返回 null，前端据此提示先生成） */
    public String latestMonth(Long projectId) {
        List<BizMonthlyOperationAnalysis> rows = analysisMapper.selectList(
                new LambdaQueryWrapper<BizMonthlyOperationAnalysis>()
                        .eq(BizMonthlyOperationAnalysis::getProjectId, projectId)
                        .orderByDesc(BizMonthlyOperationAnalysis::getAnalysisMonth)
                        .last("LIMIT 1"));
        return rows == null || rows.isEmpty() ? null : rows.get(0).getAnalysisMonth();
    }

    /** §9 十类的中文名与行序（供前端表头与导出复用，避免两处硬编码漂移） */
    public Map<String, String> categoryNames() {
        return CATEGORY_NAMES;
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new BusinessException(400, "分析月份不能为空，格式 yyyy-MM");
        }
        try {
            return YearMonth.parse(month.trim(), MONTH_FMT);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "分析月份格式非法（应为 yyyy-MM）：" + month);
        }
    }

    private static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString());
    }

    private static int toInt(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private static BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
