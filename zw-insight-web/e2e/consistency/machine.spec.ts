/**
 * machine 机械管理 —— 列表页前端展示 vs 后端数据 字段级一致性
 *  A 列表页 /machine/contract  GET /v1/machine/contract/page
 *
 * 机械合同列表无状态列；rentalType 直接展示后端原值。
 */
import { test, expect } from '@playwright/test'
import {
  gotoAndCapture,
  matchTableToRecords,
  matchPaginationTotal,
  writeModuleReport,
  type ColumnSpec,
  type PageConsistencyResult,
  type PageResult,
} from './consistency-helper'

const LIST_COLUMNS: ColumnSpec[] = [
  { label: '合同编号', index: 0, field: 'contractCode', type: 'text' },
  { label: '合同名称', index: 1, field: 'contractName', type: 'text' },
  { label: '设备供应商', index: 2, field: 'supplierName', type: 'text' },
  { label: '设备名称', index: 3, field: 'machineName', type: 'text' },
  { label: '合同金额', index: 4, field: 'contractAmount', type: 'numeric' },
  { label: '租赁方式', index: 5, field: 'rentalType', type: 'text' },
  { label: '开始日期', index: 6, field: 'startDate', type: 'date' },
  { label: '结束日期', index: 7, field: 'endDate', type: 'date' },
]

const results: PageConsistencyResult[] = []

test.describe.serial('machine 一致性', () => {
  test('A 列表页 /machine/contract 字段级一致', async ({ page }) => {
    const resp = await gotoAndCapture<PageResult>(page, '/machine/contract', /\/v1\/machine\/contract\/page/)
    expect(resp.code, `接口应返回成功码，实际 message=${resp.message}`).toBe(200)
    const records = resp.data?.records ?? []
    expect(records.length, '种子数据应保证列表非空').toBeGreaterThan(0)

    await page.locator('.el-table__body-wrapper .el-table__row').first().waitFor({ timeout: 15_000 })

    const mismatches = await matchTableToRecords(page, records, LIST_COLUMNS, { softCollect: true })
    await matchPaginationTotal(page, resp.data.total)

    results.push({
      route: '/machine/contract',
      title: '机械合同列表',
      api: 'GET /v1/machine/contract/page',
      recordCount: records.length,
      mismatches,
    })
    expect(mismatches, `列表页存在 ${mismatches.length} 处不一致：\n${JSON.stringify(mismatches, null, 2)}`).toHaveLength(0)
  })

  test.afterAll(async () => {
    writeModuleReport('machine', results)
  })
})
