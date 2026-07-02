import { test, expect } from '@playwright/test'
import { mockCrudApis, mockAllApisFallback, waitForPageReady, waitForTable } from '../fixtures/mock-api'

const mockLaborContract = {
  id: 1,
  contractCode: 'LC-2024-001',
  contractName: 'E2E劳务合同',
  teamName: '测试班组',
  contractAmount: 150000,
  startDate: '2024-06-01',
  endDate: '2025-05-31',
  status: 'DRAFT',
  remark: 'E2E测试备注',
}

const laborPages = [
  { path: '/labor/contract', title: '劳务合同' },
  { path: '/labor/team', title: '班组管理' },
  { path: '/labor/roster', title: '劳务花名册' },
  { path: '/labor/work-order', title: '用工单' },
  { path: '/labor/payroll', title: '工资单' },
  { path: '/labor/salary-stats', title: '薪资统计' },
]

test.describe('劳务管理模块 — 页面加载', () => {
  test.beforeEach(async ({ page }) => { await mockAllApisFallback(page) })

  for (const { path, title } of laborPages) {
    test(`${title} — 页面加载`, async ({ page }) => {
      await page.goto(path)
      await waitForPageReady(page)
      await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
    })
  }
})

test.describe('劳务管理模块 — 劳务合同 CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await mockCrudApis(page, '/api/v1/labor/contract', mockLaborContract)
    await mockAllApisFallback(page)
  })

  test('劳务合同 — 表格渲染', async ({ page }) => {
    await page.goto('/labor/contract')
    await waitForPageReady(page)
    await expect(page.locator('.el-table')).toBeVisible({ timeout: 10_000 })
  })

  test('劳务合同 — 搜索与重置按钮', async ({ page }) => {
    await page.goto('/labor/contract')
    await waitForPageReady(page)
    await expect(page.locator('button:has-text("搜索")')).toBeVisible({ timeout: 10_000 })
    await expect(page.locator('button:has-text("重置")')).toBeVisible()
  })

  test('劳务合同 — 新增按钮弹出表单', async ({ page }) => {
    await page.goto('/labor/contract')
    await waitForPageReady(page)
    await page.click('button:has-text("新增劳务合同")')
    // 验证弹窗
    await expect(page.locator('.el-dialog:visible')).toBeVisible({ timeout: 5_000 })
    await expect(page.locator('.el-dialog .el-form')).toBeVisible()
    // 验证表单字段
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("合同名称")')).toBeVisible()
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("施工队伍")')).toBeVisible()
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("合同金额")')).toBeVisible()
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("开始日期")')).toBeVisible()
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("结束日期")')).toBeVisible()
  })

  test('劳务合同 — 弹窗表单验证拒绝空提交', async ({ page }) => {
    await page.goto('/labor/contract')
    await waitForPageReady(page)
    await page.click('button:has-text("新增劳务合同")')
    await expect(page.locator('.el-dialog:visible')).toBeVisible({ timeout: 5_000 })
    await page.locator('.el-dialog:visible').locator('button:has-text("确定")').click()
    await expect(page.locator('.el-dialog .el-form-item__error').first()).toBeVisible({ timeout: 5_000 })
  })

  test('劳务合同 — 填写表单字段', async ({ page }) => {
    await page.goto('/labor/contract')
    await waitForPageReady(page)
    await page.click('button:has-text("新增劳务合同")')
    await expect(page.locator('.el-dialog:visible')).toBeVisible({ timeout: 5_000 })
    // 填写合同名称
    await page.locator('.el-dialog:visible .el-form-item:has(.el-form-item__label:has-text("合同名称")) input').fill('E2E新建劳务合同')
    // 填写施工队伍
    await page.locator('.el-dialog:visible .el-form-item:has(.el-form-item__label:has-text("施工队伍")) input').fill('E2E测试班组')
    // 填写合同金额
    await page.locator('.el-dialog:visible .el-form-item:has(.el-form-item__label:has-text("合同金额")) .el-input-number input').fill('250000')
    // 填写备注
    await page.locator('.el-dialog:visible textarea').fill('E2E测试备注')
    // 确定按钮可用
    await expect(page.locator('.el-dialog:visible button:has-text("确定")')).toBeEnabled()
  })

  test('劳务合同 — 分页组件存在', async ({ page }) => {
    await page.goto('/labor/contract')
    await waitForPageReady(page)
    await waitForTable(page)
    await expect(page.locator('.el-pagination')).toBeVisible()
  })
})
