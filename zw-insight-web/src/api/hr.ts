import request from '@/utils/request'

// ======================== 入离职管理 ========================
export function getHrEntryPage(params: any) {
  return request.get('/v1/hr/entry/page', { params })
}

export function getHrEntryDetail(id: number) {
  return request.get(`/v1/hr/entry/${id}`)
}

export function createHrEntry(data: any) {
  return request.post('/v1/hr/entry', data)
}

export function updateHrEntry(data: any) {
  return request.put('/v1/hr/entry', data)
}

export function deleteHrEntry(id: number) {
  return request.delete(`/v1/hr/entry/${id}`)
}

export function submitHrEntry(id: number) {
  return request.put(`/v1/hr/entry/${id}/submit`)
}

// ======================== 转正 ========================
export function getHrRegularPage(params: any) {
  return request.get('/v1/hr/regular/page', { params })
}

export function createHrRegular(data: any) {
  return request.post('/v1/hr/regular', data)
}

export function updateHrRegular(data: any) {
  return request.put('/v1/hr/regular', data)
}

export function deleteHrRegular(id: number) {
  return request.delete(`/v1/hr/regular/${id}`)
}

// ======================== 调转 ========================
export function getHrTransferPage(params: any) {
  return request.get('/v1/hr/transfer/page', { params })
}

export function createHrTransfer(data: any) {
  return request.post('/v1/hr/transfer', data)
}

export function updateHrTransfer(data: any) {
  return request.put('/v1/hr/transfer', data)
}

export function deleteHrTransfer(id: number) {
  return request.delete(`/v1/hr/transfer/${id}`)
}

// ======================== 用章申请 ========================
export function getSealApplyPage(params: any) {
  return request.get('/v1/hr/seal-apply/page', { params })
}

export function createSealApply(data: any) {
  return request.post('/v1/hr/seal-apply', data)
}

export function updateSealApply(data: any) {
  return request.put('/v1/hr/seal-apply', data)
}

export function deleteSealApply(id: number) {
  return request.delete(`/v1/hr/seal-apply/${id}`)
}

// ======================== 办公用品 ========================
export function getOfficeSupplyPage(params: any) {
  return request.get('/v1/hr/office-supply/page', { params })
}

export function createOfficeSupply(data: any) {
  return request.post('/v1/hr/office-supply', data)
}

export function updateOfficeSupply(data: any) {
  return request.put('/v1/hr/office-supply', data)
}

export function deleteOfficeSupply(id: number) {
  return request.delete(`/v1/hr/office-supply/${id}`)
}

export function submitOfficeSupply(id: number) {
  return request.put(`/v1/hr/office-supply/${id}/submit`)
}

// ======================== 车辆管理 ========================
export function getVehiclePage(params: any) {
  return request.get('/v1/hr/vehicle/page', { params })
}

export function getVehicleDetail(id: number) {
  return request.get(`/v1/hr/vehicle/${id}`)
}

export function createVehicle(data: any) {
  return request.post('/v1/hr/vehicle', data)
}

export function updateVehicle(data: any) {
  return request.put('/v1/hr/vehicle', data)
}

export function deleteVehicle(id: number) {
  return request.delete(`/v1/hr/vehicle/${id}`)
}
