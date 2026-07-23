/**
 * subcontract 分包管理 —— 列表页前端展示 vs 后端数据 字段级一致性
 *  A 列表页 /subcontract/contract  GET /v1/subcontract/contract/page
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
  { label: '分包方', index: 2, field: 'subcontractor', type: 'text' },
  { label: '合同金额', index: 3, field: 'contractAmount', type: 'numeric' },
  { label: '所属项目', index: 4, field: 'projectName', type: 'text' },
  { label: '签订日期', index: 5, field: 'signingDate', type: 'date' },
  { label: '状态', index: 6, field: 'status', type: 'enum', enumMap: SIMPLE_CONTRACT_STATUS },
]

const results: PageConsistencyResult[] = []

test.describe.serial('subcontract 一致性', () => {
  test('A 列表页 /subcontract/contract 字段级一致', async ({ page }) => {
    await runListConsistency(page, {
      route: '/subcontract/contract', title: '分包合同列表', api: 'GET /v1/subcontract/contract/page',
      urlPattern: /\/v1\/subcontract\/contract\/page/, columns: LIST_COLUMNS,
    }, results)
  })

  test.afterAll(async () => {
    writeModuleReport('subcontract', results)
  })
})
