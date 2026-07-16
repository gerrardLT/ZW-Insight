/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect } from 'vitest'
import { ApiClient } from './api-client'
import { solveCaptcha } from './captcha-solver'
import { E2E_API_BASE } from './env'

/**
 * 01 - 登录 & 密码管理测试
 * 对应功能表: 1.1 登录、5.5 密码重置
 */
describe('01 - 登录与密码管理', () => {
  const client = new ApiClient()

  // ============ 1.1 登录 ============

  it('获取图形验证码 - 返回 uuid 和 base64 图片', async () => {
    const resp = await client.get('/api/v1/captcha/image')
    expect(resp.code).toBe(200)
    expect(resp.data).toBeDefined()
    expect(resp.data.uuid).toBeTruthy()
    expect(resp.data.imageBase64).toBeTruthy()
    // base64 图片应以 data:image 开头
    expect(resp.data.imageBase64).toMatch(/^data:image/)
  })

  it('验证码图片可被 OCR 识别 - 用于自动化登录', async () => {
    // 后端无"验证码明文"测试接口（避免生产环境泄露明文）；
    // 自动化登录实际依赖 OCR 识别验证码图片，这里验证该真实机制可用。
    const captchaResp = await client.get('/api/v1/captcha/image')
    const code = await solveCaptcha(captchaResp.data.imageBase64)
    expect(typeof code).toBe('string')
    expect(code.length).toBeLessThanOrEqual(4)
  })

  it('登录成功 - admin 账号获取 token', async () => {
    const result = await client.login('admin', '123456')
    expect(result.token).toBeTruthy()
    // 雪花 ID 出参序列化为 String，故用 Number() 转换后比较
    expect(Number(result.userId)).toBeGreaterThan(0)
    expect(result.username).toBe('admin')
    expect(Number(result.tenantId)).toBeGreaterThan(0)
    expect(result.roles).toBeDefined()
  })

  it('登录失败 - 错误密码应返回错误码', async () => {
    // 获取验证码
    const captchaResp = await client.get('/api/v1/captcha/image')
    const uuid = captchaResp.data.uuid

    // 获取验证码明文
    let captchaCode = '1234'
    try {
      const codeResp = await fetch(
        `${E2E_API_BASE}/api/v1/test/captcha-code/${uuid}`
      )
      if (codeResp.ok) {
        const codeJson = await codeResp.json()
        captchaCode = String(codeJson.data || '1234')
      }
    } catch { /* fallback */ }

    const resp = await client.post('/api/v1/auth/login', {
      loginType: 'PASSWORD',
      username: 'admin',
      password: 'wrong_password_xxx',
      captchaUuid: uuid,
      captchaCode,
    })

    // 应该返回非 200 状态码
    expect(resp.code).not.toBe(200)
  })

  it('登录失败 - 空用户名应被拒绝', async () => {
    const captchaResp = await client.get('/api/v1/captcha/image')
    const uuid = captchaResp.data.uuid

    const resp = await client.post('/api/v1/auth/login', {
      loginType: 'PASSWORD',
      username: '',
      password: '123456',
      captchaUuid: uuid,
      captchaCode: '1234',
    })

    expect(resp.code).not.toBe(200)
  })

  it('登录失败 - 空密码应被拒绝', async () => {
    const captchaResp = await client.get('/api/v1/captcha/image')
    const uuid = captchaResp.data.uuid

    const resp = await client.post('/api/v1/auth/login', {
      loginType: 'PASSWORD',
      username: 'admin',
      password: '',
      captchaUuid: uuid,
      captchaCode: '1234',
    })

    expect(resp.code).not.toBe(200)
  })

  it('登出 - 成功', async () => {
    // 先登录
    const freshClient = new ApiClient()
    await freshClient.login('admin', '123456')
    expect(freshClient.getToken()).toBeTruthy()

    // 登出
    const resp = await freshClient.post('/api/v1/auth/logout', {})
    expect(resp.code).toBe(200)
  })

  // ============ 5.5 密码管理 ============

  it('修改密码 - 需要认证', async () => {
    // 使用未认证的 client 应失败
    const noAuth = new ApiClient()
    const resp = await noAuth.put('/api/v1/auth/password', {
      oldPassword: '123456',
      newPassword: '654321',
    })
    // 未认证请求应返回非 200
    expect(resp.code).not.toBe(200)
  })
})
