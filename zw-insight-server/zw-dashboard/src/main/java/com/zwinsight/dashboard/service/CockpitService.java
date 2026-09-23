package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.dashboard.domain.BizProfitSnapshot;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.mapper.BizRiskRegisterMapper;
import com.zwinsight.finance.domain.BizFundRollingForecast;
import com.zwinsight.finance.mapper.BizBankFlowMapper;
import com.zwinsight.finance.mapper.BizFundRollingForecastMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.ContractPayableMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 经营驾驶舱聚合服务（V2026_59，驾驶舱 V1 五页面的公司级数据源）
 * <p>
 * 口径立场：所有指标来自真实单据/台账/快照聚合，无 mock、无均摊模拟；
 * 预计利润 = 合同收入 − CBS 完工预测总成本（{@link ProfitSnapshotService} 权威口径）；
 * 已实现利润（realizedProfit）= total_income − total_expense（历史收付口径），
 * 两者并存且分别命名，禁止混用。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CockpitService {

    private final BizProjectMapper projectMapper;
    private final BizRiskRegisterMapper riskMapper;
    private final BizFundRollingForecastMapper rollingForecastMapper;
    private final BizBankFlowMapper bankFlowMapper;
    private final BizPaymentApplyMapper paymentApplyMapper;
    private final ContractPayableMapper contractPayableMapper;
    private final ProfitSnapshotService profitSnapshotService;
    private final DashboardService dashboardService;

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    /**
     * 经营总览（驾驶舱首页指标卡，§4 / 资金流转 §12）：
     * 经营结果 4 卡（合同收入/预计总成本/预计利润/利润率）
     * + 资金状态（累计回款/累计支付/应收未收/<b>应付未付</b>/90天资金缺口）+ 账户资金。
     * <p><b>应付未付 vs 已批未付（两者并列，语义不同不可互替）</b>：
     * 前者 = 已确认付款义务（Σ合同 cumulative_settlement − cumulative_paid），
     * 后者 = 已进入付款流程但银行未划款（status=APPROVED 且 pay_status≠PAID 的剩余未付额）。
     * 只用后者会漏掉“已结算但尚未提交付款申请”的义务（UI §9.1 要求的是前者）。</p>
     * <p><b>90 天资金缺口（资金流转 §10.4）</b>：未来预计支付 − 可用资金，<b>正数=缺钱</b>。
     * 可用资金 = 账户余额快照合计 + 窗口内预计回款。旧实现只累加正净缺口、
     * 从不减可用资金，会把“账面能覆盖的缺口”误报为重大风险（2026-09-24 审计修正）。</p>
     */
    public Map<String, Object> getOverview() {
        // 实时逐项目计算预计利润（不依赖快照是否已生成，口径同源）
        List<Map<String, Object>> forecasts = profitSnapshotService.listProjectForecasts();
        BigDecimal contractIncome = BigDecimal.ZERO;
        BigDecimal forecastCost = BigDecimal.ZERO;
        BigDecimal forecastProfit = BigDecimal.ZERO;
        for (Map<String, Object> row : forecasts) {
            contractIncome = contractIncome.add((BigDecimal) row.get("contractIncome"));
            forecastCost = forecastCost.add((BigDecimal) row.get("forecastTotalCost"));
            forecastProfit = forecastProfit.add((BigDecimal) row.get("forecastProfit"));
        }

        // 资金状态：项目回写字段聚合（total_income/total_expense 为审批回写口径的权威账）
        List<BizProject> projects = projectMapper.selectList(new LambdaQueryWrapper<>());
        BigDecimal received = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal receivable = BigDecimal.ZERO;
        BigDecimal realizedProfit = BigDecimal.ZERO;
        for (BizProject p : projects) {
            received = received.add(nvl(p.getTotalIncome()));
            paid = paid.add(nvl(p.getTotalExpense()));
            receivable = receivable.add(nvl(p.getReceivableAmount()));
            realizedProfit = realizedProfit.add(nvl(p.getTotalIncome())).subtract(nvl(p.getTotalExpense()));
        }

        // 90 天资金缺口（含构成明细，不隐藏口径）
        Map<String, BigDecimal> gap = computeFundGap90Days();

        Map<String, Object> result = new HashMap<>();
        result.put("contractIncome", contractIncome);
        result.put("forecastTotalCost", forecastCost);
        result.put("forecastProfit", forecastProfit);
        result.put("forecastProfitRate", contractIncome.signum() > 0
                ? forecastProfit.divide(contractIncome, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        result.put("realizedProfit", realizedProfit);
        result.put("cumulativeReceived", received);
        result.put("cumulativePaid", paid);
        result.put("receivableOutstanding", receivable);
        // 资金缺口（§10.4 口径）及其构成（账户余额/预计回款/可用资金/预计支付）
        result.put("gap90Days", gap.get("gap"));
        result.put("gap90DaysDetail", gap);
        result.put("accountBalance", gap.get("accountBalance"));
        result.put("availableFund", gap.get("availableFund"));
        // 应付未付（已确认义务）与已批未付（已进入付款流程）并列，语义不同不可互替
        result.put("payableOutstanding", nvl(contractPayableMapper.sumPayableOutstanding(null)));
        result.put("approvedUnpaid", nvl(paymentApplyMapper.sumApprovedUnpaidRemaining(null)));
        // 资金流转 §12：本月现金需求 / 三个月资金需求
        result.put("currentMonthCashNeed", gap.get("currentMonthPayments"));
        result.put("threeMonthCashNeed", gap.get("expectedPayments"));
        return result;
    }

    /**
     * 利润趋势（驾驶舱 §5.1）：预计利润快照曲线 + 已实现收支月度曲线（同源 profit-trend 真实口径）。
     *
     * @param projectId 项目ID（NULL=公司级）
     * @param months    快照月数（1-36）
     * @param year      已实现收支柱状图年份（默认当年）
     */
    public Map<String, Object> getProfitTrend(Long projectId, int months, Integer year) {
        List<BizProfitSnapshot> snapshots = profitSnapshotService.getTrend(projectId, months);
        Map<String, Object> realized = dashboardService.getProfitTrend(year);

        Map<String, Object> result = new HashMap<>();
        result.put("snapshots", snapshots);
        result.put("realized", realized);
        return result;
    }

    /**
     * 项目经营健康度（驾驶舱 §5.2）：规则自动定级 🟢🟡🔴 + 四级排序
     * （风险等级 → 预计亏损 → 利润率 → 资金相关影响额），异常项目排最前。
     */
    public List<Map<String, Object>> getProjectHealth() {
        List<Map<String, Object>> forecasts = profitSnapshotService.listProjectForecasts();

        // 活跃风险按项目聚合（OPEN/PROCESSING）
        List<BizRiskRegister> activeRisks = riskMapper.selectList(new LambdaQueryWrapper<BizRiskRegister>()
                .in(BizRiskRegister::getHandleStatus,
                        BizRiskRegister.HANDLE_OPEN, BizRiskRegister.HANDLE_PROCESSING));
        Map<Long, List<BizRiskRegister>> risksByProject = new HashMap<>();
        for (BizRiskRegister risk : activeRisks) {
            if (risk.getProjectId() != null) {
                risksByProject.computeIfAbsent(risk.getProjectId(), k -> new ArrayList<>()).add(risk);
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> forecast : forecasts) {
            Long projectId = (Long) forecast.get("projectId");
            BigDecimal profit = (BigDecimal) forecast.get("forecastProfit");
            List<BizRiskRegister> risks = risksByProject.getOrDefault(projectId, List.of());
            boolean hasRed = risks.stream().anyMatch(r -> BizRiskRegister.SEVERITY_RED.equals(r.getSeverity()));
            boolean hasYellow = risks.stream().anyMatch(r -> BizRiskRegister.SEVERITY_YELLOW.equals(r.getSeverity()));

            // 健康度定级规则（§15：规则自动产生，非人工选择）
            String health = "GREEN";
            if (profit.signum() < 0 || hasRed) {
                health = "RED";
            } else if (hasYellow) {
                health = "YELLOW";
            }

            Map<String, Object> row = new HashMap<>(forecast);
            row.put("health", health);
            row.put("redCount", risks.stream()
                    .filter(r -> BizRiskRegister.SEVERITY_RED.equals(r.getSeverity())).count());
            row.put("yellowCount", risks.stream()
                    .filter(r -> BizRiskRegister.SEVERITY_YELLOW.equals(r.getSeverity())).count());
            row.put("topRisks", risks.stream()
                    .sorted(Comparator
                            .comparingInt((BizRiskRegister r) ->
                                    BizRiskRegister.SEVERITY_RED.equals(r.getSeverity()) ? 0 : 1)
                            .thenComparing(Comparator.comparing(
                                    (BizRiskRegister r) -> nvl(r.getImpactAmount())).reversed()))
                    .limit(3)
                    .map(BizRiskRegister::getTitle)
                    .toList());
            result.add(row);
        }

        // 四级排序：健康度(RED>YELLOW>GREEN) → 预计亏损(利润升序) → 利润率升序
        result.sort(Comparator
                .comparingInt((Map<String, Object> m) -> healthRank((String) m.get("health")))
                .thenComparing(m -> (BigDecimal) m.get("forecastProfit"))
                .thenComparing(m -> (BigDecimal) m.get("profitRate")));
        return result;
    }

    // ==================== 私有方法 ====================

    /**
     * 90 天（当月 + 后两个月）资金缺口与可用资金明细。
     * <p>口径（资金流转 §10.4）：{@code 缺口 = 未来预计支付 − 可用资金}，正数表示缺钱；
     * {@code 可用资金 = 账户余额快照合计 + 窗口内预计回款}（回款是窗口内真实可用来源，
     * 只算账面余额会高估缺口）。</p>
     * <p>数据源为公司级滚动预测快照（projectId IS NULL，FundForecastTask 每日 01:15 刷新）。
     * 若快照缺失（任务未执行或首次部署）则各项为 0，<b>不伪造估算值</b>；
     * 同时如实返回 accountBalance，供前端提示“账户余额未登记”（biz_bank_balance 为空时）。</p>
     *
     * @return {expectedPayments, expectedReceipts, accountBalance, availableFund, gap, currentMonthPayments}
     */
    private Map<String, BigDecimal> computeFundGap90Days() {
        YearMonth current = YearMonth.now();
        YearMonth end = current.plusMonths(2);
        String currentMonthKey = current.format(MONTH_FMT);
        List<BizFundRollingForecast> snapshots = rollingForecastMapper.selectList(
                new LambdaQueryWrapper<BizFundRollingForecast>()
                        .isNull(BizFundRollingForecast::getProjectId)
                        .ge(BizFundRollingForecast::getForecastMonth, currentMonthKey)
                        .le(BizFundRollingForecast::getForecastMonth, end.format(MONTH_FMT)));
        BigDecimal expectedPayments = BigDecimal.ZERO;
        BigDecimal expectedReceipts = BigDecimal.ZERO;
        BigDecimal currentMonthPayments = BigDecimal.ZERO;
        for (BizFundRollingForecast f : snapshots) {
            expectedPayments = expectedPayments.add(nvl(f.getExpectedPayments()));
            expectedReceipts = expectedReceipts.add(nvl(f.getExpectedReceipts()));
            if (currentMonthKey.equals(f.getForecastMonth())) {
                currentMonthPayments = nvl(f.getExpectedPayments());
            }
        }
        BigDecimal accountBalance = nvl(bankFlowMapper.sumLatestBalances());
        BigDecimal availableFund = accountBalance.add(expectedReceipts);
        BigDecimal gap = expectedPayments.subtract(availableFund);

        Map<String, BigDecimal> detail = new HashMap<>();
        detail.put("expectedPayments", expectedPayments);
        detail.put("expectedReceipts", expectedReceipts);
        detail.put("accountBalance", accountBalance);
        detail.put("availableFund", availableFund);
        detail.put("gap", gap);
        detail.put("currentMonthPayments", currentMonthPayments);
        return detail;
    }

    /**
     * 90 天资金缺口（对外只读口径，供风险规则与单测复用）。
     * <p>正数 = 缺钱（预计支付 &gt; 可用资金）；负数 = 有富余。</p>
     */
    public BigDecimal getGap90Days() {
        return computeFundGap90Days().get("gap");
    }

    /**
     * 当前可用资金（账户余额快照 + 未来 90 天预计回款）——供资金风险规则判定“是否可覆盖”。
     */
    public BigDecimal getAvailableFund() {
        return computeFundGap90Days().get("availableFund");
    }

    private static int healthRank(String health) {
        return switch (health) {
            case "RED" -> 0;
            case "YELLOW" -> 1;
            default -> 2;
        };
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** 供任务/测试触发的当月快照刷新入口透传（避免多路径实现） */
    public int refreshSnapshot() {
        return profitSnapshotService.generateSnapshot();
    }

    /** 快照日期（供控制器透出"数据更新时间"） */
    public LocalDate today() {
        return LocalDate.now();
    }
}
