package com.zwinsight.system.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户类型
 */
@Data
@TableName("sys_tenant_type")
public class SysTenantType implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 类型名称 */
    private String typeName;

    /** 有效期天数(30/90/180/365) */
    private Integer durationDays;

    /** 排序 */
    private Integer sortOrder;

    /** 状态（1-启用 0-停用） */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
