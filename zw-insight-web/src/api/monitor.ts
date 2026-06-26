import request from '@/utils/request'

/**
 * 系统健康指标聚合视图
 * 对应后端 SystemHealthVO（zw-system 模块 HealthMonitorController）
 *
 * 说明：指标项无法获取时后端以 -1（数值）或 "UNKNOWN"/"DOWN"（状态）表示，
 * 前端按真实值展示，不做伪造或静默 fallback。
 */
export interface SystemHealthVO {
  /** CPU 使用率百分比（0~100），-1 表示无法获取 */
  cpuUsagePercent: number
  /** JVM 已用堆内存（MB） */
  memoryUsedMb: number
  /** JVM 最大堆内存（MB），-1 表示无上限 */
  memoryMaxMb: number
  /** JVM 堆内存使用率百分比（0~100），-1 表示无法计算 */
  memoryUsagePercent: number
  /** 磁盘已用空间（GB） */
  diskUsedGb: number
  /** 磁盘总空间（GB） */
  diskTotalGb: number
  /** 磁盘使用率百分比（0~100），-1 表示无法获取 */
  diskUsagePercent: number
  /** 数据库连接池活跃连接数，-1 表示无法获取 */
  dbPoolActive: number
  /** 数据库连接池最大连接数，-1 表示无法获取 */
  dbPoolMax: number
  /** Redis 连接状态：UP / DOWN */
  redisStatus: string
  /** 当前在线用户数 */
  onlineUsers: number
  /** 累计请求吞吐量计数 */
  requestTotal: number
  /** 采集时间戳（ISO LocalDateTime） */
  timestamp: string
}

/**
 * 聚合查询系统实时健康指标
 * GET /api/v1/system/monitor/metrics
 * 响应：R<SystemHealthVO> → res.data
 */
export function getSystemHealthMetrics() {
  return request.get('/v1/system/monitor/metrics')
}
