import { test, expect } from '@playwright/test'
import { mockAllApisFallback, waitForPageReady } from '../fixtures/mock-api'

test.describe('采购管理模块', () => {
  test.beforeEach(async ({ page }) => { await mockAllApisFallback(page) })

  test('采购合同 — 页面加载', async ({ page }) => {
    await page.goto('/purchase/contract')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })

  test('采购结算 — 页面加载', async ({ page }) => {
    await page.goto('/purchase/settlement')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })

  test('询价比价 — 页面加载', async ({ page }) => {
    await page.goto('/purchase/inquiry')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })
})
