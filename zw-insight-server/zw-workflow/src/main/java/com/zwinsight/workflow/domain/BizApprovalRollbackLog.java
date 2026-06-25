package com.zwinsight.workflow.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审批数据回滚日志实体
 * <p>
 * 记录每次回滚操作的执行结果，包括成功、失败、冲突待确认等状态。
 * </p>
 */
@Data
@TableName("biz_approval_rollback_log")
public class BizApprovalRollbackLog implements Serializable {

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
     * 回滚字段（JSON格式）
     */
    private String rollbackFields;

    /**
     * 回滚状态：1-成功 2-失败 3-冲突待确认
     */
    private Integer rollbackStatus;

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

    // ===== 回滚状态常量 =====

    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_CONFLICT = 3;
}
