package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.finance.domain.BizFinancing;
import com.zwinsight.finance.domain.BizFinancingRepayment;
import com.zwinsight.finance.mapper.BizFinancingMapper;
import com.zwinsight.finance.mapper.BizFinancingRepaymentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FinancingService 单元测试（54_V2026_52 融资借贷）
 * <p>核心断言：三种还款方式的「本金守恒」（各期本金之和 = 借款本金，末期校正防舍入漂移）、
 * 首期利息精确值、还款核销的超还拦截与结清联动。</p>
 */
@ExtendWith(MockitoExtension.class)
class FinancingServiceTest {

    @Mock private BizFinancingMapper financingMapper;
    @Mock private BizFinancingRepaymentMapper repaymentMapper;

    @InjectMocks
    private FinancingService financingService;

    /** 100 万本金、年利率 4.5%（月利率 0.00375）、12 期 */
    private BizFinancing sampleFinancing(String method) {
        BizFinancing financing = new BizFinancing();
        financing.setFinancingType(BizFinancing.TYPE_BANK_LOAN);
        financing.setContractNo("LOAN-2026-001");
        financing.setLenderName("建设银行");
        financing.setPrincipal(new BigDecimal("1000000"));
        financing.setAnnualRate(new BigDecimal("0.045"));
        financing.setStartDate(LocalDate.of(2026, 1, 1));
        financing.setTermMonths(12);
        financing.setRepaymentMethod(method);
        return financing;
    }

    /** 捕获生成的还款计划（register 内 insert 逐条调用） */
    private List<BizFinancingRepayment> capturedPlan() {
        ArgumentCaptor<BizFinancingRepayment> captor =
                ArgumentCaptor.forClass(BizFinancingRepayment.class);
        verify(repaymentMapper, times(12)).insert(captor.capture());
        return captor.getAllValues();
    }

    @Nested
    @DisplayName("register() 等额本息计划")
    class EqualInstallmentTests {

        @Test
        @DisplayName("正常路径 — 12期计划、首期利息3750、本金守恒100万")
        void equalInstallment_principalConserved() {
            BizFinancing financing = sampleFinancing(BizFinancing.METHOD_EQUAL_INSTALLMENT);
            when(financingMapper.selectCount(any())).thenReturn(0L);

            financingService.register(financing);

            List<BizFinancingRepayment> plan = capturedPlan();
            assertThat(plan).hasSize(12);
            // 首期利息 = 100万 × 0.00375 = 3750.00（精确）
            assertThat(plan.get(0).getInterestDue()).isEqualByComparingTo("3750.00");
            // 本金守恒：各期本金之和 = 100万（末期校正消除舍入漂移）
            BigDecimal principalSum = plan.stream()
                    .map(BizFinancingRepayment::getPrincipalDue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(principalSum).isEqualByComparingTo("1000000");
            // 台账回写计划总利息
            assertThat(financing.getTotalInterest())
                    .isEqualByComparingTo(plan.stream()
                            .map(BizFinancingRepayment::getInterestDue)
                            .reduce(BigDecimal.ZERO, BigDecimal::add));
        }
    }

    @Nested
    @DisplayName("register() 等额本金计划")
    class EqualPrincipalTests {

        @Test
        @DisplayName("正常路径 — 每期本金83333.33（末期余差校正）、利息递减、本金守恒")
        void equalPrincipal_principalConserved() {
            BizFinancing financing = sampleFinancing(BizFinancing.METHOD_EQUAL_PRINCIPAL);
            when(financingMapper.selectCount(any())).thenReturn(0L);

            financingService.register(financing);

            List<BizFinancingRepayment> plan = capturedPlan();
            assertThat(plan).hasSize(12);
            // 第 1 期：本金 83333.33、利息 3750.00
            assertThat(plan.get(0).getPrincipalDue()).isEqualByComparingTo("83333.33");
            assertThat(plan.get(0).getInterestDue()).isEqualByComparingTo("3750.00");
            // 第 2 期利息 = (100万-83333.33)×0.00375 = 3437.50
            assertThat(plan.get(1).getInterestDue()).isEqualByComparingTo("3437.50");
            // 本金守恒
            BigDecimal principalSum = plan.stream()
                    .map(BizFinancingRepayment::getPrincipalDue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(principalSum).isEqualByComparingTo("1000000");
        }
    }

    @Nested
    @DisplayName("register() 到期还本付息")
    class BulletTests {

