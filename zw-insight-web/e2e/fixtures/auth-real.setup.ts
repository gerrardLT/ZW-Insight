/**
 * 真实模式登录 Setup
 *
 * 通过真实登录流程获取 session 并保存 storageState，供后续 e2e-real 测试复用。
 * 流程：导航登录页 → 填写用户名密码 → 获取验证码 → 提交 → 保存 storageState
 *
 * 环境变量：
 * - E2E_API_BASE: 后端 API 地址（默认 http://129.204.3.200:18080）
 */
import { test as setup, expect } from '@playwright/test'

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'

setup('authenticate against real server', async ({ page }) => {
  // 1. 导航到登录页
  await page.goto('/login')
  await page.waitForSelector('.login-card', { timeout: 15_000 })

  // 2. 填写用户名和密码
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', '123456')

  // 3. 获取验证码 UUID：调用后端 captcha/image 接口
  const captchaResp = await page.request.get(`${API_BASE}/api/v1/captcha/image`)
  const captchaJson = await captchaResp.json()
  const uuid: string = captchaJson.data?.uuid

  if (!uuid) {
    throw new Error(
      `[auth-real.setup] 验证码接口返回异常，未获取到 uuid。响应: ${JSON.stringify(captchaJson)}`
    )
  }

  // 4. 获取验证码答案：调用测试专用接口
  let captchaCode = ''
  const codeResp = await page.request.get(
    `${API_BASE}/api/v1/test/captcha-code/${uuid}`
  )

  if (codeResp.ok()) {
    const codeJson = await codeResp.json()
    captchaCode = String(codeJson.data || '')
    console.log('[auth-real.setup] 验证码获取成功')
  } else {
    // 降级：测试接口不可用时使用硬编码验证码（常见测试环境配置）
    console.warn(
      `[auth-real.setup] ⚠️ 降级警告：/api/v1/test/captcha-code 接口不可用 ` +
      `(HTTP ${codeResp.status()})，尝试使用硬编码验证码 "1234"`
    )
    captchaCode = '1234'
  }

  if (!captchaCode) {
    throw new Error('[auth-real.setup] 无法获取验证码，登录流程中止')
  }

  // 5. 填写验证码并提交
  await page.fill('input[placeholder="验证码"]', captchaCode)
  await page.click('button:has-text("登 录")')

  // 6. 等待登录成功跳转（离开 /login 页面）
  await page.waitForURL((url) => !url.pathname.includes('/login'), {
    timeout: 15_000,
  })

  // 验证已离开登录页
  expect(page.url()).not.toContain('/login')
  console.log(`[auth-real.setup] 登录成功，当前页面: ${page.url()}`)

  // 7. 保存 storageState
  await page.context().storageState({ path: './e2e/.auth/storage-state.json' })
})
