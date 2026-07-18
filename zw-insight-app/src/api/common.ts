import request from '@/utils/request'

// 审批
export function getTodoTasks(params: any) {
  return request({ url: '/v1/workflow/approval/todo', data: params })
}
export function getDoneTasks(params: any) {
  return request({ url: '/v1/workflow/approval/done', data: params })
}
export function getMyInitiatedTasks(params: any) {
  return request({ url: '/v1/workflow/approval/my-initiated', data: params })
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
// 项目预算执行（DashboardController#getBudgetExecution，projectId 必填，startDate/endDate 可选）
export function getBudgetExecution(params: { projectId: number; startDate?: string; endDate?: string }) {
  return request({ url: '/v1/dashboard/budget-execution', data: params })
}
// 应收账款监控（DashboardController#getReceivableMonitor）
export function getReceivableMonitor() {
  return request({ url: '/v1/dashboard/receivable-monitor' })
}
// 供应商账款监控（DashboardController#getSupplierPayableMonitor，projectName/supplierName 可选）
export function getSupplierPayableMonitor(params?: { projectName?: string; supplierName?: string }) {
  return request({ url: '/v1/dashboard/supplier-payable', data: params })
}
// 投标分析（DashboardController#getTenderAnalysis）
export function getTenderAnalysis() {
  return request({ url: '/v1/dashboard/tender-analysis' })
}
// 库存分析（DashboardController#getInventoryAnalysis）
export function getInventoryAnalysis() {
  return request({ url: '/v1/dashboard/inventory-analysis' })
}
// 项目看板（DashboardController#getProjectDashboard，进度+质安+资金一屏聚合）
export function getProjectDashboard(projectId: number) {
  return request({ url: `/v1/dashboard/project/${projectId}` })
}
export function getProjectList(params?: any) {
  // 后端真实接口：GET /api/v1/project/list（ProjectController#list，下拉选择用）
  // 裸路径 /v1/project 仅接受 POST（创建），列表查询需走 /list
  return request({ url: '/v1/project/list', data: params })
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
export function getAllMessages(params: any) {
  return request({ url: '/v1/message/msg/all', data: params })
}
export function markMessageRead(id: number) {
  return request({ url: `/v1/message/msg/${id}/read`, method: 'PUT' })
}
export function markAllMessagesRead() {
  return request({ url: '/v1/message/msg/read-all', method: 'PUT' })
}
// 公告 / 通知
export function getAnnouncements(params: any) {
  return request({ url: '/v1/message/announcement', data: params })
}
export function getNotices(params: any) {
  return request({ url: '/v1/message/notice', data: params })
}

// 材料
export function saveMaterialInbound(data: any) {
  return request({ url: '/v1/material/inbound', method: 'POST', data })
}
export function saveMaterialOutbound(data: any) {
  return request({ url: '/v1/material/outbound', method: 'POST', data })
}
// 材料退货退款记录查询（MaterialRefundController，只读）
export function getMaterialRefundList(params?: any) {
  return request({ url: '/v1/material/refund', data: params })
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
// 其他费用付款（OtherPaymentController）
export function saveOtherPayment(data: any) {
  return request({ url: '/v1/finance/other-payment', method: 'POST', data })
}
// 备用金申请（ReserveFundController）
export function saveReserveFundApply(data: any) {
  return request({ url: '/v1/finance/reserve-fund/apply', method: 'POST', data })
}
// 备用金归还（ReserveFundController）
export function saveReserveFundReturn(data: any) {
  return request({ url: '/v1/finance/reserve-fund/return', method: 'POST', data })
}
// 个人报销（PersonalReimbursementController）
export function savePersonalReimbursement(data: any) {
  return request({ url: '/v1/finance/personal-reimbursement', method: 'POST', data })
}
// 收票登记（后端 POST /v1/finance/invoice-received）
export function saveInvoiceReceived(data: any) {
  return request({ url: '/v1/finance/invoice-received', method: 'POST', data })
}

// 项目档案
export function getProjectArchive(projectId: number) {
  return request({ url: `/v1/archive/project/${projectId}` })
}
