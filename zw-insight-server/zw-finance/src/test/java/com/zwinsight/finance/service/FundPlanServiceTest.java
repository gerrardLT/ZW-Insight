package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizFundCategory;
import com.zwinsight.finance.domain.BizFundMonthlyPlan;
import com.zwinsight.finance.domain.BizFundPlanDetail;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.domain.BizReceivable;
import com.zwinsight.project.domain.BizProject;

import java.util.Map;
import com.zwinsight.finance.mapper.BizFundAnnualBudgetMapper;
import com.zwinsight.finance.mapper.BizFundMonthlyPlanMapper;
import com.zwinsight.finance.mapper.BizFundRollingForecastMapper;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FundPlanService 单元测试（53_V2026_51 资金计划三层联动）
 * <p>核心断言：先计划后支付（月度计划校验）、滚动预测净缺口与风险等级。</p>
 */
@ExtendWith(MockitoExtension.class)
class FundPlanServiceTest {

    @Mock private BizFundAnnualBudgetMapper annualBudgetMapper;
    @Mock private BizFundMonthlyPlanMapper monthlyPlanMapper;
    @Mock private com.zwinsight.finance.mapper.BizFundPlanDetailMapper planDetailMapper;
    @Mock private BizFundRollingForecastMapper rollingForecastMapper;
    @Mock private BizPaymentApplyMapper paymentApplyMapper;
    @Mock private BizPaymentReceivedMapper paymentReceivedMapper;
    @Mock private com.zwinsight.finance.mapper.BizReceivableMapper receivableMapper;
    // V2026_64：预测按「剩余未付额」计入需读已勾稽合计（mock 的 default 方法返回 null，
    // 由 Service 层 null 保护归零处理，等价于“无任何勾稽”的真实场景）
    @Mock private com.zwinsight.finance.mapper.BizBankFlowMapper bankFlowMapper;
    // 缺口归因需项目名称
    @Mock private com.zwinsight.project.mapper.BizProjectMapper projectMapper;
    @Mock private FundCategoryService fundCategoryService;

    @InjectMocks
    private FundPlanService fundPlanService;

    private BizFundMonthlyPlan approvedMonthlyPlan(String expensePlan) {
        BizFundMonthlyPlan plan = new BizFundMonthlyPlan();
        plan.setPlanYear(2026);
        plan.setPlanMonth(10);
        plan.setProjectId(10L);
        plan.setIncomePlan(new BigDecimal("500000"));
        plan.setExpensePlan(new BigDecimal(expensePlan));
        plan.setStatus("APPROVED");
        return plan;
    }

    @Nested
    @DisplayName("validatePaymentAgainstPlan() 先计划后支付")
    class ValidatePaymentTests {

        @Test
        @DisplayName("正常路径 — 月度计划50万，已审批20万，本次申请30万（合计≤计划）放行")
        void validatePayment_withinPlan_pass() {
            when(monthlyPlanMapper.selectOne(any())).thenReturn(approvedMonthlyPlan("500000"));
            BizPaymentApply approved = new BizPaymentApply();
            approved.setPaymentAmount(new BigDecimal("200000"));
            when(paymentApplyMapper.selectList(any())).thenReturn(List.of(approved));

            fundPlanService.validatePaymentAgainstPlan(
                    10L, LocalDate.of(2026, 10, 15), new BigDecimal("300000"));
            // 无异常即放行
        }

