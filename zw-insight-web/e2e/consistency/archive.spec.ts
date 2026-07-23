/**
 * archive 档案管理 —— 列表页前端展示 vs 后端数据 字段级一致性
 *  A 档案首页 /archive/index                 GET /v1/archive/project/0  (⚠ 见下方发现)
 *  B 办公用品档案 /archive/office-supply        GET /v1/archive/office-supply
 *  C 其它支出合同档案 /archive/other-expense-contract GET /v1/archive/other-expense-contract
 *  D 其它收入合同档案 /archive/other-income-contract  GET /v1/archive/other-income-contract
 *
 * ⚠ 发现（archive/index.vue）：
 *  - 列表调用 getArchivePage → GET /v1/archive/project/{projectId||0}，该接口返回「单项目档案聚合」
 *    对象而非分页结构 {records,total}，页面 res.data?.records 恒为 undefined → 表格永远为空；
 *  - 顶部 5 个分类 tab / 档案名称 / 归档日期查询条件在该接口下完全不生效（接口只吃 projectId）；
 *  - createArchive/updateArchive/deleteArchive 在 api/archive.ts 中为 Promise.resolve({code:200}) 假实现，
 *    违反「真实接口、无静默 fallback」约定。以上作为一致性发现记入报告。
 *
 * 说明：other-*-contract 金额用 formatMoney = toLocaleString('zh-CN',{minimumFractionDigits:2})，
 * 空值显示 '-'，用 expect 自定义（fmtAmount + '-' 兜底）；status 直出原始 code（无翻译）。
 */
import { test } from '@playwright/test'
import {
  gotoAndCapture,
  fmtAmount,
  runListConsistency,
  writeModuleReport,
  type ColumnSpec,
  type PageConsistencyResult,
} from './consistency-helper'

const OFFICE_SUPPLY_COLUMNS: ColumnSpec[] = [
  { label: '用品名称', index: 0, field: 'supplyName', type: 'text' },
  { label: '当前库存', index: 1, field: 'currentStock', type: 'numeric' },
  { label: '累计入库', index: 2, field: 'totalInbound', type: 'numeric' },
  { label: '累计领用', index: 3, field: 'totalIssued', type: 'numeric' },
  { label: '最近入库日期', index: 4, field: 'lastInboundDate', type: 'date' },
]

const CONTRACT_COLUMNS: ColumnSpec[] = [
  { label: '合同编号', index: 0, field: 'contractCode', type: 'text' },
  { label: '合同名称', index: 1, field: 'contractName', type: 'text' },
  { label: '关联项目', index: 2, field: 'projectName', type: 'text' },
  { label: '金额', index: 3, field: 'contractAmount', expect: (r) => (r.contractAmount == null ? '-' : fmtAmount(r.contractAmount, 2, true)) },
  { label: '签约日期', index: 4, field: 'signingDate', type: 'date' },
  { label: '状态', index: 5, field: 'status', type: 'text' },
]

const results: PageConsistencyResult[] = []

test.describe.serial('archive 一致性', () => {
  test('A 档案首页 /archive/index 接口结构与列表绑定一致性', async ({ page }) => {
    const resp = await gotoAndCapture<any>(page, '/archive/index', /\/v1\/archive\/project\/0/)
    const route = '/archive/index'
    const title = '档案首页列表'
    const api = 'GET /v1/archive/project/0'
    if (resp.code !== 200) {
      results.push({ route, title, api, mismatches: [{ row: -1, column: '__apiError__', expected: 'code=200', actual: `code=${resp.code} message=${resp.message}` }] })
      return
    }
    // 列表页绑定 res.data.records，但该接口返回的是单项目档案聚合对象，不含 records 数组
    const hasRecords = Array.isArray(resp.data?.records)
    if (!hasRecords) {
      results.push({
        route, title, api,
        mismatches: [{
          row: -1,
          column: '__listBinding__',
          field: 'data.records',
          expected: 'PageResult(含 records 数组)',
          actual: `聚合对象(无 records)，页面表格恒为空；keys=${resp.data ? Object.keys(resp.data).join(',') : 'null'}`,
        }],
      })
    }
  })

  test('B 办公用品档案 /archive/office-supply 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/archive/office-supply', title: '办公用品档案列表', api: 'GET /v1/archive/office-supply', urlPattern: /\/v1\/archive\/office-supply(\?|$)/, columns: OFFICE_SUPPLY_COLUMNS }, results)
  })

  test('C 其它支出合同档案 /archive/other-expense-contract 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/archive/other-expense-contract', title: '其它支出合同档案列表', api: 'GET /v1/archive/other-expense-contract', urlPattern: /\/v1\/archive\/other-expense-contract(\?|$)/, columns: CONTRACT_COLUMNS }, results)
  })

  test('D 其它收入合同档案 /archive/other-income-contract 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/archive/other-income-contract', title: '其它收入合同档案列表', api: 'GET /v1/archive/other-income-contract', urlPattern: /\/v1\/archive\/other-income-contract(\?|$)/, columns: CONTRACT_COLUMNS }, results)
  })

  test.afterAll(async () => {
    writeModuleReport('archive', results)
  })
})
