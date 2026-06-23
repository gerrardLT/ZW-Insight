package com.zwinsight.system.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zwinsight.common.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_menu")
public class SysMenu extends BaseEntity {

    /**
     * 菜单名称
     */
    private String menuName;

    /**
     * 菜单类型（DIR-目录 MENU-菜单 BUTTON-按钮）
     */
    private String menuType;

    /**
     * 父菜单ID
     */
    private Long parentId;

    /**
     * 路由路径
     */
    private String path;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 图标
     */
    private String icon;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 权限标识
     */
    private String permission;

    /**
     * 状态（1-启用 0-停用）
     */
    private Integer status;

    /**
     * 是否隐藏（1-隐藏 0-显示）
     */
    private Integer hidden;

    /**
     * 权重（PLATFORM-平台级 NORMAL-普通）
     */
    private String weight;
}
