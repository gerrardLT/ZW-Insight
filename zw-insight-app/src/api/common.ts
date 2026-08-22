import request, { BASE_URL } from '@/utils/request'

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
// 批量通过（P0 Req8）：后端 ApprovalController#batchApprove，事务内逐条 complete，
// 任一失败整体回滚并返回业务错误
export function batchApproveTasks(data: { taskIds: string[]; comment?: string }) {
  return request({ url: '/v1/workflow/approval/batch-approve', method: 'POST', data })
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

// 按材料编码查询单个材料（P0 Req6 扫码出入库）
// 后端：GET /api/v1/basedata/material/by-code（MaterialController#getByCode），未找到返回 404 语义业务错误
export function getMaterialByCode(code: string) {
  return request({ url: '/v1/basedata/material/by-code', data: { code } })
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

// 整改闭环
export function getRectifications(inspectionId: number) {
  return request({ url: `/v1/site/rectification/by-inspection/${inspectionId}` })
}
export function submitRectification(inspectionId: number, data: any) {
  return request({ url: `/v1/site/rectification/${inspectionId}/submit`, method: 'POST', data })
}
export function approveRectification(id: number) {
  return request({ url: `/v1/site/rectification/${id}/approve`, method: 'POST' })
}

/**
 * 上传整改佐证照片到 zw-file，返回文件记录 ID
 * <p>uni.uploadFile 不走 request 封装，此处单独处理鉴权与错误提示（不吞错）。</p>
 */
export function uploadRectificationPhoto(filePath: string, inspectionId: number): Promise<number> {
  const token = uni.getStorageSync('token')
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: BASE_URL + '/v1/file/upload',
      filePath,
      name: 'file',
      formData: { businessType: 'RECTIFICATION', businessId: String(inspectionId) },
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        try {
          const body = JSON.parse(res.data as string)
          if (body.code === 200 && body.data?.id != null) {
            resolve(body.data.id)
          } else if (body.code === 401) {
            uni.removeStorageSync('token')
            uni.reLaunch({ url: '/pages/login/index' })
            reject(new Error('登录已过期'))
          } else {
            uni.showToast({ title: body.message || '照片上传失败', icon: 'none' })
            reject(new Error(body.message || '照片上传失败'))
          }
        } catch {
          uni.showToast({ title: '照片上传失败', icon: 'none' })
          reject(new Error('照片上传失败'))
        }
      },
      fail: () => {
        uni.showToast({ title: '网络异常，照片上传失败', icon: 'none' })
        reject(new Error('网络异常'))
      }
    })
  })
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
// 备用金申请分页查询（ReserveFundController，移动端归还页按 status=APPROVED 拉未还清申请）
export function getReserveFundApplyPage(params?: any) {
  return request({ url: '/v1/finance/reserve-fund/apply', data: params })
}
// 备用金申请（ReserveFundController）
export function saveReserveFundApply(data: any) {
  return request({ url: '/v1/finance/reserve-fund/apply', method: 'POST', data })
}
// 备用金申请提交审批（两段式：save 落 DRAFT 后链式 submit，ReserveFundController）
export function submitReserveFundApply(id: number) {
  return request({ url: `/v1/finance/reserve-fund/apply/${id}/submit`, method: 'POST' })
}
// 备用金归还（ReserveFundController）
export function saveReserveFundReturn(data: any) {
  return request({ url: '/v1/finance/reserve-fund/return', method: 'POST', data })
}
// 个人报销（PersonalReimbursementController）
export function savePersonalReimbursement(data: any) {
  return request({ url: '/v1/finance/personal-reimbursement', method: 'POST', data })
}
// 个人报销提交审批（两段式：save 落 DRAFT 后链式 submit）
export function submitPersonalReimbursement(id: number) {
  return request({ url: `/v1/finance/personal-reimbursement/${id}/submit`, method: 'POST' })
}
// 收票登记（后端 POST /v1/finance/invoice-received）
export function saveInvoiceReceived(data: any) {
  return request({ url: '/v1/finance/invoice-received', method: 'POST', data })
}

// 项目档案
export function getProjectArchive(projectId: number) {
  return request({ url: `/v1/archive/project/${projectId}` })
}
