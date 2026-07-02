import { test, expect } from '@playwright/test'
import { mockAllApisFallback, waitForPageReady } from '../fixtures/mock-api'

test.describe('Dashboard 首页', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApisFallback(page)
  })

  test('首页 /dashboard 正常加载', async ({ page }) => {
    await page.goto('/dashboard')
    await waitForPageReady(page)
    await expect(page).toHaveURL(/.*dashboard/)
    // 页面不应显示错误
    await expect(page.locator('.el-card').first()).toBeVisible()
  })

  test('项目看板 /project-dashboard 正常加载', async ({ page }) => {
    await page.goto('/project-dashboard')
    await waitForPageReady(page)
    await expect(page).toHaveURL(/.*project-dashboard/)
  })
})
