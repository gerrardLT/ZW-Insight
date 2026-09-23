package com.zwinsight.common.config;

/**
 * 安全上下文持有者 - 基于 ThreadLocal 存储当前请求的租户ID、用户ID 与系统任务标记
 * <p><b>系统任务标记（2026-09-24 新增）</b>：定时任务/异步作业没有登录用户，
 * 但仍需读写业务表。若不标记，数据权限处理器会因 userId==null 直接抛
 * {@code DataPermissionException}，导致任务整体失败——线上实证（2026-09-24）：
 * FundForecastTask 01:15 执行「成功 0/2」，RiskScanTask 02:00 有 3/7 条规则失败，
 * 而 TenantTaskRunner 仍报「成功 2/2」（异常被内层 catch 吞掉）。</p>
 * <p>标记后数据权限按「租户内全量」放行，租户隔离仍由 TenantLineInnerInterceptor
 * 依据 {@link #getTenantId()} 保证，不会跨租户泄露。</p>
 */
public class SecurityContextHolder {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SYSTEM_TASK = new ThreadLocal<>();

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
     * 标记当前线程为系统任务（定时任务/异步作业）：无用户身份，
     * 数据权限按租户全量放行。由 {@code TenantTaskRunner} 统一设置。
     */
    public static void markSystemTask() {
        SYSTEM_TASK.set(Boolean.TRUE);
    }

    /**
     * 当前线程是否为系统任务（未标记时为 false，即普通用户请求，数据权限正常生效）
     */
    public static boolean isSystemTask() {
        return Boolean.TRUE.equals(SYSTEM_TASK.get());
    }

    /**
     * 清除当前线程上下文（在请求结束时调用，防止内存泄漏）
     */
    public static void clear() {
        TENANT_ID.remove();
        USER_ID.remove();
        SYSTEM_TASK.remove();
    }
}
