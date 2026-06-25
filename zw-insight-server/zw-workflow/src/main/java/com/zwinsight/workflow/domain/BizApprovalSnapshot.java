package com.zwinsight.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批数据快照实体
 * <p>
 * 审批提交时记录业务数据的原始值，用于驳回/撤回时的数据回滚。
 * 每个字段存储一条记录，便于精确回滚。
 * </p>
 */
@Data
@TableName("biz_approval_snapshot")
public class BizApprovalSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
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
     * 字段名
     */
    private String fieldName;

    /**
     * 原始值（JSON TEXT）
     */
    private String originalValue;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
