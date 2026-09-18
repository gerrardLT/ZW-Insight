/**
 * haptic 触觉反馈工具测试（Batch 2-B2，2026-09-17）
 * 钉住：调用契约 + 全局开关 + 无硬件/异常时静默降级（不阻断业务主流程）。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { hapticTap, setHapticEnabled, isHapticEnabled } from '@/utils/haptic'

describe('haptic 触觉反馈工具', () => {
  const originalUni = (globalThis as any).uni

  afterEach(() => {
    ;(globalThis as any).uni = originalUni
    setHapticEnabled(true)
    vi.restoreAllMocks()
  })

  it('uni.vibrateShort 存在时以默认 light 档调用一次', () => {
    const vibrateShort = vi.fn()
    ;(globalThis as any).uni = { vibrateShort }
    hapticTap()
    expect(vibrateShort).toHaveBeenCalledTimes(1)
    expect(vibrateShort.mock.calls[0][0].type).toBe('light')
  })

  it('可指定强度档位（heavy）', () => {
    const vibrateShort = vi.fn()
    ;(globalThis as any).uni = { vibrateShort }
    hapticTap('heavy')
    expect(vibrateShort.mock.calls[0][0].type).toBe('heavy')
  })

  it('全局开关关闭后不触发振动', () => {
    const vibrateShort = vi.fn()
    ;(globalThis as any).uni = { vibrateShort }
    setHapticEnabled(false)
    expect(isHapticEnabled()).toBe(false)
    hapticTap()
    expect(vibrateShort).not.toHaveBeenCalled()
  })

  it('vibrateShort 缺失 / 抛错 / uni 不存在时静默降级，不抛异常', () => {
    ;(globalThis as any).uni = {}
    expect(() => hapticTap()).not.toThrow()

    ;(globalThis as any).uni = {
      vibrateShort: () => {
        throw new Error('boom')
      },
    }
    expect(() => hapticTap()).not.toThrow()

    delete (globalThis as any).uni
    expect(() => hapticTap()).not.toThrow()
  })
})
