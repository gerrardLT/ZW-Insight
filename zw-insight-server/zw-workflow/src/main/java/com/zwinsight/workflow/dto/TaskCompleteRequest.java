package com.zwinsight.workflow.dto;

import lombok.Data;

import java.util.Map;

/**
 * 办理（通过）请求
 */
@Data
public class TaskCompleteRequest {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 审批意见
     */
    private String comment;

    /**
     * 流程变量
     */
    private Map<String, Object> variables;
}
