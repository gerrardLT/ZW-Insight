package com.zwinsight.finance.listener;

import com.zwinsight.finance.service.RetentionReturnService;
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
 * RetentionReturnApprovalListener 单元测试
 * <p>审批事件分发：仅处理 RETENTION_RETURN 业务类型。</p>
 */
@ExtendWith(MockitoExtension.class)
class RetentionReturnApprovalListenerTest {

    @Mock
    private RetentionReturnService retentionReturnService;

    @InjectMocks
    private RetentionReturnApprovalListener listener;

    @Test
    @DisplayName("审批通过 - APPROVED 分发 onApproved，其他 result 忽略")
    void onApprovalComplete_dispatchesApprovedOnly() {
        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "RETENTION_RETURN", 1L, "APPROVED"));
        verify(retentionReturnService).onApproved(1L);

        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "RETENTION_RETURN", 2L, "REJECTED"));
        verify(retentionReturnService, never()).onApproved(2L);
    }

    @Test
    @DisplayName("审批通过 - 非质保金返还业务类型忽略")
    void onApprovalComplete_otherBusinessType_ignored() {
        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "FUND_TRANSFER", 1L, "APPROVED"));

        verifyNoInteractions(retentionReturnService);
    }

    @Test
    @DisplayName("审批驳回 - 质保金返还分发 onRejected")
    void onApprovalReject_dispatches() {
        listener.onApprovalReject(
                new ApprovalRejectEvent(this, "proc-1", "RETENTION_RETURN", 3L, "REJECT"));

        verify(retentionReturnService).onRejected(3L);
    }

    @Test
    @DisplayName("审批驳回 - 其他业务类型忽略")
    void onApprovalReject_otherBizType_ignored() {
        listener.onApprovalReject(
                new ApprovalRejectEvent(this, "proc-1", "OTHER", 3L, "REJECT"));

        verifyNoInteractions(retentionReturnService);
    }
}
