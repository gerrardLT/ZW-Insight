package com.zwinsight.tender.listener;

import com.zwinsight.tender.service.DepositApplyService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 保证金申请审批回调监听器
 * <p>
 * P1 修复（2026-08-12，批次二取证枚举）：原实现提交即置 PAID 且无任何审批回调，
 * 审批驳回后单据永久停留 PAID。本监听器补齐闭环：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ 置 PAID（确认付款）
 * - 审批驳回/撤回（ApprovalRejectEvent）→ 回退 DRAFT
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DepositApplyApprovalListener {

    private static final String BUSINESS_TYPE = "DEPOSIT_APPLY";

    private final DepositApplyService depositApplyService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        Long applyId = event.getBusinessId();
        log.info("收到保证金申请审批回调, applyId={}, result={}", applyId, event.getResult());
        if ("APPROVED".equals(event.getResult())) {
            depositApplyService.onApproved(applyId);
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
        Long applyId = event.getBizId();
        log.info("收到保证金申请审批驳回回调, applyId={}, rejectType={}", applyId, event.getRejectType());
        depositApplyService.onRejected(applyId);
    }
}
