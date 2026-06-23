package com.zwinsight.workflow.dto;

import lombok.Data;

/**
 * 转办请求
 */
@Data
public class TaskTransferRequest {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 目标用户ID
     */
    private String targetUserId;

    /**
     * 审批意见
     */
    private String comment;
}
