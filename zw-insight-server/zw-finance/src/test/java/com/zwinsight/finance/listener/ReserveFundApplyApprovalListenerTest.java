package com.zwinsight.finance.listener;

import com.zwinsight.finance.service.ReserveFundApplyService;
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
 * ReserveFundApplyApprovalListener 单元测试
 * <p>审批事件分发：仅处理 RESERVE_FUND_APPLY 业务类型。</p>
 */
@ExtendWith(MockitoExtension.class)
class ReserveFundApplyApprovalListenerTest {

    @Mock
    private ReserveFundApplyService reserveFundApplyService;

    @InjectMocks
    private ReserveFundApplyApprovalListener listener;

    @Test
    @DisplayName("审批通过 - APPROVED 分发 onApproved，其他 result 忽略")
    void onApprovalComplete_dispatchesApprovedOnly() {
        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "RESERVE_FUND_APPLY", 1L, "APPROVED"));
        verify(reserveFundApplyService).onApproved(1L);

        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "RESERVE_FUND_APPLY", 2L, "REJECTED"));
        verify(reserveFundApplyService, never()).onApproved(2L);
    }

    @Test
    @DisplayName("审批通过 - 非备用金申请业务类型忽略")
    void onApprovalComplete_otherBusinessType_ignored() {
        listener.onApprovalComplete(
                new ApprovalCompleteEvent(this, "RETENTION_RETURN", 1L, "APPROVED"));

        verifyNoInteractions(reserveFundApplyService);
    }

    @Test
    @DisplayName("审批驳回 - 备用金申请分发 onRejected")
    void onApprovalReject_dispatches() {
        listener.onApprovalReject(
                new ApprovalRejectEvent(this, "proc-1", "RESERVE_FUND_APPLY", 3L, "WITHDRAW"));

        verify(reserveFundApplyService).onRejected(3L);
    }

    @Test
    @DisplayName("审批驳回 - 其他业务类型忽略")
    void onApprovalReject_otherBizType_ignored() {
        listener.onApprovalReject(
                new ApprovalRejectEvent(this, "proc-1", "OTHER", 3L, "REJECT"));

        verifyNoInteractions(reserveFundApplyService);
    }
}
