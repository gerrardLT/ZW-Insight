/**
 * format.ts 单元测试
 *
 * 被测代码为 src/utils/format.ts 的 toWan，实现与 PC 端
 * zw-insight-web/src/utils/chart-format.ts 逐字同构。本套用例钉住其语义，
 * 防止后续单侧修改造成三端金额展示漂移。
 *
 * 覆盖：正常路径（数字/数字字符串）、边界（0 / '' / null / undefined / 负数）、
 * 异常（NaN / 非数字字符串）——其中异常两项记录的是与 PC 端共有的既有行为。
 */
import { describe, it, expect } from 'vitest'
import { toWan } from '@/utils/format'

describe('toWan — 金额转万元（保留两位小数）', () => {
  describe('正常路径', () => {
    it('数字入参：50000 → 5', () => {
      expect(toWan(50000)).toBe(5)
    })

    it('数字字符串入参（后端 BigDecimal 序列化形态）："1234567" → 123.46', () => {
      expect(toWan('1234567')).toBe(123.46)
    })

    it('四舍五入到两位小数：100000.99 → 10', () => {
      expect(toWan(100000.99)).toBe(10)
    })

    it('大额不丢精度：1e9 → 100000', () => {
      expect(toWan(1_000_000_000)).toBe(100000)
    })
  })

  describe('边界值', () => {
    it('0 元 → 0', () => {
      expect(toWan(0)).toBe(0)
    })

    it('负数（变更事件 costDelta 可为负）：-50000 → -5', () => {
      expect(toWan(-50000)).toBe(-5)
    })

    it.each([
      ['空字符串', ''],
      ['null', null],
      ['undefined', undefined],
    ])('%s → 0（被 `val || 0` 兜住）', (_label, input) => {
      expect(toWan(input as string | null | undefined)).toBe(0)
    })
  })

  describe('异常值（记录与 PC 端共有的既有行为，非本次引入）', () => {
    it('NaN 是 falsy，被 `val || 0` 兜住 → 0', () => {
      expect(toWan(NaN)).toBe(0)
    })

    it('非数字字符串是 truthy，逃过 `val || 0`，Number() 后为 NaN → 返回 NaN', () => {
      // 与 PC 端同构的既有缺口：后端 costDelta 为 BigDecimal，实际不会出现该形态，
      // 故此行为仅作契约记录；若将来要收敛，须 PC 与移动端同步修改。
      expect(Number.isNaN(toWan('abc'))).toBe(true)
    })
  })
})
