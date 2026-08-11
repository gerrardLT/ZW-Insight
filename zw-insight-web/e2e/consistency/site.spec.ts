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

// 2026-08-11 对齐页面实际结构：无施工内容/记录人列，为风力/生产记录（construction-log.vue）
const LOG_COLUMNS: ColumnSpec[] = [
  { label: '日期', index: 0, field: 'logDate', type: 'date' },
  { label: '项目名称', index: 1, field: 'projectName', type: 'text' },
  { label: '天气', index: 2, field: 'weather', type: 'text' },
  { label: '气温', index: 3, field: 'temperature', type: 'text' },
  { label: '风力', index: 4, field: 'wind', type: 'text' },
  { label: '施工人数', index: 5, field: 'workerCount', type: 'numeric' },
  { label: '生产记录', index: 6, field: 'productionRecord', type: 'text' },
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

// 2026-08-11 对齐页面实际结构（site/inspection/index.vue）：项目/检查内容/是否有问题/
// 问题描述/整改状态（RECT_MAP）/检查时间；整改状态空值渲染 '-'
const RECT_TEXT: Record<string, string> = {
  PENDING: '待整改',
  SUBMITTED: '已提交',
  APPROVED: '已通过',
  REJECTED: '已驳回',
}
const INSPECTION_COLUMNS: ColumnSpec[] = [
  { label: '项目名称', index: 0, field: 'projectName', type: 'text' },
  { label: '检查内容', index: 1, field: 'inspectionContent', type: 'text' },
  { label: '是否有问题', index: 2, field: 'hasProblem', expect: (r) => (r.hasProblem === 1 ? '有问题' : '无问题') },
  { label: '问题描述', index: 3, field: 'problemDescription', type: 'text' },
  { label: '整改状态', index: 4, field: 'rectificationStatus', expect: (r) => (r.rectificationStatus ? (RECT_TEXT[r.rectificationStatus] ?? String(r.rectificationStatus)) : '-') },
  { label: '检查时间', index: 5, field: 'createdAt', type: 'datetime' },
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