        @Test
        @DisplayName("正常路径 — 单期：本金100万、利息45000（100万×0.00375×12）")
        void bullet_singleInstallment() {
            BizFinancing financing = sampleFinancing(BizFinancing.METHOD_BULLET);
            when(financingMapper.selectCount(any())).thenReturn(0L);

            financingService.register(financing);

            ArgumentCaptor<BizFinancingRepayment> captor =
                    ArgumentCaptor.forClass(BizFinancingRepayment.class);
            verify(repaymentMapper, times(1)).insert(captor.capture());
            BizFinancingRepayment only = captor.getValue();
            assertThat(only.getPrincipalDue()).isEqualByComparingTo("1000000");
            assertThat(only.getInterestDue()).isEqualByComparingTo("45000.00");
        }
    }

    @Nested
    @DisplayName("register() 校验")
    class RegisterValidationTests {

        @Test
        @DisplayName("异常路径 — 借款合同编号重复被拦截")
        void register_duplicateContractNo_rejected() {
            BizFinancing financing = sampleFinancing(BizFinancing.METHOD_BULLET);
            when(financingMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> financingService.register(financing))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已存在");
        }

        @Test
        @DisplayName("异常路径 — 年利率超[0,1)区间被拦截")
        void register_invalidRate_rejected() {
            BizFinancing financing = sampleFinancing(BizFinancing.METHOD_BULLET);
            financing.setAnnualRate(new BigDecimal("4.5")); // 误填百分数

            assertThatThrownBy(() -> financingService.register(financing))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("年利率不合法");
        }
    }

    @Nested
    @DisplayName("recordRepayment() 还款核销")
    class RepaymentTests {

        private BizFinancing activeFinancing() {
            BizFinancing financing = sampleFinancing(BizFinancing.METHOD_BULLET);
            financing.setId(1L);
            financing.setStatus(BizFinancing.STATUS_ACTIVE);
            financing.setTotalRepaid(BigDecimal.ZERO);
            financing.setTotalInterest(new BigDecimal("45000"));
            return financing;
        }

        @Test
        @DisplayName("正常路径 — 单期全额还清：期置PAID、台账totalRepaid更新、全部还清置SETTLED")
        void recordRepayment_fullPayment_settled() {
            BizFinancing financing = activeFinancing();
            when(financingMapper.selectById(1L)).thenReturn(financing);
            BizFinancingRepayment installment = new BizFinancingRepayment();
            installment.setId(10L);
            installment.setFinancingId(1L);
            installment.setPeriodNo(1);
            installment.setPrincipalDue(new BigDecimal("1000000"));
            installment.setInterestDue(new BigDecimal("45000"));
            installment.setPrincipalPaid(BigDecimal.ZERO);
            installment.setInterestPaid(BigDecimal.ZERO);
            installment.setStatus(BizFinancingRepayment.STATUS_PENDING);
            when(repaymentMapper.selectOne(any())).thenReturn(installment);
            when(repaymentMapper.selectCount(any())).thenReturn(0L);

            financingService.recordRepayment(1L, 1, new BigDecimal("45000"), new BigDecimal("1000000"), null);

            assertThat(installment.getStatus()).isEqualTo(BizFinancingRepayment.STATUS_PAID);
            assertThat(financing.getTotalRepaid()).isEqualByComparingTo("1045000");
            assertThat(financing.getStatus()).isEqualTo(BizFinancing.STATUS_SETTLED);
            verify(repaymentMapper).updateById(installment);
            verify(financingMapper).updateById(financing);
        }

        @Test
        @DisplayName("异常路径 — 实还本金超过该期本金余额被拦截")
        void recordRepayment_overPrincipal_rejected() {
            BizFinancing financing = activeFinancing();
            when(financingMapper.selectById(1L)).thenReturn(financing);
            BizFinancingRepayment installment = new BizFinancingRepayment();
            installment.setFinancingId(1L);
            installment.setPeriodNo(1);
            installment.setPrincipalDue(new BigDecimal("1000000"));
            installment.setInterestDue(new BigDecimal("45000"));
            installment.setPrincipalPaid(BigDecimal.ZERO);
            installment.setInterestPaid(BigDecimal.ZERO);
            installment.setStatus(BizFinancingRepayment.STATUS_PENDING);
            when(repaymentMapper.selectOne(any())).thenReturn(installment);

            assertThatThrownBy(() -> financingService.recordRepayment(
                    1L, 1, BigDecimal.ZERO, new BigDecimal("1000001"), null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("超过该期本金余额");
        }

        @Test
        @DisplayName("异常路径 — 已结清融资拒绝重复还款")
        void recordRepayment_settled_rejected() {
            BizFinancing financing = activeFinancing();
            financing.setStatus(BizFinancing.STATUS_SETTLED);
            when(financingMapper.selectById(1L)).thenReturn(financing);

            assertThatThrownBy(() -> financingService.recordRepayment(1L, 1, BigDecimal.ONE, BigDecimal.ZERO, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已结清");
        }
    }
}
