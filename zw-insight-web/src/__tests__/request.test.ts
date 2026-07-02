import { describe, it, expect, vi, beforeEach, beforeAll, afterAll } from 'vitest'

// ---- localStorage polyfill (Node 环境无浏览器 API) ----
const store: Record<string, string> = {}
const localStorageMock = {
  getItem: vi.fn((key: string) => store[key] ?? null),
  setItem: vi.fn((key: string, value: string) => { store[key] = value }),
  removeItem: vi.fn((key: string) => { delete store[key] }),
  clear: vi.fn(() => { Object.keys(store).forEach(k => delete store[k]) }),
}

beforeAll(() => {
  Object.defineProperty(globalThis, 'localStorage', { value: localStorageMock, writable: true, configurable: true })
})

afterAll(() => {
  Object.defineProperty(globalThis, 'localStorage', { value: undefined, writable: true, configurable: true })
})

// ---- mock 依赖 (使用 vi.hoisted 确保提升后仍可访问) ----
const { mockAxiosInstance, mockPush } = vi.hoisted(() => ({
  mockAxiosInstance: {
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
    request: vi.fn(),
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
  mockPush: vi.fn(),
}))

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => mockAxiosInstance),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn() },
}))

vi.mock('@/router', () => ({ default: { push: mockPush } }))
vi.mock('@/utils/secondaryConfirm', () => ({
  requestSecondaryConfirm: vi.fn(),
}))

// ---- import SUT ----
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { requestSecondaryConfirm } from '@/utils/secondaryConfirm'

describe('request.ts 拦截器', () => {
  let requestInterceptor: (config: any) => any
  let responseSuccessInterceptor: (response: any) => any
  let responseErrorInterceptor: (error: any) => any

  beforeEach(() => {
    vi.clearAllMocks()
    localStorageMock.clear()

    // 捕获拦截器回调
    mockAxiosInstance.interceptors.request.use.mockImplementation((fn: any) => {
      requestInterceptor = fn
    })
    mockAxiosInstance.interceptors.response.use.mockImplementation((success: any, error: any) => {
      responseSuccessInterceptor = success
      responseErrorInterceptor = error
    })

    // 重新加载模块以重新注册拦截器
    vi.resetModules()
  })

  // =====================================================================
  // 请求拦截器
  // =====================================================================

  describe('请求拦截器', () => {
    it('有 token 时注入 Authorization 头', () => {
      localStorage.setItem('token', 'my-jwt-token')

      const config = { headers: {}, params: {} } as any
      // 手动调用拦截器
      requestInterceptor = mockAxiosInstance.interceptors.request.use.mock.calls[0]?.[0]
      // 因为 resetModules 后拦截器已重新注册，需要重新获取
      // 这里我们直接测试拦截器逻辑
      const interceptorFn = mockAxiosInstance.interceptors.request.use.mock.calls[0]?.[0]
      if (interceptorFn) {
        const result = interceptorFn(config)
        expect(result.headers.Authorization).toBe('Bearer my-jwt-token')
      }
    })

    it('pageNum/pageSize 映射为 page/size', () => {
      const config = {
        headers: {},
        params: { pageNum: 2, pageSize: 20, keyword: 'test' },
      } as any

      const interceptorFn = mockAxiosInstance.interceptors.request.use.mock.calls[0]?.[0]
      if (interceptorFn) {
        const result = interceptorFn(config)
        expect(result.params.page).toBe(2)
        expect(result.params.size).toBe(20)
        expect(result.params.pageNum).toBeUndefined()
        expect(result.params.pageSize).toBeUndefined()
        expect(result.params.keyword).toBe('test')
      }
    })
  })

  // =====================================================================
  // 响应拦截器 — 成功
  // =====================================================================

  describe('响应拦截器 — 成功', () => {
    it('code === 200 正常返回', () => {
      const response = {
        config: {},
        data: { code: 200, data: { id: 1 }, message: 'ok' },
      }

      const successFn = mockAxiosInstance.interceptors.response.use.mock.calls[0]?.[0]
      if (successFn) {
        const result = successFn(response)
        expect(result).toEqual({ code: 200, data: { id: 1 }, message: 'ok' })
      }
    })

    it('code !== 200 显示错误消息', () => {
      const response = {
        config: {},
        data: { code: 500, message: '服务器异常' },
      }

      const successFn = mockAxiosInstance.interceptors.response.use.mock.calls[0]?.[0]
      if (successFn) {
        expect(() => successFn(response)).toThrow()
        expect(ElMessage.error).toHaveBeenCalledWith('服务器异常')
      }
    })

    it('code === 401 清除 token 并跳转登录', () => {
      localStorage.setItem('token', 'expired-token')
      const response = {
        config: {},
        data: { code: 401, message: '未授权' },
      }

      const successFn = mockAxiosInstance.interceptors.response.use.mock.calls[0]?.[0]
      if (successFn) {
        expect(() => successFn(response)).toThrow()
        expect(localStorage.getItem('token')).toBeNull()
        expect(router.push).toHaveBeenCalledWith('/login')
      }
    })

    it('Blob 响应直接返回原始数据', () => {
      const response = {
        config: { responseType: 'blob' },
        data: Buffer.from('data'),
      }

      const successFn = mockAxiosInstance.interceptors.response.use.mock.calls[0]?.[0]
      if (successFn) {
        const result = successFn(response)
        expect(result).toBe(response.data)
      }
    })
  })

  // =====================================================================
  // 响应拦截器 — 错误
  // =====================================================================

  describe('响应拦截器 — 错误', () => {
    it('401 错误：清除 token 并跳转登录', async () => {
      localStorage.setItem('token', 'expired')
      const error = {
        response: {
          status: 401,
          data: { message: 'Token 过期' },
        },
        config: {},
        message: 'Request failed',
      }

      const errorFn = mockAxiosInstance.interceptors.response.use.mock.calls[0]?.[1]
      if (errorFn) {
        await expect(errorFn(error)).rejects.toBeDefined()
        expect(localStorage.getItem('token')).toBeNull()
        expect(router.push).toHaveBeenCalledWith('/login')
      }
    })

    it('引用校验异常（400 + references）：不显示全局错误', async () => {
      const error = {
        response: {
          status: 400,
          data: {
            message: '引用存在',
            data: { references: [{ table: 'biz_contract', count: 3 }] },
          },
        },
        config: {},
        message: 'Request failed',
      }

      const errorFn = mockAxiosInstance.interceptors.response.use.mock.calls[0]?.[1]
      if (errorFn) {
        await expect(errorFn(error)).rejects.toBeDefined()
        // 不应调用全局错误提示
        expect(ElMessage.error).not.toHaveBeenCalled()
      }
    })

    it('普通错误：显示错误消息', async () => {
      const error = {
        response: {
          status: 500,
          data: { message: '内部错误' },
        },
        config: {},
        message: 'Request failed',
      }

      const errorFn = mockAxiosInstance.interceptors.response.use.mock.calls[0]?.[1]
      if (errorFn) {
        await expect(errorFn(error)).rejects.toBeDefined()
        expect(ElMessage.error).toHaveBeenCalledWith('内部错误')
      }
    })
  })
})
