import request, { BASE_URL } from '@/utils/request'
import type {
  PageParams,
  TaskCompletePayload,
  TaskRejectPayload,
  MessageQueryParams,
  MaterialInboundPayload,
  MaterialOutboundPayload,
  ConstructionLogPayload,
  ProgressFeedbackPayload,
  InspectionPayload,
  InspectionResultsPayload,
  RectificationPayload,
  InvoiceApplyPayload,
  PaymentReceivedPayload,
  PaymentApplyPayload,
  ProjectReimbursementPayload,
  OtherPaymentPayload,
  ReserveFundApplyPayload,
  ReserveFundReturnPayload,
  PersonalReimbursementPayload,
  InvoiceReceivedPayload,
  ChangeEventPayload
} from './types'

// 审批
export function getTodoTasks(params: PageParams) {
  return request({ url: '/v1/workflow/approval/todo', data: params })
}
export function getDoneTasks(params: PageParams) {
  return request({ url: '/v1/workflow/approval/done', data: params })
}
export function getMyInitiatedTasks(params: PageParams) {
  return request({ url: '/v1/workflow/approval/my-initiated', data: params })
}
export function completeTask(data: TaskCompletePayload) {
  return request({ url: '/v1/workflow/approval/complete', method: 'POST', data })
}
export function rejectTask(data: TaskRejectPayload) {
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
export function getProjectList(params?: PageParams) {
  // 后端真实接口：GET /api/v1/project/list（ProjectController#list，下拉选择用）
  // 裸路径 /v1/project 仅接受 POST（创建），列表查询需走 /list
  return request({ url: '/v1/project/list', data: params })
}

// 施工合同列表（分页查询，ContractController#page）
export function getContractPage(params?: { page?: number; size?: number; projectId?: number; status?: string }) {
  return request({ url: '/v1/contract/page', data: params })
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
export function getUnreadMessages(params: MessageQueryParams) {
  return request({ url: '/v1/message/msg/unread', data: params })
}
export function getAllMessages(params: MessageQueryParams) {
  return request({ url: '/v1/message/msg/all', data: params })
}
export function markMessageRead(id: number) {
  return request({ url: `/v1/message/msg/${id}/read`, method: 'PUT' })
}
export function markAllMessagesRead() {
  return request({ url: '/v1/message/msg/read-all', method: 'PUT' })
}
// 公告 / 通知
export function getAnnouncements(params: MessageQueryParams) {
  return request({ url: '/v1/message/announcement', data: params })
}
export function getNotices(params: MessageQueryParams) {
  return request({ url: '/v1/message/notice', data: params })
}

// 材料
export function getPurchaseContractPage(params?: { page?: number; size?: number; projectId?: number; contractName?: string }) {
  return request({ url: '/v1/purchase/contract/page', data: params })
}
export function getPurchaseContractDetails(contractId: number) {
  return request({ url: `/v1/purchase/contract/${contractId}/details` })
}
export function saveMaterialInbound(data: MaterialInboundPayload) {
  return request({ url: '/v1/material/inbound', method: 'POST', data })
}
export function saveMaterialOutbound(data: MaterialOutboundPayload) {
  return request({ url: '/v1/material/outbound', method: 'POST', data })
}
// 材料退货退款记录查询（MaterialRefundController，只读）
export function getMaterialRefundList(params?: PageParams) {
  return request({ url: '/v1/material/refund', data: params })
}

// 机械设备
export function getMachineLedgerPage(params?: { page?: number; size?: number; machineName?: string; machineType?: string }) {
  return request({ url: '/v1/machine/ledger/page', data: params })
}
export function getMachineWorkLogPage(params?: { page?: number; size?: number; projectId?: number; machineId?: number; machineName?: string; workDate?: string }) {
  return request({ url: '/v1/machine/work-log/page', data: params })
}
export function saveMachineWorkLog(data: { projectId: number; machineId: number; workDate: string; shiftCount: number; workQuantity?: number; oilConsumption?: number; remark?: string }) {
  return request({ url: '/v1/machine/work-log', method: 'POST', data })
}

// 劳务分包与点工
export function getLaborTeamPage(params?: { page?: number; size?: number; projectId?: number; teamName?: string; workType?: string }) {
  return request({ url: '/v1/labor/team/page', data: params })
}
export function getWorkOrderPage(params?: { page?: number; size?: number; projectId?: number; teamId?: number; status?: string }) {
  return request({ url: '/v1/labor/work-order/page', data: params })
}
export function saveWorkOrder(data: { projectId: number; teamId?: number; workerName: string; workDate: string; hours: number; hourlyRate: number; overtime?: number; overtimeRate?: number; totalAmount: number; orderType?: string; status?: string }) {
  return request({ url: '/v1/labor/work-order', method: 'POST', data })
}

// 现场
export function siteSign(data: { projectId: number; latitude: number; longitude: number; address?: string }) {
  return request({ url: '/v1/site/sign', method: 'POST', data })
}
export function getMonthlySign(params: { projectId: number; userId: number; month: string }) {
  return request({ url: '/v1/site/sign/monthly', data: params })
}
export function saveConstructionLog(data: ConstructionLogPayload) {
  return request({ url: '/v1/site/construction-log', method: 'POST', data })
}
export function saveProgressFeedback(data: ProgressFeedbackPayload) {
  return request({ url: '/v1/site/schedule/feedback', method: 'POST', data })
}
export function saveInspection(data: InspectionPayload) {
  return request({ url: '/v1/site/inspection', method: 'POST', data })
}

export function getInspectionDetail(id: number) {
  return request({ url: `/v1/site/inspection/${id}` })
}

export function submitInspectionResults(id: number, data: InspectionResultsPayload) {
  return request({ url: `/v1/site/inspection/${id}/results`, method: 'POST', data })
}

// 整改闭环
export function getRectifications(inspectionId: number) {
  return request({ url: `/v1/site/rectification/by-inspection/${inspectionId}` })
}
export function submitRectification(inspectionId: number, data: RectificationPayload) {
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
export function saveInvoiceApply(data: InvoiceApplyPayload) {
  return request({ url: '/v1/finance/invoice-apply', method: 'POST', data })
}
export function savePaymentReceived(data: PaymentReceivedPayload) {
  return request({ url: '/v1/finance/payment-received', method: 'POST', data })
}
export function savePaymentApply(data: PaymentApplyPayload) {
  return request({ url: '/v1/finance/payment-apply', method: 'POST', data })
}
export function saveReimbursement(data: ProjectReimbursementPayload) {
  return request({ url: '/v1/finance/project-reimbursement', method: 'POST', data })
}
// 项目报销提交审批（两段式：save 落 DRAFT 后链式 submit，ProjectReimbursementController）
export function submitReimbursement(id: number) {
  return request({ url: `/v1/finance/project-reimbursement/${id}/submit`, method: 'POST' })
}
// 其他费用付款（OtherPaymentController）
export function saveOtherPayment(data: OtherPaymentPayload) {
  return request({ url: '/v1/finance/other-payment', method: 'POST', data })
}
// 备用金申请分页查询（ReserveFundController，移动端归还页按 status=APPROVED 拉未还清申请）
export function getReserveFundApplyPage(params?: MessageQueryParams) {
  return request({ url: '/v1/finance/reserve-fund/apply', data: params })
}
// 备用金申请（ReserveFundController）
export function saveReserveFundApply(data: ReserveFundApplyPayload) {
  return request({ url: '/v1/finance/reserve-fund/apply', method: 'POST', data })
}
// 备用金申请提交审批（两段式：save 落 DRAFT 后链式 submit，ReserveFundController）
export function submitReserveFundApply(id: number) {
  return request({ url: `/v1/finance/reserve-fund/apply/${id}/submit`, method: 'POST' })
}
// 备用金归还（ReserveFundController）
export function saveReserveFundReturn(data: ReserveFundReturnPayload) {
  return request({ url: '/v1/finance/reserve-fund/return', method: 'POST', data })
}
// 个人报销（PersonalReimbursementController）
export function savePersonalReimbursement(data: PersonalReimbursementPayload) {
  return request({ url: '/v1/finance/personal-reimbursement', method: 'POST', data })
}
// 个人报销提交审批（两段式：save 落 DRAFT 后链式 submit）
export function submitPersonalReimbursement(id: number) {
  return request({ url: `/v1/finance/personal-reimbursement/${id}/submit`, method: 'POST' })
}
// 收票登记（后端 POST /v1/finance/invoice-received）
export function saveInvoiceReceived(data: InvoiceReceivedPayload) {
  return request({ url: '/v1/finance/invoice-received', method: 'POST', data })
}

// 项目档案
export function getProjectArchive(projectId: number) {
  return request({ url: `/v1/archive/project/${projectId}` })
}

// ==================== 变更事件（Cost Control Backbone Phase 1）====================
// 后端：ChangeEventController（/api/v1/contract/change-event）
// 现场登记变更事件 → 商务评估影响 → 审批 → 驱动 CBS 当前预算调整
// 状态机：DRAFT → ASSESSING → APPROVING → APPROVED / REJECTED（或 CANCELLED）

/** 变更事件分页查询 */
export function getChangeEventPage(params?: {
  page?: number
  size?: number
  projectId?: number
  status?: string
  sourceType?: string
  category?: string
  keyword?: string
}) {
  return request({ url: '/v1/contract/change-event/page', data: params })
}

/** 变更事件详情 */
export function getChangeEventDetail(id: number) {
  return request({ url: `/v1/contract/change-event/${id}` })
}

/** 登记变更事件（草稿）—— 现场最低成本记录「发生了什么」 */
export function saveChangeEvent(data: ChangeEventPayload) {
  return request({ url: '/v1/contract/change-event', method: 'POST', data })
}

/** 更新变更事件（仅草稿/评估中可改） */
export function updateChangeEvent(id: number, data: ChangeEventPayload) {
  return request({ url: `/v1/contract/change-event/${id}`, method: 'PUT', data })
}

/** 转入评估中（现场登记完成，交商务测算） */
export function startChangeEventAssessment(id: number) {
  return request({ url: `/v1/contract/change-event/${id}/start-assessment`, method: 'POST' })
}

/** 提交影响评估（成本 + 工期 + 理由），流转至审批中 */
export function submitChangeEventAssessment(id: number, data: {
  costDelta: number
  scheduleDelayDays?: number
  rationale: string
  assessmentNotes?: string
}) {
  return request({ url: `/v1/contract/change-event/${id}/assessment`, method: 'POST', data })
}

/** 作废变更事件（已批准的不可作废，须登记反向变更冲销） */
export function cancelChangeEvent(id: number, reason?: string) {
  return request({
    url: `/v1/contract/change-event/${id}/cancel`,
    method: 'POST',
    data: reason ? { reason } : undefined
  })
}

/** 项目下待处理变更事件数（工作台角标） */
export function getChangeEventOpenCount(projectId: number) {
  return request({ url: '/v1/contract/change-event/open-count', data: { projectId } })
}

// ==================== WBS / CBS（成本主线基础数据，移动端只读消费）====================

/** WBS 下拉列表（仅 ACTIVE 节点） */
export function getWbsSelectList(projectId: number) {
  return request({ url: `/v1/project/${projectId}/wbs/nodes/select-list` })
}

/** CBS 成本账户分页（移动端查看成本主线用） */
export function getCostAccountPage(params?: {
  page?: number
  size?: number
  projectId?: number
  costCategory?: string
  status?: string
}) {
  return request({ url: '/v1/budget/cost-account/page', data: params })
}

/** 项目成本控制看板（Project Cost 360 六维指标） */
export function getProjectCostControl(projectId: number) {
  return request({ url: `/v1/dashboard/project/${projectId}/cost-control` })
}
