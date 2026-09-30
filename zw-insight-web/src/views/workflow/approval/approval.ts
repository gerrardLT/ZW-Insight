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
