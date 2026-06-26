import request from '@/utils/request'

// 审批
export function getTodoTasks(params: any) {
  return request({ url: '/v1/workflow/approval/todo', data: params })
}
export function getDoneTasks(params: any) {
  return request({ url: '/v1/workflow/approval/done', data: params })
}
export function completeTask(data: any) {
  return request({ url: '/v1/workflow/approval/complete', method: 'POST', data })
}
export function rejectTask(data: any) {
  return request({ url: '/v1/workflow/approval/reject-previous', method: 'POST', data })
}

// 看板
export function getCompanyOverview() {
  return request({ url: '/v1/dashboard/company-overview' })
}
export function getProjectList(params?: any) {
  return request({ url: '/v1/project', data: params })
}

// 材料字典（基础数据）
// 后端真实接口：GET /api/v1/basedata/material（MaterialController#page）
export function getMaterialDict(params?: { page?: number; size?: number; materialName?: string; categoryId?: number }) {
  return request({ url: '/v1/basedata/material', data: params })
}

// 消息
export function getUnreadCount() {
  return request({ url: '/v1/message/msg/unread-count' })
}
export function getUnreadMessages(params: any) {
  return request({ url: '/v1/message/msg/unread', data: params })
}

// 材料
export function saveMaterialInbound(data: any) {
  return request({ url: '/v1/material/inbound', method: 'POST', data })
}
export function saveMaterialOutbound(data: any) {
  return request({ url: '/v1/material/outbound', method: 'POST', data })
}

// 现场
export function saveConstructionLog(data: any) {
  return request({ url: '/v1/site/construction-log', method: 'POST', data })
}
export function saveProgressFeedback(data: any) {
  return request({ url: '/v1/site/schedule/feedback', method: 'POST', data })
}
export function saveInspection(data: any) {
  return request({ url: '/v1/site/inspection', method: 'POST', data })
}

export function getInspectionDetail(id: number) {
  return request({ url: `/v1/site/inspection/${id}` })
}

export function submitInspectionResults(id: number, data: any) {
  return request({ url: `/v1/site/inspection/${id}/results`, method: 'POST', data })
}

// 财务
export function saveInvoiceApply(data: any) {
  return request({ url: '/v1/finance/invoice-apply', method: 'POST', data })
}
export function savePaymentReceived(data: any) {
  return request({ url: '/v1/finance/payment-received', method: 'POST', data })
}
export function savePaymentApply(data: any) {
  return request({ url: '/v1/finance/payment-apply', method: 'POST', data })
}
export function saveReimbursement(data: any) {
  return request({ url: '/v1/finance/project-reimbursement', method: 'POST', data })
}

// 项目档案
export function getProjectArchive(projectId: number) {
  return request({ url: `/v1/archive/project/${projectId}` })
}
