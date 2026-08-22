package com.zwinsight.finance.service;

import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizConstructionContract;
import com.zwinsight.contract.mapper.BizConstructionContractMapper;
import com.zwinsight.finance.domain.BizPaymentReceived;
import com.zwinsight.finance.mapper.BizPaymentReceivedMapper;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.mapper.BizProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PaymentReceivedService 单元测试
 * 覆盖 P0-4：回款登记金额上限校验（不能超过已开票未收金额）+ 回写项目总收入 & 合同累计收款
 */
@ExtendWith(MockitoExtension.class)
class PaymentReceivedServiceTest {

    @Mock private BizPaymentReceivedMapper paymentReceivedMapper;
    @Mock private BizProjectMapper projectMapper;
    @Mock private BizConstructionContractMapper contractMapper;

    @InjectMocks
    private PaymentReceivedService paymentReceivedService;

    private BizPaymentReceived samplePayment;

    @BeforeEach
    void setUp() {
        samplePayment = new BizPaymentReceived();
        samplePayment.setId(1L);
        samplePayment.setProjectId(100L);
        samplePayment.setContractId(10L);
        samplePayment.setReceiveAmount(new BigDecimal("50000"));
    }

    private BizConstructionContract contract(String invoiced, String received) {
        BizConstructionContract c = new BizConstructionContract();
        c.setId(10L);
        c.setCumulativeInvoiceAmount(invoiced == null ? null : new BigDecimal(invoiced));
        c.setCumulativeReceivedAmount(received == null ? null : new BigDecimal(received));
        return c;
    }

