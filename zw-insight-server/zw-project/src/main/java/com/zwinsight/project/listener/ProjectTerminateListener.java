package com.zwinsight.project.listener;

import com.zwinsight.project.service.ProjectService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目终止审批回调监听器（P1-M1 A4，V2026_81）。
 * <p>
 * 复用 project_close_approval 审批链，businessType=PROJECT_TERMINATE 路由到本监听器：
 * 通过 → TERMINATING→TERMINATED（终态）；驳回 → 回退发起前状态（状态机从大事记回溯）。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectTerminateListener {

    private static final String BUSINESS_TYPE = "PROJECT_TERMINATE";

    private final ProjectService projectService;

    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        if ("APPROVED".equals(event.getResult())) {
            log.info("项目终止审批通过回调, projectId={}", event.getBusinessId());
            projectService.onTerminateApproved(event.getBusinessId());
        }
    }

    @EventListener
    public void onApprovalReject(ApprovalRejectEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBizType())) {
            return;
        }
        log.info("项目终止审批驳回回调, projectId={}, rejectType={}", event.getBizId(), event.getRejectType());
        projectService.onTerminateRejected(event.getBizId());
    }
}
