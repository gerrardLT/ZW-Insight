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

// ======================== 变更签证 ========================
export function getChangeVisaPage(params: any) {
  return request.get('/v1/contract/change-visa/page', { params })
}

export function createChangeVisa(data: any) {
  return request.post('/v1/contract/change-visa', data)
}

export function submitChangeVisa(id: number) {
  return request.post(`/v1/contract/change-visa/${id}/submit`)
}

// ======================== 其他合同 ========================
export function getOtherContractPage(params: any) {
  return request.get('/v1/contract/other/page', { params })
}

export function getOtherContractDetail(id: number) {
  return request.get(`/v1/contract/other/${id}`)
}

export function createOtherContract(data: any) {
  return request.post('/v1/contract/other', data)
}

export function updateOtherContract(id: number, data: any) {
  return request.put(`/v1/contract/other/${id}`, data)
}

export function deleteOtherContract(id: number) {
  return request.delete(`/v1/contract/other/${id}`)
}

// ======================== 工程量清单 ========================
export function getQuantityListPage(params: any) {
  return request.get('/v1/contract/quantity/page', { params })
}

export function createQuantityList(data: any) {
  return request.post('/v1/contract/quantity', data)
}

export function updateQuantityList(id: number, data: any) {
  return request.put(`/v1/contract/quantity/${id}`, data)
}

export function deleteQuantityList(id: number) {
  return request.delete(`/v1/contract/quantity/${id}`)
}

export function importQuantityList(data: FormData) {
  return request.post('/v1/contract/quantity/import', data, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ======================== 竣工结算 ========================
export function getFinalSettlementPage(params: any) {
  return request.get('/v1/contract/settlement/page', { params })
}

export function createFinalSettlement(data: any) {
  return request.post('/v1/contract/settlement', data)
}

export function submitFinalSettlement(id: number) {
  return request.post(`/v1/contract/settlement/${id}/submit`)
}

// ======================== 产值报告 ========================
export function getOutputReportPage(params: any) {
  return request.get('/v1/contract/output/page', { params })
}

export function createOutputReport(data: any) {
  return request.post('/v1/contract/output', data)
}

export function submitOutputReport(id: number) {
  return request.post(`/v1/contract/output/${id}/submit`)
}

// ======================== 工程量清单(BOM) ========================
export function getBomItems(contractId: number) {
  return request.get(`/v1/contract/bom/${contractId}`)
}

export function createBomItem(data: any) {
  return request.post('/v1/contract/bom', data)
}

export function updateBomItem(id: number, data: any) {
  return request.put(`/v1/contract/bom/${id}`, data)
}

export function deleteBomItem(id: number) {
  return request.delete(`/v1/contract/bom/${id}`)
}

export function importBomItems(data: FormData) {
  return request.post('/v1/contract/bom/import', data, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
