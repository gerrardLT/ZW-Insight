import { test, expect } from '@playwright/test'
import { mockAllApisFallback, waitForPageReady } from '../fixtures/mock-api'

const materialPages = [
  { path: '/material/inbound', title: '到货入库' },
  { path: '/material/outbound', title: '领料出库' },
  { path: '/material/transfer', title: '材料调拨' },
  { path: '/material/stock', title: '库存查询' },
]

test.describe('材料库存模块', () => {
  test.beforeEach(async ({ page }) => { await mockAllApisFallback(page) })

  for (const { path, title } of materialPages) {
    test(`${title} — 页面加载`, async ({ page }) => {
      await page.goto(path)
      await waitForPageReady(page)
      await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
    })
  }
})
