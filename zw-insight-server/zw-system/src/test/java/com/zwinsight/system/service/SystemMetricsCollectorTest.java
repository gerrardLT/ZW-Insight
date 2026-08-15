package com.zwinsight.system.service;

import com.zwinsight.common.util.RedisUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SystemMetricsCollector 单元测试（2026-08-15 P3 补测）
 *
 * 覆盖：JVM/磁盘指标真实采集合法性、连接池反射探测（支持/不支持两分支）、
 * Redis 探测 UP/DOWN、在线用户统计（前缀排除+userId 去重+异常兜底 0）。
 * 契约：指标取不到返回 -1/"DOWN"/0，不伪造数据。
 */
@ExtendWith(MockitoExtension.class)
class SystemMetricsCollectorTest {

    @Mock
    private RedisUtils redisUtils;
    @Mock
    private DataSource dataSource;
    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    private SystemMetricsCollector collector;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        collector = new SystemMetricsCollector(redisUtils, dataSource, redisConnectionFactory);
        // @Value 注入字段单测经反射设置（默认 / 在 Windows 单测亦合法，此处固定临时目录防歧义）
        ReflectionTestUtils.setField(collector, "diskPath", tempDir.toString());
    }

    @Test
    @DisplayName("CPU 使用率：合法区间或 -1 未知标识")
    void cpuUsage_inValidRangeOrUnknown() {
        double cpu = collector.getCpuUsagePercent();
        assertThat(cpu == -1 || (cpu >= 0 && cpu <= 100)).isTrue();
    }

    @Test
    @DisplayName("JVM 堆内存：已用 > 0，最大值 > 0 或 -1，使用率与二者自洽")
    void memoryMetrics_consistent() {
        long used = collector.getMemoryUsedMb();
        long max = collector.getMemoryMaxMb();
        double percent = collector.getMemoryUsagePercent();

        assertThat(used).isGreaterThan(0);
        if (max > 0) {
            assertThat(percent).isBetween(0.0, 100.0);
        } else {
            assertThat(max).isEqualTo(-1);
            assertThat(percent).isEqualTo(-1);
        }
    }

    @Test
    @DisplayName("磁盘指标：临时目录所在文件系统可采集，使用率介于 0~100")
    void diskMetrics_collectable() {
        long total = collector.getDiskTotalGb();
        long used = collector.getDiskUsedGb();
        double percent = collector.getDiskUsagePercent();

        assertThat(total).isGreaterThan(0);
        assertThat(used).isGreaterThanOrEqualTo(0);
        assertThat(used).isLessThanOrEqualTo(total);
        assertThat(percent).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("磁盘路径不存在时退化到工作目录文件系统（diskFile 兜底分支）")
    void diskMetrics_invalidPathFallback() {
        ReflectionTestUtils.setField(collector, "diskPath", "/definitely/not/exist/path-xyz");
        assertThat(collector.getDiskTotalGb()).isGreaterThan(0);
    }

    /** 带连接池指标方法的数据源替身（反射读取 getActiveCount/getMaxActive） */
    static class PooledDataSource implements DataSource {
        public int getActiveCount() { return 3; }
        public int getMaxActive() { return 20; }
        @Override public java.sql.Connection getConnection() { return null; }
        @Override public java.sql.Connection getConnection(String username, String password) { return null; }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return null; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }

    @Test
    @DisplayName("连接池指标：支持 getActiveCount/getMaxActive 的数据源经反射取值")
    void dbPoolMetrics_supportedDataSource() {
        SystemMetricsCollector pooledCollector =
                new SystemMetricsCollector(redisUtils, new PooledDataSource(), redisConnectionFactory);

        assertThat(pooledCollector.getDbPoolActive()).isEqualTo(3);
        assertThat(pooledCollector.getDbPoolMax()).isEqualTo(20);
    }

    @Test
    @DisplayName("连接池指标：普通 DataSource 无对应方法返回 -1（不伪造）")
    void dbPoolMetrics_unsupportedDataSource_returnsMinusOne() {
        assertThat(collector.getDbPoolActive()).isEqualTo(-1);
        assertThat(collector.getDbPoolMax()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Redis 探测：ping 正常返回 UP，连接异常返回 DOWN")
    void redisStatus_upAndDown() {
        RedisConnection okConn = mock(RedisConnection.class);
        when(redisConnectionFactory.getConnection()).thenReturn(okConn);
        when(okConn.ping()).thenReturn("PONG");
        assertThat(collector.getRedisStatus()).isEqualTo("UP");

        RedisConnectionFactory failingFactory = mock(RedisConnectionFactory.class);
        when(failingFactory.getConnection()).thenThrow(new RuntimeException("connect refused"));
        SystemMetricsCollector failingCollector =
                new SystemMetricsCollector(redisUtils, dataSource, failingFactory);
        assertThat(failingCollector.getRedisStatus()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("在线用户统计：排除黑名单/刷新/用户索引键，按 userId 去重")
    void countOnlineUsers_excludesPrefixedKeysAndDedupes() {
        when(redisUtils.keys("token:*")).thenReturn(Set.of(
                "token:aaa", "token:bbb", "token:ccc",
                "token:blacklist:xxx", "token:refresh:yyy", "token:user:1"));
        when(redisUtils.get("token:aaa")).thenReturn(101L);
        when(redisUtils.get("token:bbb")).thenReturn(101L); // 同用户多端去重为 1
        when(redisUtils.get("token:ccc")).thenReturn(102L);

        assertThat(collector.countOnlineUsers()).isEqualTo(2);
    }

    @Test
    @DisplayName("在线用户统计：值为 null 的键不计入，Redis 异常兜底 0")
    void countOnlineUsers_nullValueAndExceptionFallback() {
        when(redisUtils.keys("token:*")).thenReturn(Set.of("token:aaa"));
        when(redisUtils.get("token:aaa")).thenReturn(null);
        assertThat(collector.countOnlineUsers()).isEqualTo(0);

        when(redisUtils.keys("token:*")).thenThrow(new RuntimeException("redis down"));
        assertThat(collector.countOnlineUsers()).isEqualTo(0);
    }
}
