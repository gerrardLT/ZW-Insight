package com.zwinsight.system.controller;

import com.zwinsight.common.result.R;
import com.zwinsight.system.domain.vo.SystemHealthVO;
import com.zwinsight.system.service.SystemMetricsCollector;
import io.micrometer.core.instrument.Counter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 系统健康监控聚合接口。
 *
 * <p>为 PC 监控仪表盘提供一站式健康指标读取：CPU、JVM 内存、磁盘、数据库连接池、
 * Redis 连接状态、在线用户数与请求吞吐量。原始 Micrometer 指标另由 Spring Boot Actuator
 * 的 {@code /actuator/metrics} 端点暴露。
 *
 * @see SystemMetricsCollector
 * @see HealthMonitorController#metrics()
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/system/monitor")
@RequiredArgsConstructor
public class HealthMonitorController {

    private final SystemMetricsCollector metricsCollector;
    private final Counter requestThroughputCounter;

    /**
     * 聚合查询系统实时健康指标。
     *
     * @return 系统健康指标聚合视图
     */
    @GetMapping("/metrics")
    public R<SystemHealthVO> metrics() {
        SystemHealthVO vo = new SystemHealthVO();
        vo.setCpuUsagePercent(metricsCollector.getCpuUsagePercent());
        vo.setMemoryUsedMb(metricsCollector.getMemoryUsedMb());
        vo.setMemoryMaxMb(metricsCollector.getMemoryMaxMb());
        vo.setMemoryUsagePercent(metricsCollector.getMemoryUsagePercent());
        vo.setDiskUsedGb(metricsCollector.getDiskUsedGb());
        vo.setDiskTotalGb(metricsCollector.getDiskTotalGb());
        vo.setDiskUsagePercent(metricsCollector.getDiskUsagePercent());
        vo.setDbPoolActive(metricsCollector.getDbPoolActive());
        vo.setDbPoolMax(metricsCollector.getDbPoolMax());
        vo.setRedisStatus(metricsCollector.getRedisStatus());
        vo.setOnlineUsers(metricsCollector.countOnlineUsers());
        vo.setRequestTotal((long) requestThroughputCounter.count());
        vo.setTimestamp(LocalDateTime.now());
        return R.ok(vo);
    }
}
