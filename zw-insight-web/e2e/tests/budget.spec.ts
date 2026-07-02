import { test, expect } from '@playwright/test'
import { mockAllApisFallback, waitForPageReady } from '../fixtures/mock-api'

test.describe('预算管理模块', () => {
  test.beforeEach(async ({ page }) => { await mockAllApisFallback(page) })

  test('预算编制 — 页面加载', async ({ page }) => {
    await page.goto('/budget/list')
    await waitForPageReady(page)
    await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
  })

  test('目标成本变更 — 页面加载', async ({ page }) => {
    await page.goto('/budget/change')
    await waitForPageReady(page)
    await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
  })

  test('变更单表单 — 渲染', async ({ page }) => {
    await page.goto('/budget/change/form')
    await waitForPageReady(page)
    await expect(page.locator('.el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
  })

  test('预算配置 — 页面加载', async ({ page }) => {
    await page.goto('/budget/config')
    await waitForPageReady(page)
    await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
  })

  test('预算控制配置 — 页面加载', async ({ page }) => {
    await page.goto('/budget/control-config')
    await waitForPageReady(page)
    await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
  })
})