        @Test
        @DisplayName("异常路径 — 月度计划50万，已审批20万，本次申请40万（合计超计划）拦截")
        void validatePayment_overPlan_rejected() {
            when(monthlyPlanMapper.selectOne(any())).thenReturn(approvedMonthlyPlan("500000"));
            BizPaymentApply approved = new BizPaymentApply();
            approved.setPaymentAmount(new BigDecimal("200000"));
            when(paymentApplyMapper.selectList(any())).thenReturn(List.of(approved));

            assertThatThrownBy(() -> fundPlanService.validatePaymentAgainstPlan(
                    10L, LocalDate.of(2026, 10, 15), new BigDecimal("400000")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("超出当月资金计划");
        }

        @Test
        @DisplayName("正常路径 — 无月度计划月份不硬拦截（WARN模式，预算硬控制兜底）")
        void validatePayment_noPlan_pass() {
            when(monthlyPlanMapper.selectOne(any())).thenReturn(null);

            fundPlanService.validatePaymentAgainstPlan(
                    10L, LocalDate.of(2026, 10, 15), new BigDecimal("400000"));
            // 无计划月份放行，由 @BudgetCheck 兜底
        }
    }

    @Nested
    @DisplayName("saveAnnualBudget() 年度预算")
    class AnnualBudgetTests {

        @Test
        @DisplayName("异常路径 — 项目+年度重复编制被拦截")
        void saveAnnualBudget_duplicate_rejected() {
            com.zwinsight.finance.domain.BizFundAnnualBudget budget =
                    new com.zwinsight.finance.domain.BizFundAnnualBudget();
            budget.setBudgetYear(2026);
            budget.setProjectId(10L);
            budget.setIncomePlan(BigDecimal.TEN);
            budget.setExpensePlan(BigDecimal.TEN);
            when(annualBudgetMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> fundPlanService.saveAnnualBudget(budget))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已存在预算");
        }

        @Test
        @DisplayName("异常路径 — 负数计划金额被拦截")
        void saveAnnualBudget_negativeAmount_rejected() {
            com.zwinsight.finance.domain.BizFundAnnualBudget budget =
                    new com.zwinsight.finance.domain.BizFundAnnualBudget();
            budget.setBudgetYear(2026);
            budget.setProjectId(10L);
            budget.setIncomePlan(BigDecimal.ONE);
            budget.setExpensePlan(new BigDecimal("-100"));

            assertThatThrownBy(() -> fundPlanService.saveAnnualBudget(budget))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不能为负");
        }
    }

    @Nested
    @DisplayName("generateRollingForecast() 滚动预测")
    class RollingForecastTests {

        @Test
        @DisplayName("正常路径 — 已审批付款30万（当月到期）、计划收款50万 → 净缺口-20万（盈余），风险 LOW")
        void generateRollingForecast_gapComputed() {
            BizPaymentApply apply = new BizPaymentApply();
            apply.setPaymentAmount(new BigDecimal("300000"));
            // 付款日落在当月：属「计划内」而非逾期（V2026_63 后两者分开统计）
            apply.setPaymentDate(YearMonth.now().atDay(15));
            when(paymentApplyMapper.selectList(any())).thenReturn(List.of(apply));
            when(monthlyPlanMapper.selectOne(any())).thenReturn(approvedMonthlyPlan("500000"));

            var result = fundPlanService.generateRollingForecast(10L, 1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getExpectedPayments()).isEqualByComparingTo("300000");
            assertThat(result.get(0).getOverdueUnpaid()).isEqualByComparingTo("0");
            assertThat(result.get(0).getExpectedReceipts()).isEqualByComparingTo("500000");
            // 净缺口 = 付款 - 收款 = 300000 - 500000 = -200000（盈余）
            assertThat(result.get(0).getNetGap()).isEqualByComparingTo("-200000");
            assertThat(result.get(0).getRiskLevel()).isEqualTo("LOW");
        }

        @Test
        @DisplayName("异常路径 — 预测月数超出1-12范围被拦截")
        void generateRollingForecast_invalidMonths_rejected() {
            assertThatThrownBy(() -> fundPlanService.generateRollingForecast(null, 13))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("预测月数需在1-12之间");
        }

        @Test
        @DisplayName("收款侧数据源 — 存在应收台账时按到期日落月聚合，不再读月度计划（V2026_57）")
        void generateRollingForecast_receivableDrivenReceipts() {
            // 台账到期日落在预测窗口首月（当前月）
            BizReceivable r = new BizReceivable();
            r.setStatus(BizReceivable.STATUS_OPEN);
            r.setReceivableAmount(new BigDecimal("250000"));
            r.setWrittenOffAmount(new BigDecimal("50000"));
            r.setDueDate(YearMonth.now().atDay(15));
            when(receivableMapper.selectList(any())).thenReturn(List.of(r));
            when(paymentApplyMapper.selectList(any())).thenReturn(List.of());

            var result = fundPlanService.generateRollingForecast(10L, 1);

            // OPEN 余额 20万入账；月度计划未被读取（台账非空不走兜底）
            assertThat(result.get(0).getExpectedReceipts()).isEqualByComparingTo("200000");
            verify(monthlyPlanMapper, never()).selectOne(any());
        }

        // ---------- V2026_63：已逾期未付必须计入当月（线上 5850 万漏计缺陷的回归钉子） ----------

        private BizPaymentApply applyOn(String amount, YearMonth ym, int day) {
            BizPaymentApply a = new BizPaymentApply();
            a.setPaymentAmount(new BigDecimal(amount));
            a.setPaymentDate(ym.atDay(day));
            a.setStatus("APPROVED");
            a.setPayStatus(BizPaymentApply.PAY_STATUS_UNPAID);
            return a;
        }

        @Test
        @DisplayName("逾期未付计入当月 — 已过付款日仍未支付的单据不得从预测中消失，且不向后续月份摊开")
        void generateRollingForecast_overdueUnpaidCountedInCurrentMonthOnly() {
            // 两笔逾期（付款日在当月之前）+ 当月计划内一笔 + 下月一笔
            BizPaymentApply overdue1 = applyOn("2000000", YearMonth.now().minusMonths(3), 15);
            BizPaymentApply overdue2 = applyOn("500000", YearMonth.now().minusMonths(1), 10);
            BizPaymentApply inCurrent = applyOn("300000", YearMonth.now(), 20);
            BizPaymentApply inNext = applyOn("400000", YearMonth.now().plusMonths(1), 5);
            when(paymentApplyMapper.selectList(any()))
                    .thenReturn(List.of(overdue1, overdue2, inCurrent, inNext));
            when(receivableMapper.selectList(any())).thenReturn(List.of());
            when(monthlyPlanMapper.selectOne(any())).thenReturn(null);

            var result = fundPlanService.generateRollingForecast(10L, 2);

            // 当月 = 逾期 250万 + 计划内 30万 = 280万；overdueUnpaid 单列 250万（构成项，不可与总额相加）
            assertThat(result.get(0).getExpectedPayments()).isEqualByComparingTo("2800000");
            assertThat(result.get(0).getOverdueUnpaid()).isEqualByComparingTo("2500000");
            assertThat(result.get(0).getNetGap()).isEqualByComparingTo("2800000");
            // 下月仅计划内 40万：逾期不重复摊入后续月份
            assertThat(result.get(1).getExpectedPayments()).isEqualByComparingTo("400000");
            assertThat(result.get(1).getOverdueUnpaid()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("逾期计入后风险等级修正 — 收款无法覆盖时判 HIGH（原口径会因漏计而误判 LOW）")
        void generateRollingForecast_overdueRaisesRiskLevel() {
            BizPaymentApply overdue = applyOn("5000000", YearMonth.now().minusMonths(2), 10);
            when(paymentApplyMapper.selectList(any())).thenReturn(List.of(overdue));
            BizReceivable r = new BizReceivable();
            r.setStatus(BizReceivable.STATUS_OPEN);
            r.setReceivableAmount(new BigDecimal("100000"));
            r.setWrittenOffAmount(BigDecimal.ZERO);
            r.setDueDate(YearMonth.now().atDay(15));
            when(receivableMapper.selectList(any())).thenReturn(List.of(r));

            var result = fundPlanService.generateRollingForecast(10L, 1);

            // 付款 500万、收款 10万 → 缺口 490万，覆盖率 2% < 50% → HIGH
            assertThat(result.get(0).getNetGap()).isEqualByComparingTo("4900000");
            assertThat(result.get(0).getRiskLevel()).isEqualTo("HIGH");
        }

        @Test
        @DisplayName("无付款日单据防御 — paymentDate 为 null 的已审批单不落月也不报错")
        void generateRollingForecast_nullPaymentDateSkipped() {
            BizPaymentApply noDate = new BizPaymentApply();
            noDate.setPaymentAmount(new BigDecimal("999999"));
            noDate.setPaymentDate(null);
            when(paymentApplyMapper.selectList(any())).thenReturn(List.of(noDate));
            when(receivableMapper.selectList(any())).thenReturn(List.of());
            when(monthlyPlanMapper.selectOne(any())).thenReturn(null);

            var result = fundPlanService.generateRollingForecast(10L, 1);

            assertThat(result.get(0).getExpectedPayments()).isEqualByComparingTo("0");
            assertThat(result.get(0).getOverdueUnpaid()).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("futureExpenseTop() 待支付大额支出 TOP（V2026_63 含逾期）")
    class FutureExpenseTopTests {

        private BizPaymentApply applyWithCategory(String amount, java.time.LocalDate payDate, String category) {
            BizPaymentApply a = new BizPaymentApply();
            a.setPaymentAmount(new BigDecimal(amount));
            a.setPaymentDate(payDate);
            a.setPaymentCategory(category);
            a.setStatus("APPROVED");
            a.setPayStatus(BizPaymentApply.PAY_STATUS_UNPAID);
            return a;
        }

        @Test
        @DisplayName("正常路径 — 含已逾期单据，并单列 overdueAmount 构成（不混为一个数）")
        void futureExpenseTop_includesOverdueWithBreakdown() {
            java.time.LocalDate today = java.time.LocalDate.now();
            BizPaymentApply overdue = applyWithCategory("800000", today.minusDays(30), "MAT");
            BizPaymentApply upcoming = applyWithCategory("200000", today.plusDays(10), "MAT");
            when(paymentApplyMapper.selectList(any())).thenReturn(List.of(overdue, upcoming));
            BizFundCategory category = new BizFundCategory();
            category.setCode("MAT");
            category.setName("材料费");
            when(fundCategoryService.getByCode(anyString(), anyString())).thenReturn(category);

            var result = fundPlanService.futureExpenseTop(10L, 30);

            assertThat(result).hasSize(1);
            // 总额含逾期（原口径下界为 today 会返回空数组，线上实测已暴露）
            assertThat((BigDecimal) result.get(0).get("amount")).isEqualByComparingTo("1000000");
            assertThat((BigDecimal) result.get(0).get("overdueAmount")).isEqualByComparingTo("800000");
            assertThat(result.get(0).get("count")).isEqualTo(2);
            assertThat(result.get(0).get("categoryName")).isEqualTo("材料费");
        }

        @Test
        @DisplayName("异常路径 — days 超范围被拦截")
        void futureExpenseTop_invalidDays_rejected() {
            assertThatThrownBy(() -> fundPlanService.futureExpenseTop(null, 999))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("预测天数需在1-365之间");
        }
    }

    @Nested
    @DisplayName("forecastByDays() 未来 N 天预测（UI §9.2 的 30/60/90 天三档）")
    class ForecastByDaysTests {

        private BizPaymentApply applyOn(Long id, String amount, java.time.LocalDate payDate, String supplier) {
            BizPaymentApply a = new BizPaymentApply();
            a.setId(id);
            a.setProjectId(10L);
            a.setPaymentAmount(new BigDecimal(amount));
            a.setPaymentDate(payDate);
            a.setSupplierName(supplier);
            a.setStatus("APPROVED");
            a.setPayStatus(BizPaymentApply.PAY_STATUS_UNPAID);
            return a;
        }

        private BizReceivable openReceivable(String amount, String written, java.time.LocalDate dueDate) {
            BizReceivable r = new BizReceivable();
            r.setProjectId(10L);
            r.setStatus(BizReceivable.STATUS_OPEN);
            r.setReceivableAmount(new BigDecimal(amount));
            r.setWrittenOffAmount(new BigDecimal(written));
            r.setDueDate(dueDate);
            return r;
        }

        @Test
        @DisplayName("正常路径 — 累计窗口聚合：付款含逾期，netFlow（§9.2）与 gap（§10.4）两套口径并存")
        void forecastByDays_aggregatesBothBases() {
            java.time.LocalDate today = java.time.LocalDate.now();
            when(paymentApplyMapper.selectList(any())).thenReturn(List.of(
                    applyOn(1L, "1000000", today.minusDays(10), "甲公司"),
                    applyOn(2L, "600000", today.plusDays(20), "乙公司")));
            when(receivableMapper.selectList(any()))
                    .thenReturn(List.of(openReceivable("500000", "100000", today.plusDays(15))));
            when(bankFlowMapper.sumLatestBalances()).thenReturn(new BigDecimal("200000"));

            Map<String, Object> res = fundPlanService.forecastByDays(null, 30);

            assertThat(res.get("days")).isEqualTo(30);
            assertThat((BigDecimal) res.get("expectedPayments")).isEqualByComparingTo("1600000");
            assertThat((BigDecimal) res.get("overdueUnpaid")).isEqualByComparingTo("1000000");
            // OPEN 余额 = 50万 − 10万 = 40万
            assertThat((BigDecimal) res.get("expectedReceipts")).isEqualByComparingTo("400000");
            // §9.2 资金差额 = 回款 − 付款 = −120万（负数=净流出）
            assertThat((BigDecimal) res.get("netFlow")).isEqualByComparingTo("-1200000");
            // §10.4 可用资金 = 余额 20万 + 回款 40万 = 60万；缺口 = 160万 − 60万 = 100万
            assertThat((BigDecimal) res.get("availableFund")).isEqualByComparingTo("600000");
            assertThat((BigDecimal) res.get("gap")).isEqualByComparingTo("1000000");
            assertThat(res.get("coverable")).isEqualTo(false);
        }

        @Test
        @DisplayName("边界路径 — 可用资金足额覆盖时 coverable=true（§15 黄而非红）")
        void forecastByDays_coverableWhenFundSufficient() {
            java.time.LocalDate today = java.time.LocalDate.now();
            when(paymentApplyMapper.selectList(any()))
                    .thenReturn(List.of(applyOn(1L, "300000", today.plusDays(10), "甲公司")));
            when(receivableMapper.selectList(any())).thenReturn(List.of());
            when(bankFlowMapper.sumLatestBalances()).thenReturn(new BigDecimal("500000"));

            Map<String, Object> res = fundPlanService.forecastByDays(null, 30);

            assertThat((BigDecimal) res.get("gap")).isEqualByComparingTo("-200000");
            assertThat(res.get("coverable")).isEqualTo(true);
        }

        @Test
        @DisplayName("边界路径 — 部分支付只计剩余未付额（与滚动预测同口径）")
        void forecastByDays_partialPaidCountsRemainingOnly() {
            java.time.LocalDate today = java.time.LocalDate.now();
            BizPaymentApply partial = applyOn(3L, "1000000", today.plusDays(5), "甲公司");
            partial.setPayStatus(BizPaymentApply.PAY_STATUS_PARTIAL);
            when(paymentApplyMapper.selectList(any())).thenReturn(List.of(partial));
            when(receivableMapper.selectList(any())).thenReturn(List.of());
            // 已勾稽 60万 → 剩余 40万
            when(bankFlowMapper.matchedAmountByPaymentApply())
                    .thenReturn(java.util.Map.of(3L, new BigDecimal("600000")));
            when(bankFlowMapper.sumLatestBalances()).thenReturn(BigDecimal.ZERO);

            Map<String, Object> res = fundPlanService.forecastByDays(null, 30);

            assertThat((BigDecimal) res.get("expectedPayments")).isEqualByComparingTo("400000");
        }

        @Test
        @DisplayName("异常路径 — days 超范围被拒绍")
        void forecastByDays_invalidDays_rejected() {
            assertThatThrownBy(() -> fundPlanService.forecastByDays(null, 999))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("预测天数需在1-365之间");
        }
    }

    @Nested
    @DisplayName("gapAttribution() 缺口归因（§9.2：哪个项目导致 + 主要付款对象）")
    class GapAttributionTests {

        private BizPaymentApply applyOn(Long id, Long projectId, String amount,
                                        java.time.LocalDate payDate, String supplier) {
            BizPaymentApply a = new BizPaymentApply();
            a.setId(id);
            a.setProjectId(projectId);
            a.setPaymentAmount(new BigDecimal(amount));
            a.setPaymentDate(payDate);
            a.setSupplierName(supplier);
            a.setStatus("APPROVED");
            a.setPayStatus(BizPaymentApply.PAY_STATUS_UNPAID);
            return a;
        }

        private BizProject project(Long id, String name) {
            BizProject p = new BizProject();
            p.setId(id);
            p.setProjectName(name);
            return p;
        }

        @Test
        @DisplayName("正常路径 — 按项目与付款对象双维度归因，项目按净缺口降序")
        void attribution_byProjectAndPayee() {
            java.time.LocalDate today = java.time.LocalDate.now();
            when(paymentApplyMapper.selectList(any())).thenReturn(List.of(
                    applyOn(1L, 10L, "3000000", today.plusDays(10), "甲供应商"),
                    applyOn(2L, 10L, "1000000", today.plusDays(20), "乙供应商"),
                    applyOn(3L, 20L, "500000", today.plusDays(5), "甲供应商")));
            when(receivableMapper.selectList(any())).thenReturn(List.of());
            when(projectMapper.selectById(10L)).thenReturn(project(10L, "项目A"));
            when(projectMapper.selectById(20L)).thenReturn(project(20L, "项目B"));

            Map<String, Object> res = fundPlanService.gapAttribution(null, 90, 10);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> byProject = (List<Map<String, Object>>) res.get("byProject");
            assertThat(byProject).hasSize(2);
            // 项目A 缺口 400万 排在项目B 50万 之前
            assertThat(byProject.get(0).get("projectName")).isEqualTo("项目A");
            assertThat((BigDecimal) byProject.get(0).get("netGap")).isEqualByComparingTo("4000000");
            assertThat((BigDecimal) byProject.get(1).get("netGap")).isEqualByComparingTo("500000");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> byPayee = (List<Map<String, Object>>) res.get("byPayee");
            assertThat(byPayee).hasSize(2);
            // 甲供应商合计 350万（两笔）排首
            assertThat(byPayee.get(0).get("supplierName")).isEqualTo("甲供应商");
            assertThat((BigDecimal) byPayee.get(0).get("amount")).isEqualByComparingTo("3500000");
            assertThat(byPayee.get(0).get("count")).isEqualTo(2);
        }

        @Test
        @DisplayName("边界路径 — 无供应商名称归入「（未登记收款方）」，不隐藏也不丢弃")
        void attribution_missingSupplierGroupedExplicitly() {
            java.time.LocalDate today = java.time.LocalDate.now();
            when(paymentApplyMapper.selectList(any())).thenReturn(List.of(
                    applyOn(1L, 10L, "800000", today.plusDays(10), null)));
            when(receivableMapper.selectList(any())).thenReturn(List.of());
            when(projectMapper.selectById(10L)).thenReturn(project(10L, "项目A"));

            Map<String, Object> res = fundPlanService.gapAttribution(null, 30, 10);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> byPayee = (List<Map<String, Object>>) res.get("byPayee");
            assertThat(byPayee).hasSize(1);
            assertThat(byPayee.get(0).get("supplierName")).isEqualTo("（未登记收款方）");
        }

        @Test
        @DisplayName("边界路径 — topN 限制生效（只返回前 N 条）")
        void attribution_topNLimits() {
            java.time.LocalDate today = java.time.LocalDate.now();
            when(paymentApplyMapper.selectList(any())).thenReturn(List.of(
                    applyOn(1L, 10L, "300000", today.plusDays(1), "A"),
                    applyOn(2L, 10L, "200000", today.plusDays(2), "B"),
                    applyOn(3L, 10L, "100000", today.plusDays(3), "C")));
            when(receivableMapper.selectList(any())).thenReturn(List.of());
            when(projectMapper.selectById(10L)).thenReturn(project(10L, "项目A"));

            Map<String, Object> res = fundPlanService.gapAttribution(null, 30, 2);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> byPayee = (List<Map<String, Object>>) res.get("byPayee");
            assertThat(byPayee).hasSize(2);
            assertThat(byPayee.get(0).get("supplierName")).isEqualTo("A");
        }

        @Test
        @DisplayName("异常路径 — topN 超范围被拒绍（不静默截取）")
        void attribution_invalidTopN_rejected() {
            assertThatThrownBy(() -> fundPlanService.gapAttribution(null, 30, 999))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("TOP 条数需在1-50之间");
        }
    }

    @Nested
    @DisplayName("saveMonthlyPlan() 科目明细级联保存（V2026_58）")
    class PlanDetailTests {

        private BizFundMonthlyPlan newPlan(String income, String expense) {
            BizFundMonthlyPlan plan = new BizFundMonthlyPlan();
            plan.setPlanYear(2026);
            plan.setPlanMonth(11);
            plan.setProjectId(10L);
            plan.setIncomePlan(new BigDecimal(income));
            plan.setExpensePlan(new BigDecimal(expense));
            return plan;
        }

        private BizFundPlanDetail detail(String direction, String code, String amount) {
            BizFundPlanDetail d = new BizFundPlanDetail();
            d.setDirection(direction);
            d.setCategoryCode(code);
            d.setAmount(new BigDecimal(amount));
            return d;
        }

        @Test
        @DisplayName("正常路径 — 明细合计与总额一致时级联落库")
        void savePlan_withConsistentDetails_inserts() {
            BizFundMonthlyPlan plan = newPlan("0", "300000");
            List<BizFundPlanDetail> details = new ArrayList<>();
            details.add(detail(BizFundPlanDetail.DIRECTION_EXPENSE, "EXP-DIRECT-MATERIAL", "200000"));
            details.add(detail(BizFundPlanDetail.DIRECTION_EXPENSE, "EXP-DIRECT-LABOR", "100000"));
            plan.setDetails(details);
            when(monthlyPlanMapper.selectOne(any())).thenReturn(null);
            // 模拟 MyBatis-Plus 雪花主键回填，使明细 planId 绑定断言真实有效
            when(monthlyPlanMapper.insert(any(BizFundMonthlyPlan.class))).thenAnswer(inv -> {
                inv.getArgument(0, BizFundMonthlyPlan.class).setId(77L);
                return 1;
            });
            BizFundCategory category = new BizFundCategory();
            category.setCode("X");
            when(fundCategoryService.getByCode(anyString(), anyString())).thenReturn(category);

            fundPlanService.saveMonthlyPlan(plan);

            verify(monthlyPlanMapper).insert(plan);
            // 明细逐笔落库，且 planId 在插入时已绑定主计划
            org.mockito.ArgumentCaptor<BizFundPlanDetail> captor =
                    org.mockito.ArgumentCaptor.forClass(BizFundPlanDetail.class);
            verify(planDetailMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
            assertThat(captor.getAllValues())
                    .extracting(BizFundPlanDetail::getPlanId)
                    .containsExactly(77L, 77L);
        }

        @Test
        @DisplayName("异常路径 — 明细合计与计划总额不一致被拦截（不静默落库）")
        void savePlan_inconsistentDetails_rejected() {
            BizFundMonthlyPlan plan = newPlan("0", "300000");
            List<BizFundPlanDetail> details = new ArrayList<>();
            details.add(detail(BizFundPlanDetail.DIRECTION_EXPENSE, "EXP-DIRECT-MATERIAL", "100000"));
            plan.setDetails(details);
            BizFundCategory category = new BizFundCategory();
            when(fundCategoryService.getByCode(anyString(), anyString())).thenReturn(category);

            assertThatThrownBy(() -> fundPlanService.saveMonthlyPlan(plan))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不一致");
            verify(monthlyPlanMapper, never()).insert(any(BizFundMonthlyPlan.class));
        }

        @Test
        @DisplayName("异常路径 — 同方向科目重复被拦截")
        void savePlan_duplicateCategory_rejected() {
            BizFundMonthlyPlan plan = newPlan("0", "300000");
            List<BizFundPlanDetail> details = new ArrayList<>();
            details.add(detail(BizFundPlanDetail.DIRECTION_EXPENSE, "EXP-DIRECT-MATERIAL", "150000"));
            details.add(detail(BizFundPlanDetail.DIRECTION_EXPENSE, "EXP-DIRECT-MATERIAL", "150000"));
            plan.setDetails(details);
            BizFundCategory category = new BizFundCategory();
            when(fundCategoryService.getByCode(anyString(), anyString())).thenReturn(category);

            assertThatThrownBy(() -> fundPlanService.saveMonthlyPlan(plan))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("同方向科目重复");
        }

        @Test
        @DisplayName("兼容路径 — 无明细（details=null）仍按总额保存，向后兼容存量计划")
        void savePlan_withoutDetails_backwardCompatible() {
            BizFundMonthlyPlan plan = newPlan("100000", "300000");
            when(monthlyPlanMapper.selectOne(any())).thenReturn(null);

            fundPlanService.saveMonthlyPlan(plan);

            verify(monthlyPlanMapper).insert(plan);
            verify(planDetailMapper, never()).insert(any(BizFundPlanDetail.class));
        }
    }
}
