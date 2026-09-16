/**
 * 离线数据读取助手（需求 4.2、4.8）
 *
 * 统一封装「在线优先 + 离线回退缓存」的数据读取逻辑，供材料 / 项目列表页面复用：
 *  - 在线：调用真实接口获取数据，并顺便刷新本地缓存（offlineCache.set）
 *  - 离线（或接口失败回退）：读取 offlineCache 中的缓存数据展示
 *  - 离线且无缓存：返回空结果并附带提示文案「无可用离线数据，请联网后同步」
 *
 * 返回结构统一为 { records, fromCache, empty, message }，页面据此渲染列表或空状态。
 */

import { getProjectList, getMaterialDict } from '@/api/common'
import { offlineCache, STORAGE_KEYS } from './offlineCache'
import { useNetworkStore } from '@/stores/network'
import type { OfflineListResult, OfflineReadMeta, FallbackReason, Freshness } from '@/types/offline'

export type { OfflineListResult }

/** 无可用离线数据时的统一提示（需求 4.8） */
export const NO_OFFLINE_DATA_TIP = '无可用离线数据，请联网后同步'

/** 7 天毫秒数 */
const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000

/** 从任意响应/缓存数据结构中提取 records 数组 */
function extractRecords<T = any>(data: any): T[] {
  if (!data) return []
  if (Array.isArray(data)) return data as T[]
  if (Array.isArray(data.records)) return data.records as T[]
  if (Array.isArray(data.list)) return data.list as T[]
  if (data.data) return extractRecords<T>(data.data)
  return []
}

/** 读取缓存条目并组装为列表结果 */
function readFromCache<T = any>(
  key: string,
  onlineAttempted: boolean,
  fallbackReason?: FallbackReason
): OfflineListResult<T> {
  const entry = offlineCache.get(key)
  const records = extractRecords<T>(entry?.data)
  const now = Date.now()

  let freshness: Freshness = 'UNKNOWN'
  let ageMs: number | undefined
  if (entry?.cachedAt) {
    ageMs = Math.max(0, now - entry.cachedAt)
    freshness = ageMs > SEVEN_DAYS_MS ? 'STALE' : 'FRESH'
  }

  const meta: OfflineReadMeta = {
    source: 'CACHE',
    freshness,
    cachedAt: entry?.cachedAt,
    ageMs,
    onlineAttempted,
    fallbackReason
  }

  if (records.length === 0) {
    return {
      records: [],
      fromCache: true,
      empty: true,
      message: NO_OFFLINE_DATA_TIP,
      meta
    }
  }

  return {
    records,
    fromCache: true,
    empty: false,
    message: fallbackReason === 'NETWORK_ERROR' ? '在线请求失败，当前展示本地缓存' : undefined,
    meta
  }
}

/**
 * 加载项目列表（在线优先，离线回退缓存）
 * @param params 分页参数（与 getProjectList 一致）
 */
export async function loadProjectList<T = any>(params?: any): Promise<OfflineListResult<T>> {
  const network = useNetworkStore()

  // 离线：直接读取缓存
  if (network.isOffline) {
    return readFromCache<T>(STORAGE_KEYS.PROJECT_LIST, false, 'CACHE_ONLY')
  }

  // 在线：调用真实接口并刷新缓存
  try {
    const res: any = await getProjectList(params)
    const data = res?.data ?? res
    const records = extractRecords<T>(data)
    // 刷新本地缓存（版本号用当前时间戳，与 offlineCache.sync 语义一致）
    offlineCache.set(STORAGE_KEYS.PROJECT_LIST, data, Date.now())
    return {
      records,
      fromCache: false,
      empty: records.length === 0,
      meta: {
        source: 'NETWORK',
        freshness: 'FRESH',
        onlineAttempted: true
      }
    }
  } catch (e) {
    // 接口失败回退缓存，保证现场可用，但明确标注网络失败回退
    return readFromCache<T>(STORAGE_KEYS.PROJECT_LIST, true, 'NETWORK_ERROR')
  }
}

/**
 * 加载材料字典（在线优先，离线回退缓存）
 * @param params 查询参数（与 getMaterialDict 一致）
 */
export async function loadMaterialDict<T = any>(params?: any): Promise<OfflineListResult<T>> {
  const network = useNetworkStore()

  if (network.isOffline) {
    return readFromCache<T>(STORAGE_KEYS.MATERIAL_DICT, false, 'CACHE_ONLY')
  }

  try {
    const res: any = await getMaterialDict(params)
    const data = res?.data ?? res
    const records = extractRecords<T>(data)
    offlineCache.set(STORAGE_KEYS.MATERIAL_DICT, data, Date.now())
    return {
      records,
      fromCache: false,
      empty: records.length === 0,
      meta: {
        source: 'NETWORK',
        freshness: 'FRESH',
        onlineAttempted: true
      }
    }
  } catch (e) {
    return readFromCache<T>(STORAGE_KEYS.MATERIAL_DICT, true, 'NETWORK_ERROR')
  }
}
