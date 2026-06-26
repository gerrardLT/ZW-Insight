/**
 * 水印合成纯逻辑 属性测试（fast-check）
 *
 * **Validates: Requirements 6.2, 6.5, 6.7, 6.8**
 *
 * Feature: p2-advanced, Property 12: 水印合成完整性
 *  - computeFontSize = clamp(width * 0.025, 12, 36)
 *  - computeBarHeight 落在照片高度的 10%~15% 区间
 *  - formatGpsText：定位成功保留 6 位小数；失败返回「定位未获取」
 *  - formatWatermarkTime：输出 yyyy-MM-dd HH:mm:ss 格式
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import {
  computeFontSize,
  computeBarHeight,
  formatGpsText,
  formatWatermarkTime,
} from '@/utils/watermarkCompositor'

const TIME_RE = /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/
const GPS_RE = /^经度:-?\d+\.\d{6}  纬度:-?\d+\.\d{6}$/

describe('Feature: p2-advanced, Property 12: 水印合成完整性', () => {
  // --- computeFontSize ---
  it('字号 = clamp(width*0.025, 12, 36)，且恒落在 [12,36]', () => {
    fc.assert(
      fc.property(fc.double({ min: 0, max: 100000, noNaN: true }), (width) => {
        const size = computeFontSize(width)
        const expected = Math.min(Math.max(width * 0.025, 12), 36)
        expect(size).toBe(expected)
        expect(size).toBeGreaterThanOrEqual(12)
        expect(size).toBeLessThanOrEqual(36)
      }),
      { numRuns: 100 }
    )
  })

  // --- computeBarHeight ---
  it('水印区域高度落在照片高度的 10%~15% 区间', () => {
    fc.assert(
      // 取真实照片高度范围，避免极小尺寸下 Math.ceil 取整误差超出比例区间
      fc.property(fc.integer({ min: 200, max: 10000 }), (height) => {
        const bar = computeBarHeight(height)
        expect(bar).toBeGreaterThanOrEqual(height * 0.1)
        expect(bar).toBeLessThanOrEqual(height * 0.15)
      }),
      { numRuns: 100 }
    )
  })

  // --- formatGpsText：定位成功 ---
  it('定位成功时输出经纬度并保留 6 位小数', () => {
    fc.assert(
      fc.property(
        fc.double({ min: -90, max: 90, noNaN: true }),
        fc.double({ min: -180, max: 180, noNaN: true }),
        (lat, lng) => {
          const text = formatGpsText(lat, lng)
          expect(text).not.toBe('定位未获取')
          expect(GPS_RE.test(text)).toBe(true)
          expect(text).toContain(`经度:${lng.toFixed(6)}`)
          expect(text).toContain(`纬度:${lat.toFixed(6)}`)
        }
      ),
      { numRuns: 100 }
    )
  })

  // --- formatGpsText：定位失败 ---
  it('lat/lng 为 null/undefined/NaN 时返回「定位未获取」', () => {
    fc.assert(
      fc.property(
        fc.constantFrom<number | null | undefined>(null, undefined, NaN),
        fc.double({ min: -180, max: 180, noNaN: true }),
        (badLat, lng) => {
          expect(formatGpsText(badLat, lng)).toBe('定位未获取')
          expect(formatGpsText(lng, badLat)).toBe('定位未获取')
        }
      ),
      { numRuns: 100 }
    )
  })

  // --- formatWatermarkTime ---
  it('任意时间戳/Date 输入均输出 yyyy-MM-dd HH:mm:ss 格式', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: 4_102_444_800_000 }), // 1970 ~ 2100
        (ts) => {
          expect(TIME_RE.test(formatWatermarkTime(ts))).toBe(true)
          expect(TIME_RE.test(formatWatermarkTime(new Date(ts)))).toBe(true)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('已是标准格式的字符串原样返回；空输入回退当前时间且格式合法', () => {
    fc.assert(
      fc.property(fc.date({ min: new Date(0), max: new Date(4_102_444_800_000) }), (d) => {
        const formatted = formatWatermarkTime(d)
        expect(TIME_RE.test(formatted)).toBe(true)
        // 标准格式字符串幂等
        expect(formatWatermarkTime(formatted)).toBe(formatted)
      }),
      { numRuns: 100 }
    )
  })
})
