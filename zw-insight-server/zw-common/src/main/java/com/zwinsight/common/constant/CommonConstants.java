package com.zwinsight.common.constant;

/**
 * 通用常量
 */
public final class CommonConstants {

    private CommonConstants() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }

    // ========== 状态相关 ==========

    /**
     * 启用状态
     */
    public static final int STATUS_ENABLE = 1;

    /**
     * 禁用状态
     */
    public static final int STATUS_DISABLE = 0;

    // ========== 删除标识 ==========

    /**
     * 未删除
     */
    public static final int NOT_DELETED = 0;

    /**
     * 已删除
     */
    public static final int DELETED = 1;

    // ========== 是否标识 ==========

    /**
     * 是
     */
    public static final int YES = 1;

    /**
     * 否
     */
    public static final int NO = 0;

    // ========== 默认值 ==========

    /**
     * 默认页码
     */
    public static final int DEFAULT_PAGE = 1;

    /**
     * 默认每页大小
     */
    public static final int DEFAULT_PAGE_SIZE = 10;

    /**
     * 最大每页大小
     */
    public static final int MAX_PAGE_SIZE = 500;

    // ========== 租户相关 ==========

    /**
     * 租户ID请求头
     */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    /**
     * 默认租户ID
     */
    public static final Long DEFAULT_TENANT_ID = 1L;

    // ========== 系统表前缀 ==========

    /**
     * 系统表前缀（不走多租户过滤）
     */
    public static final String SYS_TABLE_PREFIX = "sys_";
}
