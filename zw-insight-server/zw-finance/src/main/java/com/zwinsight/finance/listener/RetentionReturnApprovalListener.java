package com.zwinsight.finance.listener;

import com.zwinsight.finance.service.RetentionReturnService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 质保金返还审批回调监听器
 * <p>
 * 监听审批事件，当 businessType == "RETENTION_RETURN" 时分发到 {@link RetentionReturnService}：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ 状态置 APPROVED 并回写质保金已返还金额
 * - 审批驳回/撤回（ApprovalRejectEvent）→ 状态置 REJECTED（质保金未回写，无需回滚）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetentionReturnApprovalListener {

    private static final String BUSINESS_TYPE = "RETENTION_RETURN";

    private final RetentionReturnService retentionReturnService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        Long returnId = event.getBusinessId();
        log.info("收到质保金返还审批回调, returnId={}, result={}", returnId, event.getResult());
        if ("APPROVED".equals(event.getResult())) {
            retentionReturnService.onApproved(returnId);
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
        Long returnId = event.getBizId();
        log.info("收到质保金返还审批驳回回调, returnId={}, rejectType={}", returnId, event.getRejectType());
        retentionReturnService.onRejected(returnId);
    }
}
