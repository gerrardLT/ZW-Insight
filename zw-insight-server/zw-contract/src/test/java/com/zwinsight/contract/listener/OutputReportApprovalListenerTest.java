package com.zwinsight.contract.listener;

import com.zwinsight.contract.service.OutputReportService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * OutputReportApprovalListener 单元测试（P1 OUT-12 补测，2026-08-13）
 * <p>审批事件分发：APPROVED→onApproved；驳回/撤回事件→onRejected；其他业务类型/结果忽略。</p>
 */
@ExtendWith(MockitoExtension.class)
class OutputReportApprovalListenerTest {

    @Mock
    private OutputReportService outputReportService;

    @InjectMocks
    private OutputReportApprovalListener listener;

    @Test
    @DisplayName("审批通过（APPROVED）分发 onApproved（P1 OUT-12）")
    void approved_dispatches() {
        listener.onApprovalComplete(new ApprovalCompleteEvent(this, "OUTPUT_REPORT", 1L, "APPROVED"));

        verify(outputReportService).onApproved(1L);
        verify(outputReportService, never()).onRejected(any());
    }

    @Test
    @DisplayName("完成事件非 APPROVED 结果（如 RECALLED）不分发")
    void nonApprovedResult_ignored() {
        listener.onApprovalComplete(new ApprovalCompleteEvent(this, "OUTPUT_REPORT", 2L, "RECALLED"));

        verifyNoInteractions(outputReportService);
    }

    @Test
    @DisplayName("非产值业务类型的完成事件忽略")
    void otherBusinessType_ignored() {
        listener.onApprovalComplete(new ApprovalCompleteEvent(this, "PAYMENT_APPLY", 3L, "APPROVED"));

        verifyNoInteractions(outputReportService);
    }

    @Test
    @DisplayName("驳回事件（REJECT）分发 onRejected（P1 OUT-12）")
    void rejectEvent_dispatches() {
        listener.onApprovalReject(new ApprovalRejectEvent(this, "wf-001", "OUTPUT_REPORT", 4L, "REJECT"));

        verify(outputReportService).onRejected(4L);
        verify(outputReportService, never()).onApproved(any());
    }

    @Test
    @DisplayName("撤回事件（WITHDRAW）同样分发 onRejected")
    void withdrawEvent_dispatches() {
        listener.onApprovalReject(new ApprovalRejectEvent(this, "wf-002", "OUTPUT_REPORT", 5L, "WITHDRAW"));

        verify(outputReportService).onRejected(5L);
    }

    @Test
    @DisplayName("非产值业务类型的驳回事件忽略")
    void rejectEvent_otherBizType_ignored() {
        listener.onApprovalReject(new ApprovalRejectEvent(this, "wf-003", "BUDGET_CHANGE", 6L, "REJECT"));

        verifyNoInteractions(outputReportService);
    }
}
