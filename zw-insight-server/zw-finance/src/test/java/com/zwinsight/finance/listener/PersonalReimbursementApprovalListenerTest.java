package com.zwinsight.finance.listener;

import com.zwinsight.finance.service.PersonalReimbursementService;
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
 * PersonalReimbursementApprovalListener 单元测试
 * <p>审批事件分发：仅处理 PERSONAL_REIMBURSEMENT 业务类型。</p>
 */
@ExtendWith(MockitoExtension.class)
class PersonalReimbursementApprovalListenerTest {

    @Mock
    private PersonalReimbursementService personalReimbursementService;

    @InjectMocks
    private PersonalReimbursementApprovalListener listener;

    @Test
    @DisplayName("审批通过 - APPROVED 分发 onApproved，其他 result 忽略")
    void onApprovalComplete_dispatchesApprovedOnly() {
        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "PERSONAL_REIMBURSEMENT", 1L, "APPROVED"));
        verify(personalReimbursementService).onApproved(1L);

        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "PERSONAL_REIMBURSEMENT", 2L, "REJECTED"));
        verify(personalReimbursementService, never()).onApproved(2L);
    }

    @Test
    @DisplayName("审批通过 - 非个人报销业务类型忽略")
    void onApprovalComplete_otherBusinessType_ignored() {
        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "PROJECT_REIMBURSEMENT", 1L, "APPROVED"));

        verifyNoInteractions(personalReimbursementService);
    }

    @Test
    @DisplayName("审批驳回 - 个人报销分发 onRejected")
    void onApprovalReject_dispatches() {
        listener.onApprovalReject(
                new ApprovalRejectEvent(this, "proc-1", "PERSONAL_REIMBURSEMENT", 3L, "REJECT"));

        verify(personalReimbursementService).onRejected(3L);
    }

    @Test
    @DisplayName("审批驳回 - 其他业务类型忽略")
    void onApprovalReject_otherBizType_ignored() {
        listener.onApprovalReject(
                new ApprovalRejectEvent(this, "proc-1", "OTHER", 3L, "REJECT"));

        verifyNoInteractions(personalReimbursementService);
    }
}
