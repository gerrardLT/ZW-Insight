package com.zwinsight.site.listener;

import com.zwinsight.site.service.CompletionAcceptanceService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 竣工验收审批回调监听器
 * <p>
 * P1 修复（2026-08-13，批次三取证枚举）：原实现提交即置 APPROVED 且未等审批
 * 直接将项目置 COMPLETED。本监听器补齐审批后生效闭环：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ onApproved（验收单 APPROVED + 项目 COMPLETED）
 * - 审批驳回/撤回（ApprovalRejectEvent）→ onRejected（回退 DRAFT）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompletionAcceptanceApprovalListener {

    private static final String BUSINESS_TYPE = "COMPLETION_ACCEPTANCE";

    private final CompletionAcceptanceService completionAcceptanceService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        Long acceptanceId = event.getBusinessId();
        log.info("收到竣工验收审批回调, acceptanceId={}, result={}", acceptanceId, event.getResult());
        if ("APPROVED".equals(event.getResult())) {
            completionAcceptanceService.onApproved(acceptanceId);
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
        Long acceptanceId = event.getBizId();
        log.info("收到竣工验收审批驳回回调, acceptanceId={}, rejectType={}", acceptanceId, event.getRejectType());
        completionAcceptanceService.onRejected(acceptanceId);
    }
}
