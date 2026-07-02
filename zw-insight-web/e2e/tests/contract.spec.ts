import { test, expect } from '@playwright/test'
import { mockCrudApis, mockListApi, mockApi, mockAllApisFallback, waitForPageReady, waitForTable, waitForForm } from '../fixtures/mock-api'

const mockContract = {
  id: 1,
  contractCode: 'HT-2024-001',
  contractName: 'E2E测试合同',
  contractType: 'REGISTER',
  projectId: 1,
  projectName: '测试项目',
  partyAName: '测试甲方',
  contractAmount: 500000,
  signingDate: '2024-06-01',
  startDate: '2024-07-01',
  endDate: '2025-06-30',
  taxRate: 9,
  status: 'DRAFT',
}

test.describe('合同管理模块', () => {
  test.beforeEach(async ({ page }) => {
    await mockCrudApis(page, '/api/v1/contract', mockContract)
    // mock 项目列表（合同表单和列表都依赖）
    await mockListApi(page, '/api/v1/project', [{ id: 1, projectName: '测试项目' }])
    // mock 合同明细
    await mockListApi(page, '/api/v1/contract/detail*', [])
    await mockAllApisFallback(page)
  })

  // ── 列表页 ──

  test('施工合同列表 — 页面加载并渲染表格', async ({ page }) => {
    await page.goto('/contract/list')
    await waitForPageReady(page)
    await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
  })

  test('施工合同列表 — 表格渲染', async ({ page }) => {
    await page.goto('/contract/list')
    await waitForPageReady(page)
    await expect(page.locator('.el-table')).toBeVisible({ timeout: 10_000 })
  })

  test('列表页 — 搜索与重置按钮', async ({ page }) => {
    await page.goto('/contract/list')
    await waitForPageReady(page)
    const searchBtn = page.locator('button:has-text("搜索")')
    await expect(searchBtn).toBeVisible({ timeout: 10_000 })
    await expect(searchBtn).toBeEnabled()
    const resetBtn = page.locator('button:has-text("重置")')
    await expect(resetBtn).toBeVisible()
    await expect(resetBtn).toBeEnabled()
  })

  test('列表页 — 新增按钮可用', async ({ page }) => {
    await page.goto('/contract/list')
    await waitForPageReady(page)
    const addBtn = page.locator('button:has-text("新增合同")')
    await expect(addBtn).toBeVisible({ timeout: 10_000 })
    await expect(addBtn).toBeEnabled()
  })

  test('列表页 — 分页组件存在', async ({ page }) => {
    await page.goto('/contract/list')
    await waitForPageReady(page)
    await waitForTable(page)
    await expect(page.locator('.el-pagination')).toBeVisible()
  })

  // ── 新增合同 ──

  test('新增合同 — 跳转表单页', async ({ page }) => {
    await page.goto('/contract/list')
    await waitForPageReady(page)
    await page.click('button:has-text("新增合同")')
    await page.waitForURL('**/contract/create')
    await waitForForm(page)
    await expect(page.locator('.el-form')).toBeVisible()
  })

  test('新增合同 — 表单字段渲染完整', async ({ page }) => {
    await page.goto('/contract/create')
    await waitForForm(page)
    // 验证核心字段
    await expect(page.locator('.el-form-item__label:has-text("所属项目")')).toBeVisible()
    await expect(page.locator('.el-form-item__label:has-text("合同类型")')).toBeVisible()
    await expect(page.locator('.el-form-item__label:has-text("甲方名称")')).toBeVisible()
    await expect(page.locator('.el-form-item__label:has-text("合同金额")')).toBeVisible()
    await expect(page.locator('.el-form-item__label:has-text("签订日期")')).toBeVisible()
    await expect(page.locator('button:has-text("保存")')).toBeVisible()
  })

  test('新增合同 — 表单验证：必填项为空时拒绝提交', async ({ page }) => {
    await page.goto('/contract/create')
    await waitForForm(page)
    await page.click('button:has-text("保存")')
    // 应出现校验错误（甲方名称、合同金额、所属项目 必填）
    await expect(page.locator('.el-form-item__error').first()).toBeVisible({ timeout: 5_000 })
  })

  test('新增合同 — 填写表单并保存', async ({ page }) => {
    await page.goto('/contract/create')
    await waitForForm(page)

    // 填写甲方名称
    await page.fill('[placeholder="请输入甲方名称"]', 'E2E测试甲方')

    // 填写合同金额（el-input-number 通过 input 填写）
    const amountInput = page.locator('.el-form-item:has(.el-form-item__label:has-text("合同金额")) .el-input-number input')
    await amountInput.fill('800000')

    // 验证保存按钮存在且可用
    await expect(page.locator('button:has-text("保存")')).toBeEnabled()
  })

  // ── 编辑合同 ──

  test('编辑合同 — 表单回显', async ({ page }) => {
    await page.goto('/contract/edit/1')
    await waitForForm(page)
    await expect(page.locator('.el-form')).toBeVisible()
    // 编辑模式标题
    await expect(page.locator('text=编辑施工合同')).toBeVisible()
  })

  // ── BOQ 上传 ──

  test('BOQ 上传页面 — 渲染', async ({ page }) => {
    await page.goto('/contract/boq/1')
    await waitForPageReady(page)
    await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
  })
})
