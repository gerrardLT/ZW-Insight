/**
 * workflow 工作流模块 —— 前端展示 vs 后端数据 一致性
 *  分页列表页（records/total）：
 *   - 审批待办     /workflow/approval  GET /v1/workflow/approval/todo (pageNum/pageSize, 默认 todo 页签)
 *   - 回滚日志     /workflow/rollback  GET /v1/workflow/rollback/logs (page/size)
 *  非分页「直出数组」列表页（data 为数组）：
 *   - 流程定义     /workflow/process   GET /v1/workflow/process
 *
 * ⚠ 分页参数不一致：approval 用 pageNum/pageSize，rollback 用 page/size；process 无分页。
 */
import { test } from '@playwright/test'
import {
  runListConsistency,
  runFlatListConsistency,
  finalizeModuleConsistency,
  type PageConsistencyResult,
  type ColumnSpec,
} from './consistency-helper'

const results: PageConsistencyResult[] = []

// —— 回滚日志：业务类型 / 状态映射（与页面 bizTypeNameMap / statusNameMap 一致） ——
const ROLLBACK_BIZ: Record<string, string> = {
  LABOR_SETTLEMENT: '劳务结算', MACHINE_SETTLEMENT: '机械结算', PURCHASE_SETTLEMENT: '采购结算',
  SUBCONTRACT_SETTLEMENT: '分包结算', PAYMENT_APPLY: '付款申请', INVOICE_APPLY: '开票申请',
}
const ROLLBACK_STATUS: Record<number, string> = { 0: '回滚成功', 1: '回滚失败', 2: '冲突待确认', 3: '重试中' }

// —— 审批待办：业务类型编码→中文名映射（与 views/workflow/approval/approval.ts BUSINESS_TYPE_NAMES 一致） ——
const APPROVAL_BIZ: Record<string, string> = {
  CONSTRUCTION_CONTRACT: '施工合同', CHANGE_VISA: '变更签证', FINAL_SETTLEMENT: '竣工结算', OUTPUT_REPORT: '产值报告',
  BUDGET_CHANGE: '预算变更', INVOICE_APPLY: '开票申请', PAYMENT_APPLY: '付款申请', PROJECT_REIMBURSEMENT: '项目报销',
  PERSONAL_REIMBURSEMENT: '个人报销', RETENTION_RETURN: '质保金退还', RESERVE_FUND_APPLY: '备用金申请',
  FUND_TRANSFER: '资金调拨', PROJECT_SETTLEMENT: '项目结算', PURCHASE_CONTRACT: '采购合同', PURCHASE_SETTLEMENT: '采购结算',
  LABOR_CONTRACT: '劳务合同', LABOR_OUTPUT: '劳务产值', LABOR_SETTLEMENT: '劳务结算', LABOR_PAYROLL: '劳务工资',
  LABOR_REWARD_PUNISH: '劳务奖惩', MACHINE_CONTRACT: '机械合同', machine_settlement: '机械结算',
  MATERIAL_TRANSFER: '材料调拨', MATERIAL_REFUND: '材料退库', COMPLETION_ACCEPTANCE: '竣工验收', DEPOSIT_APPLY: '保证金申请',
  PROJECT_CLOSE: '项目结项', PROJECT_FILING: '项目报备', PROJECT_TERMINATE: '项目终止', ENTRY_APPLY: '入职申请',
  REGULAR_APPLY: '转正申请', TRANSFER_APPLY: '调动申请', RESIGN_APPLY: '离职申请', SEAL_APPLY: '用印申请', VEHICLE_APPLY: '车辆申请',
}
/** 业务类型编码转中文名；未收录的原样显示（复刻 approval.ts businessTypeName） */
function approvalBizName(code: unknown): string {
  const key = String(code ?? '')
  return APPROVAL_BIZ[key] || key || '未知类型'
}
/** 后端 UTC ISO 串按北京时间显示到分钟（复刻 approval.ts formatTime） */
function approvalFormatTime(value: unknown): string {
  if (!value) return '未提供'
  const d = new Date(String(value))
  if (Number.isNaN(d.getTime())) return String(value)
  const p = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).formatToParts(d)
  const g = (t: string) => p.find((x) => x.type === t)?.value || ''
  return `${g('year')}-${g('month')}-${g('day')} ${g('hour')}:${g('minute')}`
}

test.describe.serial('workflow 一致性', () => {
  test('审批待办 /workflow/approval', async ({ page }) => {
    const columns: ColumnSpec[] = [
      // index 0 为 selection 列（勾选框，无文本）
      { label: '任务名称', index: 1, field: 'taskName' },
      // 业务类型列 formatter=businessTypeName（index.vue:17）
      { label: '业务类型', index: 2, field: 'businessType', expect: (r) => approvalBizName(r.businessType) },
      // 发起人列 prop=startUserName，回退 initiator，再回退 '—'（index.vue:18）
      { label: '发起人', index: 3, expect: (r) => r.startUserName || r.initiator || '—' },
      // 创建时间列 formatter=formatTime（index.vue:19）
      { label: '创建时间', index: 4, field: 'createTime', expect: (r) => approvalFormatTime(r.createTime) },
    ]
    await runListConsistency(page, {
      route: '/workflow/approval', title: '审批待办', api: 'GET /v1/workflow/approval/todo',
      urlPattern: /\/v1\/workflow\/approval\/todo/, columns,
    }, results)
  })

  test('流程定义 /workflow/process (直出数组)', async ({ page }) => {
    const columns: ColumnSpec[] = [
      // 页面 normalizeItem 映射原始字段：processName/processKey/versionNum/createdAt（process/index.vue:115-125）
      { label: '流程名称', index: 0, expect: (r) => r.processName || r.name || r.processKey || '-' },
      { label: '流程标识', index: 1, expect: (r) => r.processKey || r.key || '-' },
      { label: '版本', index: 2, expect: (r) => `V${r.versionNum ?? r.version ?? 0}` },
      { label: '部署时间', index: 3, expect: (r) => r.createdAt || r.deploymentTime || '-' },
    ]
    await runFlatListConsistency(page, {
      route: '/workflow/process', title: '流程定义', api: 'GET /v1/workflow/process',
      urlPattern: /\/v1\/workflow\/process(\?|$)/, columns,
    }, results)
  })

  test('回滚日志 /workflow/rollback', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '流程实例ID', index: 0, field: 'workflowInstanceId' },
      { label: '业务类型', index: 1, expect: (r) => ROLLBACK_BIZ[r.bizType] || r.bizType },
      { label: '业务单据ID', index: 2, field: 'bizId' },
      { label: '状态', index: 3, expect: (r) => ROLLBACK_STATUS[r.rollbackStatus] || '-' },
      { label: '重试次数', index: 4, field: 'retryCount', type: 'numeric' },
      { label: '错误信息', index: 5, field: 'errorMessage' },
      { label: '创建时间', index: 6, field: 'createdAt', type: 'datetime' },
      { label: '更新时间', index: 7, field: 'updatedAt', type: 'datetime' },
    ]
    await runListConsistency(page, {
      route: '/workflow/rollback', title: '回滚日志', api: 'GET /v1/workflow/rollback/logs',
      urlPattern: /\/v1\/workflow\/rollback\/logs/, columns,
    }, results)
  })

  test.afterAll(async () => {
    finalizeModuleConsistency('workflow', results)
  })
})
