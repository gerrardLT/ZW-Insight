/**
 * finance 财务管理 —— 列表页前端展示 vs 后端数据 字段级一致性
 *  A 开票申请 /finance/invoice-apply  GET /v1/finance/invoice-apply/page
 *  A 付款申请 /finance/payment-apply  GET /v1/finance/payment-apply/page
 *  B 财务封账 /finance/finance-lock   GET /v1/finance/lock/page（2026-08-14 P0 扩展，@matrix C-13）
 *
 * 金额列前端用 formatMoney（toLocaleString zh-CN，强制 2 位小数），空值显示 '-'。
 */
import { test, expect } from '@playwright/test'
import {
  gotoAndCapture,
  matchTableToRecords,
  matchPaginationTotal,
  finalizeModuleConsistency,
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

/** 封账页枚举基线（finance-lock/index.vue lockTypeLabel + 状态 tag 实证） */
const LOCK_TYPE_MAP: Record<string, string> = { MONTHLY: '月度', QUARTERLY: '季度' }
const LOCK_STATUS_MAP: Record<string, string> = { LOCKED: '已封账', UNLOCKED: '已解封' }
/** 直出文本列：null/空跳过比对（后端可能无操作人/时间） */
const rawText = (field: string) => (r: any) =>
  r[field] == null || r[field] === '' ? null : String(r[field])

const LOCK_COLUMNS: ColumnSpec[] = [
  { label: '期间', index: 0, field: 'period', type: 'text' },
  { label: '封账类型', index: 1, field: 'lockType', type: 'enum', enumMap: LOCK_TYPE_MAP },
  { label: '状态', index: 2, field: 'status', type: 'enum', enumMap: LOCK_STATUS_MAP },
  { label: '操作人', index: 3, field: 'lockBy', expect: rawText('lockBy') },
  { label: '操作时间', index: 4, field: 'lockTime', expect: rawText('lockTime') },
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

  // @matrix C-13 封账页字段级勾稽（只读；封账拦截行为闭环在 21-finance-chain.spec.ts）
  test('B 财务封账 /finance/finance-lock 字段级一致', async ({ page }) => {
    const resp = await gotoAndCapture<PageResult>(page, '/finance/finance-lock', /\/v1\/finance\/lock\/page/)
    expect(resp.code, `接口应返回成功码，实际 message=${resp.message}`).toBe(200)
    const records = resp.data?.records ?? []

    if (records.length === 0) {
      // 租户 1 无封账记录：记录豁免说明（与 runListConsistency 的 __empty__ 语义一致）
      results.push({
        route: '/finance/finance-lock',
        title: '财务封账列表',
        api: 'GET /v1/finance/lock/page',
        recordCount: 0,
        mismatches: [{ row: -1, column: '__empty__', expected: '有封账记录', actual: '租户无封账记录，跳过逐行比对（非一致性缺陷）' }],
      })
      return
    }

    await page.locator('.el-table__body-wrapper .el-table__row').first().waitFor({ timeout: 15_000 })

    const mismatches = await matchTableToRecords(page, records, LOCK_COLUMNS, { softCollect: true })
    await matchPaginationTotal(page, resp.data.total)

    results.push({
      route: '/finance/finance-lock',
      title: '财务封账列表',
      api: 'GET /v1/finance/lock/page',
      recordCount: records.length,
      mismatches,
    })
    expect(mismatches, `封账列表存在 ${mismatches.length} 处不一致：\n${JSON.stringify(mismatches, null, 2)}`).toHaveLength(0)
  })

  test.afterAll(async () => {
    finalizeModuleConsistency('finance', results)
  })
})
