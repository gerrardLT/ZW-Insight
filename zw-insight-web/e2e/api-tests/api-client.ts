/* eslint-disable @typescript-eslint/no-explicit-any */
import { E2E_API_BASE } from './env'
import { solveCaptcha } from './captcha-solver'

/**
 * API 测试客户端
 * 封装认证、HTTP 请求、分页查询等通用方法
 */

export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
}

export interface PageResult<T = any> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface LoginResponse {
  token: string
  userId: number
  username: string
  realName: string
  tenantId: number
  tenantName: string
  roles: string[]
  permissions: string[]
}

export class ApiClient {
  private token = ''
  private tenantId: number | null = null
  private baseUrl: string

  constructor(baseUrl?: string) {
    this.baseUrl = baseUrl || E2E_API_BASE
  }

  /** 获取当前 token */
  getToken() {
    return this.token
  }

  /** 获取当前 tenantId */
  getTenantId() {
    return this.tenantId
  }

  /** 设置 token（手动） */
  setToken(token: string) {
    this.token = token
  }

  // ============ 认证相关 ============

  /**
   * 登录：获取验证码 → 获取验证码明文 → 登录
   */
  async login(username = 'admin', password = '123456'): Promise<LoginResponse> {
    // 先尝试无验证码登录（验证码关闭时直接成功）
    const directResp = await this.post<LoginResponse>('/api/v1/auth/login', {
      loginType: 'PASSWORD',
      username,
      password,
    })

    if (directResp.code === 200 && directResp.data?.token) {
      this.token = directResp.data.token
      this.tenantId = directResp.data.tenantId
      return directResp.data
    }

    // 需要验证码，重试最多 5 次
    for (let attempt = 1; attempt <= 5; attempt++) {
      // 1. 获取验证码
      const captchaResp = await this.get<{ uuid: string; imageBase64: string }>(
        '/api/v1/captcha/image'
      )
      const uuid = captchaResp.data?.uuid
      if (!uuid) {
        throw new Error(`验证码接口异常: ${JSON.stringify(captchaResp)}`)
      }

      // 2. OCR 解码验证码
      let captchaCode = ''
      try {
        captchaCode = await solveCaptcha(captchaResp.data.imageBase64)
      } catch {
        continue
      }
      if (!captchaCode || captchaCode.length < 3) continue

      // 3. 登录
      const loginResp = await this.post<LoginResponse>('/api/v1/auth/login', {
        loginType: 'PASSWORD',
        username,
        password,
        captchaUuid: uuid,
        captchaCode,
      })

      if (loginResp.code === 200 && loginResp.data?.token) {
        this.token = loginResp.data.token
        this.tenantId = loginResp.data.tenantId
        return loginResp.data
      }
    }

    throw new Error('登录失败，请检查 auth.captcha-enabled 是否已关闭')
  }

  /** 登出 */
  async logout() {
    if (this.token) {
      await this.post('/api/v1/auth/logout', {})
      this.token = ''
    }
  }

  // ============ HTTP 方法 ============

  async get<T = any>(
    path: string,
    params?: Record<string, any>
  ): Promise<ApiResponse<T>> {
    const url = new URL(path, this.baseUrl)
    if (params) {
      Object.entries(params).forEach(([k, v]) => {
        if (v !== undefined && v !== null) {
          url.searchParams.append(k, String(v))
        }
      })
    }
    const resp = await fetch(url.toString(), {
      method: 'GET',
      headers: this.headers(),
    })
    return resp.json()
  }

  async post<T = any>(
    path: string,
    body?: any
  ): Promise<ApiResponse<T>> {
    const resp = await fetch(`${this.baseUrl}${path}`, {
      method: 'POST',
      headers: this.headers(),
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
    return resp.json()
  }

  async put<T = any>(
    path: string,
    body?: any
  ): Promise<ApiResponse<T>> {
    const resp = await fetch(`${this.baseUrl}${path}`, {
      method: 'PUT',
      headers: this.headers(),
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
    return resp.json()
  }

  async delete<T = any>(path: string): Promise<ApiResponse<T>> {
    const resp = await fetch(`${this.baseUrl}${path}`, {
      method: 'DELETE',
      headers: this.headers(),
    })
    return resp.json()
  }

  /**
   * 分页查询
   */
  async page<T = any>(
    path: string,
    params?: Record<string, any>,
    page = 1,
    size = 10
  ): Promise<ApiResponse<PageResult<T>>> {
    return this.get<PageResult<T>>(path, { current: page, size, ...params })
  }

  // ============ 工具方法 ============

  private headers(): Record<string, string> {
    const h: Record<string, string> = {
      'Content-Type': 'application/json',
    }
    if (this.token) {
      h['Authorization'] = `Bearer ${this.token}`
    }
    if (this.tenantId) {
      h['X-Tenant-Id'] = String(this.tenantId)
    }
    return h
  }
}

/** 全局单例 */
let _client: ApiClient | null = null

export function getClient(): ApiClient {
  if (!_client) {
    _client = new ApiClient()
  }
  return _client
}

/** 确保已登录，返回已认证的 client */
export async function getAuthedClient(): Promise<ApiClient> {
  const client = getClient()
  if (!client.getToken()) {
    await client.login()
  }
  return client
}
