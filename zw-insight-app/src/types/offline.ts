/**
 * 移动端离线数据与状态模型
 */

export type DataSource = 'NETWORK' | 'CACHE'

export type Freshness = 'FRESH' | 'AGING' | 'STALE' | 'UNKNOWN'

export type FallbackReason = 'NETWORK_ERROR' | 'HTTP_ERROR' | 'CACHE_ONLY'

/** 离线读取元数据 */
export interface OfflineReadMeta {
  /** 数据来源 */
  source: DataSource
  /** 新鲜度评估：7天以内 FRESH，超过7天 STALE，未记账 UNKNOWN */
  freshness: Freshness
  /** 缓存写入时间戳 (ms) */
  cachedAt?: number
  /** 缓存年龄 (ms) */
  ageMs?: number
  /** 是否曾尝试在线请求 */
  onlineAttempted: boolean
  /** 回退缓存原因 */
  fallbackReason?: FallbackReason
}

/** 统一离线列表读取结果 */
export interface OfflineListResult<T = any> {
  /** 列表数据 */
  records: T[]
  /** 向后兼容：是否来自本地缓存 */
  fromCache: boolean
  /** 是否为空 */
  empty: boolean
  /** 空状态提示文案 */
  message?: string
  /** 离线/缓存元数据（真实性叙事） */
  meta: OfflineReadMeta
}
