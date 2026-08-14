/**
 * archive 档案管理 —— 列表页前端展示 vs 后端数据 字段级一致性
 *  A 档案首页 /archive/index                 GET /v1/project/list + GET /v1/archive/project/{id}
 *  B 办公用品档案 /archive/office-supply        GET /v1/archive/office-supply
 *  C 其它支出合同档案 /archive/other-expense-contract GET /v1/archive/other-expense-contract
 *  D 其它收入合同档案 /archive/other-income-contract  GET /v1/archive/other-income-contract
 *
 * 历史发现（2026-08-11 核实已修复，commit 17ee02c）：旧版首页直接调 project/{projectId||0}
 * 致表格恒空、api/archive.ts 曾有 Promise.resolve 假实现；现页面已重构为
 * 「项目下拉（/v1/project/list）→ 选中后查 /v1/archive/project/{id} 聚合视图」，
 * api/archive.ts 全部为真实 request 调用。
 *
 * 说明：other-*-contract 金额用 formatMoney = toLocaleString('zh-CN',{minimumFractionDigits:2})，
 * 空值显示 '-'，用 expect 自定义（fmtAmount + '-' 兜底）；status 直出原始 code（无翻译）。
 */
import { test, expect } from '@playwright/test'
import {
  gotoAndCapture,
  fmtAmount,
  runListConsistency,
  finalizeModuleConsistency,
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
    // 重构后页面：首载项目下拉（GET /v1/project/list，R<Project[]> 直返数组），
    // 选中项目后调 GET /v1/archive/project/{id} 加载聚合档案视图
    const route = '/archive/index'
    const title = '档案首页'
    const resp = await gotoAndCapture<any>(page, route, /\/v1\/project\/list/)
    const api = 'GET /v1/project/list'
    if (resp.code !== 200) {
      results.push({ route, title, api, mismatches: [{ row: -1, column: '__apiError__', expected: 'code=200', actual: `code=${resp.code} message=${resp.message}` }] })
      return
    }
    const projects = Array.isArray(resp.data) ? resp.data : []
    if (projects.length === 0) {
      results.push({ route, title, api, mismatches: [{ row: -1, column: '__listBinding__', field: 'data', expected: '项目数组非空（种子数据保证）', actual: '空数组，无法验证档案加载链路' }] })
      return
    }
    // 选中第一个项目，验证档案聚合接口真实可达
    await page.locator('.el-select').first().click()
    const [archiveResp] = await Promise.all([
      page.waitForResponse(
        (r) => /\/v1\/archive\/project\/\d+/.test(r.url()) && r.request().method() === 'GET',
        { timeout: 15_000 }
      ),
      page.locator('.el-select-dropdown__item').first().click(),
    ])
    const archiveJson = await archiveResp.json()
    results.push({
      route, title,
      api: 'GET /v1/project/list + GET /v1/archive/project/{id}',
      recordCount: projects.length,
      mismatches: archiveJson.code === 200
        ? []
        : [{ row: -1, column: '__apiError__', expected: '档案聚合接口 code=200', actual: `code=${archiveJson.code} message=${archiveJson.message}` }],
    })
    expect(archiveJson.code, `选中项目后档案聚合接口应返回成功码`).toBe(200)
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
    finalizeModuleConsistency('archive', results)
  })
})
