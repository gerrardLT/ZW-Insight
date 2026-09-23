package com.zwinsight.dashboard.risk.rule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.budget.domain.BizCostAccount;
import com.zwinsight.budget.mapper.BizCostAccountMapper;
import com.zwinsight.dashboard.domain.BizProfitSnapshot;
import com.zwinsight.dashboard.domain.BizRiskRegister;
import com.zwinsight.dashboard.risk.RiskFinding;
import com.zwinsight.dashboard.service.ProfitSnapshotService;
import com.zwinsight.finance.domain.BizFundRollingForecast;
import com.zwinsight.finance.domain.BizReceivable;
import com.zwinsight.finance.domain.BizRetentionMoney;
import com.zwinsight.finance.domain.BizWageSpecialAccount;
import com.zwinsight.finance.mapper.BizFundRollingForecastMapper;
import com.zwinsight.finance.mapper.BizReceivableMapper;
import com.zwinsight.finance.mapper.BizRetentionMoneyMapper;
import com.zwinsight.finance.mapper.BizWageSpecialAccountMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 风险规则边界测试（V2026_59）
 * <p>钉住驾驶舱 §15「状态必须由规则自动产生」的判定式边界：
 * 预算执行率恰好 80%/100%、预计利润恰好为 0、应收逾期恰好 90 天、
 * 滚动预测 HIGH/MEDIUM 映射 RED/YELLOW。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskRulesBoundaryTest {

    @Mock private BizProjectMapper projectMapper;
    @Mock private BizCostAccountMapper costAccountMapper;
    @Mock private BizReceivableMapper receivableMapper;
    @Mock private BizFundRollingForecastMapper rollingForecastMapper;
    @Mock private BizRetentionMoneyMapper retentionMoneyMapper;
    @Mock private BizWageSpecialAccountMapper wageAccountMapper;
    @Mock private ProfitSnapshotService profitSnapshotService;

    private BizProject project(Long id, String status) {
        BizProject p = new BizProject();
        p.setId(id);
        p.setProjectName("项目" + id);
        p.setStatus(status);
        return p;
    }

    private BizCostAccount account(Long id, Long parentId, String category, String actual, String current) {
        BizCostAccount a = new BizCostAccount();
        a.setId(id);
        a.setParentId(parentId);
        a.setCostCategory(category);
        a.setActualAmount(new BigDecimal(actual));
        a.setCurrentAmount(new BigDecimal(current));
        return a;
    }

    @Nested
    @DisplayName("ProfitLossRiskRule — 利润风险判定式")
    class ProfitLossTests {

        private ProfitLossRiskRule rule() {
            ProfitLossRiskRule r = new ProfitLossRiskRule(projectMapper, profitSnapshotService);
            ReflectionTestUtils.setField(r, "yellowProfitRate", new BigDecimal("0.05"));
            return r;
        }

        private ProfitSnapshotService.ProjectForecast forecast(String income, String forecastCost) {
            return new ProfitSnapshotService.ProjectForecast(
                    new BigDecimal(income), BizProfitSnapshot.BASIS_CONSTRUCTION_CONTRACT,
                    new BigDecimal(forecastCost), new BigDecimal(forecastCost),
                    BizProfitSnapshot.BASIS_CBS_FORECAST,
                    new BigDecimal(income).subtract(new BigDecimal(forecastCost)),
                    BigDecimal.ZERO, java.util.Map.of());
        }

        @Test
        @DisplayName("预计利润为负 → RED（预计亏损，需管理层介入）")
        void negativeProfit_red() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(project(1L, "CONSTRUCTION")));
            // 收入 800万，预测成本 832万 → 亏损 32万
            when(profitSnapshotService.computeProjectForecast(any())).thenReturn(forecast("8000000", "8320000"));

            List<RiskFinding> findings = rule().evaluate();

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).severity()).isEqualTo(BizRiskRegister.SEVERITY_RED);
            assertThat(findings.get(0).title()).contains("预计亏损");
            assertThat(findings.get(0).impactAmount()).isEqualByComparingTo("320000");
        }

        @Test
        @DisplayName("边界：预计利润恰好为 0 → 利润率 0% < 5% 判 YELLOW（非 RED，0 不算亏损）")
        void zeroProfit_yellowNotRed() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(project(2L, "CONSTRUCTION")));
            when(profitSnapshotService.computeProjectForecast(any())).thenReturn(forecast("8000000", "8000000"));

            List<RiskFinding> findings = rule().evaluate();

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).severity()).isEqualTo(BizRiskRegister.SEVERITY_YELLOW);
        }

        @Test
        @DisplayName("边界：利润率恰好等于阈值 5% → 不产生风险（判定式为严格小于）")
        void rateExactlyAtThreshold_noFinding() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(project(3L, "CONSTRUCTION")));
            // 收入 800万，成本 760万 → 利润 40万，利润率恰好 5%
            when(profitSnapshotService.computeProjectForecast(any())).thenReturn(forecast("8000000", "7600000"));

            assertThat(rule().evaluate()).isEmpty();
        }

        @Test
        @DisplayName("边界：利润率略低于阈值 → YELLOW")
        void rateBelowThreshold_yellow() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(project(4L, "CONSTRUCTION")));
            // 利润率 4.875% < 5%
            when(profitSnapshotService.computeProjectForecast(any())).thenReturn(forecast("8000000", "7610000"));

            List<RiskFinding> findings = rule().evaluate();

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).severity()).isEqualTo(BizRiskRegister.SEVERITY_YELLOW);
        }

        @Test
        @DisplayName("范围守卫 — 仅评估 CONSTRUCTION/COMPLETED/CLOSING，投标期项目不产生误报")
        void onlyEvaluatesActiveStatuses() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

            assertThat(rule().evaluate()).isEmpty();
        }
    }

    @Nested
    @DisplayName("BudgetOverRiskRule — 预算执行率判定式（80% 黄 / 100% 红）")
    class BudgetOverTests {

        private BudgetOverRiskRule rule() {
            BudgetOverRiskRule r = new BudgetOverRiskRule(costAccountMapper, projectMapper);
            ReflectionTestUtils.setField(r, "yellowRate", new BigDecimal("80"));
            ReflectionTestUtils.setField(r, "redRate", new BigDecimal("100"));
            return r;
        }

        @Test
        @DisplayName("边界：执行率恰好 100% → YELLOW 而非 RED（文档§11 判定式为 >100% 才红）")
        void exactlyAt100Percent_yellowNotRed() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(project(1L, "CONSTRUCTION")));
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(account(10L, null, "MATERIAL", "1000000", "1000000")));

            List<RiskFinding> findings = rule().evaluate();

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).severity()).isEqualTo(BizRiskRegister.SEVERITY_YELLOW);
            // 未超支，影响金额为 0（不把未发生的超支计入损失）
            assertThat(findings.get(0).impactAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("边界：执行率超 100% → RED，影响金额为超支额")
        void over100Percent_red() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(project(1L, "CONSTRUCTION")));
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(account(10L, null, "MATERIAL", "1080000", "1000000")));

            List<RiskFinding> findings = rule().evaluate();

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).severity()).isEqualTo(BizRiskRegister.SEVERITY_RED);
            assertThat(findings.get(0).impactAmount()).isEqualByComparingTo("80000");
            assertThat(findings.get(0).title()).contains("MATERIAL").contains("108.0%");
        }

        @Test
        @DisplayName("边界：执行率恰好 80% → 不产生风险；81% → YELLOW")
        void exactlyAt80Percent_noFinding_above_yellow() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(project(1L, "CONSTRUCTION")));
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(account(10L, null, "LABOR", "800000", "1000000")));
            assertThat(rule().evaluate()).isEmpty();

            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(account(10L, null, "LABOR", "810000", "1000000")));
            List<RiskFinding> findings = rule().evaluate();
            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).severity()).isEqualTo(BizRiskRegister.SEVERITY_YELLOW);
        }

        @Test
        @DisplayName("去重守卫 — 子账户不重复计入（仅根账户参与执行率计算）")
        void childAccountsNotDoubleCounted() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(project(1L, "CONSTRUCTION")));
            // 根账户 100万/100万（恰好 100% → YELLOW）+ 子账户 50万/50万
            // 若子账户被重复计入，聚合将变 150万/150万 —— 仍为 100%；
            // 故另用不等额子账户钉住：子账户 50万/20万，若计入则总额 150万/120万 = 125% → RED
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    account(10L, null, "MATERIAL", "1000000", "1000000"),
                    account(11L, 10L, "MATERIAL", "500000", "200000")));

            List<RiskFinding> findings = rule().evaluate();

            // 仅根账户：100% → 单条 YELLOW（非 RED，证明子账户未被重复计入）
            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).severity()).isEqualTo(BizRiskRegister.SEVERITY_YELLOW);
            assertThat(findings.get(0).title()).contains("100.0%");
        }

        @Test
        @DisplayName("除零守卫 — 预算为 0 的类别不评估（不产生全额误报）")
        void zeroBudget_skipped() {
            when(projectMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(project(1L, "CONSTRUCTION")));
            when(costAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(account(10L, null, "OTHER", "500000", "0")));

            assertThat(rule().evaluate()).isEmpty();
        }
    }

    @Nested
    @DisplayName("FundGapRiskRule — 资金缺口映射")
    class FundGapTests {

        private BizFundRollingForecast forecast(Long id, Long projectId, String riskLevel, String netGap) {
            BizFundRollingForecast f = new BizFundRollingForecast();
            f.setId(id);
            f.setProjectId(projectId);
            f.setForecastMonth("2026-10");
            f.setRiskLevel(riskLevel);
            f.setNetGap(new BigDecimal(netGap));
            f.setExpectedPayments(new BigDecimal("1600000"));
            f.setExpectedReceipts(new BigDecimal("1200000"));
            f.setSnapshotDate(LocalDate.now());
            return f;
    }

        @Test
        @DisplayName("HIGH 风险级 → RED；MEDIUM → YELLOW（映射不断级）")
        void riskLevelMapping() {
            when(rollingForecastMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    forecast(1L, 10L, "HIGH", "400000"),
                    forecast(2L, 20L, "MEDIUM", "50000")));
            when(projectMapper.selectById(10L)).thenReturn(project(10L, "CONSTRUCTION"));
            when(projectMapper.selectById(20L)).thenReturn(project(20L, "CONSTRUCTION"));

            List<RiskFinding> findings =
                    new FundGapRiskRule(rollingForecastMapper, projectMapper).evaluate();

            assertThat(findings).hasSize(2);
            assertThat(findings.get(0).severity()).isEqualTo(BizRiskRegister.SEVERITY_RED);
            assertThat(findings.get(0).impactAmount()).isEqualByComparingTo("400000");
            assertThat(findings.get(1).severity()).isEqualTo(BizRiskRegister.SEVERITY_YELLOW);
            assertThat(findings.get(0).title()).contains("项目10");
        }

        @Test
        @DisplayName("公司级缺口 — projectId 为空时标题标注公司整体，riskCode 用 COMPANY 占位")
        void companyLevelGap() {
            when(rollingForecastMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(forecast(3L, null, "HIGH", "1200000")));

            List<RiskFinding> findings =
                    new FundGapRiskRule(rollingForecastMapper, projectMapper).evaluate();

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).title()).contains("公司整体");
            assertThat(findings.get(0).riskCode()).isEqualTo("FUND_GAP:COMPANY:3");
        }
    }

    @Nested
    @DisplayName("ReceivableOverdueRiskRule — 应收逾期分桶")
    class ReceivableOverdueTests {

        private ReceivableOverdueRiskRule rule() {
            ReceivableOverdueRiskRule r =
                    new ReceivableOverdueRiskRule(receivableMapper, projectMapper);
            ReflectionTestUtils.setField(r, "redDays", 90L);
            return r;
        }

        private BizReceivable receivable(Long id, String amount, String writtenOff, LocalDate dueDate) {
            BizReceivable r = new BizReceivable();
            r.setId(id);
            r.setProjectId(1L);
            r.setReceivableAmount(new BigDecimal(amount));
            r.setWrittenOffAmount(new BigDecimal(writtenOff));
            r.setDueDate(dueDate);
            r.setStatus(BizReceivable.STATUS_OPEN);
            return r;
        }

        @Test
        @DisplayName("逾期 > 90 天 → RED；同项目多笔聚合为一条，影响金额为余额合计")
        void over90Days_red_aggregated() {
            LocalDate today = LocalDate.now();
            when(receivableMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(
                    receivable(1L, "3000000", "0", today.minusDays(100)),
                    receivable(2L, "2000000", "500000", today.minusDays(20))));
            when(projectMapper.selectById(1L)).thenReturn(project(1L, "CONSTRUCTION"));

            List<RiskFinding> findings = rule().evaluate();

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).severity()).isEqualTo(BizRiskRegister.SEVERITY_RED);
            // 300万 + (200万−50万) = 450万
            assertThat(findings.get(0).impactAmount()).isEqualByComparingTo("4500000");
            assertThat(findings.get(0).title()).contains("项目1").contains("最长逾期 100 天").contains("2 笔");
        }

        @Test
        @DisplayName("边界：最长逾期恰好 90 天 → YELLOW（判定式为严格大于）")
        void exactly90Days_yellow() {
            when(receivableMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(receivable(1L, "1000000", "0", LocalDate.now().minusDays(90))));
            when(projectMapper.selectById(1L)).thenReturn(project(1L, "CONSTRUCTION"));

            List<RiskFinding> findings = rule().evaluate();

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).severity()).isEqualTo(BizRiskRegister.SEVERITY_YELLOW);
        }

        @Test
        @DisplayName("零余额守卫 — 已全额核销记录不产生风险")
        void fullyWrittenOff_skipped() {
            when(receivableMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(receivable(1L, "1000000", "1000000", LocalDate.now().minusDays(50))));

            assertThat(rule().evaluate()).isEmpty();
        }
    }

    @Nested
    @DisplayName("RetentionOverdueRiskRule — 质保金逾期未退")
    class RetentionTests {

        private RetentionOverdueRiskRule rule() {
            RetentionOverdueRiskRule r = new RetentionOverdueRiskRule(retentionMoneyMapper, projectMapper);
            ReflectionTestUtils.setField(r, "redDays", 90L);
            return r;
        }

        private BizRetentionMoney retention(Long id, String amount, String returned, LocalDate expireDate) {
            BizRetentionMoney m = new BizRetentionMoney();
            m.setId(id);
            m.setProjectId(1L);
            m.setRetentionAmount(new BigDecimal(amount));
            m.setReturnedAmount(new BigDecimal(returned));
            m.setExpireDate(expireDate);
            m.setStatus("ACTIVE");
            return m;
        }

        @Test
        @DisplayName("逾期超 90 天 → RED，影响金额为未退余额")
        void over90Days_red() {
            when(retentionMoneyMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(retention(1L, "500000", "200000", LocalDate.now().minusDays(120))));
            when(projectMapper.selectById(1L)).thenReturn(project(1L, "COMPLETED"));

            List<RiskFinding> findings = rule().evaluate();

            assertThat(findings).hasSize(1);
            assertThat(findings.get(0).severity()).isEqualTo(BizRiskRegister.SEVERITY_RED);
            assertThat(findings.get(0).impactAmount()).isEqualByComparingTo("300000");
            assertThat(findings.get(0).title()).contains("项目1").contains("到期未退");
        }

        @Test
        @DisplayName("已全额退还 → 不产生风险")
        void fullyReturned_skipped() {
            when(retentionMoneyMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(retention(2L, "500000", "500000", LocalDate.now().minusDays(10))));

            assertThat(rule().evaluate()).isEmpty();
        }
    }

    @Nested
    @DisplayName("WageComplianceRiskRule — 工资专户合规")
    class WageTests {

        private BizWageSpecialAccount account(Long id, String flag) {
            BizWageSpecialAccount a = new BizWageSpecialAccount();
            a.setId(id);
            a.setProjectId(1L);
            a.setAccountNo("622200" + id);
            a.setStatus(BizWageSpecialAccount.STATUS_ACTIVE);
            a.setComplianceFlag(flag);
            a.setWageBudget(new BigDecimal("1000000"));
            a.setTotalReceived(new BigDecimal("600000"));
            a.setTotalPaid(new BigDecimal("500000"));
            return a;
        }

        @Test
        @DisplayName("OVERDUE（拨付逾期，条例第24条）→ RED；INSUFFICIENT → YELLOW")
        void complianceFlagMapping() {
            when(wageAccountMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(account(1L, BizWageSpecialAccount.COMPLIANCE_OVERDUE),
                            account(2L, BizWageSpecialAccount.COMPLIANCE_INSUFFICIENT)));
            when(projectMapper.selectById(1L)).thenReturn(project(1L, "CONSTRUCTION"));

            List<RiskFinding> findings =
                    new WageComplianceRiskRule(wageAccountMapper, projectMapper).evaluate();

            assertThat(findings).hasSize(2);
            assertThat(findings.get(0).severity()).isEqualTo(BizRiskRegister.SEVERITY_RED);
            assertThat(findings.get(0).title()).contains("拨付逾期");
            assertThat(findings.get(1).severity()).isEqualTo(BizRiskRegister.SEVERITY_YELLOW);
            // 影响金额 = 工资预算 − 已到账 = 40万
            assertThat(findings.get(0).impactAmount()).isEqualByComparingTo("400000");
        }
    }
}
