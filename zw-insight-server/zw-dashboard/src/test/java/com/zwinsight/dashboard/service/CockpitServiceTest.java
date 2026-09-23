package com.zwinsight.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.mapper.BizRiskRegisterMapper;
import com.zwinsight.finance.domain.BizFundRollingForecast;
import com.zwinsight.finance.mapper.BizBankFlowMapper;
import com.zwinsight.finance.mapper.BizFundRollingForecastMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CockpitService 单元测试（V2026_59 经营驾驶舱聚合）
 * <p>核心断言：8 卡口径正确（预计利润 ≠ 已实现利润，两套口径并存不混用）、
 * 90 天缺口仅计正缺口、健康度按规则定级并四级排序（异常项目排最前）。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CockpitServiceTest {

    @Mock private BizProjectMapper projectMapper;
    @Mock private BizRiskRegisterMapper riskMapper;
    @Mock private BizFundRollingForecastMapper rollingForecastMapper;
    @Mock private BizBankFlowMapper bankFlowMapper;
    // V2026_64：应付未付（合同聚合）与已批未付（付款申请剩余未付额）并列返回
    @Mock private com.zwinsight.finance.mapper.BizPaymentApplyMapper paymentApplyMapper;
    @Mock private com.zwinsight.finance.mapper.ContractPayableMapper contractPayableMapper;
    // 合同执行率（§10.3）需施工合同产值与动态合同额
    @Mock private com.zwinsight.contract.mapper.BizConstructionContractMapper constructionContractMapper;
    @Mock private ProfitSnapshotService profitSnapshotService;
    @Mock private DashboardService dashboardService;

    @InjectMocks
    private CockpitService cockpitService;

    private BizProject project(Long id, String income, String expense, String receivable) {
        BizProject p = new BizProject();
        p.setId(id);
        p.setProjectName("项目" + id);
        p.setTotalIncome(new BigDecimal(income));
        p.setTotalExpense(new BigDecimal(expense));
        p.setReceivableAmount(new BigDecimal(receivable));
        return p;
    }

    /** 构造 listProjectForecasts 的行（与 ProfitSnapshotService 输出字段一致） */
    private Map<String, Object> forecastRow(Long projectId, String name, String income,
                                            String forecastCost, String profit) {
        Map<String, Object> row = new HashMap<>();
        row.put("projectId", projectId);
        row.put("projectName", name);
        row.put("status", "CONSTRUCTION");
        row.put("contractIncome", new BigDecimal(income));
        row.put("incomeBasis", "CONSTRUCTION_CONTRACT");
        row.put("actualCost", new BigDecimal(forecastCost));
        row.put("forecastTotalCost", new BigDecimal(forecastCost));
        row.put("costBasis", "CBS_FORECAST");
        row.put("forecastProfit", new BigDecimal(profit));
        BigDecimal rate = new BigDecimal(income).signum() > 0
                ? new BigDecimal(profit).divide(new BigDecimal(income), 4, java.math.RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        row.put("profitRate", rate);
        return row;
    }

    private BizRiskRegister risk(Long projectId, String severity, String impact) {
        BizRiskRegister r = new BizRiskRegister();
        r.setProjectId(projectId);
        r.setSeverity(severity);
        r.setImpactAmount(new BigDecimal(impact));
        r.setTitle("风险-" + severity);
        r.setHandleStatus(BizRiskRegister.HANDLE_OPEN);
        return r;
    }

    @Nested
    @DisplayName("getOverview() 经营总览 8 卡")
    class OverviewTests {

        @Test
        @DisplayName("正常路径 — 预计利润与已实现利润并存且分别命名（两套口径不混用）")
        void overview_forecastAndRealizedCoexist() {
            // 两个项目：预计利润合计 (800−500) + (600−580) = 320万
            when(profitSnapshotService.listProjectForecasts()).thenReturn(List.of(
                    forecastRow(1L, "项目1", "8000000", "5000000", "3000000"),
                    forecastRow(2L, "项目2", "6000000", "5800000", "200000")));
            // 已实现：收入 500万+300万=800万，支出 400万+290万=690万 → 已实现利润 110万
            when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    project(1L, "5000000", "4000000", "1200000"),
                    project(2L, "3000000", "2900000", "300000")));
            when(rollingForecastMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(bankFlowMapper.sumLatestBalances()).thenReturn(new BigDecimal("18000000"));

            Map<String, Object> result = cockpitService.getOverview();

            // 经营结果 4 卡
            assertThat((BigDecimal) result.get("contractIncome")).isEqualByComparingTo("14000000");
            assertThat((BigDecimal) result.get("forecastTotalCost")).isEqualByComparingTo("10800000");
            assertThat((BigDecimal) result.get("forecastProfit")).isEqualByComparingTo("3200000");
            assertThat((BigDecimal) result.get("forecastProfitRate")).isEqualByComparingTo("0.2286");
            // 已实现利润为独立字段，数值与预计利润不同（证明未混用口径）
            assertThat((BigDecimal) result.get("realizedProfit")).isEqualByComparingTo("1100000");
            // 资金状态 4 卡
            assertThat((BigDecimal) result.get("cumulativeReceived")).isEqualByComparingTo("8000000");
            assertThat((BigDecimal) result.get("cumulativePaid")).isEqualByComparingTo("6900000");
            assertThat((BigDecimal) result.get("receivableOutstanding")).isEqualByComparingTo("1500000");
            assertThat((BigDecimal) result.get("accountBalance")).isEqualByComparingTo("18000000");
        }

        private BizFundRollingForecast forecast(String month, String payments, String receipts) {
            BizFundRollingForecast f = new BizFundRollingForecast();
            f.setForecastMonth(month);
            f.setExpectedPayments(new BigDecimal(payments));
            f.setExpectedReceipts(new BigDecimal(receipts));
            f.setNetGap(new BigDecimal(payments).subtract(new BigDecimal(receipts)));
            return f;
        }

        @Test
        @DisplayName("90天缺口（§10.4）— 缺口 = 未来预计支付 − 可用资金（账户余额 + 窗口内预计回款）")
        void overview_gap90Days_paymentsMinusAvailableFund() {
            when(profitSnapshotService.listProjectForecasts()).thenReturn(List.of());
            when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            // 三个月快照：预计支付 300+200+100=600万，预计回款 50万（仅当月）
            String m0 = java.time.YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            String m1 = java.time.YearMonth.now().plusMonths(1).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            String m2 = java.time.YearMonth.now().plusMonths(2).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            when(rollingForecastMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    forecast(m0, "3000000", "500000"),
                    forecast(m1, "2000000", "0"),
                    forecast(m2, "1000000", "0")));
            when(bankFlowMapper.sumLatestBalances()).thenReturn(new BigDecimal("1000000"));
            when(contractPayableMapper.sumPayableOutstanding(null)).thenReturn(new BigDecimal("3200000"));
            when(paymentApplyMapper.sumApprovedUnpaidRemaining(null)).thenReturn(new BigDecimal("58500000"));

            Map<String, Object> result = cockpitService.getOverview();

            // 可用资金 = 账户余额 100万 + 预计回款 50万 = 150万
            assertThat((BigDecimal) result.get("availableFund")).isEqualByComparingTo("1500000");
            // 缺口 = 600万 − 150万 = 450万（正数=缺钱）。旧实现为“正净缺口合计”=600−50=550万，不减可用资金
            assertThat((BigDecimal) result.get("gap90Days")).isEqualByComparingTo("4500000");
            // 应付未付（已确认义务）与已批未付（已进入付款流程）并列，语义不同不可互替
            assertThat((BigDecimal) result.get("payableOutstanding")).isEqualByComparingTo("3200000");
            assertThat((BigDecimal) result.get("approvedUnpaid")).isEqualByComparingTo("58500000");
            // 资金流转 §12：本月现金需求 = 当月预计支付；三个月资金需求 = 窗口合计
            assertThat((BigDecimal) result.get("currentMonthCashNeed")).isEqualByComparingTo("3000000");
            assertThat((BigDecimal) result.get("threeMonthCashNeed")).isEqualByComparingTo("6000000");
            // 构成明细必须可追溯（不隐藏口径）
            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> detail = (Map<String, BigDecimal>) result.get("gap90DaysDetail");
            assertThat(detail).containsKeys("expectedPayments", "expectedReceipts", "accountBalance", "availableFund", "gap");
        }

        @Test
        @DisplayName("边界路径 — 可用资金充足时缺口为负（有富余），不得报错也不得归零美化")
        void overview_gap90Days_surplusIsNegative() {
            when(profitSnapshotService.listProjectForecasts()).thenReturn(List.of());
            when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            String m0 = java.time.YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
            when(rollingForecastMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(forecast(m0, "1000000", "200000")));
            when(bankFlowMapper.sumLatestBalances()).thenReturn(new BigDecimal("5000000"));

            Map<String, Object> result = cockpitService.getOverview();

            // 可用资金 520万 > 预计支付 100万 → 缺口 −420万（富余）
            assertThat((BigDecimal) result.get("gap90Days")).isEqualByComparingTo("-4200000");
        }

        @Test
        @DisplayName("边界路径 — 账户余额未登记时如实计 0（不伪造），无快照时各项为 0")
        void overview_noBalanceNoSnapshot_zerosNotFabricated() {
            when(profitSnapshotService.listProjectForecasts()).thenReturn(List.of());
            when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(rollingForecastMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
            when(bankFlowMapper.sumLatestBalances()).thenReturn(null);

            Map<String, Object> result = cockpitService.getOverview();

            // 余额无登记时按 0 呈现（不返回 null 导致前端 NaN），且缺口为 0 而非估算值
            assertThat((BigDecimal) result.get("accountBalance")).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat((BigDecimal) result.get("availableFund")).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat((BigDecimal) result.get("gap90Days")).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat((BigDecimal) result.get("threeMonthCashNeed")).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("getFundRatios() 资金比率（§10.2 支付率 / §10.3 合同执行率）")
    class FundRatioTests {

        private com.zwinsight.contract.domain.BizConstructionContract contract(
                String amount, String change, String output) {
            com.zwinsight.contract.domain.BizConstructionContract c =
                    new com.zwinsight.contract.domain.BizConstructionContract();
            c.setContractAmount(new BigDecimal(amount));
            c.setCumulativeChangeAmount(new BigDecimal(change));
            c.setCumulativeOutput(new BigDecimal(output));
            c.setStatus("EFFECTIVE");
            return c;
        }

        @Test
        @DisplayName("正常路径 — 支付率=已付÷已确认应付；合同执行率=产值÷(合同额+累计变更)")
        void fundRatios_computed() {
            when(contractPayableMapper.sumConfirmedPayable(null)).thenReturn(new BigDecimal("10000000"));
            when(contractPayableMapper.sumCumulativePaid(null)).thenReturn(new BigDecimal("8000000"));
            when(bankFlowMapper.matchedAmountByPaymentApply())
                    .thenReturn(java.util.Map.of(1L, new BigDecimal("5000000")));
            when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(contract("8000000", "500000", "6000000")));

            Map<String, Object> r = cockpitService.getFundRatios();

            assertThat((BigDecimal) r.get("paymentRate")).isEqualByComparingTo("0.8000");
            // 现金口径并列返回（银行勾稽 500万 / 应付 1000万）
            assertThat((BigDecimal) r.get("cashPaymentRate")).isEqualByComparingTo("0.5000");
            assertThat(r.get("paidBasis")).isEqualTo("APPROVAL_WRITEBACK");
            assertThat((BigDecimal) r.get("dynamicContractTotal")).isEqualByComparingTo("8500000");
            assertThat((BigDecimal) r.get("contractExecutionRate")).isEqualByComparingTo("0.7059");
        }

        @Test
        @DisplayName("边界路径 — 分母为 0 时比率为 0（不抛除零异常）；无银行流水时现金口径如实为 0")
        void fundRatios_zeroDenominator_returnsZero() {
            when(contractPayableMapper.sumConfirmedPayable(null)).thenReturn(BigDecimal.ZERO);
            when(contractPayableMapper.sumCumulativePaid(null)).thenReturn(BigDecimal.ZERO);
            when(bankFlowMapper.matchedAmountByPaymentApply()).thenReturn(java.util.Map.of());
            when(constructionContractMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            Map<String, Object> r = cockpitService.getFundRatios();

            assertThat((BigDecimal) r.get("paymentRate")).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat((BigDecimal) r.get("contractExecutionRate")).isEqualByComparingTo(BigDecimal.ZERO);
            // 现金口径为 0 时如实返回 0，不得用审批口径冒充
            assertThat((BigDecimal) r.get("cashPaidTotal")).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("getProjectHealth() 健康度定级与排序")
    class HealthTests {

        @Test
        @DisplayName("定级规则 — 预计亏损 → RED；有 RED 风险 → RED；仅 YELLOW 风险 → YELLOW；无风险 → GREEN")
        void health_gradedByRules() {
            when(profitSnapshotService.listProjectForecasts()).thenReturn(List.of(
                    forecastRow(1L, "亏损项目", "8000000", "8320000", "-320000"),
                    forecastRow(2L, "健康项目", "8000000", "6000000", "2000000"),
                    forecastRow(3L, "有黄风险项目", "8000000", "7000000", "1000000")));
            when(riskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    risk(3L, BizRiskRegister.SEVERITY_YELLOW, "26000")));
            when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            List<Map<String, Object>> result = cockpitService.getProjectHealth();

            Map<Long, String> healthByProject = new HashMap<>();
            for (Map<String, Object> row : result) {
                healthByProject.put((Long) row.get("projectId"), (String) row.get("health"));
            }
            assertThat(healthByProject.get(1L)).isEqualTo("RED");    // 预计亏损
            assertThat(healthByProject.get(2L)).isEqualTo("GREEN");  // 盈利且无风险
            assertThat(healthByProject.get(3L)).isEqualTo("YELLOW"); // 盈利但有黄色风险
        }

        @Test
        @DisplayName("排序规则 — RED 在前，同级按预计亏损额（利润升序），异常项目冒到最前")
        void health_sortedBySeverityThenLoss() {
            when(profitSnapshotService.listProjectForecasts()).thenReturn(List.of(
                    forecastRow(1L, "微亏项目", "8000000", "8100000", "-100000"),
                    forecastRow(2L, "重亏项目", "8000000", "9000000", "-1000000"),
                    forecastRow(3L, "健康项目", "8000000", "6000000", "2000000")));
            when(riskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            List<Map<String, Object>> result = cockpitService.getProjectHealth();

            assertThat(result).extracting(m -> m.get("projectName"))
                    .containsExactly("重亏项目", "微亏项目", "健康项目");
        }

        @Test
        @DisplayName("TOP风险 — 每项目最多带 3 条，RED 优先且按影响金额降序")
        @SuppressWarnings("unchecked")
        void health_topRisksLimitedAndOrdered() {
            when(profitSnapshotService.listProjectForecasts()).thenReturn(List.of(
                    forecastRow(1L, "项目1", "8000000", "8320000", "-320000")));
            when(riskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    risk(1L, BizRiskRegister.SEVERITY_YELLOW, "900000"),
                    risk(1L, BizRiskRegister.SEVERITY_RED, "100000"),
                    risk(1L, BizRiskRegister.SEVERITY_YELLOW, "800000"),
                    risk(1L, BizRiskRegister.SEVERITY_YELLOW, "700000")));

            List<Map<String, Object>> result = cockpitService.getProjectHealth();

            List<String> topRisks = (List<String>) result.get(0).get("topRisks");
            assertThat(topRisks).hasSize(3);
            assertThat(result.get(0).get("redCount")).isEqualTo(1L);
            assertThat(result.get(0).get("yellowCount")).isEqualTo(3L);
        }

        @Test
        @DisplayName("边界路径 — 无项目时返回空列表（不伪造健康数据）")
        void health_noProjects_empty() {
            when(profitSnapshotService.listProjectForecasts()).thenReturn(List.of());
            when(riskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            assertThat(cockpitService.getProjectHealth()).isEmpty();
        }
    }

    @Nested
    @DisplayName("getProfitTrend() 趋势组装")
    class TrendTests {

        @Test
        @DisplayName("正常路径 — 同时返回快照曲线与已实现月度曲线（两者独立命名，前端不混用）")
        void trend_returnsBothSeries() {
            when(profitSnapshotService.getTrend(null, 6)).thenReturn(List.of());
            Map<String, Object> realized = Map.of("year", 2026, "months", List.of());
            when(dashboardService.getProfitTrend(2026)).thenReturn(realized);

            Map<String, Object> result = cockpitService.getProfitTrend(null, 6, 2026);

            assertThat(result).containsKeys("snapshots", "realized");
            assertThat(result.get("realized")).isSameAs(realized);
        }
    }
}
