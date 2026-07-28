package com.zwinsight.finance.service;

import com.zwinsight.common.exception.BusinessException;
import com.zwinsight.contract.domain.BizOtherContract;
import com.zwinsight.contract.mapper.BizOtherContractMapper;
import com.zwinsight.finance.domain.BizPaymentApply;
import com.zwinsight.finance.mapper.BizPaymentApplyMapper;
import com.zwinsight.finance.mapper.SettlementDataMapper;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.workflow.service.ApprovalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PaymentApplyService 单元测试
 * <p>审批后生效模式：submit 置 SUBMITTED（不回写），onApproved 回写合同已付与项目支出（含净奖惩额度校验）。</p>
 */
@ExtendWith(MockitoExtension.class)
class PaymentApplyServiceTest {

    @Mock private BizPaymentApplyMapper paymentApplyMapper;
    @Mock private BizOtherContractMapper otherContractMapper;
    @Mock private BizProjectMapper projectMapper;
    @Mock private SettlementDataMapper settlementDataMapper;
    @Mock private ApprovalService approvalService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PaymentApplyService paymentApplyService;

    @Nested
    @DisplayName("submit() 提交付款申请")
    class SubmitTests {

        @Test
        @DisplayName("正常路径 — 校验通过后状态置 SUBMITTED，不回写合同/项目")
        void submit_normalPath_statusSubmitted() {
            Long id = 1L;
            Long contractId = 100L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setContractId(contractId);
            apply.setProjectId(10L);
            apply.setPaymentAmount(new BigDecimal("30000.00"));
            apply.setStatus("DRAFT");

            BizOtherContract contract = new BizOtherContract();
            contract.setId(contractId);
            contract.setCumulativeSettlement(new BigDecimal("100000.00"));
            contract.setCumulativePaid(new BigDecimal("50000.00"));

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(otherContractMapper.selectById(contractId)).thenReturn(contract);
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(BigDecimal.ZERO);
            when(approvalService.startProcess(eq("PAYMENT_APPLY"), eq(id), eq("payment_apply_approval"), anyMap()))
                    .thenReturn("proc-1");

            paymentApplyService.submit(id);

            assertThat(apply.getStatus()).isEqualTo("SUBMITTED");
            assertThat(apply.getWorkflowInstanceId()).isEqualTo("proc-1");
            verify(otherContractMapper, never()).addCumulativePaid(anyLong(), any());
            verify(projectMapper, never()).addTotalExpense(anyLong(), any());
        }

        @Test
        @DisplayName("付款超限（含净奖惩） — 抛 BusinessException")
        void submit_exceedsLimit_throws() {
            Long id = 2L;
            Long contractId = 200L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setContractId(contractId);
            apply.setPaymentAmount(new BigDecimal("60000.00"));
            apply.setStatus("DRAFT");

            BizOtherContract contract = new BizOtherContract();
            contract.setId(contractId);
            contract.setCumulativeSettlement(new BigDecimal("100000.00"));
            contract.setCumulativePaid(new BigDecimal("50000.00"));

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(otherContractMapper.selectById(contractId)).thenReturn(contract);
            // 处罚 5000（净奖惩 -5000）→ 可付 = 100000 - 5000 - 50000 = 45000 < 60000
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(new BigDecimal("-5000.00"));

            assertThatThrownBy(() -> paymentApplyService.submit(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("付款金额不能超过");

            verify(approvalService, never()).startProcess(anyString(), anyLong(), anyString(), anyMap());
        }

        @Test
        @DisplayName("非 DRAFT/REJECTED 状态 — 抛 BusinessException")
        void submit_nonDraft_throws() {
            Long id = 3L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setStatus("APPROVED");
            when(paymentApplyMapper.selectById(id)).thenReturn(apply);

            assertThatThrownBy(() -> paymentApplyService.submit(id))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅草稿或已驳回状态可提交");
        }
    }

    @Nested
    @DisplayName("onApproved() / onRejected() 审批回调")
    class ApprovalCallbackTests {

        @Test
        @DisplayName("审批通过 — 状态置 APPROVED + 原子回写合同已付与项目支出")
        void onApproved_writesBack() {
            Long id = 1L;
            Long contractId = 100L;
            Long projectId = 10L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setContractId(contractId);
            apply.setProjectId(projectId);
            apply.setPaymentAmount(new BigDecimal("30000.00"));
            apply.setStatus("SUBMITTED");

            BizOtherContract contract = new BizOtherContract();
            contract.setId(contractId);
            contract.setCumulativeSettlement(new BigDecimal("100000.00"));
            contract.setCumulativePaid(new BigDecimal("50000.00"));

            when(paymentApplyMapper.selectById(id)).thenReturn(apply);
            when(otherContractMapper.selectById(contractId)).thenReturn(contract);
            when(settlementDataMapper.sumRewardPunishNetByContract(contractId)).thenReturn(BigDecimal.ZERO);

            paymentApplyService.onApproved(id);

            assertThat(apply.getStatus()).isEqualTo("APPROVED");
            verify(otherContractMapper).addCumulativePaid(contractId, new BigDecimal("30000.00"));
            verify(projectMapper).addTotalExpense(projectId, new BigDecimal("30000.00"));
        }

        @Test
        @DisplayName("审批通过 — 已生效幂等跳过")
        void onApproved_idempotent() {
            Long id = 1L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setStatus("APPROVED");
            when(paymentApplyMapper.selectById(id)).thenReturn(apply);

            paymentApplyService.onApproved(id);

            verify(otherContractMapper, never()).addCumulativePaid(anyLong(), any());
            verify(projectMapper, never()).addTotalExpense(anyLong(), any());
        }

        @Test
        @DisplayName("审批驳回 — SUBMITTED 置 REJECTED，不回写")
        void onRejected_setsRejected() {
            Long id = 1L;
            BizPaymentApply apply = new BizPaymentApply();
            apply.setId(id);
            apply.setStatus("SUBMITTED");
            when(paymentApplyMapper.selectById(id)).thenReturn(apply);

            paymentApplyService.onRejected(id);

            assertThat(apply.getStatus()).isEqualTo("REJECTED");
            verify(otherContractMapper, never()).addCumulativePaid(anyLong(), any());
        }
    }
}
