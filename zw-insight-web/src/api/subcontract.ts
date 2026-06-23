import request from '@/utils/request'

// ======================== 分包合同 ========================
export function getSubcontractPage(params: any) {
  return request.get('/v1/subcontract/contract/page', { params })
}

export function getSubcontractDetail(id: number) {
  return request.get(`/v1/subcontract/contract/${id}`)
}

export function createSubcontract(data: any) {
  return request.post('/v1/subcontract/contract', data)
}

export function updateSubcontract(data: any) {
  return request.put('/v1/subcontract/contract', data)
}

export function deleteSubcontract(id: number) {
  return request.delete(`/v1/subcontract/contract/${id}`)
}

export function submitSubcontract(id: number) {
  return request.put(`/v1/subcontract/contract/${id}/submit`)
}

// ======================== 产值上报 ========================
export function getSubcontractOutputPage(params: any) {
  return request.get('/v1/subcontract/output/page', { params })
}

export function createSubcontractOutput(data: any) {
  return request.post('/v1/subcontract/output', data)
}

export function updateSubcontractOutput(data: any) {
  return request.put('/v1/subcontract/output', data)
}

export function deleteSubcontractOutput(id: number) {
  return request.delete(`/v1/subcontract/output/${id}`)
}

// ======================== 分包结算 ========================
export function getSubcontractSettlementPage(params: any) {
  return request.get('/v1/subcontract/settlement/page', { params })
}

export function getSubcontractSettlementDetail(id: number) {
  return request.get(`/v1/subcontract/settlement/${id}`)
}

export function createSubcontractSettlement(data: any) {
  return request.post('/v1/subcontract/settlement', data)
}

export function updateSubcontractSettlement(data: any) {
  return request.put('/v1/subcontract/settlement', data)
}

export function deleteSubcontractSettlement(id: number) {
  return request.delete(`/v1/subcontract/settlement/${id}`)
}

export function submitSubcontractSettlement(id: number) {
  return request.put(`/v1/subcontract/settlement/${id}/submit`)
}

// ======================== 奖惩 ========================
export function getSubcontractRewardPage(params: any) {
  return request.get('/v1/subcontract/reward/page', { params })
}

export function createSubcontractReward(data: any) {
  return request.post('/v1/subcontract/reward', data)
}

export function deleteSubcontractReward(id: number) {
  return request.delete(`/v1/subcontract/reward/${id}`)
}
