/**
 * secondaryConfirm 协调器单元测试（2026-08-14 前端深度补测）
 *
 * 覆盖：注册/注销生命周期、requestSecondaryConfirm 透传消息与密码、
 * 无 opener 时返回 null（终止重试不静默吞错）、
 * unregister 仅注销当前注册的 opener（防误注销）。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  registerConfirmOpener,
  unregisterConfirmOpener,
  requestSecondaryConfirm,
  type ConfirmOpener
} from '@/utils/secondaryConfirm'

describe('utils/secondaryConfirm', () => {
  beforeEach(() => {
    // 模块级 opener 状态复位：注册后注销
    const noop: ConfirmOpener = async () => null
    registerConfirmOpener(noop)
    unregisterConfirmOpener(noop)
  })

  it('未注册 opener：返回 null（终止重试，不静默吞错）', async () => {
    await expect(requestSecondaryConfirm('请确认')).resolves.toBeNull()
  })

  it('注册后：透传消息给 opener 并返回用户输入的密码', async () => {
    const opener = vi.fn(async (_msg: string) => 'pwd-123')
    registerConfirmOpener(opener)

    const result = await requestSecondaryConfirm('删除操作需要确认')

    expect(opener).toHaveBeenCalledWith('删除操作需要确认')
    expect(result).toBe('pwd-123')

    unregisterConfirmOpener(opener)
  })

  it('用户取消：opener 返回 null 原样透传', async () => {
    const opener = vi.fn(async () => null)
    registerConfirmOpener(opener)

    await expect(requestSecondaryConfirm('确认')).resolves.toBeNull()

    unregisterConfirmOpener(opener)
  })

  it('unregister 传入其他函数：不误注销当前 opener', async () => {
    const opener = vi.fn(async () => 'pwd')
    const other: ConfirmOpener = async () => 'other'
    registerConfirmOpener(opener)

    unregisterConfirmOpener(other)
    await expect(requestSecondaryConfirm('确认')).resolves.toBe('pwd')

    unregisterConfirmOpener(opener)
  })

  it('注销后：回到返回 null 的兜底行为', async () => {
    const opener: ConfirmOpener = async () => 'pwd'
    registerConfirmOpener(opener)
    unregisterConfirmOpener(opener)

    await expect(requestSecondaryConfirm('确认')).resolves.toBeNull()
  })
})
