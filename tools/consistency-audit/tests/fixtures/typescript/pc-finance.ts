import request from '@/utils/request'

// ======================== 开票申请 ========================
export function getInvoiceApplyPage(params: any) {
  return request.get('/v1/finance/invoice-apply/page', { params })
}

export function getInvoiceApplyDetail(id: number) {
  return request.get(`/v1/finance/invoice-apply/${id}`)
}

export function createInvoiceApply(data: any) {
  return request.post('/v1/finance/invoice-apply', data)
}

export function updateInvoiceApply(data: any) {
  return request.put('/v1/finance/invoice-apply', data)
}

export function deleteInvoiceApply(id: number) {
  return request.delete(`/v1/finance/invoice-apply/${id}`)
}

export function submitInvoiceApply(id: number) {
  return request.put(`/v1/finance/invoice-apply/${id}/submit`)
}

// ======================== 回款登记 ========================
export function getPaymentReceivedPage(params: any) {
  return request.get('/v1/finance/payment-received/page', { params })
}

export function getPaymentReceivedDetail(id: number) {
  return request.get(`/v1/finance/payment-received/${id}`)
}

export function createPaymentReceived(data: any) {
  return request.post('/v1/finance/payment-received', data)
}

export function updatePaymentReceived(data: any) {
  return request.put('/v1/finance/payment-received', data)
}

export function deletePaymentReceived(id: number) {
  return request.delete(`/v1/finance/payment-received/${id}`)
}

// ======================== 付款申请 ========================
export function getPaymentApplyPage(params: any) {
  return request.get('/v1/finance/payment-apply/page', { params })
}

export function getPaymentApplyDetail(id: number) {
  return request.get(`/v1/finance/payment-apply/${id}`)
}

export function createPaymentApply(data: any) {
  return request.post('/v1/finance/payment-apply', data)
}

export function updatePaymentApply(data: any) {
  return request.put('/v1/finance/payment-apply', data)
}

export function deletePaymentApply(id: number) {
  return request.delete(`/v1/finance/payment-apply/${id}`)
}

export function submitPaymentApply(id: number) {
  return request.put(`/v1/finance/payment-apply/${id}/submit`)
}

// ======================== 项目报销 ========================
export function getReimbursementPage(params: any) {
  return request.get('/v1/finance/project-reimbursement/page', { params })
}

export function createReimbursement(data: any) {
  return request.post('/v1/finance/project-reimbursement', data)
}
