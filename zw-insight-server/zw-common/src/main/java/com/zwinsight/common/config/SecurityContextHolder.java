package com.zwinsight.common.config;

/**
 * 安全上下文持有者 - 基于 ThreadLocal 存储当前请求的租户ID和用户ID
 */
public class SecurityContextHolder {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    public static void setTenantId(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long getTenantId() {
        return TENANT_ID.get();
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    /**
     * 清除当前线程上下文（在请求结束时调用，防止内存泄漏）
     */
    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
    }
}
