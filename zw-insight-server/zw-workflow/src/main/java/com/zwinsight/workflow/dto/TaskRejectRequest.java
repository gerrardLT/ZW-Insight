package com.zwinsight.workflow.dto;

import lombok.Data;

/**
 * 退回请求
 */
@Data
public class TaskRejectRequest {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 审批意见
     */
    private String comment;

    /**
     * 目标节点ID（可选，用于指定退回到具体节点）
     */
    private String targetNodeId;
}
