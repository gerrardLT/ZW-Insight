package com.zwinsight.system.service;

import com.sun.management.OperatingSystemMXBean;
import com.zwinsight.common.util.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * 系统运行指标采集器。
 *
 * <p>统一采集 CPU 使用率、JVM 堆内存、磁盘空间、数据库连接池、Redis 连接状态以及在线用户数等
 * 运行时指标，供监控仪表盘聚合接口（{@code HealthMonitorController}）与阈值告警任务
 * （{@code HealthMonitorConfig}）复用。
 *
 * <p>所有指标均来自真实运行时数据源（JMX、Druid 连接池、Redis 连接工厂、Redis token 键），
 * 当某项指标在当前运行环境下无法获取时，返回明确的"未知"标识（{@code -1} 或 {@code "UNKNOWN"}），
 * 不返回伪造数据。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SystemMetricsCollector {

    private final RedisUtils redisUtils;
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;

    /** 磁盘统计路径，默认取文件系统根路径。 */
    @Value("${monitor.disk-path:/}")
    private String diskPath;

    /** 登录 Token 在 Redis 中的前缀（与 AuthService 保持一致）。 */
    private static final String TOKEN_PREFIX = "token:";
    private static final String TOKEN_BLACKLIST_PREFIX = "token:blacklist:";
    private static final String TOKEN_REFRESH_PREFIX = "token:refresh:";
    private static final String TOKEN_USER_PREFIX = "token:user:";

    /**
     * 采集系统 CPU 使用率。
     *
     * @return CPU 使用率百分比（0~100），无法获取时返回 -1
     */
    public double getCpuUsagePercent() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean sunOs) {
            double load = sunOs.getCpuLoad();
            if (load < 0 || Double.isNaN(load)) {
                // 首次采样可能返回 NaN，退化为进程 CPU 负载
                load = sunOs.getProcessCpuLoad();
            }
            if (load >= 0 && !Double.isNaN(load)) {
                return round2(load * 100.0);
            }
        }
        return -1;
    }

    /**
     * 采集 JVM 堆内存使用量。
     *
     * @return 已用堆内存（MB）
     */
    public long getMemoryUsedMb() {
        return toMb(heapUsage().getUsed());
    }

    /**
     * 采集 JVM 堆内存最大值。
     *
     * @return 最大堆内存（MB），未设置上限时返回 -1
     */
    public long getMemoryMaxMb() {
        long max = heapUsage().getMax();
        return max < 0 ? -1 : toMb(max);
    }

    /**
     * 采集 JVM 堆内存使用率。
     *
     * @return 内存使用率百分比（0~100），无最大值时返回 -1
     */
    public double getMemoryUsagePercent() {
        MemoryUsage heap = heapUsage();
        if (heap.getMax() <= 0) {
            return -1;
        }
        return round2((double) heap.getUsed() / heap.getMax() * 100.0);
    }

    /**
     * 采集磁盘总空间。
     *
     * @return 磁盘总空间（GB），无法获取时返回 -1
     */
    public long getDiskTotalGb() {
        long total = diskFile().getTotalSpace();
        return total <= 0 ? -1 : toGb(total);
    }

    /**
     * 采集磁盘已用空间。
     *
     * @return 磁盘已用空间（GB），无法获取时返回 -1
     */
    public long getDiskUsedGb() {
        File f = diskFile();
        long total = f.getTotalSpace();
        long usable = f.getUsableSpace();
        if (total <= 0) {
            return -1;
        }
        return toGb(total - usable);
    }

    /**
     * 采集磁盘使用率。
     *
     * @return 磁盘使用率百分比（0~100），无法获取时返回 -1
     */
    public double getDiskUsagePercent() {
        File f = diskFile();
        long total = f.getTotalSpace();
        long usable = f.getUsableSpace();
        if (total <= 0) {
            return -1;
        }
        return round2((double) (total - usable) / total * 100.0);
    }

    /**
     * 采集数据库连接池活跃连接数。
     *
     * <p>通过反射读取连接池实现（如 Druid {@code getActiveCount()}），避免对具体连接池产生编译期依赖。
     *
     * @return 活跃连接数，无法获取时返回 -1
     */
    public int getDbPoolActive() {
        return invokeIntMethod(dataSource, "getActiveCount");
    }

    /**
     * 采集数据库连接池最大连接数。
     *
     * @return 最大连接数，无法获取时返回 -1
     */
    public int getDbPoolMax() {
        return invokeIntMethod(dataSource, "getMaxActive");
    }

    /**
     * 探测 Redis 连接状态。
     *
     * @return "UP" 表示连接正常，"DOWN" 表示连接异常
     */
    public String getRedisStatus() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            return (pong != null) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.warn("Redis 连接探测失败: {}", e.getMessage());
            return "DOWN";
        }
    }

    /**
     * 统计当前在线用户数。
     *
     * <p>扫描 Redis 中 {@code token:{token} -> userId} 的登录会话键，排除黑名单/刷新/按用户索引键后，
     * 按 userId 去重统计在线用户数量。
     *
     * @return 在线用户数
     */
    public long countOnlineUsers() {
        try {
            Set<String> keys = redisUtils.keys(TOKEN_PREFIX + "*");
            Set<Object> userIds = new HashSet<>();
            for (String key : keys) {
                if (key.startsWith(TOKEN_BLACKLIST_PREFIX)
                        || key.startsWith(TOKEN_REFRESH_PREFIX)
                        || key.startsWith(TOKEN_USER_PREFIX)) {
                    continue;
                }
                Object userId = redisUtils.get(key);
                if (userId != null) {
                    userIds.add(userId);
                }
            }
            return userIds.size();
        } catch (Exception e) {
            log.warn("统计在线用户数失败: {}", e.getMessage());
            return 0;
        }
    }

    // ============================ 内部辅助方法 ============================

    private MemoryUsage heapUsage() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        return memoryMXBean.getHeapMemoryUsage();
    }

    private File diskFile() {
        File f = new File(diskPath);
        // 路径不存在时退化到工作目录所在文件系统
        return f.getTotalSpace() > 0 ? f : new File(".").getAbsoluteFile();
    }

    private int invokeIntMethod(Object target, String methodName) {
        if (target == null) {
            return -1;
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            if (result instanceof Number number) {
                return number.intValue();
            }
        } catch (NoSuchMethodException e) {
            log.debug("当前数据源不支持连接池指标 method={}", methodName);
        } catch (Exception e) {
            log.debug("连接池指标读取失败 method={}: {}", methodName, e.getMessage());
        }
        return -1;
    }

    private static long toMb(long bytes) {
        return bytes / 1024 / 1024;
    }

    private static long toGb(long bytes) {
        return bytes / 1024 / 1024 / 1024;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
