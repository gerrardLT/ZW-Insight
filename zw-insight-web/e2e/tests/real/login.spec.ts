/**
 * 真实模式 E2E 测试：登录流程验证
 *
 * 验证真实服务器上的登录页与登录前置约束：
 * 1. 登录页渲染（密码登录 / 短信登录双 Tab）
 * 2. TAC 滑块安全验证资源与挑战接口可用
 * 3. 未完成安全验证时拒绝登录（滑块为强制前置）
 * 4. 未认证访问受保护页重定向到 /login
 *
 * ⚠️ 2026-10-12 校正：登录页已从「图形验证码」改为 **天安 TAC 滑块 + 短信登录双 Tab**
 * （`views/login/SliderCaptcha.vue`），旧的 `.captcha-img` / `img[alt*="验证码"]` /
 * `/v1/captcha/image` 断言已失效，本文件按新实现重写。
 *
 * 正向「完整登录」不在此文件重复实现——`e2e/fixtures/auth-real.setup.ts` 已通过
 * 真实 TAC 滑块（挑战目标经 SSH 从 Redis 读取）完成一次完整 UI 登录并落 storageState。
 *
 * 依赖 auth-real.setup.ts 提供的 storageState（已登录状态）。
 * 本文件中的 "未认证登录流程" 测试使用独立上下文（不带 storageState）。
 *
 * 需求: 6.1, 6.2
 */
import { test, expect } from '@playwright/test'

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
    await expect(page.locator('.layout-container').first()).toBeVisible({ timeout: 20_000 })
  })

  test('已认证状态 — 侧边栏菜单可见', async ({ page }) => {
    await page.goto('/')
    await page.waitForLoadState('networkidle')
    // 验证侧边栏菜单渲染
    // 侧边导航：业务域轨道（DefaultLayout 的 nav.domain-rail）
    await expect(page.locator('nav.domain-rail').first()).toBeVisible({ timeout: 15_000 })
  })
})

/**
 * 未认证登录流程测试（使用独立浏览器上下文，不带 storageState）
 */
test.describe('未认证登录流程', () => {
  // 使用空 storageState 模拟未登录用户
  test.use({ storageState: { cookies: [], origins: [] } })

  test('登录页面正常渲染（双 Tab + TAC 安全验证）', async ({ page }) => {
    await page.goto('/login')
    await page.waitForLoadState('networkidle')

    // 容器与表单元素（类名与 src/views/login/index.vue 保持一致）
    await expect(page.locator('.login-box').first()).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('input[placeholder*="用户名"]')).toBeVisible()
    await expect(page.locator('input[placeholder*="密码"]')).toBeVisible()

    // 登录方式双 Tab：密码登录 / 短信登录
    await expect(page.locator('.login-tabs')).toBeVisible()
    await expect(page.locator('.login-tab')).toHaveCount(2)

    // 安全验证已由「图形验证码」改为 TAC 滑块（views/login/SliderCaptcha.vue）
    await expect(page.locator('.captcha-trigger')).toBeVisible()
    await expect(page.locator('.captcha-trigger')).toHaveText('点击完成安全验证')
  })

  test('TAC 安全验证资源与挑战接口可用', async ({ page, request }) => {
    await page.goto('/login')
    await page.waitForLoadState('networkidle')

    // TAC SDK 静态资源（SliderCaptcha 打开时动态注入 /tac/load.min.js）
    const sdk = await request.get('/tac/load.min.js')
    expect(sdk.ok()).toBeTruthy()

    // 滑块挑战生成接口真实可用，返回 SLIDER 挑战
    const challenge = await request.get('/api/v1/captcha/slider')
    expect(challenge.ok()).toBeTruthy()
    const body = await challenge.json()
    expect(body?.code).toBe(200)
    expect(body?.data?.id).toBeTruthy()
    expect(body?.data?.type).toBe('SLIDER')

    // 触发控件就绪且可点击（不实际完成校验，避免消费挑战）
    await expect(page.locator('.captcha-trigger')).toBeEnabled()
  })

  test('空表单提交 — 显示验证错误', async ({ page }) => {
    await page.goto('/login')
    await page.waitForLoadState('networkidle')

    // 密码登录表单的「进入系统」按钮（页面两个 Tab 各有一个同名按钮，取首个可见的）
    await page.locator('.login-btn').first().click()
    // 应出现表单验证错误提示
    await expect(page.locator('.el-form-item__error').first()).toBeVisible({ timeout: 5_000 })
  })

  test('未完成安全验证时拒绝登录（滑块为强制前置）', async ({ page }) => {
    await page.goto('/login')
    await page.waitForLoadState('networkidle')

    // 记录是否发出过登录请求（用于断言「前端已拦截」而非「后端拒绝」）
    let loginRequested = false
    page.on('request', (req) => {
      if (req.url().includes('/v1/auth/login')) loginRequested = true
    })

    // 填写正确的账号密码，但不完成 TAC 滑块验证
    await page.fill('input[placeholder*="用户名"]', 'admin')
    await page.fill('input[placeholder*="密码"]', '123456')
    await page.locator('.login-btn').first().click()

    // 应提示「请先拖动滑块完成验证」，停留在登录页，且**不发登录请求**
    await expect(page.locator('.el-message')).toContainText('请先拖动滑块完成验证', { timeout: 5_000 })
    expect(page.url()).toContain('/login')
    expect(loginRequested).toBe(false)
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
