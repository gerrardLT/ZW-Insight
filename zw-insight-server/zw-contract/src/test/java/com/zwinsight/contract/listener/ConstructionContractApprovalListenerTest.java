package com.zwinsight.contract.listener;

import com.zwinsight.contract.service.ConstructionContractService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * ConstructionContractApprovalListener 单元测试
 * <p>审批事件分发：仅处理 CONSTRUCTION_CONTRACT 业务类型。</p>
 */
@ExtendWith(MockitoExtension.class)
class ConstructionContractApprovalListenerTest {

    @Mock
    private ConstructionContractService contractService;

    @InjectMocks
    private ConstructionContractApprovalListener listener;

    @Test
    @DisplayName("审批通过 - APPROVED 分发 onApproved，其他 result 忽略")
    void onApprovalComplete_dispatchesApprovedOnly() {
        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "CONSTRUCTION_CONTRACT", 1L, "APPROVED"));
        verify(contractService).onApproved(1L);

        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "CONSTRUCTION_CONTRACT", 2L, "REJECTED"));
        verify(contractService, never()).onApproved(2L);
    }

    @Test
    @DisplayName("审批通过 - 非施工合同业务类型忽略")
    void onApprovalComplete_otherBusinessType_ignored() {
        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "PAYMENT_APPLY", 1L, "APPROVED"));

        verifyNoInteractions(contractService);
    }

    @Test
    @DisplayName("审批驳回 - 施工合同分发 onRejected")
    void onApprovalReject_dispatches() {
        listener.onApprovalReject(
                new ApprovalRejectEvent(this, "proc-1", "CONSTRUCTION_CONTRACT", 3L, "REJECT"));

        verify(contractService).onRejected(3L);
    }

    @Test
    @DisplayName("审批驳回 - 其他业务类型忽略")
    void onApprovalReject_otherBizType_ignored() {
        listener.onApprovalReject(
                new ApprovalRejectEvent(this, "proc-1", "OTHER", 3L, "REJECT"));

        verifyNoInteractions(contractService);
    }
}
