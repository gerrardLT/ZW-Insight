/**
 * contract 施工合同 —— 列表页前端展示 vs 后端数据 字段级一致性
 *
 *  A 列表页 /contract/list  GET /v1/contract/page
 *
 * 说明：合同"查看"打开的是编辑表单（form.vue，字段以 select/number 为主，
 * 非文本回显），故本模块聚焦列表页字段级一致性（金额/日期/枚举/分页全断言）。
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
import { CONTRACT_STATUS } from './enum-baseline'

/** 列规格与 contract/index.vue <el-table-column> 顺序一致（0-based） */
const LIST_COLUMNS: ColumnSpec[] = [
  { label: '合同编号', index: 0, field: 'contractCode', type: 'text' },
  { label: '所属项目', index: 1, field: 'projectName', type: 'text' },
  { label: '甲方', index: 2, field: 'partyAName', type: 'text' },
  {
    label: '合同金额',
    index: 3,
    field: 'contractAmount',
    // 后端为空时前端显示占位符 '-'，跳过比对；非空按独立公式（千分位+2位）严格比对
    expect: (r) => (r.contractAmount == null || r.contractAmount === '' ? null : fmtAmount(r.contractAmount, 2, true)),
  },
  { label: '签订日期', index: 4, field: 'signingDate', type: 'date' },
  { label: '状态', index: 5, field: 'status', type: 'enum', enumMap: CONTRACT_STATUS },
]

const results: PageConsistencyResult[] = []

test.describe.serial('contract 一致性', () => {
  test('A 列表页 /contract/list 字段级一致', async ({ page }) => {
    const resp = await gotoAndCapture<PageResult>(page, '/contract/list', /\/v1\/contract\/page/)
    expect(resp.code, `接口应返回成功码，实际 message=${resp.message}`).toBe(200)
    const records = resp.data?.records ?? []
    expect(records.length, '种子数据应保证列表非空').toBeGreaterThan(0)

    await page.locator('.el-table__body-wrapper .el-table__row').first().waitFor({ timeout: 15_000 })

    const mismatches = await matchTableToRecords(page, records, LIST_COLUMNS, { softCollect: true })
    await matchPaginationTotal(page, resp.data.total)

    results.push({
      route: '/contract/list',
      title: '施工合同列表',
      api: 'GET /v1/contract/page',
      recordCount: records.length,
      mismatches,
    })
    expect(mismatches, `列表页存在 ${mismatches.length} 处不一致：\n${JSON.stringify(mismatches, null, 2)}`).toHaveLength(0)
  })

  test.afterAll(async () => {
    writeModuleReport('contract', results)
  })
})
