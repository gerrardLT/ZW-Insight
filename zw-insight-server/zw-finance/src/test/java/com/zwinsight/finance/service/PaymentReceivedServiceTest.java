package com.zwinsight.finance.service;

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
}
