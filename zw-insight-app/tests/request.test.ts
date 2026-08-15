/**
 * utils/request.ts 请求封装单元测试（2026-08-15 P3 方向3 补测）
 *
 * uni.request 由测试覆盖为记录器（setup.ts 约定：需要它的测试自行覆盖），
 * 被测代码为 src/utils/request.ts 真实实现：
 * - token 注入 Authorization: Bearer
 * - code=200 解包 resolve
 * - code=401 清 token + reLaunch 登录页 + reject
 * - 业务错误码 toast + reject
 * - statusCode 非 200 / fail 回调 reject
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { resetUniStorage, getUni } from './setup'
import request from '@/utils/request'

interface CapturedRequest {
  url: string
  method: string
  data: any
  header: Record<string, string>
  success: (res: any) => void
  fail: (err: any) => void
}

let captured: CapturedRequest | null = null

beforeEach(() => {
  resetUniStorage()
  captured = null
  ;(getUni() as any).request = (options: any) => { captured = options }
  ;(getUni() as any).reLaunch = vi.fn()
})

function respondOk(data: any, statusCode = 200) {
  captured!.success({ statusCode, data })
}

describe('request 请求封装', () => {
  it('有 token 时注入 Bearer，URL 拼 /api 前缀，默认 GET', async () => {
    getUni().setStorageSync('token', 'tk-1')
    const p = request({ url: '/v1/project/page' })
    expect(captured!.url).toBe('/api/v1/project/page')
    expect(captured!.method).toBe('GET')
    expect(captured!.header['Authorization']).toBe('Bearer tk-1')
    expect(captured!.header['Content-Type']).toBe('application/json')
    respondOk({ code: 200, data: [] })
    await expect(p).resolves.toEqual({ code: 200, data: [] })
  })

  it('无 token 时不注入 Authorization', async () => {
    const p = request({ url: '/v1/auth/login', method: 'POST', data: { a: 1 } })
    expect(captured!.header['Authorization']).toBeUndefined()
    expect(captured!.data).toEqual({ a: 1 })
    respondOk({ code: 200 })
    await p
  })

  it('code=401：清除 token + reLaunch 登录页 + reject 登录已过期', async () => {
    getUni().setStorageSync('token', 'tk-expired')
    const p = request({ url: '/v1/anything' })
    respondOk({ code: 401, message: 'expired' })
    await expect(p).rejects.toThrow('登录已过期')
    expect(getUni().getStorageSync('token')).toBe('')
    expect(((getUni() as any).reLaunch as any)).toHaveBeenCalledWith({ url: '/pages/login/index' })
  })

  it('业务错误码：toast 提示 message 并 reject', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const p = request({ url: '/v1/x' })
    respondOk({ code: 500, message: '业务异常' })
    await expect(p).rejects.toThrow('业务异常')
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '业务异常' }))
  })

  it('statusCode 非 200：toast 网络错误并 reject', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const p = request({ url: '/v1/x' })
    respondOk({}, 502)
    await expect(p).rejects.toThrow('网络错误')
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '网络错误' }))
  })

  it('fail 回调：toast 网络异常并 reject 原始错误', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const p = request({ url: '/v1/x' })
    captured!.fail(new Error('timeout'))
    await expect(p).rejects.toThrow('timeout')
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '网络异常' }))
  })
})
