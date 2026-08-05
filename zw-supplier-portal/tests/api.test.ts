/**
 * supplier-portal API 层单元测试
 *
 * 测试真实拦截器逻辑（src/api/index.ts）：
 * 1. 请求拦截器：localStorage 有 supplier_token 时注入 Authorization: Bearer
 * 2. 响应拦截器：401 时清除 token 并跳转 /login；其余错误透传 reject
 * 3. 响应拦截器：成功响应解包 res.data
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import axios from 'axios'

// 拿到 axios.create 返回实例上注册的拦截器，直接调用其 handler 验证行为
// （不发真实网络请求，但被测代码是 src/api/index.ts 的真实拦截器实现）
let requestFulfilled: (config: any) => any
let responseFulfilled: (res: any) => any
let responseRejected: (err: any) => Promise<never>

vi.mock('axios', async importOriginal => {
  const actual: any = await importOriginal()
  const instance = actual.default.create()
  const origCreate = actual.default.create
  return {
    ...actual,
    default: {
      ...actual.default,
      create: (config?: any) => {
        const inst = origCreate.call(actual.default, config)
        const reqUse = inst.interceptors.request.use.bind(inst.interceptors.request)
        const resUse = inst.interceptors.response.use.bind(inst.interceptors.response)
        inst.interceptors.request.use = (onFulfilled: any, onRejected: any) => {
          requestFulfilled = onFulfilled
          return reqUse(onFulfilled, onRejected)
        }
        inst.interceptors.response.use = (onFulfilled: any, onRejected: any) => {
          responseFulfilled = onFulfilled
          responseRejected = onRejected
          return resUse(onFulfilled, onRejected)
        }
        return inst
      }
    }
  }
})

// 导入被测模块（触发 axios.create 与拦截器注册）
await import('../src/api/index')

describe('supplier-portal API 拦截器', () => {
  beforeEach(() => {
    localStorage.clear()
    // happy-dom 下重置 location（跳转断言用）
    window.location.href = 'http://localhost/inquiry'
  })

  describe('请求拦截器', () => {
    it('有 supplier_token 时注入 Bearer Authorization', () => {
      localStorage.setItem('supplier_token', 'tk-123')
      const config: any = { headers: {} }

      const out = requestFulfilled(config)

      expect(out.headers.Authorization).toBe('Bearer tk-123')
    })

    it('无 token 时不注入 Authorization', () => {
      const config: any = { headers: {} }

      const out = requestFulfilled(config)

      expect(out.headers.Authorization).toBeUndefined()
    })
  })

  describe('响应拦截器', () => {
    it('成功响应解包 res.data', () => {
      const res: any = { data: { code: 200, data: [1, 2] }, status: 200 }

      expect(responseFulfilled(res)).toEqual({ code: 200, data: [1, 2] })
    })

    it('401 时清除 token 并跳转 /login，且仍 reject', async () => {
      localStorage.setItem('supplier_token', 'tk-old')
      const err: any = { response: { status: 401 } }

      await expect(responseRejected(err)).rejects.toBe(err)
      expect(localStorage.getItem('supplier_token')).toBeNull()
      expect(window.location.href).toContain('/login')
    })

    it('非 401 错误透传 reject 且不清 token', async () => {
      localStorage.setItem('supplier_token', 'tk-keep')
      const err: any = { response: { status: 500 } }

      await expect(responseRejected(err)).rejects.toBe(err)
      expect(localStorage.getItem('supplier_token')).toBe('tk-keep')
    })
  })
})
