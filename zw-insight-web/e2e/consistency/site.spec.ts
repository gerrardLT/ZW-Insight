/**
 * site 现场管理 —— 列表页前端展示 vs 后端数据 字段级一致性
 *  A 施工日志 /site/construction-log  GET /v1/site/construction-log/page
 *  B 进度计划 /site/schedule          GET /v1/site/schedule/page
 *  C 质量检查 /site/inspection         GET /v1/site/inspection/page (inspectionType=quality)
 *
 * 说明：schedule.progress 用 el-progress 渲染，单元格文本形如 "50%"；
 * status / result 为内联三元翻译，无后端字典，使用 expect 自定义期望函数。
 */
import { test } from '@playwright/test'
import {
  runListConsistency,
  writeModuleReport,
  type ColumnSpec,
  type PageConsistencyResult,
} from './consistency-helper'

const LOG_COLUMNS: ColumnSpec[] = [
  { label: '日期', index: 0, field: 'logDate', type: 'date' },
  { label: '项目名称', index: 1, field: 'projectName', type: 'text' },
  { label: '天气', index: 2, field: 'weather', type: 'text' },
  { label: '气温', index: 3, field: 'temperature', type: 'text' },
  { label: '施工内容', index: 4, field: 'workContent', type: 'text' },
  { label: '出工人数', index: 5, field: 'workerCount', type: 'numeric' },
  { label: '记录人', index: 6, field: 'recorder', type: 'text' },
]

const SCHEDULE_COLUMNS: ColumnSpec[] = [
  { label: '任务名称', index: 0, field: 'taskName', type: 'text' },
  { label: '所属项目', index: 1, field: 'projectName', type: 'text' },
  { label: '计划开始', index: 2, field: 'planStartDate', type: 'date' },
  { label: '计划完成', index: 3, field: 'planEndDate', type: 'date' },
  { label: '完成进度', index: 4, field: 'progress', expect: (r) => `${r.progress || 0}%` },
  { label: '负责人', index: 5, field: 'responsible', type: 'text' },
  { label: '状态', index: 6, field: 'status', expect: (r) => (r.status === 'COMPLETED' ? '已完成' : r.status === 'DELAYED' ? '滞后' : '进行中') },
]

const INSPECTION_COLUMNS: ColumnSpec[] = [
  { label: '检查编号', index: 0, field: 'inspectionNo', type: 'text' },
  { label: '项目名称', index: 1, field: 'projectName', type: 'text' },
  { label: '检查项', index: 2, field: 'checkItem', type: 'text' },
  { label: '检查人', index: 3, field: 'inspector', type: 'text' },
  { label: '检查日期', index: 4, field: 'inspectionDate', type: 'date' },
  { label: '结果', index: 5, field: 'result', expect: (r) => (r.result === 'PASS' ? '合格' : '不合格') },
  { label: '整改说明', index: 6, field: 'remark', type: 'text' },
]

const results: PageConsistencyResult[] = []

test.describe.serial('site 一致性', () => {
  test('A 施工日志 /site/construction-log 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/site/construction-log', title: '施工日志列表', api: 'GET /v1/site/construction-log/page', urlPattern: /\/v1\/site\/construction-log\/page/, columns: LOG_COLUMNS }, results)
  })

  test('B 进度计划 /site/schedule 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/site/schedule', title: '进度计划列表', api: 'GET /v1/site/schedule/page', urlPattern: /\/v1\/site\/schedule\/page/, columns: SCHEDULE_COLUMNS }, results)
  })

  test('C 质量检查 /site/inspection 字段级一致', async ({ page }) => {
    await runListConsistency(page, { route: '/site/inspection', title: '质量检查列表', api: 'GET /v1/site/inspection/page', urlPattern: /\/v1\/site\/inspection\/page/, columns: INSPECTION_COLUMNS }, results)
  })

  test.afterAll(async () => {
    writeModuleReport('site', results)
  })
})
