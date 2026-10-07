import request from '@/utils/request'

// ======================== 投标报名 ========================
export function getTenderRegisterPage(params: any) {
  return request.get('/v1/tender/register/page', { params })
}

export function getTenderRegisterDetail(id: number | string) {
  return request.get(`/v1/tender/register/${id}`)
}

export function createTenderRegister(data: any) {
  return request.post('/v1/tender/register', data)
}

export function updateTenderRegister(data: any) {
  return request.put(`/v1/tender/register/${data.id}`, data)
}

export function deleteTenderRegister(id: number | string) {
  return request.delete(`/v1/tender/register/${id}`)
}

export function submitTenderRegister(id: number | string) {
  return request.post(`/v1/tender/register/${id}/submit`)
}

// ======================== 任务分配 ========================
export function getTenderTaskList(registerId: number | string) {
  return request.get(`/v1/tender/task/${registerId}`)
}

export function createTenderTask(data: any) {
  return request.post('/v1/tender/task', data)
}

export function completeTenderTask(id: number | string) {
  return request.post(`/v1/tender/task/${id}/complete`)
}

export function updateTenderTask(data: any) {
  return request.put(`/v1/tender/task/${data.id}`, data)
}

export function deleteTenderTask(id: number | string) {
  return request.delete(`/v1/tender/task/${id}`)
}

// ======================== 费用缴纳 ========================
export function getTenderFeePage(params: any) {
  return request.get('/v1/tender/fee/page', { params })
}

export function createTenderFee(data: any) {
  return request.post('/v1/tender/fee', data)
}

export function confirmTenderFeePayment(id: number | string, receiptFile?: string) {
  return request.post(`/v1/tender/fee/${id}/confirm-payment`, { receiptFile: receiptFile || '' })
}

export function updateTenderFee(data: any) {
  return request.put(`/v1/tender/fee/${data.id}`, data)
}

export function deleteTenderFee(id: number | string) {
  return request.delete(`/v1/tender/fee/${id}`)
}

// ======================== 保证金 ========================
export function getTenderDepositPage(params: any) {
  return request.get('/v1/tender/deposit/apply', { params })
}

export function createTenderDeposit(data: any) {
  return request.post('/v1/tender/deposit/apply', data)
}

export function submitTenderDeposit(id: number | string) {
  return request.post(`/v1/tender/deposit/apply/${id}/submit`)
}

export function updateTenderDeposit(data: any) {
  return request.put(`/v1/tender/deposit/apply/${data.id}`, data)
}

export function deleteTenderDeposit(id: number | string) {
  return request.delete(`/v1/tender/deposit/apply/${id}`)
}

// ======================== 开标记录 ========================
export function createTenderOpen(data: any) {
  return request.post('/v1/tender/open-bid', data)
}

export function getOpenBidByRegister(registerId: number | string) {
  return request.get(`/v1/tender/open-bid/${registerId}`)
}

export function updateTenderOpen(data: any) {
  return request.put(`/v1/tender/open-bid/${data.id}`, data)
}

export function deleteTenderOpen(id: number | string) {
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

export function deleteTenderRefund(id: number | string) {
  return request.delete(`/v1/tender/deposit/return/${id}`)
}

// ======================== 人员押证绑定（P1-M2 B1/TI-2） ========================
export function getPersonBindings(registerId: number | string) {
  return request.get(`/v1/tender/register/${registerId}/person-bindings`)
}

export function bindPerson(registerId: number | string, data: any) {
  return request.post(`/v1/tender/register/${registerId}/person-bindings/single`, data)
}

export function bindPersons(registerId: number | string, data: any[]) {
  return request.post(`/v1/tender/register/${registerId}/person-bindings`, data)
}

// ======================== 证件管理 ========================
export function getCertificatePage(params: any) {
  const type = params.type || 'person'
  return request.get(`/v1/tender/certificate/${type}`, { params })
}

export function createCertificate(data: any) {
  const type = data.type || 'person'
  return request.post(`/v1/tender/certificate/${type}`, data)
}

export function updateCertificate(data: any) {
  const type = data.type || 'person'
  return request.put(`/v1/tender/certificate/${type}/${data.id}`, data)
}

export function deleteCertificate(type: string, id: number | string) {
  return request.delete(`/v1/tender/certificate/${type}/${id}`)
}
