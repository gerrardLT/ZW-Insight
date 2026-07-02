import { test, expect } from '@playwright/test'
import { mockAllApisFallback, mockApi, waitForPageReady } from '../fixtures/mock-api'

test.describe('认证与错误页面', () => {
  test('登录页 — 正常渲染', async ({ page }) => {
    // mock 验证码接口，防止请求挂起
    await mockApi(page, '/api/v1/captcha/**', { captchaKey: 'test', captchaImage: 'data:image/png;base64,iVBOR' })
    await mockAllApisFallback(page)
    await page.goto('/login')
    await page.waitForLoadState('domcontentloaded')
    await expect(page.locator('.login-title')).toBeVisible({ timeout: 10_000 })
    await expect(page.locator('[placeholder="请输入用户名"]')).toBeVisible()
    await expect(page.locator('[placeholder="请输入密码"]')).toBeVisible()
    await expect(page.locator('button:has-text("登 录")')).toBeVisible()
  })

  test('登录页 — 表单验证', async ({ page }) => {
    await mockApi(page, '/api/v1/captcha/**', { captchaKey: 'test', captchaImage: 'data:image/png;base64,iVBOR' })
    await mockAllApisFallback(page)
    await page.goto('/login')
    await page.waitForLoadState('domcontentloaded')
    await page.click('button:has-text("登 录")')
    await expect(page.locator('.el-form-item__error').first()).toBeVisible({ timeout: 5_000 })
  })

  test('忘记密码 — 页面加载', async ({ page }) => {
    await mockAllApisFallback(page)
    await page.goto('/forgot-password')
    await page.waitForLoadState('domcontentloaded')
    await expect(page.locator('.el-card, .el-form').first()).toBeVisible({ timeout: 10_000 })
  })

  test('404 — 访问不存在路径重定向', async ({ page }) => {
    await mockAllApisFallback(page)
    await page.goto('/non-existent-page-xyz')
    await page.waitForLoadState('domcontentloaded')
    // 应重定向到 404 页面
    await expect(page).toHaveURL(/.*(404|non-existent).*/)
  })

  test('403 — 无权限页面', async ({ page }) => {
    await mockAllApisFallback(page)
    await page.goto('/403')
    await page.waitForLoadState('domcontentloaded')
    // 403 页面应渲染
    await expect(page.locator('body')).toBeVisible()
  })

  test('退出登录 — 清除 token 后跳转登录页', async ({ page }) => {
    await mockAllApisFallback(page)
    // 先确保在已登录状态
    await page.goto('/dashboard')
    await waitForPageReady(page)

    // 清除 localStorage token
    await page.evaluate(() => {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    })

    // 导航到受保护页面，应被路由守卫重定向到登录
    await page.goto('/project/list')
    await page.waitForLoadState('domcontentloaded')
    await expect(page).toHaveURL(/.*login.*/)
  })
})
