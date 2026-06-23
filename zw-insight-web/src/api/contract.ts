import request from '@/utils/request'

// ======================== 施工合同 ========================
export function getContractPage(params: any) {
  return request.get('/v1/contract/page', { params })
}

export function getContractDetail(id: number) {
  return request.get(`/v1/contract/${id}`)
}

export function createContract(data: any) {
  return request.post('/v1/contract', data)
}

export function updateContract(data: any) {
  return request.put('/v1/contract', data)
}

export function deleteContract(id: number) {
  return request.delete(`/v1/contract/${id}`)
}

export function submitContract(id: number) {
  return request.put(`/v1/contract/${id}/submit`)
}

// ======================== 合同明细 ========================
export function getContractDetails(contractId: number) {
  return request.get(`/v1/contract/${contractId}/details`)
}

export function saveContractDetails(contractId: number, items: any[]) {
  return request.post(`/v1/contract/${contractId}/details`, { items })
}
