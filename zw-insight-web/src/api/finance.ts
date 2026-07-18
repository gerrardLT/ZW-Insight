import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'
import type {
  InvoiceApply,
  InvoiceApplyCreateRequest,
  InvoiceApplyPageQuery,
  PaymentReceived,
  PaymentApply,
  PaymentApplyCreateRequest,
  InvoiceReceived,
  InvoiceSummary,
  InvoiceSummaryQuery,
  OtherPayment,
  ProjectReimbursement,
  ReserveFundApply,
  ReserveFundReturn,
  PersonalReimbursement,
  RetentionMoney,
  RetentionReturn
} from '@/types/finance'

// ======================== 开票申请 ========================
export function getInvoiceApplyPage(params: InvoiceApplyPageQuery) {
  return request.get<R<PageResult<InvoiceApply>>>('/v1/finance/invoice-apply/page', { params })
}

export function getInvoiceApplyDetail(id: number) {
  return request.get<R<InvoiceApply>>(`/v1/finance/invoice-apply/${id}`)
}

export function createInvoiceApply(data: InvoiceApplyCreateRequest) {
  return request.post<R<void>>('/v1/finance/invoice-apply', data)
}

export function updateInvoiceApply(data: InvoiceApplyCreateRequest & { id: number }) {
  return request.put<R<void>>(`/v1/finance/invoice-apply/${data.id}`, data)
}

export function deleteInvoiceApply(id: number) {
  return request.delete<R<void>>(`/v1/finance/invoice-apply/${id}`)
}

export function submitInvoiceApply(id: number) {
  return request.put<R<void>>(`/v1/finance/invoice-apply/${id}/submit`)
}

// ======================== 收票登记 ========================
export function getInvoiceReceivedPage(params: { page?: number; size?: number; projectId?: number }) {
  return request.get<R<PageResult<InvoiceReceived>>>('/v1/finance/invoice-received', { params })
}

export function createInvoiceReceived(data: Partial<InvoiceReceived>) {
  return request.post<R<void>>('/v1/finance/invoice-received', data)
}

// ======================== 发票明细汇总 ========================
export function getInvoiceSummary(params: InvoiceSummaryQuery) {
  return request.get<R<InvoiceSummary[]>>('/v1/finance/invoice-summary', { params })
}

// ======================== 回款登记 ========================
export function getPaymentReceivedPage(params: { page?: number; size?: number; projectId?: number; contractId?: number }) {
  return request.get<R<PageResult<PaymentReceived>>>('/v1/finance/payment-received/page', { params })
}

export function getPaymentReceivedDetail(id: number) {
  return request.get<R<PaymentReceived>>(`/v1/finance/payment-received/${id}`)
}

export function createPaymentReceived(data: Partial<PaymentReceived>) {
  return request.post<R<void>>('/v1/finance/payment-received', data)
}

export function updatePaymentReceived(data: Partial<PaymentReceived> & { id: number }) {
  return request.put<R<void>>(`/v1/finance/payment-received/${data.id}`, data)
}

export function deletePaymentReceived(id: number) {
  return request.delete<R<void>>(`/v1/finance/payment-received/${id}`)
}

// ======================== 付款申请 ========================
export function getPaymentApplyPage(params: { page?: number; size?: number; projectId?: number }) {
  return request.get<R<PageResult<PaymentApply>>>('/v1/finance/payment-apply/page', { params })
}

export function getPaymentApplyDetail(id: number) {
  return request.get<R<PaymentApply>>(`/v1/finance/payment-apply/${id}`)
}

export function createPaymentApply(data: PaymentApplyCreateRequest) {
  return request.post<R<void>>('/v1/finance/payment-apply', data)
}

export function updatePaymentApply(data: PaymentApplyCreateRequest & { id: number }) {
  return request.put<R<void>>(`/v1/finance/payment-apply/${data.id}`, data)
}

export function deletePaymentApply(id: number) {
  return request.delete<R<void>>(`/v1/finance/payment-apply/${id}`)
}

export function submitPaymentApply(id: number) {
  return request.put<R<void>>(`/v1/finance/payment-apply/${id}/submit`)
}

// ======================== 其他费用付款 ========================
// 后端：OtherPaymentController @RequestMapping("/api/v1/finance/other-payment")
export function getOtherPaymentPage(params: { page?: number; size?: number; projectId?: number }) {
  return request.get<R<PageResult<OtherPayment>>>('/v1/finance/other-payment', { params })
}

export function createOtherPayment(data: Partial<OtherPayment>) {
  return request.post<R<void>>('/v1/finance/other-payment', data)
}

// ======================== 项目报销 ========================
// 后端：ProjectReimbursementController @RequestMapping("/api/v1/finance/project-reimbursement")
export function getProjectReimbursementPage(params: { page?: number; size?: number; projectId?: number }) {
  return request.get<R<PageResult<ProjectReimbursement>>>('/v1/finance/project-reimbursement', { params })
}

export function createProjectReimbursement(data: Partial<ProjectReimbursement>) {
  return request.post<R<void>>('/v1/finance/project-reimbursement', data)
}

export function submitProjectReimbursement(id: number) {
  return request.post<R<void>>(`/v1/finance/project-reimbursement/${id}/submit`)
}

// ======================== 备用金（申请 + 归还） ========================
// 后端：ReserveFundController @RequestMapping("/api/v1/finance/reserve-fund")
export function getReserveFundApplyPage(params: { page?: number; size?: number; projectId?: number }) {
  return request.get<R<PageResult<ReserveFundApply>>>('/v1/finance/reserve-fund/apply', { params })
}

export function createReserveFundApply(data: Partial<ReserveFundApply>) {
  return request.post<R<void>>('/v1/finance/reserve-fund/apply', data)
}

export function submitReserveFundApply(id: number) {
  return request.post<R<void>>(`/v1/finance/reserve-fund/apply/${id}/submit`)
}

export function createReserveFundReturn(data: ReserveFundReturn) {
  return request.post<R<void>>('/v1/finance/reserve-fund/return', data)
}

// ======================== 个人报销 ========================
// 后端：PersonalReimbursementController @RequestMapping("/api/v1/finance/personal-reimbursement")
export function getPersonalReimbursementPage(params: { page?: number; size?: number }) {
  return request.get<R<PageResult<PersonalReimbursement>>>('/v1/finance/personal-reimbursement', { params })
}

export function createPersonalReimbursement(data: Partial<PersonalReimbursement>) {
  return request.post<R<void>>('/v1/finance/personal-reimbursement', data)
}

export function submitPersonalReimbursement(id: number) {
  return request.post<R<void>>(`/v1/finance/personal-reimbursement/${id}/submit`)
}

// ======================== 质保金管理 ========================
// 后端：RetentionController @RequestMapping("/api/v1/finance/retention")
export function getRetentionPage(params: { page?: number; size?: number; projectId?: number; contractId?: number }) {
  return request.get<R<PageResult<RetentionMoney>>>('/v1/finance/retention/page', { params })
}

export function createRetention(data: Partial<RetentionMoney>) {
  return request.post<R<void>>('/v1/finance/retention', data)
}

// 即将到期质保金预警
export function getExpiringRetention(days = 30) {
  return request.get<R<RetentionMoney[]>>('/v1/finance/retention/expiring', { params: { days } })
}

export function createRetentionReturn(data: RetentionReturn) {
  return request.post<R<void>>('/v1/finance/retention/return', data)
}

export function submitRetentionReturn(id: number) {
  return request.post<R<void>>(`/v1/finance/retention/return/${id}/submit`)
}
