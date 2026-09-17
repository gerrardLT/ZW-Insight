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
import { describe, it, expect, vi, afterEach } from 'vitest'
import { toWan, shortTime } from '@/utils/format'

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

describe('shortTime — 列表时间短格式（S3.1：home / message-center 共享）', () => {
  afterEach(() => {
    vi.useRealTimers()
  })

  /** 固定系统时间，避免用例跨日/跨年飘移 */
  function freezeNow(iso: string) {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(iso))
  }

  it('今日时间戳 → HH:mm（仅留时分，降噪）', () => {
    freezeNow('2026-09-17T14:05:00')
    expect(shortTime('2026-09-17 08:30:00')).toBe('08:30')
  })

  it('同年非今日 → MM-DD', () => {
    freezeNow('2026-09-17T14:05:00')
    expect(shortTime('2026-08-16 09:00:00')).toBe('08-16')
  })

  it('跨年仍只留 MM-DD（列表位宽优先，完整时间戳留给详情页）', () => {
    freezeNow('2026-01-02T10:00:00')
    expect(shortTime('2025-12-31 23:59:59')).toBe('12-31')
  })

  it('时/分与月/日均补零，不出现 8:5 或 9-7 这种单位数', () => {
    freezeNow('2026-09-07T09:05:00')
    // 今日：只留时分，且补零
    expect(shortTime('2026-09-07 08:05:00')).toBe('08:05')
    // 非今日：只留月日，且补零
    expect(shortTime('2026-01-07 00:00:00')).toBe('01-07')
  })

  it.each([
    ['undefined', undefined],
    ['null', null],
    ['空字符串', ''],
  ])('%s → 空串（不渲染 undefined 字样）', (_label, input) => {
    expect(shortTime(input as string | null | undefined)).toBe('')
  })

  it('今日但只有日期（无时分）→ 原样返回，不返回空串', () => {
    // 原 home 内联实现的潜在缺口：s.slice(11, 16) 对长度 10 的入参返回 ''
    freezeNow('2026-09-17T14:05:00')
    expect(shortTime('2026-09-17')).toBe('2026-09-17')
  })

  it('长度不足 10 的异常入参原样返回（不抛不截出乱码）', () => {
    freezeNow('2026-09-17T14:05:00')
    expect(shortTime('12:30')).toBe('12:30')
  })
})
