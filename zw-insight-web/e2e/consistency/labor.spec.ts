/**
 * labor 劳务管理 —— 列表页前端展示 vs 后端数据 字段级一致性
 *  A 列表页 /labor/contract  GET /v1/labor/contract/page
 */
import { test } from '@playwright/test'
import {
  runListConsistency,
  writeModuleReport,
  type ColumnSpec,
  type PageConsistencyResult,
} from './consistency-helper'
import { SIMPLE_CONTRACT_STATUS } from './enum-baseline'

const LIST_COLUMNS: ColumnSpec[] = [
  { label: '合同编号', index: 0, field: 'contractCode', type: 'text' },
  { label: '合同名称', index: 1, field: 'contractName', type: 'text' },
  { label: '施工队伍', index: 2, field: 'teamName', type: 'text' },
  { label: '合同金额', index: 3, field: 'contractAmount', type: 'numeric' },
  { label: '开始日期', index: 4, field: 'startDate', type: 'date' },
  { label: '结束日期', index: 5, field: 'endDate', type: 'date' },
  { label: '状态', index: 6, field: 'status', type: 'enum', enumMap: SIMPLE_CONTRACT_STATUS },
]

const results: PageConsistencyResult[] = []

test.describe.serial('labor 一致性', () => {
  test('A 列表页 /labor/contract 字段级一致', async ({ page }) => {
    await runListConsistency(page, {
      route: '/labor/contract', title: '劳务合同列表', api: 'GET /v1/labor/contract/page',
      urlPattern: /\/v1\/labor\/contract\/page/, columns: LIST_COLUMNS,
    }, results)
  })

  test.afterAll(async () => {
    writeModuleReport('labor', results)
  })
})
