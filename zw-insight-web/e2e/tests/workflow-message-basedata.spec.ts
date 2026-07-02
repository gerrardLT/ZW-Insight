import { test, expect } from '@playwright/test'
import { mockAllApisFallback, waitForPageReady } from '../fixtures/mock-api'

const workflowPages = [
  { path: '/workflow/designer', title: '流程设计器' },
  { path: '/workflow/process', title: '流程定义' },
  { path: '/workflow/business-type', title: '业务类型' },
  { path: '/workflow/approval', title: '审批管理' },
  { path: '/workflow/rollback', title: '审批回滚' },
]

test.describe('工作流管理模块', () => {
  test.beforeEach(async ({ page }) => { await mockAllApisFallback(page) })

  test('流程设计器 — 页面加载', async ({ page }) => {
    await page.goto('/workflow/designer')
    await waitForPageReady(page)
    // bpmn-js 设计器使用 .bjs-container 或 .designer-container
    await expect(page.locator('.bjs-container, .designer-container, .el-card').first()).toBeVisible({ timeout: 15_000 })
  })

  for (const { path, title } of workflowPages.slice(1)) {
    test(`${title} — 页面加载`, async ({ page }) => {
      await page.goto(path)
      await waitForPageReady(page)
      await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
    })
  }
})

const messagePages = [
  { path: '/message/notice', title: '通知管理' },
  { path: '/message/announcement', title: '公告管理' },
  { path: '/message/push-config', title: '推送渠道配置' },
  { path: '/message/center', title: '消息中心' },
]

test.describe('消息管理模块', () => {
  test.beforeEach(async ({ page }) => { await mockAllApisFallback(page) })

  for (const { path, title } of messagePages) {
    test(`${title} — 页面加载`, async ({ page }) => {
      await page.goto(path)
      await waitForPageReady(page)
      await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
    })
  }
})

const basedataPages = [
  { path: '/basedata/material', title: '材料字典' },
  { path: '/basedata/supplier', title: '供应商' },
  { path: '/basedata/owner', title: '甲方单位' },
  { path: '/basedata/company', title: '自持公司' },
  { path: '/basedata/inspection-scheme', title: '检查方案' },
  { path: '/basedata/supplier-evaluation', title: '供应商评价' },
  { path: '/basedata/supplier-blacklist', title: '供应商黑名单' },
]

test.describe('基础数据模块', () => {
  test.beforeEach(async ({ page }) => { await mockAllApisFallback(page) })

  for (const { path, title } of basedataPages) {
    test(`${title} — 页面加载`, async ({ page }) => {
      await page.goto(path)
      await waitForPageReady(page)
      await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
    })
  }
})
