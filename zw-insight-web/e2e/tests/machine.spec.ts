import { test, expect } from '@playwright/test'
import { mockCrudApis, mockAllApisFallback, waitForPageReady, waitForTable } from '../fixtures/mock-api'

const mockMachineContract = {
  id: 1,
  contractCode: 'MC-2024-001',
  contractName: 'E2E机械合同',
  supplierName: '测试供应商',
  machineName: '挖掘机',
  contractAmount: 200000,
  rentalType: '月租',
  startDate: '2024-06-01',
  endDate: '2025-05-31',
}

const machinePages = [
  { path: '/machine/contract', title: '机械合同' },
  { path: '/machine/ledger', title: '机械台账' },
  { path: '/machine/entry', title: '进出场登记' },
  { path: '/machine/work-log', title: '台班/工作量' },
  { path: '/machine/repair', title: '故障维修' },
  { path: '/machine/settlement', title: '机械结算' },
]

test.describe('机械管理模块 — 页面加载', () => {
  test.beforeEach(async ({ page }) => { await mockAllApisFallback(page) })

  for (const { path, title } of machinePages) {
    test(`${title} — 页面加载`, async ({ page }) => {
      await page.goto(path)
      await waitForPageReady(page)
      await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
    })
  }

  test('新建结算单 — 页面渲染', async ({ page }) => {
    await page.goto('/machine/settlement/create')
    await waitForPageReady(page)
    await expect(page.locator('.el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
  })

  test('结算单详情 — 页面渲染', async ({ page }) => {
    await page.goto('/machine/settlement/detail/1')
    await waitForPageReady(page)
    await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
  })
})

test.describe('机械管理模块 — 机械合同 CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await mockCrudApis(page, '/api/v1/machine/contract', mockMachineContract)
    await mockAllApisFallback(page)
  })

  test('机械合同 — 表格渲染', async ({ page }) => {
    await page.goto('/machine/contract')
    await waitForPageReady(page)
    await expect(page.locator('.el-table')).toBeVisible({ timeout: 10_000 })
  })

  test('机械合同 — 搜索与重置按钮', async ({ page }) => {
    await page.goto('/machine/contract')
    await waitForPageReady(page)
    await expect(page.locator('button:has-text("搜索")')).toBeVisible({ timeout: 10_000 })
    await expect(page.locator('button:has-text("重置")')).toBeVisible()
  })

  test('机械合同 — 新增按钮弹出表单', async ({ page }) => {
    await page.goto('/machine/contract')
    await waitForPageReady(page)
    await page.click('button:has-text("新增机械合同")')
    // 验证弹窗
    await expect(page.locator('.el-dialog:visible')).toBeVisible({ timeout: 5_000 })
    await expect(page.locator('.el-dialog .el-form')).toBeVisible()
    // 验证表单字段
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("合同名称")')).toBeVisible()
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("设备供应商")')).toBeVisible()
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("设备名称")')).toBeVisible()
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("合同金额")')).toBeVisible()
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("租赁方式")')).toBeVisible()
  })

  test('机械合同 — 弹窗表单验证拒绝空提交', async ({ page }) => {
    await page.goto('/machine/contract')
    await waitForPageReady(page)
    await page.click('button:has-text("新增机械合同")')
    await expect(page.locator('.el-dialog:visible')).toBeVisible({ timeout: 5_000 })
    await page.locator('.el-dialog:visible').locator('button:has-text("确定")').click()
    await expect(page.locator('.el-dialog .el-form-item__error').first()).toBeVisible({ timeout: 5_000 })
  })

  test('机械合同 — 填写表单字段', async ({ page }) => {
    await page.goto('/machine/contract')
    await waitForPageReady(page)
    await page.click('button:has-text("新增机械合同")')
    await expect(page.locator('.el-dialog:visible')).toBeVisible({ timeout: 5_000 })
    // 填写合同名称
    await page.locator('.el-dialog:visible .el-form-item:has(.el-form-item__label:has-text("合同名称")) input').fill('E2E新建机械合同')
    // 填写设备供应商
    await page.locator('.el-dialog:visible .el-form-item:has(.el-form-item__label:has-text("设备供应商")) input').fill('E2E测试供应商')
    // 填写设备名称
    await page.locator('.el-dialog:visible .el-form-item:has(.el-form-item__label:has-text("设备名称")) input').fill('塔吊')
    // 填写合同金额
    await page.locator('.el-dialog:visible .el-form-item:has(.el-form-item__label:has-text("合同金额")) .el-input-number input').fill('300000')
    // 确定按钮可用
    await expect(page.locator('.el-dialog:visible button:has-text("确定")')).toBeEnabled()
  })

  test('机械合同 — 分页组件存在', async ({ page }) => {
    await page.goto('/machine/contract')
    await waitForPageReady(page)
    await waitForTable(page)
    await expect(page.locator('.el-pagination')).toBeVisible()
  })
})
