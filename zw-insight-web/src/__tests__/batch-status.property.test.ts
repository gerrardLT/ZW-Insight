/**
 * Property 4: 批量操作排除当前用户
 *
 * **Validates: Requirements 3.9**
 *
 * 使用 Vitest + fast-check 验证：
 * - 对于包含 currentUserId 的 ID 数组，过滤后结果不包含 currentUserId，长度 == 原始长度 - 1
 * - 对于不包含 currentUserId 的 ID 数组，过滤结果 == 原始数组
 *
 * 依赖安装：npm install -D vitest fast-check
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { filterBatchIds } from '@/utils/batch-status'

describe('Property 4: 批量操作排除当前用户', () => {
  it('包含 currentUserId 的数组，过滤后不包含该 ID 且长度减 1', () => {
    fc.assert(
      fc.property(
        // 生成一个不含重复的 ID 数组，以及一个 currentUserId
        fc.integer({ min: 1, max: 100000 }).chain((currentUserId) =>
          fc.tuple(
            // 生成不包含 currentUserId 的 ID 数组
            fc.array(
              fc.integer({ min: 1, max: 100000 }).filter(id => id !== currentUserId),
              { minLength: 0, maxLength: 50 }
            ),
            fc.constant(currentUserId)
          )
        ),
        ([otherIds, currentUserId]) => {
          // 将 currentUserId 插入数组中随机位置
          const insertIndex = otherIds.length > 0
            ? Math.floor(Math.random() * (otherIds.length + 1))
            : 0
          const selectedIds = [
            ...otherIds.slice(0, insertIndex),
            currentUserId,
            ...otherIds.slice(insertIndex)
          ]

          const result = filterBatchIds(selectedIds, currentUserId)

          // 过滤后不包含 currentUserId
          expect(result).not.toContain(currentUserId)
          // 长度应为原始长度 - 1
          expect(result.length).toBe(selectedIds.length - 1)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('不包含 currentUserId 的数组，过滤后与原始数组相同', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 100000 }).chain((currentUserId) =>
          fc.tuple(
            // 生成不包含 currentUserId 的 ID 数组
            fc.array(
              fc.integer({ min: 1, max: 100000 }).filter(id => id !== currentUserId),
              { minLength: 0, maxLength: 50 }
            ),
            fc.constant(currentUserId)
          )
        ),
        ([selectedIds, currentUserId]) => {
          const result = filterBatchIds(selectedIds, currentUserId)

          // 结果应与原始数组完全相同
          expect(result).toEqual(selectedIds)
          expect(result.length).toBe(selectedIds.length)
        }
      ),
      { numRuns: 100 }
    )
  })
})
