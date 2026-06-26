/**
 * 离线缓存 / 同步引擎 属性测试（fast-check）
 *
 * **Validates: Requirements 4.5, 4.6, 5.2**
 *
 * 覆盖属性：
 *  - Feature: p2-advanced, Property 7: 缓存过期标记
 *      cachedAt 超过 7 天的 entry，markExpired() 后 expired=true；未超过的保持 false。
 *  - Feature: p2-advanced, Property 8: LRU 缓存淘汰
 *      超过 100MB 后 evictLRU() 将总占用降至 ≤ 80% 上限，且按 lastAccessedAt 升序淘汰已过期项，
 *      未过期项永不被淘汰。
 *  - Feature: p2-advanced, Property 9: 离线操作队列有序提交
 *      syncAll() 按 timestamp 升序逐条提交操作。
 *
 * 说明：被测类通过 uni.* 存储读写，测试用内存存储桩真实后备（见 tests/setup.ts），
 *       不 mock 业务逻辑，仅替换平台存储/网络底座。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import * as fc from 'fast-check'
import { resetUniStorage, getUni } from './setup'

// offlineCache.sync() 依赖的接口与 store —— 本测试不涉及 sync()，
// 仅 mock 以切断 @/utils/request、pinia 的导入链，保持纯逻辑可测。
vi.mock('@/api/common', () => ({
  getMaterialDict: vi.fn(),
  getProjectList: vi.fn(),
}))
vi.mock('@/stores/user', () => ({
  useUserStore: vi.fn(() => ({ userInfo: null })),
}))

import {
  OfflineCacheManager,
  STORAGE_KEYS,
  type CacheMeta,
} from '@/utils/offlineCache'
import { SyncEngine } from '@/utils/syncEngine'

const DAY_MS = 24 * 60 * 60 * 1000
const EXPIRE_MS = 7 * DAY_MS
const MAX_BYTES = 100 * 1024 * 1024
const TARGET_BYTES = Math.floor(MAX_BYTES * 0.8)

beforeEach(() => {
  resetUniStorage()
})

// ---------------------------------------------------------------------------
// Property 7: 缓存过期标记
// ---------------------------------------------------------------------------

describe('Feature: p2-advanced, Property 7: 缓存过期标记', () => {
  it('cachedAt 超过 7 天 → expired=true；未超过 → expired=false', () => {
    fc.assert(
      fc.property(
        // 每个条目一个「相对当前时间的年龄(ms)」，覆盖过期/未过期两侧
        fc.array(
          fc.integer({ min: 0, max: 30 * DAY_MS }),
          { minLength: 1, maxLength: 20 }
        ),
        (ages) => {
          resetUniStorage()
          const now = Date.now()
          const cache = new OfflineCacheManager()

          const meta: CacheMeta = { totalSize: 0, entries: {} }
          ages.forEach((age, i) => {
            const key = `entry_${i}`
            const cachedAt = now - age
            meta.entries[key] = {
              version: 1,
              cachedAt,
              lastAccessedAt: cachedAt,
              size: 100,
              expired: false,
            }
            meta.totalSize += 100
            // 同步写入条目本体，markExpired 会更新它
            getUni().setStorageSync(key, {
              data: { v: i },
              version: 1,
              cachedAt,
              lastAccessedAt: cachedAt,
              expired: false,
            })
          })
          getUni().setStorageSync(STORAGE_KEYS.META, meta)

          cache.markExpired()

          const after = getUni().getStorageSync(STORAGE_KEYS.META) as CacheMeta
          ages.forEach((age, i) => {
            const key = `entry_${i}`
            const shouldExpire = now - (now - age) > EXPIRE_MS // = age > EXPIRE_MS
            expect(after.entries[key].expired).toBe(shouldExpire)
          })
        }
      ),
      { numRuns: 100 }
    )
  })
})

// ---------------------------------------------------------------------------
// Property 8: LRU 缓存淘汰
// ---------------------------------------------------------------------------

describe('Feature: p2-advanced, Property 8: LRU 缓存淘汰', () => {
  it('超出上限后淘汰至 ≤80%，按 lastAccessedAt 升序淘汰过期项，未过期项保留', () => {
    fc.assert(
      fc.property(
        // 已过期条目：各自的占用字节 + 互异的 lastAccessedAt 序号
        fc.array(fc.integer({ min: 1 * 1024 * 1024, max: 30 * 1024 * 1024 }), {
          minLength: 4,
          maxLength: 12,
        }),
        // 未过期条目占用（总和会被约束到 ≤ target）
        fc.array(fc.integer({ min: 1 * 1024 * 1024, max: 10 * 1024 * 1024 }), {
          minLength: 0,
          maxLength: 5,
        }),
        (expiredSizes, freshSizesRaw) => {
          resetUniStorage()
          const cache = new OfflineCacheManager()
          const now = Date.now()

          // 约束未过期条目总和 ≤ target，确保淘汰所有过期项后必能降到 target 以下
          let freshTotal = 0
          const freshSizes: number[] = []
          for (const s of freshSizesRaw) {
            if (freshTotal + s > TARGET_BYTES) break
            freshSizes.push(s)
            freshTotal += s
          }

          const meta: CacheMeta = { totalSize: 0, entries: {} }

          // 未过期条目（expired=false）
          freshSizes.forEach((size, i) => {
            const key = `fresh_${i}`
            meta.entries[key] = {
              version: 1,
              cachedAt: now,
              lastAccessedAt: now + i,
              size,
              expired: false,
            }
            meta.totalSize += size
            getUni().setStorageSync(key, { data: 1, version: 1, cachedAt: now, lastAccessedAt: now, expired: false })
          })

          // 已过期条目（expired=true），lastAccessedAt 互异（用索引区分）
          expiredSizes.forEach((size, i) => {
            const key = `exp_${i}`
            meta.entries[key] = {
              version: 1,
              cachedAt: now - 10 * DAY_MS,
              lastAccessedAt: now - (expiredSizes.length - i) * 1000, // 越靠前越久未访问
              size,
              expired: true,
            }
            meta.totalSize += size
            getUni().setStorageSync(key, { data: 1, version: 1, cachedAt: now, lastAccessedAt: now, expired: true })
          })

          // 前置条件：总占用确实超过上限（否则 evictLRU 直接返回，属性平凡成立）
          fc.pre(meta.totalSize > MAX_BYTES)

          getUni().setStorageSync(STORAGE_KEYS.META, meta)

          cache.evictLRU()

          const after = getUni().getStorageSync(STORAGE_KEYS.META) as CacheMeta

          // (1) 总占用降至 ≤ 80% 上限
          expect(after.totalSize).toBeLessThanOrEqual(TARGET_BYTES)

          // (2) 未过期条目全部保留
          freshSizes.forEach((_s, i) => {
            expect(after.entries[`fresh_${i}`]).toBeDefined()
          })

          // (3) 淘汰的过期项是 lastAccessedAt 最小的若干个：
          //     任意「保留的过期项」的 lastAccessedAt ≥ 任意「被淘汰过期项」的 lastAccessedAt
          const removedLAT: number[] = []
          const keptLAT: number[] = []
          expiredSizes.forEach((_s, i) => {
            const key = `exp_${i}`
            const lat = now - (expiredSizes.length - i) * 1000
            if (after.entries[key]) keptLAT.push(lat)
            else removedLAT.push(lat)
          })
          if (removedLAT.length > 0 && keptLAT.length > 0) {
            expect(Math.max(...removedLAT)).toBeLessThanOrEqual(Math.min(...keptLAT))
          }

          // (4) totalSize 记账与剩余条目体积之和一致
          const sumKept = Object.values(after.entries).reduce((acc, e) => acc + e.size, 0)
          expect(after.totalSize).toBe(sumKept)
        }
      ),
      { numRuns: 100 }
    )
  })
})

// ---------------------------------------------------------------------------
// Property 9: 离线操作队列有序提交
// ---------------------------------------------------------------------------

describe('Feature: p2-advanced, Property 9: 离线操作队列有序提交', () => {
  it('syncAll() 按 timestamp 升序逐条提交，全部成功后队列清空', async () => {
    await fc.assert(
      fc.asyncProperty(
        fc.array(fc.integer({ min: 0, max: 10_000_000 }), { minLength: 1, maxLength: 25 }),
        async (timestamps) => {
          resetUniStorage()

          // 记录真实提交顺序（uni.request 桩按调用顺序记录 payload.ts）
          const submitted: number[] = []
          getUni().request = (options: any) => {
            submitted.push(options.data.ts)
            // 模拟 2xx 成功响应（需求 5.4）
            options.success({ statusCode: 200, data: { code: 200 } })
          }

          const engine = new SyncEngine()
          timestamps.forEach((ts, i) => {
            engine.enqueue({
              type: 'CREATE',
              endpoint: `/v1/op/${i}`,
              payload: { ts },
              timestamp: ts,
            })
          })

          await engine.syncAll()

          // 提交顺序必须为 timestamp 升序（非递减）
          const sorted = [...timestamps].sort((a, b) => a - b)
          expect(submitted).toEqual(sorted)

          // 全部成功 → 队列清空（需求 5.4）
          expect(engine.getQueue()).toHaveLength(0)
        }
      ),
      { numRuns: 100 }
    )
  })
})
