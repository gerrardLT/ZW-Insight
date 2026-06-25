package com.zwinsight.workflow.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 回滚记录 VO
 */
@Data
public class RollbackLogVO {

    /**
     * 日志ID
     */
    private Long id;

    /**
     * 流程实例ID
     */
    private String workflowInstanceId;

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 业务记录ID
     */
    private Long bizId;

    /**
     * 回滚字段（JSON）
     */
    private String rollbackFields;

    /**
     * 回滚状态：1-成功 2-失败 3-冲突待确认
     */
    private Integer rollbackStatus;

    /**
     * 状态描述
     */
    private String statusDesc;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 错误信息
     */
    private String errorMsg;

    /**
     * 操作人ID
     */
    private Long operatorId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
