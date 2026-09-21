package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizFundMonthlyPlan;
import com.zwinsight.finance.domain.BizPaymentApply;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * FundPlanService 单元测试（53_V2026_51 资金计划三层联动）
 * <p>核心断言：先计划后支付（月度计划校验）、滚动预测净缺口与风险等级。</p>
 */
@ExtendWith(MockitoExtension.class)
class FundPlanServiceTest {

    @Mock private BizFundAnnualBudgetMapper annualBudgetMapper;
    @Mock private BizFundMonthlyPlanMapper monthlyPlanMapper;
    @Mock private BizFundRollingForecastMapper rollingForecastMapper;
    @Mock private BizPaymentApplyMapper paymentApplyMapper;
    @Mock private BizPaymentReceivedMapper paymentReceivedMapper;

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
        @DisplayName("正常路径 — 已审批付款30万、计划收款50万 → 净缺口-20万（盈余），风险 LOW")
        void generateRollingForecast_gapComputed() {
            BizPaymentApply apply = new BizPaymentApply();
            apply.setPaymentAmount(new BigDecimal("300000"));
            when(paymentApplyMapper.selectList(any())).thenReturn(List.of(apply));
            when(monthlyPlanMapper.selectOne(any())).thenReturn(approvedMonthlyPlan("500000"));

            var result = fundPlanService.generateRollingForecast(10L, 1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getExpectedPayments()).isEqualByComparingTo("300000");
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
    }
}
