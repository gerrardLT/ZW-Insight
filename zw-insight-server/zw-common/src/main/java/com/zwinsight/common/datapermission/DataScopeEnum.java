package com.zwinsight.common.datapermission;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据范围枚举
 * <p>
 * 优先级从高到低：ALL(5) > DEPT_AND_CHILDREN(4) > DEPT(3) > PROJECT(2) > SELF(1)
 * 多角色场景取最大优先级
 */
@Getter
@AllArgsConstructor
public enum DataScopeEnum {

    /** 仅本人数据 */
    SELF(1, "仅本人"),

    /** 本项目数据 */
    PROJECT(2, "本项目"),

    /** 本部门数据 */
    DEPT(3, "本部门"),

    /** 本部门及下级部门数据 */
    DEPT_AND_CHILDREN(4, "本部门及下级"),

    /** 全部数据 */
    ALL(5, "全部数据");

    /** 优先级，数值越大范围越大 */
    private final int priority;

    /** 描述 */
    private final String description;

    /**
     * 根据名称获取枚举（忽略大小写）
     *
     * @param name 枚举名称
     * @return DataScopeEnum，未找到返回 SELF
     */
    public static DataScopeEnum fromName(String name) {
        if (name == null || name.isBlank()) {
            return SELF;
        }
        for (DataScopeEnum scope : values()) {
            if (scope.name().equalsIgnoreCase(name.trim())) {
                return scope;
            }
        }
        return SELF;
    }
}
