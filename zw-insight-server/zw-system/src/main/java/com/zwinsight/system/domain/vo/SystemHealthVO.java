package com.zwinsight.system.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统健康指标聚合视图，供 PC 监控仪表盘读取。
 *
 * <p>聚合 CPU、JVM 内存、磁盘、数据库连接池、Redis 连接状态及在线用户数等运行时指标。
 * 指标项无法获取时以 {@code -1}（数值）或 {@code "UNKNOWN"/"DOWN"}（状态）表示，不返回伪造数据。
 */
@Data
public class SystemHealthVO {

    /** CPU 使用率百分比（0~100），-1 表示无法获取 */
    private double cpuUsagePercent;

    /** JVM 已用堆内存（MB） */
    private long memoryUsedMb;

    /** JVM 最大堆内存（MB），-1 表示无上限 */
    private long memoryMaxMb;

    /** JVM 堆内存使用率百分比（0~100），-1 表示无法计算 */
    private double memoryUsagePercent;

    /** 磁盘已用空间（GB） */
    private long diskUsedGb;

    /** 磁盘总空间（GB） */
    private long diskTotalGb;

    /** 磁盘使用率百分比（0~100），-1 表示无法获取 */
    private double diskUsagePercent;

    /** 数据库连接池活跃连接数，-1 表示无法获取 */
    private int dbPoolActive;

    /** 数据库连接池最大连接数，-1 表示无法获取 */
    private int dbPoolMax;

    /** Redis 连接状态：UP / DOWN */
    private String redisStatus;

    /** 当前在线用户数 */
    private long onlineUsers;

    /** 累计请求吞吐量计数 */
    private long requestTotal;

    /** 采集时间戳 */
    private LocalDateTime timestamp;
}
