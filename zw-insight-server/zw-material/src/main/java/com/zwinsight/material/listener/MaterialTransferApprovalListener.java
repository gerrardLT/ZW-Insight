package com.zwinsight.material.listener;

import com.zwinsight.material.service.MaterialTransferService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 材料调拨审批回调监听器
 * <p>
 * 监听审批事件，当 businessType == "MATERIAL_TRANSFER" 时分发到 {@link MaterialTransferService}：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ 执行双向库存变更
 * - 审批驳回/撤回（ApprovalRejectEvent）→ 状态置 REJECTED（库存未变更，无需回滚）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialTransferApprovalListener {

    private static final String BUSINESS_TYPE = "MATERIAL_TRANSFER";

    private final MaterialTransferService materialTransferService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        Long transferId = event.getBusinessId();
        log.info("收到材料调拨审批回调, transferId={}, result={}", transferId, event.getResult());
        if ("APPROVED".equals(event.getResult())) {
            materialTransferService.onApproved(transferId);
        }
    }

    /**
     * 审批驳回/撤回回调
     */
    @EventListener
    public void onApprovalReject(ApprovalRejectEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBizType())) {
            return;
        }
        Long transferId = event.getBizId();
        log.info("收到材料调拨审批驳回回调, transferId={}, rejectType={}", transferId, event.getRejectType());
        materialTransferService.onRejected(transferId);
    }
}
