import { test, expect } from '@playwright/test'
import { mockCrudApis, mockListApi, mockAllApisFallback, waitForPageReady, waitForTable } from '../fixtures/mock-api'

const mockInvoice = {
  id: 1,
  projectName: '测试项目',
  invoiceAmount: 100000,
  invoiceType: '增值税专用发票',
  applyDate: '2024-06-15',
  taxRate: 9,
  status: 'DRAFT',
  remark: 'E2E测试',
}

const mockPayment = {
  id: 1,
  projectName: '测试项目',
  receivedAmount: 50000,
  receivedDate: '2024-07-01',
  paymentMethod: '银行转账',
  remark: 'E2E回款',
}

const financePages = [
  { path: '/finance/invoice-apply', title: '开票申请' },
  { path: '/finance/invoice-received', title: '收票登记' },
  { path: '/finance/payment-received', title: '回款登记' },
  { path: '/finance/payment-apply', title: '付款申请' },
  { path: '/finance/settlement', title: '项目最终结算' },
  { path: '/finance/finance-lock', title: '财务封账' },
  { path: '/finance/tax-rate', title: '税率管理' },
]

test.describe('财务管理模块 — 页面加载', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApisFallback(page)
  })

  for (const { path, title } of financePages) {
    test(`${title} — 页面加载`, async ({ page }) => {
      await page.goto(path)
      await waitForPageReady(page)
      await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
    })
  }

  test('结算单详情 — 页面渲染', async ({ page }) => {
    await page.goto('/finance/settlement/1')
    await waitForPageReady(page)
    await expect(page.locator('.el-table, .el-form, .el-card').first()).toBeVisible({ timeout: 15_000 })
  })
})

test.describe('财务管理模块 — 开票申请 CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await mockCrudApis(page, '/api/v1/finance/invoice-apply', mockInvoice)
    await mockListApi(page, '/api/v1/project', [{ id: 1, projectName: '测试项目' }])
    await mockAllApisFallback(page)
  })

  test('开票申请 — 表格渲染', async ({ page }) => {
    await page.goto('/finance/invoice-apply')
    await waitForPageReady(page)
    await expect(page.locator('.el-table')).toBeVisible({ timeout: 10_000 })
  })

  test('开票申请 — 搜索与重置按钮', async ({ page }) => {
    await page.goto('/finance/invoice-apply')
    await waitForPageReady(page)
    await expect(page.locator('button:has-text("搜索")')).toBeVisible({ timeout: 10_000 })
    await expect(page.locator('button:has-text("重置")')).toBeVisible()
  })

  test('开票申请 — 新增按钮弹出表单', async ({ page }) => {
    await page.goto('/finance/invoice-apply')
    await waitForPageReady(page)
    await page.click('button:has-text("新增开票申请")')
    // 验证弹窗
    await expect(page.locator('.el-dialog:visible')).toBeVisible({ timeout: 5_000 })
    await expect(page.locator('.el-dialog .el-form')).toBeVisible()
    // 验证表单字段
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("项目")')).toBeVisible()
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("开票金额")')).toBeVisible()
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("发票类型")')).toBeVisible()
  })

  test('开票申请 — 弹窗表单验证拒绝空提交', async ({ page }) => {
    await page.goto('/finance/invoice-apply')
    await waitForPageReady(page)
    await page.click('button:has-text("新增开票申请")')
    await expect(page.locator('.el-dialog:visible')).toBeVisible({ timeout: 5_000 })
    // 直接点确定，必填项应报错
    await page.locator('.el-dialog:visible').locator('button:has-text("确定")').click()
    await expect(page.locator('.el-dialog .el-form-item__error').first()).toBeVisible({ timeout: 5_000 })
  })

  test('开票申请 — 分页组件存在', async ({ page }) => {
    await page.goto('/finance/invoice-apply')
    await waitForPageReady(page)
    await waitForTable(page)
    await expect(page.locator('.el-pagination')).toBeVisible()
  })
})

test.describe('财务管理模块 — 回款登记 CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await mockCrudApis(page, '/api/v1/finance/payment-received', mockPayment)
    await mockListApi(page, '/api/v1/project', [{ id: 1, projectName: '测试项目' }])
    await mockAllApisFallback(page)
  })

  test('回款登记 — 表格渲染', async ({ page }) => {
    await page.goto('/finance/payment-received')
    await waitForPageReady(page)
    await expect(page.locator('.el-table')).toBeVisible({ timeout: 10_000 })
  })

  test('回款登记 — 新增按钮弹出表单', async ({ page }) => {
    await page.goto('/finance/payment-received')
    await waitForPageReady(page)
    await page.click('button:has-text("新增回款登记")')
    // 验证弹窗
    await expect(page.locator('.el-dialog:visible')).toBeVisible({ timeout: 5_000 })
    await expect(page.locator('.el-dialog .el-form')).toBeVisible()
    // 验证表单字段
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("项目")')).toBeVisible()
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("回款金额")')).toBeVisible()
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("回款日期")')).toBeVisible()
    await expect(page.locator('.el-dialog .el-form-item__label:has-text("收款方式")')).toBeVisible()
  })

  test('回款登记 — 填写金额', async ({ page }) => {
    await page.goto('/finance/payment-received')
    await waitForPageReady(page)
    await page.click('button:has-text("新增回款登记")')
    await expect(page.locator('.el-dialog:visible')).toBeVisible({ timeout: 5_000 })
    // 填写回款金额
    const amountInput = page.locator('.el-dialog:visible .el-form-item:has(.el-form-item__label:has-text("回款金额")) .el-input-number input')
    await amountInput.fill('120000')
    // 填写备注
    await page.locator('.el-dialog:visible textarea').fill('E2E测试回款备注')
    // 确定按钮可用
    await expect(page.locator('.el-dialog:visible button:has-text("确定")')).toBeEnabled()
  })

  test('回款登记 — 分页组件存在', async ({ page }) => {
    await page.goto('/finance/payment-received')
    await waitForPageReady(page)
    await waitForTable(page)
    await expect(page.locator('.el-pagination')).toBeVisible()
  })
})
