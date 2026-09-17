/**
 * 移动端 API 请求类型定义（字段名与后端 Controller/DTO 严格一致，后端为 Source of Truth）
 *
 * 约定：
 * - 分页查询统一 PageParams（page/size），附加过滤字段按后端 Query 参数扩展
 * - 提交体字段对齐后端实体/DTO（如 BizPaymentApply、TaskCompleteRequest），
 *   仅声明移动端实际发送的字段子集，不发明后端不存在的字段
 */

/** 通用分页查询参数 */
export interface PageParams {
  page?: number
  size?: number
}

// ==================== 审批（ApprovalController / Task*Request）====================

/** 办理（通过）请求：TaskCompleteRequest */
export interface TaskCompletePayload {
  taskId: string
  comment?: string
  variables?: Record<string, unknown>
}

/** 退回请求：TaskRejectRequest */
export interface TaskRejectPayload {
  taskId: string
  comment?: string
  targetNodeId?: string
}

// ==================== 消息（MessageController / Announcement / Notice）====================

/** 消息/公告/通知分页查询（公告页附加 status=PUBLISHED 过滤） */
export interface MessageQueryParams extends PageParams {
  status?: string
}

// ==================== 材料（InboundController / OutboundController）====================

/** 出入库明细行 */
export interface MaterialDocDetail {
  materialName: string
  specification?: string
  unit?: string
  quantity: number
  unitPrice?: number
}

/** 入库提交体（含扫码批量与单项兼容字段；无采购合同直入时 contractId 为 null） */
export interface MaterialInboundPayload {
  projectId: number
  contractId?: number | null
  inboundDate: string
  totalAmount: number
  details: MaterialDocDetail[]
  materialName?: string
  quantity?: number
  unitPrice?: number
}

/** 出库/退货提交体（outboundType: PICK 领料 / RETURN 退货，后端 String 枚举） */
export interface MaterialOutboundPayload {
  projectId: number
  outboundType: string
  outboundDate: string
  operatorName?: string
  returnType?: string
  contractId?: number | null
  details: MaterialDocDetail[]
}

// ==================== 现场（ConstructionLog / ScheduleFeedback / Inspection / Rectification）====================

/** 施工日志提交体 */
export interface ConstructionLogPayload {
  projectId: number
  logDate: string
  weather?: string
  constructionPart?: string
  todayWork?: string
  tomorrowPlan?: string
  attendanceCount?: number
  safetyStatus?: string
  remark?: string
  photos?: string[]
}

/** 进度反馈提交体 */
export interface ProgressFeedbackPayload {
  projectId: number
  feedbackDate: string
  constructionPart?: string
  plannedProgress: number
  actualProgress: number
  progressDescription?: string
  issues?: string
  remark?: string
}

/** 质量/安全检查提交体（inspectionType: QUALITY / SAFETY，后端 String 枚举） */
export interface InspectionPayload {
  projectId: number
  inspectionType: string
  inspectionContent: string
  hasProblem: number
  problemDescription?: string
  rectificationDeadline?: string
}

/** 检查项结果行 */
export interface InspectionResultItem {
  index: number
  itemName: string
  result: string
}

/** 检查结果提交体 */
export interface InspectionResultsPayload {
  results: InspectionResultItem[]
}

/** 整改提交体（attachmentIds 为逗号分隔的文件 ID 串） */
export interface RectificationPayload {
  rectificationContent: string
  attachmentIds?: string
}

// ==================== 财务（Invoice/Payment/Reimbursement/ReserveFund）====================

/** 开票申请提交体 */
export interface InvoiceApplyPayload {
  projectId: number
  contractId: number
  amount: number
  invoiceAmount: number
  invoiceType: string
  type: string
  invoiceTitle: string
  buyerName: string
  taxpayerId: string
  buyerTaxNo: string
  content?: string
  applyDate: string
  remark?: string
}

/** 回款登记提交体（未关联合同时 contractId 为 null） */
export interface PaymentReceivedPayload {
  projectId: number
  contractId: number | null
  amount: number
  receiveAmount: number
  receivedDate: string
  receiveDate: string
  payer: string
  payMethod: string
  receiveType: string
  contractName?: string
  remark?: string
}

/** 付款申请提交体 */
export interface PaymentApplyPayload {
  projectId: number
  amount: number
  paymentAmount: number
  supplierName: string
  payee: string
  payeeAccount?: string
  payeeBank?: string
  reason: string
  payMethod: string
  expectedDate: string
  paymentDate: string
  remark?: string
}

/** 项目报销提交体 */
export interface ProjectReimbursementPayload {
  projectId: number
  amount: number
  totalAmount: number
  expenseType: string
  expenseDate: string
  reimbursementDate: string
  description?: string
  invoiceCount?: number
  remark?: string
}

/** 其他费用付款提交体 */
export interface OtherPaymentPayload {
  projectId: number
  payerName: string
  paymentAmount: number
  paymentDate: string
  remark?: string
}

/** 备用金申请提交体 */
export interface ReserveFundApplyPayload {
  projectId: number
  applicant: string
  applyDate: string
  applyAmount: number
}

/** 备用金归还提交体 */
export interface ReserveFundReturnPayload {
  reserveApplyId: number
  returnAmount: number
  returnDate: string
}

/** 个人报销提交体 */
export interface PersonalReimbursementPayload {
  totalAmount: number
  reimbursementDate: string
  remark?: string
}

/** 收票登记提交体（taxRate 允许 null 表示未填） */
export interface InvoiceReceivedPayload {
  projectId: number
  supplierName: string
  invoiceAmount: number
  taxRate: number | null
  invoiceDate: string
}

// ==================== 变更事件（ChangeEventController / BizChangeEvent）====================

/** 变更事件佐证附件 */
export interface ChangeEventDoc {
  url: string
  name: string
  type: string
}

/** 变更事件登记/更新提交体 */
export interface ChangeEventPayload {
  projectId: number
  sourceType: string
  sourceRef?: string
  title: string
  description: string
  category?: string
  supportingDocs?: ChangeEventDoc[]
}
