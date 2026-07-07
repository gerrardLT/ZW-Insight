import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'
import type {
  InvoiceApply,
  InvoiceApplyCreateRequest,
  InvoiceApplyPageQuery,
  PaymentReceived,
  PaymentApply,
  PaymentApplyCreateRequest,
  InvoiceReceived
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
