package com.zwinsight.project.listener;

import com.zwinsight.project.service.ProjectService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目结项审批回调监听器
 * <p>
 * 监听审批事件，当 businessType == "PROJECT_CLOSE" 时分发到 {@link ProjectService}：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ 项目状态 CLOSING → CLOSED
 * - 审批驳回/撤回（ApprovalRejectEvent）→ 项目状态 CLOSING → COMPLETED（回退）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCloseListener {

    private static final String BUSINESS_TYPE = "PROJECT_CLOSE";

    private final ProjectService projectService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        Long projectId = event.getBusinessId();
        log.info("收到项目结项审批通过回调, projectId={}, result={}", projectId, event.getResult());
        if ("APPROVED".equals(event.getResult())) {
            projectService.onCloseApproved(projectId);
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
        Long projectId = event.getBizId();
        log.info("收到项目结项审批驳回回调, projectId={}, rejectType={}", projectId, event.getRejectType());
        projectService.onCloseRejected(projectId);
    }
}
