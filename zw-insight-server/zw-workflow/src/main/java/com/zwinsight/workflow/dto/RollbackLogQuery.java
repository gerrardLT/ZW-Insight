package com.zwinsight.workflow.dto;

import lombok.Data;

/**
 * 回滚记录查询参数
 */
@Data
public class RollbackLogQuery {

    /**
     * 流程实例ID
     */
    private String workflowInstanceId;

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 回滚状态：1-成功 2-失败 3-冲突待确认
     */
    private Integer rollbackStatus;

    /**
     * 页码
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer size = 10;
}
