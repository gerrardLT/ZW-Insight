package com.zwinsight.workflow.listener;

import org.springframework.context.ApplicationEvent;

/**
 * 审批驳回/撤回事件
 * <p>
 * 当 Flowable 流程中发生驳回或撤回操作时发布此事件，
 * 由 ApprovalRollbackService 监听并执行数据回滚。
 * </p>
 */
public class ApprovalRejectEvent extends ApplicationEvent {

    private final String workflowInstanceId;
    private final String bizType;
    private final Long bizId;
    private final String rejectType;

    /**
     * @param source             事件源
     * @param workflowInstanceId 流程实例ID
     * @param bizType            业务类型
     * @param bizId              业务记录ID
     * @param rejectType         驳回类型：REJECT / WITHDRAW
     */
    public ApprovalRejectEvent(Object source, String workflowInstanceId, String bizType, Long bizId, String rejectType) {
        super(source);
        this.workflowInstanceId = workflowInstanceId;
        this.bizType = bizType;
        this.bizId = bizId;
        this.rejectType = rejectType;
    }

    public String getWorkflowInstanceId() {
        return workflowInstanceId;
    }

    public String getBizType() {
        return bizType;
    }

    public Long getBizId() {
        return bizId;
    }

    public String getRejectType() {
        return rejectType;
    }
}
