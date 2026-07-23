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
  writeModuleReport,
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

test.describe.serial('workflow 一致性', () => {
  test('审批待办 /workflow/approval', async ({ page }) => {
    const columns: ColumnSpec[] = [
      // index 0 为 selection 列（勾选框，无文本）
      { label: '任务名称', index: 1, field: 'taskName' },
      { label: '业务类型', index: 2, field: 'businessType' },
      { label: '发起人', index: 3, field: 'initiator' },
      { label: '创建时间', index: 4, field: 'createTime', type: 'datetime' },
    ]
    await runListConsistency(page, {
      route: '/workflow/approval', title: '审批待办', api: 'GET /v1/workflow/approval/todo',
      urlPattern: /\/v1\/workflow\/approval\/todo/, columns,
    }, results)
  })

  test('流程定义 /workflow/process (直出数组)', async ({ page }) => {
    const columns: ColumnSpec[] = [
      { label: '流程名称', index: 0, field: 'name' },
      { label: '流程标识', index: 1, field: 'key' },
      { label: '版本', index: 2, expect: (r) => `V${r.version}` },
      { label: '部署时间', index: 3, field: 'deploymentTime', type: 'datetime' },
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
    writeModuleReport('workflow', results)
  })
})
