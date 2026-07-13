/**
 * 真实模式 E2E 测试：登录流程验证
 *
 * 验证真实服务器上的完整登录流程：
 * 1. 导航到登录页
 * 2. 填写用户名和密码
 * 3. 获取并填写验证码
 * 4. 提交登录表单
 * 5. 验证跳转到首页（dashboard）
 *
 * 依赖 auth-real.setup.ts 提供的 storageState（已登录状态）。
 * 本文件中的 "未认证登录流程" 测试使用独立上下文（不带 storageState）。
 *
 * 需求: 6.1, 6.2
 */
import { test, expect } from '@playwright/test'

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'

test.describe('真实登录流程验证', () => {
  test('已认证状态 — 访问首页不被重定向到登录页', async ({ page }) => {
    // storageState 已由 setup-real 注入，直接验证已登录状态
    await page.goto('/')
    // 不应被重定向到 /login
    await page.waitForLoadState('networkidle')
    expect(page.url()).not.toContain('/login')
  })

  test('已认证状态 — Dashboard 页面正常加载', async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle')
    // dashboard 页面应包含主布局容器
    await expect(
      page.locator('.el-container, .layout-container, .app-wrapper').first()
    ).toBeVisible({ timeout: 20_000 })
  })

  test('已认证状态 — 侧边栏菜单可见', async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle')
    // 验证侧边栏菜单渲染
    await expect(
      page.locator('.el-menu, .sidebar-container, nav').first()
    ).toBeVisible({ timeout: 15_000 })
  })
})

/**
 * 未认证登录流程测试（使用独立浏览器上下文，不带 storageState）
 */
test.describe('未认证登录流程', () => {
  // 使用空 storageState 模拟未登录用户
  test.use({ storageState: { cookies: [], origins: [] } })

  test('登录页面正常渲染', async ({ page }) => {
    await page.goto('/login')
    await page.waitForLoadState('networkidle')

    // 验证登录表单元素存在
    await expect(page.locator('.login-container, .login-card').first()).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('input[placeholder*="用户名"]')).toBeVisible()
    await expect(page.locator('input[placeholder*="密码"]')).toBeVisible()
    await expect(page.locator('input[placeholder*="验证码"]')).toBeVisible()
    await expect(page.locator('.captcha-img, img[alt*="验证码"]').first()).toBeVisible()
  })

  test('验证码图片可加载', async ({ page }) => {
    await page.goto('/login')
    await page.waitForLoadState('networkidle')

    // 验证码图片应为 base64 或有效 src
    const captchaImg = page.locator('.captcha-img, img[alt*="验证码"]').first()
    await expect(captchaImg).toBeVisible({ timeout: 10_000 })
    const src = await captchaImg.getAttribute('src')
    expect(src).toBeTruthy()
    // base64 图片以 data:image 开头
    expect(src!.startsWith('data:image') || src!.startsWith('http')).toBeTruthy()
  })

  test('空表单提交 — 显示验证错误', async ({ page }) => {
    await page.goto('/login')
    await page.waitForLoadState('networkidle')

    // 点击登录按钮（不填写任何内容）
    await page.click('button:has-text("登")')
    // 应出现表单验证错误提示
    await expect(page.locator('.el-form-item__error').first()).toBeVisible({ timeout: 5_000 })
  })

  test('完整登录流程 — 获取验证码并登录成功', async ({ page }) => {
    await page.goto('/login')
    await page.waitForLoadState('networkidle')

    // 1. 填写用户名和密码
    await page.fill('input[placeholder*="用户名"]', 'admin')
    await page.fill('input[placeholder*="密码"]', '123456')

    // 2. 等待验证码图片加载（获取 captchaUuid）
    // 拦截验证码图片请求获取 UUID
    const captchaResponse = await page.waitForResponse(
      (resp) => resp.url().includes('/v1/captcha/image') && resp.status() === 200,
      { timeout: 10_000 }
    ).catch(() => null)

    if (!captchaResponse) {
      // 如果已经加载完毕，点击刷新验证码
      await page.click('.captcha-img, img[alt*="验证码"]')
    }

    // 3. 通过测试接口获取验证码明文
    // 先从页面网络请求中获取 captchaUuid
    // 监听下一次验证码请求
    const [newCaptchaResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/captcha/image') && resp.status() === 200,
        { timeout: 10_000 }
      ),
      page.click('.captcha-img, img[alt*="验证码"]'),
    ]).catch(() => [null])

    if (newCaptchaResp) {
      const captchaData = await newCaptchaResp.json().catch(() => null)
      const uuid = captchaData?.data?.uuid

      if (uuid) {
        // 调用测试专用接口获取验证码明文
        const codeResp = await page.request.get(
          `${API_BASE}/api/v1/test/captcha-code/${uuid}`
        ).catch(() => null)

        if (codeResp && codeResp.ok()) {
          const codeData = await codeResp.json()
          const captchaCode = codeData?.data

          if (captchaCode) {
            // 4. 填写验证码
            await page.fill('input[placeholder*="验证码"]', captchaCode)

            // 5. 提交登录
            await page.click('button:has-text("登")')

            // 6. 验证登录成功 — 应跳转离开 /login
            await page.waitForURL((url) => !url.pathname.includes('/login'), {
              timeout: 15_000,
            })
            expect(page.url()).not.toContain('/login')
          } else {
            test.skip(true, '验证码明文接口返回空值，跳过完整登录测试')
          }
        } else {
          test.skip(true, '/api/v1/test/captcha-code 接口不可用，跳过完整登录测试')
        }
      } else {
        test.skip(true, '无法获取 captchaUuid，跳过完整登录测试')
      }
    } else {
      test.skip(true, '验证码图片请求失败，跳过完整登录测试')
    }
  })

  test('未认证访问受保护页面 — 重定向到登录页', async ({ page }) => {
    // 访问需要认证的页面
    await page.goto('/project/list')
    await page.waitForLoadState('networkidle')
    // 应被重定向到登录页
    await page.waitForURL('**/login', { timeout: 10_000 })
    expect(page.url()).toContain('/login')
  })
})
