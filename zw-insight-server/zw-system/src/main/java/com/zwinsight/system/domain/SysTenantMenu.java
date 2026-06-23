package com.zwinsight.system.domain;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;

/**
 * 租户菜单权限关联
 */
@Data
@TableName("sys_tenant_menu")
public class SysTenantMenu implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 菜单ID */
    private Long menuId;
}
