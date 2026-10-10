package com.zwinsight.finance.listener;

import com.zwinsight.finance.service.ProjectReimbursementService;
import com.zwinsight.workflow.listener.ApprovalRejectEvent;
import com.zwinsight.workflow.listener.ProcessCompleteListener.ApprovalCompleteEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 项目报销审批回调监听器
 * <p>
 * 监听审批事件，当 businessType == "PROJECT_REIMBURSEMENT" 时分发到 {@link ProjectReimbursementService}：
 * - 审批通过（ApprovalCompleteEvent，result=APPROVED）→ 状态置 APPROVED 并冲抵备用金
 * - 审批驳回/撤回（ApprovalRejectEvent）→ 状态置 REJECTED（数据未生效，无需回滚）
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectReimbursementApprovalListener {

    private static final String BUSINESS_TYPE = "PROJECT_REIMBURSEMENT";

    private final ProjectReimbursementService projectReimbursementService;

    /**
     * 审批通过回调
     */
    @EventListener
    public void onApprovalComplete(ApprovalCompleteEvent event) {
        if (!BUSINESS_TYPE.equals(event.getBusinessType())) {
            return;
        }
        Long reimbursementId = event.getBusinessId();
        log.info("收到项目报销审批回调, reimbursementId={}, result={}", reimbursementId, event.getResult());
        if ("APPROVED".equals(event.getResult())) {
            projectReimbursementService.onApproved(reimbursementId);
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
        Long reimbursementId = event.getBizId();
        log.info("收到项目报销审批驳回回调, reimbursementId={}, rejectType={}", reimbursementId, event.getRejectType());
        projectReimbursementService.onRejected(reimbursementId);
    }
}
