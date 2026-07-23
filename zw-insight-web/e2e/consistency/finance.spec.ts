/**
 * finance 财务管理 —— 列表页前端展示 vs 后端数据 字段级一致性
 *  A 开票申请 /finance/invoice-apply  GET /v1/finance/invoice-apply/page
 *  A 付款申请 /finance/payment-apply  GET /v1/finance/payment-apply/page
 *
 * 金额列前端用 formatMoney（toLocaleString zh-CN，强制 2 位小数），空值显示 '-'。
 */
import { test, expect } from '@playwright/test'
import {
  gotoAndCapture,
  matchTableToRecords,
  matchPaginationTotal,
  writeModuleReport,
  fmtAmount,
  type ColumnSpec,
  type PageConsistencyResult,
  type PageResult,
} from './consistency-helper'
import { INVOICE_APPLY_STATUS, PAYMENT_APPLY_STATUS } from './enum-baseline'

/** 金额列：空→前端 '-'（跳过），非空→千分位+2位严格比对 */
const amountExpect = (field: string) => (r: any) =>
  r[field] == null || r[field] === '' ? null : fmtAmount(r[field], 2, true)

const INVOICE_COLUMNS: ColumnSpec[] = [
  { label: '项目名称', index: 0, field: 'projectName', type: 'text' },
  { label: '开票金额', index: 1, field: 'invoiceAmount', expect: amountExpect('invoiceAmount') },
  { label: '发票类型', index: 2, field: 'invoiceType', type: 'text' },
  { label: '申请日期', index: 3, field: 'applyDate', type: 'date' },
  { label: '状态', index: 4, field: 'status', type: 'enum', enumMap: INVOICE_APPLY_STATUS },
]

const PAYMENT_COLUMNS: ColumnSpec[] = [
  { label: '项目名称', index: 0, field: 'projectName', type: 'text' },
  { label: '收款单位', index: 1, field: 'supplierName', type: 'text' },
  { label: '付款金额', index: 2, field: 'paymentAmount', expect: amountExpect('paymentAmount') },
  { label: '付款日期', index: 3, field: 'paymentDate', type: 'date' },
  { label: '状态', index: 4, field: 'status', type: 'enum', enumMap: PAYMENT_APPLY_STATUS },
]

const results: PageConsistencyResult[] = []

test.describe.serial('finance 一致性', () => {
  test('A 开票申请 /finance/invoice-apply 字段级一致', async ({ page }) => {
    const resp = await gotoAndCapture<PageResult>(page, '/finance/invoice-apply', /\/v1\/finance\/invoice-apply\/page/)
    expect(resp.code, `接口应返回成功码，实际 message=${resp.message}`).toBe(200)
    const records = resp.data?.records ?? []
    expect(records.length, '种子数据应保证列表非空').toBeGreaterThan(0)

    await page.locator('.el-table__body-wrapper .el-table__row').first().waitFor({ timeout: 15_000 })

    const mismatches = await matchTableToRecords(page, records, INVOICE_COLUMNS, { softCollect: true })
    await matchPaginationTotal(page, resp.data.total)

    results.push({
      route: '/finance/invoice-apply',
      title: '开票申请列表',
      api: 'GET /v1/finance/invoice-apply/page',
      recordCount: records.length,
      mismatches,
    })
    expect(mismatches, `开票申请存在 ${mismatches.length} 处不一致：\n${JSON.stringify(mismatches, null, 2)}`).toHaveLength(0)
  })

  test('A 付款申请 /finance/payment-apply 字段级一致', async ({ page }) => {
    const resp = await gotoAndCapture<PageResult>(page, '/finance/payment-apply', /\/v1\/finance\/payment-apply\/page/)
    expect(resp.code, `接口应返回成功码，实际 message=${resp.message}`).toBe(200)
    const records = resp.data?.records ?? []
    expect(records.length, '种子数据应保证列表非空').toBeGreaterThan(0)

    await page.locator('.el-table__body-wrapper .el-table__row').first().waitFor({ timeout: 15_000 })

    const mismatches = await matchTableToRecords(page, records, PAYMENT_COLUMNS, { softCollect: true })
    await matchPaginationTotal(page, resp.data.total)

    results.push({
      route: '/finance/payment-apply',
      title: '付款申请列表',
      api: 'GET /v1/finance/payment-apply/page',
      recordCount: records.length,
      mismatches,
    })
    expect(mismatches, `付款申请存在 ${mismatches.length} 处不一致：\n${JSON.stringify(mismatches, null, 2)}`).toHaveLength(0)
  })

  test.afterAll(async () => {
    writeModuleReport('finance', results)
  })
})
