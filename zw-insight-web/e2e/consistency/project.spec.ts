/**
 * project 项目管理 —— 前端展示 vs 后端数据 字段级一致性
 *
 * 覆盖页面：
 *  A 列表页  /project/list        GET /v1/project/page
 *  B 详情页  /project/detail/:id  GET /v1/project/{id}
 *  C 编辑回显 /project/edit/:id    GET /v1/project/{id}
 *
 * 方法论：抓取页面真实调用的后端 JSON，再把 DOM 渲染值与响应字段逐一断言。
 * 状态枚举用独立基线 PROJECT_STATUS 校验前端翻译；日期用独立公式。
 */
import { test, expect } from '@playwright/test'
import {
  gotoAndCapture,
  matchTableToRecords,
  matchPaginationTotal,
  matchDetailFields,
  getFormInputValue,
  writeModuleReport,
  type ColumnSpec,
  type PageConsistencyResult,
  type PageResult,
} from './consistency-helper'
import { PROJECT_STATUS } from './enum-baseline'

/** 列表页列规格（index 与 project/index.vue 的 <el-table-column> 顺序一致，0-based） */
const LIST_COLUMNS: ColumnSpec[] = [
  { label: '项目编号', index: 0, field: 'projectCode', type: 'text' },
  { label: '项目名称', index: 1, field: 'projectName', type: 'text' },
  { label: '项目性质', index: 2, field: 'projectNature', type: 'text' },
  { label: '项目类型', index: 3, field: 'projectType', type: 'text' },
  { label: '业主单位', index: 4, field: 'ownerCompanyName', type: 'text' },
  { label: '签约公司', index: 5, field: 'signingCompanyName', type: 'text' },
  { label: '状态', index: 6, field: 'status', type: 'enum', enumMap: PROJECT_STATUS },
  { label: '创建时间', index: 7, field: 'createdAt', type: 'datetime' },
  // index 8 = 操作列，不比对
]

/** 详情页字段规格（project/detail.vue 的 el-descriptions） */
const DETAIL_FIELDS = {
  projectCode: { label: '项目编号', type: 'text' as const },
  projectName: { label: '项目名称', type: 'text' as const },
  projectNature: { label: '项目性质', type: 'text' as const },
  projectType: { label: '项目类型', type: 'text' as const },
  ownerCompanyName: { label: '业主单位', type: 'text' as const },
  signingCompanyName: { label: '签约公司', type: 'text' as const },
  projectAddress: { label: '项目地址', type: 'text' as const },
  contactName: { label: '联系人', type: 'text' as const },
  contactPhone: { label: '联系电话', type: 'text' as const },
  status: { label: '状态', type: 'enum' as const, enumMap: PROJECT_STATUS },
}

const results: PageConsistencyResult[] = []
/** 详情/编辑用例复用的真实项目 id（从列表首条取） */
let sampleId: number | undefined

// serial：详情/编辑用例复用列表页取到的 sampleId，需与列表页同 worker 顺序执行
test.describe.serial('project 一致性', () => {
  test('A 列表页 /project/list 字段级一致', async ({ page }) => {
    const resp = await gotoAndCapture<PageResult>(page, '/project/list', /\/v1\/project\/page/)
    expect(resp.code, `接口应返回成功码，实际 message=${resp.message}`).toBe(200)
    const records = resp.data?.records ?? []
    expect(records.length, '种子数据应保证列表非空').toBeGreaterThan(0)
    sampleId = records[0]?.id

    // 等首行渲染，避免抓包早于 DOM 更新
    await page.locator('.el-table__body-wrapper .el-table__row').first().waitFor({ timeout: 15_000 })

    const mismatches = await matchTableToRecords(page, records, LIST_COLUMNS, { softCollect: true })
    await matchPaginationTotal(page, resp.data.total)

    results.push({
      route: '/project/list',
      title: '项目列表',
      api: 'GET /v1/project/page',
      recordCount: records.length,
      mismatches,
    })
    expect(mismatches, `列表页存在 ${mismatches.length} 处不一致：\n${JSON.stringify(mismatches, null, 2)}`).toHaveLength(0)
  })

  test('B 详情页 /project/detail/:id 字段级一致', async ({ page }) => {
    test.skip(!sampleId, '列表用例未取到样本 id')
    const resp = await gotoAndCapture<any>(page, `/project/detail/${sampleId}`, /\/v1\/project\/\d+/)
    expect(resp.code).toBe(200)
    const data = resp.data ?? {}

    await page.locator('.el-descriptions').first().waitFor({ timeout: 15_000 })

    const mismatches = await matchDetailFields(page, data, DETAIL_FIELDS, { softCollect: true })

    results.push({
      route: '/project/detail/:id',
      title: '项目详情',
      api: 'GET /v1/project/{id}',
      mismatches,
    })
    expect(mismatches, `详情页存在 ${mismatches.length} 处不一致：\n${JSON.stringify(mismatches, null, 2)}`).toHaveLength(0)
  })

  test('C 编辑回显 /project/edit/:id 表单值一致', async ({ page }) => {
    test.skip(!sampleId, '列表用例未取到样本 id')
    const resp = await gotoAndCapture<any>(page, `/project/edit/${sampleId}`, /\/v1\/project\/\d+/)
    expect(resp.code).toBe(200)
    const data = resp.data ?? {}

    await page.locator('input[placeholder*="项目名称"]').first().waitFor({ timeout: 15_000 })

    const mismatches: PageConsistencyResult['mismatches'] = []
    // 表单文本 input 回显：placeholder -> 字段
    const inputChecks: Array<{ ph: string; field: string; label: string }> = [
      { ph: '项目名称', field: 'projectName', label: '项目名称' },
      { ph: '系统自动生成', field: 'projectCode', label: '项目编号' },
      { ph: '联系人', field: 'contactName', label: '联系人' },
      { ph: '联系电话', field: 'contactPhone', label: '联系电话' },
      { ph: '项目地址', field: 'projectAddress', label: '项目地址' },
    ]
    for (const c of inputChecks) {
      const expected = data[c.field] == null ? '' : String(data[c.field])
      if (expected === '') continue
      const actual = await getFormInputValue(page, c.ph)
      if (actual !== expected) {
        mismatches.push({ row: -1, column: c.label, field: c.field, expected, actual })
      }
    }

    results.push({
      route: '/project/edit/:id',
      title: '项目编辑回显',
      api: 'GET /v1/project/{id}',
      mismatches,
    })
    expect(mismatches, `编辑回显存在 ${mismatches.length} 处不一致：\n${JSON.stringify(mismatches, null, 2)}`).toHaveLength(0)
  })

  test.afterAll(async () => {
    writeModuleReport('project', results)
  })
})