    @Nested
    @DisplayName("回款上限校验")
    class CapValidationTests {
        @Test
        @DisplayName("回款金额超过已开票未收金额：抛异常且不落库")
        void save_exceedsCap_throwsAndNoInsert() {
            // 已开票 100000 - 已回款 60000 = 可回款 40000，本次 50000 超限
            when(contractMapper.selectById(10L)).thenReturn(contract("100000", "60000"));

            assertThatThrownBy(() -> paymentReceivedService.save(samplePayment))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("回款金额不能超过已开票未收金额");

            verify(paymentReceivedMapper, never()).insert(any());
            verify(projectMapper, never()).updateById(any());
            verify(contractMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("回款金额等于上限：允许通过（边界）")
        void save_equalsCap_allowed() {
            // 可回款 50000，本次 50000 恰好等于上限
            when(contractMapper.selectById(10L)).thenReturn(contract("80000", "30000"));
            when(projectMapper.selectById(100L)).thenReturn(null);

            paymentReceivedService.save(samplePayment);

            verify(paymentReceivedMapper).insert(samplePayment);
        }
    }

    @Nested
    @DisplayName("落库与回写")
    class SaveAndWritebackTests {
        @Test
        @DisplayName("保存：状态置 APPROVED + 回写项目总收入 + 合同累计收款")
        void save_writesBackProjectAndContract() {
            when(contractMapper.selectById(10L)).thenReturn(contract("200000", "30000"));
            BizProject project = new BizProject();
            project.setId(100L);
            project.setTotalIncome(new BigDecimal("120000"));
            when(projectMapper.selectById(100L)).thenReturn(project);

            paymentReceivedService.save(samplePayment);

            assertThat(samplePayment.getStatus()).isEqualTo("APPROVED");
            verify(paymentReceivedMapper).insert(samplePayment);
            // 项目总收入 120000 + 50000 = 170000
            verify(projectMapper).updateById(argThat(p ->
                    p.getTotalIncome().compareTo(new BigDecimal("170000")) == 0));
            // 合同累计收款 30000 + 50000 = 80000
            verify(contractMapper).updateById(argThat(c ->
                    c.getCumulativeReceivedAmount().compareTo(new BigDecimal("80000")) == 0));
        }

        @Test
        @DisplayName("保存：contractId 为 null 时不校验上限、不回写合同")
        void save_contractIdNull_skipContract() {
            samplePayment.setContractId(null);
            BizProject project = new BizProject();
            project.setId(100L);
            project.setTotalIncome(null);
            when(projectMapper.selectById(100L)).thenReturn(project);

            paymentReceivedService.save(samplePayment);

            verify(contractMapper, never()).selectById(any());
            verify(contractMapper, never()).updateById(any());
            // 项目总收入 null 从零累加 = 50000
            verify(projectMapper).updateById(argThat(p ->
                    p.getTotalIncome().compareTo(new BigDecimal("50000")) == 0));
        }

        @Test
        @DisplayName("保存：合同不存在时不校验上限、不回写合同")
        void save_contractNotFound_skipContract() {
            when(contractMapper.selectById(10L)).thenReturn(null);
            when(projectMapper.selectById(100L)).thenReturn(null);

            paymentReceivedService.save(samplePayment);

            verify(paymentReceivedMapper).insert(samplePayment);
            verify(contractMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("保存：项目不存在时跳过项目回写，仍回写合同")
        void save_projectNotFound_skipProjectWriteback() {
            when(contractMapper.selectById(10L)).thenReturn(contract("200000", "0"));
            when(projectMapper.selectById(100L)).thenReturn(null);

            paymentReceivedService.save(samplePayment);

            verify(projectMapper, never()).updateById(any());
            verify(contractMapper).updateById(argThat(c ->
                    c.getCumulativeReceivedAmount().compareTo(new BigDecimal("50000")) == 0));
        }
    }

    @Nested
    @DisplayName("查询")
    class QueryTests {
        @Test
        @DisplayName("查询详情：不存在抛异常")
        void getById_notFound_throws() {
            when(paymentReceivedMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> paymentReceivedService.getById(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("收款记录不存在");
        }
    }

    @Nested
    @DisplayName("删除回冲（与 save 回写对称）")
    class DeleteRollbackTests {
        @Test
        @DisplayName("删除：回冲项目总收入与合同累计收款")
        void delete_rollsBackProjectAndContract() {
            when(paymentReceivedMapper.selectById(1L)).thenReturn(samplePayment);
            BizProject project = new BizProject();
            project.setId(100L);
            project.setTotalIncome(new BigDecimal("200000"));
            when(projectMapper.selectById(100L)).thenReturn(project);
            when(contractMapper.selectById(10L)).thenReturn(contract("300000", "150000"));

            paymentReceivedService.delete(1L);

            verify(paymentReceivedMapper).deleteById(1L);
            // 项目总收入 200000 - 50000 = 150000
            verify(projectMapper).updateById(argThat(p ->
                    p.getTotalIncome().compareTo(new BigDecimal("150000")) == 0));
            // 合同累计收款 150000 - 50000 = 100000
            verify(contractMapper).updateById(argThat(c ->
                    c.getCumulativeReceivedAmount().compareTo(new BigDecimal("100000")) == 0));
        }

        @Test
        @DisplayName("删除：记录不存在抛异常且不删除")
        void delete_notFound_throws() {
            when(paymentReceivedMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> paymentReceivedService.delete(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("收款记录不存在");

            verify(paymentReceivedMapper, never()).deleteById(any());
        }

        @Test
        @DisplayName("删除：无合同关联时仅回冲项目收入")
        void delete_noContract_rollsBackProjectOnly() {
            samplePayment.setContractId(null);
            when(paymentReceivedMapper.selectById(1L)).thenReturn(samplePayment);
            BizProject project = new BizProject();
            project.setId(100L);
            project.setTotalIncome(new BigDecimal("80000"));
            when(projectMapper.selectById(100L)).thenReturn(project);

            paymentReceivedService.delete(1L);

            verify(paymentReceivedMapper).deleteById(1L);
            verify(projectMapper).updateById(argThat(p ->
                    p.getTotalIncome().compareTo(new BigDecimal("30000")) == 0));
            verify(contractMapper, never()).updateById(any());
        }
    }

    @Nested
    @DisplayName("金额有效性（B2：负数/零拒绝）")
    class AmountValidityTests {
        @Test
        @DisplayName("保存：负数金额拒绝且不落库（原实现可反向扣减累计字段）")
        void save_negativeAmount_rejected() {
            samplePayment.setReceiveAmount(new BigDecimal("-10000"));

            assertThatThrownBy(() -> paymentReceivedService.save(samplePayment))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("回款金额必须大于0");

            verify(paymentReceivedMapper, never()).insert(any());
        }

        @Test
        @DisplayName("保存：零金额拒绝")
        void save_zeroAmount_rejected() {
            samplePayment.setReceiveAmount(BigDecimal.ZERO);

            assertThatThrownBy(() -> paymentReceivedService.save(samplePayment))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("回款金额必须大于0");
        }
    }

    @Nested
    @DisplayName("更新差额回冲（B1：与 save/delete 对称）")
    class UpdateDiffTests {
        @Test
        @DisplayName("改大金额：按差额追加项目收入与合同累计收款，增量校验可回款上限")
        void update_increase_addsDiff() {
            // 原额 50000 改 70000，差额 +20000；可回款 = 300000-150000 = 150000 充足
            when(paymentReceivedMapper.selectById(1L)).thenReturn(samplePayment);
            when(contractMapper.selectById(10L)).thenReturn(contract("300000", "150000"));
            BizProject project = new BizProject();
            project.setId(100L);
            project.setTotalIncome(new BigDecimal("200000"));
            when(projectMapper.selectById(100L)).thenReturn(project);

            BizPaymentReceived updated = new BizPaymentReceived();
            updated.setId(1L);
            updated.setReceiveAmount(new BigDecimal("70000"));
            paymentReceivedService.update(updated);

            verify(paymentReceivedMapper).updateById(updated);
            // 项目总收入 200000 + 20000 = 220000
            verify(projectMapper).updateById(argThat(p ->
                    p.getTotalIncome().compareTo(new BigDecimal("220000")) == 0));
            // 合同累计收款 150000 + 20000 = 170000
            verify(contractMapper).updateById(argThat(c ->
                    c.getCumulativeReceivedAmount().compareTo(new BigDecimal("170000")) == 0));
        }

        @Test
        @DisplayName("改小金额：按差额回冲（不校验上限）")
        void update_decrease_subtractsDiff() {
            // 原额 50000 改 30000，差额 -20000
            when(paymentReceivedMapper.selectById(1L)).thenReturn(samplePayment);
            BizProject project = new BizProject();
            project.setId(100L);
            project.setTotalIncome(new BigDecimal("200000"));
            when(projectMapper.selectById(100L)).thenReturn(project);
            when(contractMapper.selectById(10L)).thenReturn(contract("300000", "150000"));

            BizPaymentReceived updated = new BizPaymentReceived();
            updated.setId(1L);
            updated.setReceiveAmount(new BigDecimal("30000"));
            paymentReceivedService.update(updated);

            // 项目总收入 200000 - 20000 = 180000
            verify(projectMapper).updateById(argThat(p ->
                    p.getTotalIncome().compareTo(new BigDecimal("180000")) == 0));
            // 合同累计收款 150000 - 20000 = 130000
            verify(contractMapper).updateById(argThat(c ->
                    c.getCumulativeReceivedAmount().compareTo(new BigDecimal("130000")) == 0));
        }

        @Test
        @DisplayName("改大超出可回款额度：拒绝且不更新")
        void update_increaseExceedsCap_rejected() {
            // 原额 50000 改 90000，差额 +40000；可回款 = 100000-90000 = 10000 不足
            when(paymentReceivedMapper.selectById(1L)).thenReturn(samplePayment);
            when(contractMapper.selectById(10L)).thenReturn(contract("100000", "90000"));

            BizPaymentReceived updated = new BizPaymentReceived();
            updated.setId(1L);
            updated.setReceiveAmount(new BigDecimal("90000"));

            assertThatThrownBy(() -> paymentReceivedService.update(updated))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("回款金额不能超过已开票未收金额");

            verify(paymentReceivedMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("编辑金额为负/零拒绝（P1 FIN-RCV-10）")
        void update_invalidAmount_rejected() {
            when(paymentReceivedMapper.selectById(1L)).thenReturn(samplePayment);

            BizPaymentReceived neg = new BizPaymentReceived();
            neg.setId(1L);
            neg.setReceiveAmount(new BigDecimal("-100"));
            assertThatThrownBy(() -> paymentReceivedService.update(neg))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("回款金额必须大于0");

            BizPaymentReceived zero = new BizPaymentReceived();
            zero.setId(1L);
            zero.setReceiveAmount(BigDecimal.ZERO);
            assertThatThrownBy(() -> paymentReceivedService.update(zero))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("回款金额必须大于0");

            verify(paymentReceivedMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("记录不存在：拒绝更新")
        void update_notFound_throws() {
            when(paymentReceivedMapper.selectById(999L)).thenReturn(null);

            BizPaymentReceived updated = new BizPaymentReceived();
            updated.setId(999L);
            updated.setReceiveAmount(new BigDecimal("100"));

            assertThatThrownBy(() -> paymentReceivedService.update(updated))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("收款记录不存在");
        }
    }

    @Nested
    @DisplayName("回款认领/核销状态机（P0 Req4）")
    class ClaimStateTests {

        private BizPaymentReceived withClaimStatus(String claimStatus) {
            BizPaymentReceived r = new BizPaymentReceived();
            r.setId(1L);
            r.setProjectId(100L);
            r.setClaimStatus(claimStatus);
            return r;
        }

        @Test
        @DisplayName("认领成功：UNCLAIMED → CLAIMED，记录认领人与时间")
        void claim_unclaimed_success() {
            BizPaymentReceived record = withClaimStatus("UNCLAIMED");
            when(paymentReceivedMapper.selectById(1L)).thenReturn(record);
            try (var sc = mockStatic(SecurityContextHolder.class)) {
                sc.when(SecurityContextHolder::getUserId).thenReturn(88L);

                paymentReceivedService.claim(1L);
            }

            assertThat(record.getClaimStatus()).isEqualTo("CLAIMED");
            assertThat(record.getClaimedBy()).isEqualTo(88L);
            assertThat(record.getClaimedAt()).isNotNull();
            verify(paymentReceivedMapper).updateById(record);
        }

        @Test
        @DisplayName("存量数据 claimStatus 为 null：按待认领处理可认领")
        void claim_nullStatus_treatedAsUnclaimed() {
            BizPaymentReceived record = withClaimStatus(null);
            when(paymentReceivedMapper.selectById(1L)).thenReturn(record);
            try (var sc = mockStatic(SecurityContextHolder.class)) {
                sc.when(SecurityContextHolder::getUserId).thenReturn(88L);

                paymentReceivedService.claim(1L);
            }

            assertThat(record.getClaimStatus()).isEqualTo("CLAIMED");
        }

        @Test
        @DisplayName("重复认领：CLAIMED 再认领拒绝")
        void claim_alreadyClaimed_rejected() {
            when(paymentReceivedMapper.selectById(1L)).thenReturn(withClaimStatus("CLAIMED"));

            assertThatThrownBy(() -> paymentReceivedService.claim(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅待认领的回款可以认领");
            verify(paymentReceivedMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("已核销再认领：拒绝")
        void claim_writtenOff_rejected() {
            when(paymentReceivedMapper.selectById(1L)).thenReturn(withClaimStatus("WRITTEN_OFF"));

            assertThatThrownBy(() -> paymentReceivedService.claim(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅待认领的回款可以认领");
            verify(paymentReceivedMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("核销成功：CLAIMED → WRITTEN_OFF")
        void writeOff_claimed_success() {
            BizPaymentReceived record = withClaimStatus("CLAIMED");
            when(paymentReceivedMapper.selectById(1L)).thenReturn(record);

            paymentReceivedService.writeOff(1L);

            assertThat(record.getClaimStatus()).isEqualTo("WRITTEN_OFF");
            verify(paymentReceivedMapper).updateById(record);
        }

        @Test
        @DisplayName("未认领直接核销：拒绝")
        void writeOff_unclaimed_rejected() {
            when(paymentReceivedMapper.selectById(1L)).thenReturn(withClaimStatus("UNCLAIMED"));

            assertThatThrownBy(() -> paymentReceivedService.writeOff(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅已认领的回款可以核销");
            verify(paymentReceivedMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("重复核销：拒绝")
        void writeOff_alreadyWrittenOff_rejected() {
            when(paymentReceivedMapper.selectById(1L)).thenReturn(withClaimStatus("WRITTEN_OFF"));

            assertThatThrownBy(() -> paymentReceivedService.writeOff(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅已认领的回款可以核销");
            verify(paymentReceivedMapper, never()).updateById(any());
        }

        @Test
        @DisplayName("记录不存在：认领/核销均拒绝")
        void claimAndWriteOff_notFound_throws() {
            when(paymentReceivedMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> paymentReceivedService.claim(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("收款记录不存在");
            assertThatThrownBy(() -> paymentReceivedService.writeOff(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("收款记录不存在");
        }
    }
}
