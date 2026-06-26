/**
 * OfflineCacheManager — 移动端离线缓存管理器
 *
 * 负责：
 *  - sync()：首次登录/手动同步，将材料字典、项目列表、当前用户信息缓存到本地
 *  - get()/set()：读写缓存条目（带版本号与时间戳）
 *  - markExpired()：7 天过期标记
 *  - evictLRU()：超过 100MB 时按 LRU 淘汰已过期缓存至 80% 以下
 *  - getUsedSize()：当前已用空间（bytes）
 *
 * 数据来源均为真实后端接口：
 *  - 材料字典：GET /api/v1/basedata/material （common.ts#getMaterialDict）
 *  - 项目列表：GET /api/v1/project          （common.ts#getProjectList）
 *  - 当前用户信息：登录接口返回并存入 user store（/api/v1/auth/login 的真实响应数据）
 *
 * 对应需求：4.1, 4.2, 4.4, 4.5, 4.6
 */

import { getMaterialDict, getProjectList } from '@/api/common'
import { useUserStore } from '@/stores/user'

// ---------------------------------------------------------------------------
// 类型定义
// ---------------------------------------------------------------------------

/** 单条缓存数据结构 */
export interface CacheEntry<T = any> {
  /** 业务数据 */
  data: T
  /** 数据版本号（同步时间戳，单调递增） */
  version: number
  /** 缓存写入时间戳（ms） */
  cachedAt: number
  /** 最后访问时间戳（ms），LRU 排序依据 */
  lastAccessedAt: number
  /** 是否已过期 */
  expired: boolean
}

/** 缓存元数据中单条记录（不含 data 本体，仅记账信息） */
export interface CacheMetaEntry {
  version: number
  cachedAt: number
  lastAccessedAt: number
  /** 该条目占用的字节数 */
  size: number
  expired: boolean
}

/** 缓存元数据 */
export interface CacheMeta {
  /** 当前总占用 bytes */
  totalSize: number
  /** 各缓存条目记账信息，key 为存储 key */
  entries: Record<string, CacheMetaEntry>
}

// ---------------------------------------------------------------------------
// 常量
// ---------------------------------------------------------------------------

/** uni.setStorageSync 存储 key 约定（与设计文档一致） */
export const STORAGE_KEYS = {
  META: 'offline_meta',
  MATERIAL_DICT: 'offline_material',
  PROJECT_LIST: 'offline_projects',
  USER_INFO: 'offline_user',
  OP_QUEUE: 'offline_op_queue'
} as const

const MB = 1024 * 1024

// ---------------------------------------------------------------------------
// OfflineCacheManager
// ---------------------------------------------------------------------------

export class OfflineCacheManager {
  /** 缓存容量上限（MB） */
  private readonly MAX_SIZE_MB = 100
  /** 缓存有效期（天） */
  private readonly EXPIRE_DAYS = 7
  /** LRU 淘汰目标比例（淘汰至上限的 80%） */
  private readonly EVICT_TARGET_RATIO = 0.8

  /** 单次同步材料字典最大条数（需求 4.1） */
  private readonly MAX_MATERIAL = 10000
  /** 单次同步项目列表最大条数（需求 4.1） */
  private readonly MAX_PROJECT = 500

  private get maxBytes(): number {
    return this.MAX_SIZE_MB * MB
  }

  private get expireMs(): number {
    return this.EXPIRE_DAYS * 24 * 60 * 60 * 1000
  }

  // -------------------------------------------------------------------------
  // 元数据读写
  // -------------------------------------------------------------------------

  private readMeta(): CacheMeta {
    const raw = uni.getStorageSync(STORAGE_KEYS.META)
    if (raw && typeof raw === 'object' && raw.entries) {
      return raw as CacheMeta
    }
    return { totalSize: 0, entries: {} }
  }

  private writeMeta(meta: CacheMeta): void {
    uni.setStorageSync(STORAGE_KEYS.META, meta)
  }

  /** 估算数据占用字节数（按 UTF-8 字节长度） */
  private byteSize(data: any): number {
    let str: string
    try {
      str = JSON.stringify(data) ?? ''
    } catch {
      str = String(data ?? '')
    }
    let bytes = 0
    for (let i = 0; i < str.length; i++) {
      const code = str.charCodeAt(i)
      if (code < 0x80) bytes += 1
      else if (code < 0x800) bytes += 2
      else if (code >= 0xd800 && code <= 0xdbff) {
        // 代理对：占用 4 字节，跳过低位代理
        bytes += 4
        i++
      } else bytes += 3
    }
    return bytes
  }

  // -------------------------------------------------------------------------
  // 公共 API
  // -------------------------------------------------------------------------

  /** 当前已用空间（bytes） */
  getUsedSize(): number {
    return this.readMeta().totalSize
  }

