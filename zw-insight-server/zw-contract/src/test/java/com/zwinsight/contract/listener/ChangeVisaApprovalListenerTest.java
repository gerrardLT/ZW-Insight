package com.zwinsight.contract.listener;

import com.zwinsight.contract.service.ChangeVisaService;
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
 * ChangeVisaApprovalListener 单元测试
 * <p>审批事件分发：仅处理 CHANGE_VISA 业务类型。</p>
 */
@ExtendWith(MockitoExtension.class)
class ChangeVisaApprovalListenerTest {

    @Mock
    private ChangeVisaService changeVisaService;

    @InjectMocks
    private ChangeVisaApprovalListener listener;

    @Test
    @DisplayName("审批通过 - APPROVED 分发 onApproved，其他 result 忽略")
    void onApprovalComplete_dispatchesApprovedOnly() {
        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "CHANGE_VISA", 1L, "APPROVED"));
        verify(changeVisaService).onApproved(1L);

        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "CHANGE_VISA", 2L, "REJECTED"));
        verify(changeVisaService, never()).onApproved(2L);
    }

    @Test
    @DisplayName("审批通过 - 非变更签证业务类型忽略")
    void onApprovalComplete_otherBusinessType_ignored() {
        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "CONSTRUCTION_CONTRACT", 1L, "APPROVED"));

        verifyNoInteractions(changeVisaService);
    }

    @Test
    @DisplayName("审批驳回 - 变更签证分发 onRejected")
    void onApprovalReject_dispatches() {
        listener.onApprovalReject(
                new ApprovalRejectEvent(this, "proc-1", "CHANGE_VISA", 3L, "REJECT"));

        verify(changeVisaService).onRejected(3L);
    }

    @Test
    @DisplayName("审批驳回 - 其他业务类型忽略")
    void onApprovalReject_otherBizType_ignored() {
        listener.onApprovalReject(
                new ApprovalRejectEvent(this, "proc-1", "OTHER", 3L, "REJECT"));

        verifyNoInteractions(changeVisaService);
    }
}
