package com.zwinsight.workflow.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量通过请求
 */
@Data
public class BatchApproveRequest {

    /**
     * 任务ID列表
     */
    private List<String> taskIds;

    /**
     * 审批意见
     */
    private String comment;
}
