/**
 * budget 预算管理 —— 列表页前端展示 vs 后端数据 字段级一致性
 *
 *  A 列表页 /budget/list  GET /v1/budget/page
 *
 * 注意：金额列前端用 `Number.toLocaleString()`（仅千分位、不强制小数位），
 * 故用 numeric 类型做数值归一比较；状态用三元渲染，基线取筛选区产品声明。
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
import { BUDGET_STATUS } from './enum-baseline'

/** 列规格与 budget/index.vue <el-table-column> 顺序一致（0-based） */
const LIST_COLUMNS: ColumnSpec[] = [
  { label: '项目名称', index: 0, field: 'projectName', type: 'text' },
  { label: '预算年度', index: 1, field: 'budgetYear', type: 'text' },
  { label: '预算总额', index: 2, field: 'totalAmount', type: 'numeric' },
  { label: '已用金额', index: 3, field: 'usedAmount', type: 'numeric' },
  { label: '状态', index: 4, field: 'status', type: 'enum', enumMap: BUDGET_STATUS },
  { label: '创建时间', index: 5, field: 'createTime', type: 'datetime' },
]

const results: PageConsistencyResult[] = []

test.describe.serial('budget 一致性', () => {
  test('A 列表页 /budget/list 字段级一致', async ({ page }) => {
    const resp = await gotoAndCapture<PageResult>(page, '/budget/list', /\/v1\/budget\/page/)
    expect(resp.code, `接口应返回成功码，实际 message=${resp.message}`).toBe(200)
    const records = resp.data?.records ?? []
    expect(records.length, '种子数据应保证列表非空').toBeGreaterThan(0)

    await page.locator('.el-table__body-wrapper .el-table__row').first().waitFor({ timeout: 15_000 })

    const mismatches = await matchTableToRecords(page, records, LIST_COLUMNS, { softCollect: true })
    await matchPaginationTotal(page, resp.data.total)

    results.push({
      route: '/budget/list',
      title: '预算列表',
      api: 'GET /v1/budget/page',
      recordCount: records.length,
      mismatches,
    })
    expect(mismatches, `列表页存在 ${mismatches.length} 处不一致：\n${JSON.stringify(mismatches, null, 2)}`).toHaveLength(0)
  })

  test.afterAll(async () => {
    writeModuleReport('budget', results)
  })
})
