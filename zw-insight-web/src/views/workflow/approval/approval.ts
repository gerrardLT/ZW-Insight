export const PAYMENT_TYPE = 'PAYMENT_APPLY'
export function validId(value: unknown): boolean {
  return (typeof value === 'string' || typeof value === 'number') && /^[1-9]\d*$/.test(String(value))
}
export function approvalBlock(detail: any, taskId: string, payment?: any): string {
  if (!detail || !taskId || detail.taskId !== taskId || !['pending', 'done'].includes(detail.status) || !detail.businessType || !validId(detail.businessId)) return '审批详情缺失或身份不一致，请重新加载'
  if (detail.status !== 'pending') return '该任务已办结，不可操作'
  if (detail.businessType === PAYMENT_TYPE) {
    if (!payment || String(payment.id) !== String(detail.businessId)) return '付款源单未成功加载或ID不一致，不可审批'
    if (!validId(payment.projectId)) return '付款源单项目ID无效，不可审批'
    if ((typeof payment.paymentAmount !== 'number' && typeof payment.paymentAmount !== 'string') || !/^\d+(\.\d+)?$/.test(String(payment.paymentAmount)) || !Number.isFinite(Number(payment.paymentAmount)) || Number(payment.paymentAmount) <= 0) return '付款源单金额缺失或无效，金额必须大于0'
    if (payment.status !== 'SUBMITTED' || !detail.processInstanceId || payment.workflowInstanceId !== detail.processInstanceId) return '付款源单状态或流程不一致，请刷新核对'
  }
  return ''
}
export function batchBlock(details: any[]): string {
  if (!details.length || details.some(d => !d.businessType)) return '请选择有效任务'
  if (new Set(details.map(d => d.businessType)).size !== 1) return '批量确认仅限同一业务类型'
  // ponytail: 付款本轮逐单确认；待服务端阈值配置及审计契约落地后再开放批量。
  if (details[0].businessType === PAYMENT_TYPE) return '付款申请须逐单核对源单并确认，不支持批量通过'
  return ''
}
export function money(value: unknown): string {
  if ((typeof value !== 'number' && typeof value !== 'string') || String(value).trim() === '' || !Number.isFinite(Number(value))) return '未提供'
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(Number(value))
}
const BUSINESS_TYPE_NAMES: Record<string, string> = {
  CONSTRUCTION_CONTRACT: '施工合同', CHANGE_VISA: '变更签证', FINAL_SETTLEMENT: '竣工结算', OUTPUT_REPORT: '产值报告',
  BUDGET_CHANGE: '预算变更', INVOICE_APPLY: '开票申请', PAYMENT_APPLY: '付款申请', PROJECT_REIMBURSEMENT: '项目报销',
  PERSONAL_REIMBURSEMENT: '个人报销', RETENTION_RETURN: '质保金退还', RESERVE_FUND_APPLY: '备用金申请',
  FUND_TRANSFER: '资金调拨', PROJECT_SETTLEMENT: '项目结算', PURCHASE_CONTRACT: '采购合同', PURCHASE_SETTLEMENT: '采购结算',
  LABOR_CONTRACT: '劳务合同', LABOR_OUTPUT: '劳务产值', LABOR_SETTLEMENT: '劳务结算', LABOR_PAYROLL: '劳务工资',
  LABOR_REWARD_PUNISH: '劳务奖惩', MACHINE_CONTRACT: '机械合同', machine_settlement: '机械结算',
  MATERIAL_TRANSFER: '材料调拨', MATERIAL_REFUND: '材料退库', COMPLETION_ACCEPTANCE: '竣工验收', DEPOSIT_APPLY: '保证金申请',
  PROJECT_CLOSE: '项目结项', PROJECT_FILING: '项目报备', PROJECT_TERMINATE: '项目终止', ENTRY_APPLY: '入职申请',
  REGULAR_APPLY: '转正申请', TRANSFER_APPLY: '调动申请', RESIGN_APPLY: '离职申请', SEAL_APPLY: '用印申请', VEHICLE_APPLY: '车辆申请'
}
/** 业务类型编码转中文名；未收录的原样显示，避免吞掉信息 */
export function businessTypeName(code: unknown): string {
  const key = String(code ?? '')
  return BUSINESS_TYPE_NAMES[key] || key || '未知类型'
}
/** 后端时间为 UTC 带偏移的 ISO 串，统一按北京时间显示到分钟 */
export function formatTime(value: unknown): string {
  if (!value) return '未提供'
  const d = new Date(String(value))
  if (Number.isNaN(d.getTime())) return String(value)
  const p = new Intl.DateTimeFormat('zh-CN', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).formatToParts(d)
  const g = (t: string) => p.find(x => x.type === t)?.value || ''
  return `${g('year')}-${g('month')}-${g('day')} ${g('hour')}:${g('minute')}`
}
