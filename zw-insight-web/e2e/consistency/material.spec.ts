/**
 * material 材料库存 —— 列表页前端展示 vs 后端数据 字段级一致性
 *  A 列表页 /material/inbound  GET /v1/material/inbound/page
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
import { DOC_APPROVE_STATUS } from './enum-baseline'

const LIST_COLUMNS: ColumnSpec[] = [
  { label: '入库单号', index: 0, field: 'inboundCode', type: 'text' },
  { label: '入库日期', index: 1, field: 'inboundDate', type: 'date' },
  { label: '入库总金额', index: 2, field: 'totalAmount', type: 'numeric' },
  // 直接出库：1→是 0→否（数字标志映射，独立声明）
  { label: '直接出库', index: 3, field: 'directOutbound', expect: (r) => (r.directOutbound === 1 || r.directOutbound === '1' ? '是' : '否') },
  { label: '状态', index: 4, field: 'status', type: 'enum', enumMap: DOC_APPROVE_STATUS },
]

const results: PageConsistencyResult[] = []

test.describe.serial('material 一致性', () => {
  test('A 列表页 /material/inbound 字段级一致', async ({ page }) => {
    const resp = await gotoAndCapture<PageResult>(page, '/material/inbound', /\/v1\/material\/inbound\/page/)
    expect(resp.code, `接口应返回成功码，实际 message=${resp.message}`).toBe(200)
    const records = resp.data?.records ?? []
    expect(records.length, '种子数据应保证列表非空').toBeGreaterThan(0)

    await page.locator('.el-table__body-wrapper .el-table__row').first().waitFor({ timeout: 15_000 })

    const mismatches = await matchTableToRecords(page, records, LIST_COLUMNS, { softCollect: true })
    await matchPaginationTotal(page, resp.data.total)

    results.push({
      route: '/material/inbound',
      title: '材料入库列表',
      api: 'GET /v1/material/inbound/page',
      recordCount: records.length,
      mismatches,
    })
    expect(mismatches, `列表页存在 ${mismatches.length} 处不一致：\n${JSON.stringify(mismatches, null, 2)}`).toHaveLength(0)
  })

  test.afterAll(async () => {
    writeModuleReport('material', results)
  })
})
