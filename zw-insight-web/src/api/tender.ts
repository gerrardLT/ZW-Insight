import request from '@/utils/request'

// ======================== 投标报名 ========================
export function getTenderRegisterPage(params: any) {
  return request.get('/v1/tender/register/page', { params })
}

export function getTenderRegisterDetail(id: number) {
  return request.get(`/v1/tender/register/${id}`)
}

export function createTenderRegister(data: any) {
  return request.post('/v1/tender/register', data)
}

export function updateTenderRegister(data: any) {
  return request.put(`/v1/tender/register/${data.id}`, data)
}

export function deleteTenderRegister(id: number) {
  return request.delete(`/v1/tender/register/${id}`)
}

export function submitTenderRegister(id: number) {
  return request.put(`/v1/tender/register/${id}/submit`)
}

// ======================== 任务分配 ========================
export function getTenderTaskPage(params: any) {
  return request.get('/v1/tender/task/page', { params })
}

export function createTenderTask(data: any) {
  return request.post('/v1/tender/task', data)
}

export function updateTenderTask(data: any) {
  return request.put(`/v1/tender/task/${data.id}`, data)
}

export function deleteTenderTask(id: number) {
  return request.delete(`/v1/tender/task/${id}`)
}

// ======================== 费用缴纳 ========================
export function getTenderFeePage(params: any) {
  return request.get('/v1/tender/fee/page', { params })
}

export function createTenderFee(data: any) {
  return request.post('/v1/tender/fee', data)
}

export function updateTenderFee(data: any) {
  return request.put(`/v1/tender/fee/${data.id}`, data)
}

export function deleteTenderFee(id: number) {
  return request.delete(`/v1/tender/fee/${id}`)
}

// ======================== 保证金 ========================
export function getTenderDepositPage(params: any) {
  return request.get('/v1/tender/deposit/apply', { params })
}

export function createTenderDeposit(data: any) {
  return request.post('/v1/tender/deposit/apply', data)
}

export function updateTenderDeposit(data: any) {
  return request.put(`/v1/tender/deposit/${data.id}`, data)
}

export function deleteTenderDeposit(id: number) {
  return request.delete(`/v1/tender/deposit/${id}`)
}

// ======================== 开标记录 ========================
export function getTenderOpenPage(params: any) {
  return request.get('/v1/tender/open-bid/page', { params })
}

export function createTenderOpen(data: any) {
  return request.post('/v1/tender/open-bid', data)
}

export function updateTenderOpen(data: any) {
  return request.put(`/v1/tender/open-bid/${data.id}`, data)
}

export function deleteTenderOpen(id: number) {
  return request.delete(`/v1/tender/open-bid/${id}`)
}

// ======================== 保证金退还 ========================
export function getTenderRefundPage(params: any) {
  return request.get('/v1/tender/deposit/return', { params })
}

export function createTenderRefund(data: any) {
  return request.post('/v1/tender/deposit/return', data)
}

export function updateTenderRefund(data: any) {
  return request.put(`/v1/tender/deposit/return/${data.id}`, data)
}

export function deleteTenderRefund(id: number) {
  return request.delete(`/v1/tender/deposit/return/${id}`)
}

// ======================== 证件管理 ========================
export function getCertificatePage(params: any) {
  return request.get('/v1/tender/certificate/page', { params })
}

export function getCertificateDetail(id: number) {
  return request.get(`/v1/tender/certificate/${id}`)
}

export function createCertificate(data: any) {
  return request.post('/v1/tender/certificate', data)
}

export function updateCertificate(data: any) {
  return request.put(`/v1/tender/certificate/${data.type}/${data.id}`, data)
}

export function deleteCertificate(id: number) {
  return request.delete(`/v1/tender/certificate/${id}`)
}
