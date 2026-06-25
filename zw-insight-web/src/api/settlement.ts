import request from '@/utils/request'

// ======================== 项目最终结算 ========================
export function getSettlementPage(params: any) {
  return request.get('/v1/project-settlements', { params })
}

export function createSettlement(projectId: number) {
  return request.post('/v1/project-settlements', null, { params: { projectId } })
}

export function getSettlement(id: number) {
  return request.get(`/v1/project-settlements/${id}`)
}

export function updateSettlement(id: number, data: any) {
  return request.put(`/v1/project-settlements/${id}`, data)
}

export function submitSettlement(id: number) {
  return request.post(`/v1/project-settlements/${id}/submit`)
}

export function exportSettlement(id: number) {
  return request.post(`/v1/project-settlements/${id}/export`, null, { responseType: 'blob' })
}

export function getUnsettledContracts(id: number) {
  return request.get(`/v1/project-settlements/${id}/unsettled-contracts`)
}
