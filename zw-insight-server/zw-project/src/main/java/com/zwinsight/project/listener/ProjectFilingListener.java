package com.zwinsight.project.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zwinsight.common.config.SecurityContextHolder;
import com.zwinsight.project.domain.BizProject;
import com.zwinsight.project.domain.enums.ProjectEvent;
import com.zwinsight.project.mapper.BizProjectMapper;
import com.zwinsight.project.service.ProjectStateMachine;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目立项审批回调监听器（P1-M1 B1，V2026_81）。
 * <p>
 * sys_config project_filing_approval_enabled=true 时，submit 发起
 * businessType=PROJECT_FILING 审批（复用 project_close_approval 链）。
 * 项目此时已为 FILED（信息可用）：通过仅记大事记；驳回回退 DRAFT 可改后重提。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectFilingListener {

    private static final String BUSINESS_TYPE = "PROJECT_FILING";

    private final BizProjectMapper projectMapper;
    private final ProjectStateMachine stateMachine;

    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType()) || !"APPROVED".equals(event.getResult())) {
            return;
        }
        BizProject project = projectMapper.selectById(event.getBusinessId());
        if (project != null) {
            log.info("立项审批通过, projectId={}", event.getBusinessId());
        }
    }

    @EventListener
    public void onApprovalReject(ApprovalRejectEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBizType())) {
            return;
        }
        BizProject project = projectMapper.selectById(event.getBizId());
        if (project != null && "FILED".equals(project.getStatus())) {
            stateMachine.fire(project, ProjectEvent.WITHDRAW, "立项审批驳回，退回草稿", null);
            log.info("立项审批驳回，项目退回草稿, projectId={}", event.getBizId());
        }
    }
}
