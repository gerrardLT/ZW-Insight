package com.zwinsight.system.config;

import com.zwinsight.system.service.SystemMetricsCollector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 系统健康监控配置。
 *
 * <p>职责：
 * <ol>
 *   <li>向 Micrometer 注册自定义指标：在线用户数（Gauge {@code app.online.users}）、
 *       请求吞吐量（Counter {@code app.requests.total}）。</li>
 *   <li>提供阈值告警：定时采集 CPU/内存/磁盘使用率，超过配置阈值时记录 WARN 日志
 *       （默认 CPU &gt; 90%、内存 &gt; 85%、磁盘 &gt; 90%）。</li>
 * </ol>
 *
 * <p>Actuator 端点暴露（health/metrics/info）与阈值数值通过 {@code application.yml} 配置。
 *
 * @see SystemMetricsCollector 实际指标采集
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class HealthMonitorConfig {

    private final SystemMetricsCollector metricsCollector;

    /** CPU 使用率告警阈值（百分比）。 */
    @Value("${monitor.threshold.cpu:90}")
    private double cpuThreshold;

    /** 内存使用率告警阈值（百分比）。 */
    @Value("${monitor.threshold.memory:85}")
    private double memoryThreshold;

    /** 磁盘使用率告警阈值（百分比）。 */
    @Value("${monitor.threshold.disk:90}")
    private double diskThreshold;

    /**
     * 注册在线用户数 Gauge 指标 {@code app.online.users}。
     *
     * <p>指标值实时反映 Redis 中去重后的登录会话用户数。
     *
     * @return MeterBinder，由 Micrometer 在 MeterRegistry 就绪时绑定
     */
    @Bean
    public MeterBinder onlineUserGauge() {
        return registry -> Gauge.builder("app.online.users", metricsCollector,
                        SystemMetricsCollector::countOnlineUsers)
                .description("当前在线用户数")
                .register(registry);
    }

    /**
     * 注册请求吞吐量 Counter 指标 {@code app.requests.total}。
     *
     * <p>由 {@link com.zwinsight.system.interceptor.RequestThroughputFilter} 在每次 HTTP 请求时递增。
     *
     * @param registry Micrometer 指标注册中心
     * @return Counter 实例
     */
    @Bean
    public Counter requestThroughputCounter(MeterRegistry registry) {
        return Counter.builder("app.requests.total")
                .description("请求吞吐量（累计请求数）")
                .register(registry);
    }

    /**
     * 阈值告警定时任务。
     *
     * <p>每 60 秒采集一次 CPU/内存/磁盘使用率，任一指标超过配置阈值时记录 WARN 日志。
     * 指标无法获取（返回负值）时跳过该项判断，避免误报。
     */
    @Scheduled(fixedRate = 60_000L, initialDelay = 60_000L)
    public void checkThresholds() {
        double cpu = metricsCollector.getCpuUsagePercent();
        if (cpu >= 0 && cpu > cpuThreshold) {
            log.warn("【健康告警】CPU 使用率 {}% 超过阈值 {}%", cpu, cpuThreshold);
        }

        double memory = metricsCollector.getMemoryUsagePercent();
        if (memory >= 0 && memory > memoryThreshold) {
            log.warn("【健康告警】内存使用率 {}% 超过阈值 {}%", memory, memoryThreshold);
        }

        double disk = metricsCollector.getDiskUsagePercent();
        if (disk >= 0 && disk > diskThreshold) {
            log.warn("【健康告警】磁盘使用率 {}% 超过阈值 {}%", disk, diskThreshold);
        }
    }
}
