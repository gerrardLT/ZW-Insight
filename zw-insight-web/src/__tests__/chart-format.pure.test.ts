/**
 * chart-format.ts 数值变换纯函数测试（2026-08-14 P2 补测）
 *
 * @matrix C-29-3/C-29-4（formatWan 换算与空值）、C-30-4（剩余预算非负）、
 *   C-30-5（完成率裁剪）、C-30-6/C-30-7（toWan 换算精度）
 * 单一事实源：dashboard/index.vue 与 project-dashboard.vue 均 import 本模块，
 * 提取迁移无语义变更（与原组件内实现逐字对齐实证）。
 */
import { describe, it, expect } from 'vitest'
import { formatWan, toAmount2, toWan, clampPercent, nonNegativeRemaining } from '@/utils/chart-format'

describe('chart-format 数值变换纯函数（@matrix C-29/C-30）', () => {
  // ---- formatWan（dashboard/index.vue 迁入）----
  describe('formatWan 金额转万元展示', () => {
    // @matrix C-29-3
    it('正常金额换算（1 位小数）', () => {
      expect(formatWan(1234567)).toBe('123.5')
      expect(formatWan(10000)).toBe('1.0')
      expect(formatWan(0)).toBe('0.0')
    })

    // @matrix C-29-4
    it('空值/NaN 归零字符串', () => {
      expect(formatWan(null)).toBe('0')
      expect(formatWan(undefined)).toBe('0')
      expect(formatWan('')).toBe('0')
      expect(formatWan('abc')).toBe('0')
    })

    it('数值字符串可换算', () => {
      expect(formatWan('50000')).toBe('5.0')
    })
  })

  // ---- toWan / toAmount2（project-dashboard.vue 迁入）----
  describe('toWan/toAmount2 金额精度', () => {
    // @matrix C-30-6
    it('toWan 万元换算两位小数', () => {
      expect(toWan(1234567)).toBe(123.46)
      expect(toWan(10000)).toBe(1)
      expect(toWan(0)).toBe(0)
    })

    // @matrix C-30-7
    it('toWan 空值归零不抛错', () => {
      expect(toWan(undefined as any)).toBe(0)
      expect(toWan(null as any)).toBe(0)
    })

    it('toAmount2 四舍五入两位小数', () => {
      expect(toAmount2(1.005)).toBe(1)   // 1.005*100=100.49999… → 100
      expect(toAmount2(1.006)).toBe(1.01)
      expect(toAmount2(undefined as any)).toBe(0)
    })
  })

  // ---- clampPercent ----
  describe('clampPercent 完成率裁剪', () => {
    // @matrix C-30-5
    it('正常比率转百分比整数', () => {
      expect(clampPercent(0.85)).toBe(85)
      expect(clampPercent(0)).toBe(0)
      expect(clampPercent(1)).toBe(100)
    })

    // @matrix C-30-5（越界裁剪）
    it('越界值裁剪到 [0,100]', () => {
      expect(clampPercent(1.2)).toBe(100)
      expect(clampPercent(-0.3)).toBe(0)
      expect(clampPercent(undefined as any)).toBe(0)
    })
  })

  // ---- nonNegativeRemaining ----
  describe('nonNegativeRemaining 剩余预算非负', () => {
    // @matrix C-30-4
    it('正常预算差值', () => {
      expect(nonNegativeRemaining(100000, 30000)).toBe(70000)
    })

    // @matrix C-30-4（超预算归零）
    it('支出超预算归零不为负', () => {
      expect(nonNegativeRemaining(100000, 150000)).toBe(0)
      expect(nonNegativeRemaining(0, 5000)).toBe(0)
      expect(nonNegativeRemaining(undefined as any, 100)).toBe(0)
    })
  })
})
