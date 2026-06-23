package com.zwinsight.system.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审计日志实体
 */
@Data
@TableName("sys_audit_log")
public class SysAuditLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 表名 */
    private String tableName;

    /** 记录ID */
    private Long recordId;

    /** 字段名 */
    private String fieldName;

    /** 旧值 */
    private String oldValue;

    /** 新值 */
    private String newValue;

    /** 操作人ID */
    private Long operUserId;

    /** 操作人姓名 */
    private String operUserName;

    /** 操作时间 */
    private LocalDateTime operTime;

    /** 租户ID */
    private Long tenantId;
}
