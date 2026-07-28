package com.zwinsight.common.security;

/**
 * 权限校验逻辑关系。
 * <p>
 * 用于 {@link RequiresPermission} 指定多个权限标识之间的组合方式。
 * </p>
 */
public enum Logical {

    /** 满足全部权限标识才放行 */
    AND,

    /** 满足任一权限标识即放行（默认，对齐前端 v-permission 语义） */
    OR
}
