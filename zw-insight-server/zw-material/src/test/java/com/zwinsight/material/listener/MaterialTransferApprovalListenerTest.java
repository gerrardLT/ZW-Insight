package com.zwinsight.material.listener;

import com.zwinsight.material.service.MaterialTransferService;
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
 * MaterialTransferApprovalListener 单元测试
 * <p>材料调拨审批事件分发：仅处理 MATERIAL_TRANSFER 业务类型。</p>
 */
@ExtendWith(MockitoExtension.class)
class MaterialTransferApprovalListenerTest {

    @Mock
    private MaterialTransferService materialTransferService;

    @InjectMocks
    private MaterialTransferApprovalListener listener;

    @Test
    @DisplayName("APPROVED 分发 onApproved；其他结果忽略")
    void onComplete_dispatchesApprovedOnly() {
        listener.onApprovalComplete(new ApprovalCompleteEvent(this, "MATERIAL_TRANSFER", 1L, "APPROVED"));
        verify(materialTransferService).onApproved(1L);

        listener.onApprovalComplete(new ApprovalCompleteEvent(this, "MATERIAL_TRANSFER", 2L, "REJECTED"));
        verify(materialTransferService, never()).onApproved(2L);
    }

    @Test
    @DisplayName("非材料调拨业务类型忽略")
    void onComplete_otherBusinessType_ignored() {
        listener.onApprovalComplete(new ApprovalCompleteEvent(this, "PAYMENT_APPLY", 1L, "APPROVED"));

        verifyNoInteractions(materialTransferService);
    }

    @Test
    @DisplayName("驳回事件 - 材料调拨分发 onRejected，其他类型忽略")
    void onReject_dispatches() {
        listener.onApprovalReject(new ApprovalRejectEvent(this, "proc-1", "MATERIAL_TRANSFER", 3L, "REJECT"));
        verify(materialTransferService).onRejected(3L);

        listener.onApprovalReject(new ApprovalRejectEvent(this, "proc-2", "OTHER", 4L, "REJECT"));
        verify(materialTransferService, never()).onRejected(4L);
    }
}
