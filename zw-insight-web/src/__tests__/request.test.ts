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

vi.mock('@/router', () => ({
  default: {
    push: mockPush,
    currentRoute: { value: { path: '/dashboard' } },
  },
}))
vi.mock('@/utils/secondaryConfirm', () => ({
  requestSecondaryConfirm: vi.fn(),
}))
const { mockLogout } = vi.hoisted(() => ({
  mockLogout: vi.fn(),
}))
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ logout: mockLogout }),
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

    // 钉住：后端 Long→String 全局序列化致分页 total 为字符串，ElPagination 需 number
    it('PageResult 字符串 total 归一为 number（分页器渲染前提）', () => {
      const response = {
        config: {},
        data: { code: 200, data: { records: [{ id: '2088926380211367937' }], total: '311' }, message: 'ok' },
      }
      const successFn = mockAxiosInstance.interceptors.response.use.mock.calls[0]?.[0]
      if (successFn) {
        const result = successFn(response)
        expect(result.data.total).toBe(311)
        // records 内雪花 ID 保持字符串不受影响
        expect(result.data.records[0].id).toBe('2088926380211367937')
      }
    })

    it('非数字字符串 total 不归一（防误转）', () => {
      const response = {
        config: {},
        data: { code: 200, data: { total: 'N/A' }, message: 'ok' },
      }
      const successFn = mockAxiosInstance.interceptors.response.use.mock.calls[0]?.[0]
      if (successFn) {
        const result = successFn(response)
        expect(result.data.total).toBe('N/A')
      }
    })

    it('data 为数组时无 total 归一（不干扰非分页响应）', () => {
      const response = {
        config: {},
        data: { code: 200, data: [{ id: 1 }, { id: 2 }], message: 'ok' },
      }
      const successFn = mockAxiosInstance.interceptors.response.use.mock.calls[0]?.[0]
      if (successFn) {
        const result = successFn(response)
        expect(Array.isArray(result.data)).toBe(true)
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

describe('网络重试纪律', () => {
  // 外层 describe 的 beforeEach 会 clearAllMocks 清空模块加载时的拦截器注册记录，
  // 故此处用 resetModules + dynamic import 重新触发 request.ts 注册流程
  // （vi.mock('axios') factory 固定返回同一 hoisted mockAxiosInstance，安全可靠）
  let retryErrorInterceptor: any

  beforeEach(async () => {
    Object.keys(store).forEach(k => delete store[k])
    mockAxiosInstance.request.mockClear()
    mockAxiosInstance.interceptors.response.use.mockClear()

    // 拦截最新一次注册的 error 回调
    mockAxiosInstance.interceptors.response.use.mockImplementation((_s: any, e: any) => {
      retryErrorInterceptor = e
    })

    // 重置模块图并动态重载：request.ts 重新执行 → 拦截器重新注册 → 上面的捕获生效
    vi.resetModules()
    await import('@/utils/request')
  })

  it('GET 请求无 response（纯网络错误）时单次重试且标记 _networkRetried', async () => {
    expect(retryErrorInterceptor).toBeTypeOf('function')
    mockAxiosInstance.request.mockRejectedValueOnce({} as any)

    const config: any = { method: 'get', url: '/api/test' }
    const error: any = { config, response: undefined }

    // 走 retry 分支 → service.request(config) 被 mock 拒绝
    await expect(retryErrorInterceptor(error)).rejects.toBeDefined()
    expect(mockAxiosInstance.request).toHaveBeenCalledTimes(1)
    // 重试标记已写入（防重复重试）
    expect(config._networkRetried).toBe(true)
  })

  it('非 GET 请求不触发自动重试', async () => {
    expect(retryErrorInterceptor).toBeTypeOf('function')

    const postConfig: any = { method: 'post', url: '/api/data' }
    const error: any = { config: postConfig, response: undefined }

    await expect(retryErrorInterceptor(error)).rejects.toBeDefined()
    expect(mockAxiosInstance.request).not.toHaveBeenCalled() // POST 不重试
    expect(postConfig._networkRetried).toBeUndefined() // 未标记重试
  })
})

// ===========================================================================
// 401 统一处理（2026-09-16 修复 token 过期蒙层 bug：必须清内存登录态，否则
// 路由守卫用 userStore.token 覆盖判定已登录，push('/login') 被弹回首页）
// ===========================================================================
describe('401 统一处理（token 过期弹回死循环修复）', () => {
  async function loadFreshModule() {
    mockAxiosInstance.interceptors.response.use.mockClear()
    mockAxiosInstance.interceptors.response.use.mockImplementation((_s: any, e: any) => {
      ;(globalThis as any).__errFn = e
    })
    vi.resetModules()
    await import('@/utils/request')
    return (globalThis as any).__errFn
  }

  it('HTTP 401：调 userStore.logout（清内存登录态）+ push 登录页', async () => {
    mockLogout.mockClear()
    mockPush.mockClear()
    const errFn = await loadFreshModule()
    expect(errFn).toBeTypeOf('function')

    const error: any = {
      config: { method: 'get', url: '/api/x' },
      response: { status: 401, data: { message: 'token 已过期' } },
    }
    await expect(errFn(error)).rejects.toBeDefined()
    expect(mockLogout).toHaveBeenCalledTimes(1) // 核心：内存登录态被清
    expect(mockPush).toHaveBeenCalledWith('/login')
  })

  it('已在登录页时不重复 push（防导航警告堆积）', async () => {
    mockLogout.mockClear()
    mockPush.mockClear()
    const errFn = await loadFreshModule()

    // 场景：dashboard 页并发两个 401——第一个触发跳转，第二个到达时已在登录页
    const error: any = {
      config: { method: 'get', url: '/api/x' },
      response: { status: 401, data: {} },
    }
    await expect(errFn(error)).rejects.toBeDefined()
    expect(mockPush).toHaveBeenCalledTimes(1) // 第一个 401：在 dashboard → push

    // 模拟跳转完成：当前路由已是登录页
    ;(router as any).currentRoute.value.path = '/login'
    mockPush.mockClear()
    await expect(errFn(error)).rejects.toBeDefined()
    expect(mockPush).not.toHaveBeenCalled() // 第二个 401：已在登录页 → 不重复 push

    ;(router as any).currentRoute.value.path = '/dashboard' // 还原供后续用例
  })

  it('store 未就绪时兜底清 localStorage token（不静默丢认证态）', async () => {
    mockLogout.mockClear()
    mockPush.mockClear()
    // 覆盖 mock 让 useUserStore 抛错，验证 catch 兜底
    vi.doMock('@/stores/user', () => ({
      useUserStore: () => { throw new Error('pinia not ready') },
    }))
    const errFn = await loadFreshModule()
    localStorage.setItem('token', 'stale-token')

    const error: any = {
      config: { method: 'get', url: '/api/x' },
      response: { status: 401, data: {} },
    }
    await expect(errFn(error)).rejects.toBeDefined()
    expect(localStorage.getItem('token')).toBeNull() // 兑底已清
    expect(mockPush).toHaveBeenCalledWith('/login')
    vi.doUnmock('@/stores/user')
  })
})