  /**
   * 写入/更新一条缓存（需求 4.4：记录版本号与缓存时间戳）
   * @param key     存储 key（建议使用 STORAGE_KEYS 中的常量）
   * @param data    业务数据
   * @param version 数据版本号
   */
  set<T>(key: string, data: T, version: number): void {
    const now = Date.now()
    const entry: CacheEntry<T> = {
      data,
      version,
      cachedAt: now,
      lastAccessedAt: now,
      expired: false
    }
    uni.setStorageSync(key, entry)

    const size = this.byteSize(data)
    const meta = this.readMeta()
    const previous = meta.entries[key]
    // 重新计账：减去旧体积、加上新体积
    meta.totalSize = meta.totalSize - (previous?.size ?? 0) + size
    if (meta.totalSize < 0) meta.totalSize = 0
    meta.entries[key] = {
      version,
      cachedAt: now,
      lastAccessedAt: now,
      size,
      expired: false
    }
    this.writeMeta(meta)

    // 超过上限触发 LRU 淘汰（需求 4.6）
    if (meta.totalSize > this.maxBytes) {
      this.evictLRU()
    }
  }

  /**
   * 读取一条缓存（需求 4.2：离线读取）
   * 读取时更新 lastAccessedAt 以维护 LRU 顺序。
   * @returns CacheEntry 或 null（不存在/读取失败）
   */
  get<T>(key: string): CacheEntry<T> | null {
    let entry: CacheEntry<T> | null = null
    try {
      const raw = uni.getStorageSync(key)
      if (raw && typeof raw === 'object' && 'data' in raw) {
        entry = raw as CacheEntry<T>
      }
    } catch {
      return null
    }
    if (!entry) return null

    const now = Date.now()
    entry.lastAccessedAt = now
    try {
      uni.setStorageSync(key, entry)
    } catch {
      // 写回失败不影响读取结果
    }

    const meta = this.readMeta()
    if (meta.entries[key]) {
      meta.entries[key].lastAccessedAt = now
      this.writeMeta(meta)
    }
    return entry
  }

  /**
   * 标记过期数据（需求 4.5）
   * 将 cachedAt 超过有效期（默认 7 天）的缓存 expired 置为 true。
   */
  markExpired(): void {
    const now = Date.now()
    const meta = this.readMeta()
    let changed = false
    for (const key of Object.keys(meta.entries)) {
      const metaEntry = meta.entries[key]
      if (!metaEntry.expired && now - metaEntry.cachedAt > this.expireMs) {
        metaEntry.expired = true
        changed = true
        // 同步更新缓存本体的 expired 标记
        try {
          const raw = uni.getStorageSync(key)
          if (raw && typeof raw === 'object' && 'data' in raw) {
            ;(raw as CacheEntry).expired = true
            uni.setStorageSync(key, raw)
          }
        } catch {
          // 忽略单条写入异常，元数据已标记
        }
      }
    }
    if (changed) this.writeMeta(meta)
  }

  /**
   * LRU 淘汰（需求 4.6）
   * 当已用空间超过上限时，按最近最少使用策略逐条清除「已过期」缓存，
   * 直至已用空间降至上限的 80% 以下。
   */
  evictLRU(): void {
    const meta = this.readMeta()
    if (meta.totalSize <= this.maxBytes) return

    const target = Math.floor(this.maxBytes * this.EVICT_TARGET_RATIO)

    // 仅淘汰已过期条目，按 lastAccessedAt 升序（最久未访问优先）
    const expiredKeys = Object.keys(meta.entries)
      .filter((k) => meta.entries[k].expired)
      .sort((a, b) => meta.entries[a].lastAccessedAt - meta.entries[b].lastAccessedAt)

    for (const key of expiredKeys) {
      if (meta.totalSize <= target) break
      const size = meta.entries[key].size
      try {
        uni.removeStorageSync(key)
      } catch {
        // 移除失败仍从元数据剔除，避免计账不一致
      }
      delete meta.entries[key]
      meta.totalSize -= size
      if (meta.totalSize < 0) meta.totalSize = 0
    }

    this.writeMeta(meta)
  }

  /**
   * 同步（需求 4.1 / 4.4）
   * 首次登录成功或用户手动触发时调用：
   * 拉取材料字典、项目列表、当前用户信息并写入本地缓存，
   * 每条数据携带版本号与缓存时间戳。
   *
   * 同步前先标记过期数据（需求 4.5：联网时优先刷新过期数据）。
   */
  async sync(): Promise<void> {
    // 同步前先刷新过期标记
    this.markExpired()

    // 以本次同步时间戳作为版本号（单调递增，服务端为准的全量覆盖语义）
    const version = Date.now()

    // 1. 材料字典（最多 10000 条）
    const materialRes: any = await getMaterialDict({ page: 1, size: this.MAX_MATERIAL })
    this.set(STORAGE_KEYS.MATERIAL_DICT, materialRes?.data ?? materialRes, version)

    // 2. 项目列表（最多 500 条）
    const projectRes: any = await getProjectList({ page: 1, size: this.MAX_PROJECT })
    this.set(STORAGE_KEYS.PROJECT_LIST, projectRes?.data ?? projectRes, version)

    // 3. 当前用户信息（来自登录接口的真实响应，存于 user store）
    const userStore = useUserStore()
    if (userStore.userInfo) {
      this.set(STORAGE_KEYS.USER_INFO, userStore.userInfo, version)
    }
  }
}

/** 单例实例 */
export const offlineCache = new OfflineCacheManager()

export default offlineCache
