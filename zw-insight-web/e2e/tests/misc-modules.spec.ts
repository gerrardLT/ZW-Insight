import { test, expect } from '@playwright/test'
import { mockAllApisFallback, waitForPageReady } from '../fixtures/mock-api'

test.describe('分包管理模块', () => {
  test.beforeEach(async ({ page }) => { await mockAllApisFallback(page) })

  test('分包合同 — 页面加载', async ({ page }) => {
    await page.goto('/subcontract/contract')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })

  test('分包结算 — 页面加载', async ({ page }) => {
    await page.goto('/subcontract/settlement')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })
})

test.describe('现场管理模块', () => {
  test.beforeEach(async ({ page }) => { await mockAllApisFallback(page) })

  test('进度计划 — 页面加载', async ({ page }) => {
    await page.goto('/site/schedule')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })

  test('施工日志 — 页面加载', async ({ page }) => {
    await page.goto('/site/construction-log')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })

  test('质量安全检查 — 页面加载', async ({ page }) => {
    await page.goto('/site/inspection')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })
})

test.describe('投标管理模块', () => {
  test.beforeEach(async ({ page }) => { await mockAllApisFallback(page) })

  test('投标报名 — 页面加载', async ({ page }) => {
    await page.goto('/tender/register')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })

  test('证件管理 — 页面加载', async ({ page }) => {
    await page.goto('/tender/certificate')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })
})

test.describe('行政人事模块', () => {
  test.beforeEach(async ({ page }) => { await mockAllApisFallback(page) })

  test('人事统计 — 页面加载', async ({ page }) => {
    await page.goto('/hr/statistics')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })

  test('入职申请 — 页面加载', async ({ page }) => {
    await page.goto('/hr/entry')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })

  test('办公用品 — 页面加载', async ({ page }) => {
    await page.goto('/hr/office-supply')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })

  test('车辆管理 — 页面加载', async ({ page }) => {
    await page.goto('/hr/vehicle')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })
})

test.describe('档案管理模块', () => {
  test.beforeEach(async ({ page }) => { await mockAllApisFallback(page) })

  test('档案查询 — 页面加载', async ({ page }) => {
    await page.goto('/archive/index')
    await waitForPageReady(page)
    await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
  })
})
