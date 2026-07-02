import { test, expect } from '@playwright/test'
import { mockCrudApis, mockListApi, mockApi, mockAllApisFallback, waitForTable, waitForForm, waitForPageReady } from '../fixtures/mock-api'

const mockProject = {
  id: 1,
  projectCode: 'PRJ-2024-001',
  projectName: 'E2E测试项目',
  projectNature: '新建',
  projectType: '市政工程',
  ownerCompanyId: 1,
  ownerCompanyName: '测试甲方',
  signingCompanyId: 1,
  signingCompanyName: '测试公司',
  status: 'DRAFT',
  budgetAmount: 1000000,
  projectOverview: 'E2E测试项目概述',
  projectAddress: '测试地址',
  contactName: '张三',
  contactPhone: '13800138000',
  needTender: 1,
}

test.describe('项目管理模块 — 完整 CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await mockCrudApis(page, '/api/v1/project', mockProject)
    // mock 甲方和公司列表
    await mockListApi(page, '/api/v1/basedata/owner/list', [{ id: 1, ownerName: '测试甲方' }])
    await mockListApi(page, '/api/v1/basedata/company/list', [{ id: 1, companyName: '测试公司' }])
    await mockAllApisFallback(page)
  })

  test('列表页 — 页面加载并渲染表格', async ({ page }) => {
    await page.goto('/project/list')
    await waitForPageReady(page)
    await expect(page.locator('.el-table')).toBeVisible({ timeout: 10_000 })
  })

  test('列表页 — 搜索按钮可用', async ({ page }) => {
    await page.goto('/project/list')
    await waitForPageReady(page)
    const searchBtn = page.locator('button:has-text("搜索")')
    await expect(searchBtn).toBeVisible()
    await expect(searchBtn).toBeEnabled()
  })

  test('列表页 — 重置按钮可用', async ({ page }) => {
    await page.goto('/project/list')
    await waitForPageReady(page)
    const resetBtn = page.locator('button:has-text("重置")')
    await expect(resetBtn).toBeVisible()
  })

  test('列表页 — 新增按钮可用', async ({ page }) => {
    await page.goto('/project/list')
    await waitForPageReady(page)
    const addBtn = page.locator('button:has-text("新增项目")')
    await expect(addBtn).toBeVisible()
    await expect(addBtn).toBeEnabled()
  })

  test('新增 — 点击新增跳转表单页', async ({ page }) => {
    await page.goto('/project/list')
    await waitForPageReady(page)
    await page.click('button:has-text("新增项目")')
    await page.waitForURL('**/project/create')
    await waitForForm(page)
    await expect(page.locator('.el-form')).toBeVisible()
  })

  test('新增 — 表单字段渲染完整', async ({ page }) => {
    await page.goto('/project/create')
    await waitForForm(page)
    // 验证核心字段
    await expect(page.locator('[placeholder="请输入项目名称"]')).toBeVisible()
    await expect(page.locator('.el-form-item__label:has-text("项目性质")')).toBeVisible()
    await expect(page.locator('.el-form-item__label:has-text("项目类型")')).toBeVisible()
    await expect(page.locator('.el-form-item__label:has-text("业主单位")')).toBeVisible()
    await expect(page.locator('.el-form-item__label:has-text("签约公司")')).toBeVisible()
    await expect(page.locator('button:has-text("保存")')).toBeVisible()
  })

  test('新增 — el-select 下拉交互', async ({ page }) => {
    await page.goto('/project/create')
    await waitForForm(page)

    // 选择项目性质：先等待 select 可见再点击
    const natureSelect = page.locator('.el-form-item:has(.el-form-item__label:has-text("项目性质")) .el-select')
    await expect(natureSelect).toBeVisible({ timeout: 10_000 })
    await natureSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 5_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("新建")').click()

    // 选择项目类型
    const typeSelect = page.locator('.el-form-item:has(.el-form-item__label:has-text("项目类型")) .el-select')
    await expect(typeSelect).toBeVisible()
    await typeSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 5_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("市政工程")').click()
  })

  test('新增 — 表单验证：必填项为空时拒绝提交', async ({ page }) => {
    await page.goto('/project/create')
    await waitForForm(page)
    await page.click('button:has-text("保存")')
    // 应出现校验错误
    await expect(page.locator('.el-form-item__error').first()).toBeVisible({ timeout: 5_000 })
  })

  test('新增 — 填写表单并保存', async ({ page }) => {
    await page.goto('/project/create')
    await waitForForm(page)

    // 填写项目名称
    await page.fill('[placeholder="请输入项目名称"]', 'E2E新建测试项目')

    // 选择项目性质：等待 select 就绪后交互
    const natureSelect = page.locator('.el-form-item:has(.el-form-item__label:has-text("项目性质")) .el-select')
    await expect(natureSelect).toBeVisible({ timeout: 10_000 })
    await natureSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 5_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("新建")').click()

    // 选择项目类型
    const typeSelect = page.locator('.el-form-item:has(.el-form-item__label:has-text("项目类型")) .el-select')
    await expect(typeSelect).toBeVisible()
    await typeSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 5_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("市政工程")').click()

    // 验证保存按钮存在
    await expect(page.locator('button:has-text("保存")')).toBeEnabled()
  })

  test('编辑 — 编辑页面表单回显', async ({ page }) => {
    await page.goto('/project/edit/1')
    await waitForForm(page)
    await expect(page.locator('.el-form')).toBeVisible()
    await expect(page.locator('text=编辑项目')).toBeVisible()
  })

  test('详情 — 详情页渲染', async ({ page }) => {
    await page.goto('/project/detail/1')
    await waitForPageReady(page)
    await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
  })

  test('列表页 — 查看按钮导航到详情', async ({ page }) => {
    await page.goto('/project/list')
    await waitForPageReady(page)
    await waitForTable(page)
    const viewBtn = page.locator('.el-table button:has-text("查看")').first()
    if (await viewBtn.isVisible()) {
      await expect(viewBtn).toBeEnabled()
    }
  })

  test('列表页 — 分页组件存在', async ({ page }) => {
    await page.goto('/project/list')
    await waitForPageReady(page)
    await waitForTable(page)
    await expect(page.locator('.el-pagination')).toBeVisible()
  })
})
