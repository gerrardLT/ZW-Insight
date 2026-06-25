import request from '@/utils/request'

// ======================== 入库管理 ========================
export function getMaterialInboundPage(params: any) {
  return request.get('/v1/material/inbound/page', { params })
}

export function getMaterialInboundDetail(id: number) {
  return request.get(`/v1/material/inbound/${id}`)
}

export function createMaterialInbound(data: any) {
  return request.post('/v1/material/inbound', data)
}

export function updateMaterialInbound(data: any) {
  return request.put(`/v1/material/inbound/${data.id}`, data)
}

export function deleteMaterialInbound(id: number) {
  return request.delete(`/v1/material/inbound/${id}`)
}

export function submitMaterialInbound(id: number) {
  return request.put(`/v1/material/inbound/${id}/submit`)
}

// ======================== 出库管理 ========================
export function getMaterialOutboundPage(params: any) {
  return request.get('/v1/material/outbound/page', { params })
}

export function getMaterialOutboundDetail(id: number) {
  return request.get(`/v1/material/outbound/${id}`)
}

export function createMaterialOutbound(data: any) {
  return request.post('/v1/material/outbound', data)
}

export function updateMaterialOutbound(data: any) {
  return request.put(`/v1/material/outbound/${data.id}`, data)
}

export function deleteMaterialOutbound(id: number) {
  return request.delete(`/v1/material/outbound/${id}`)
}

export function submitMaterialOutbound(id: number) {
  return request.put(`/v1/material/outbound/${id}/submit`)
}

// ======================== 调拨管理 ========================
export function getMaterialTransferPage(params: any) {
  return request.get('/v1/material/transfer/page', { params })
}

export function getMaterialTransferDetail(id: number) {
  return request.get(`/v1/material/transfer/${id}`)
}

export function createMaterialTransfer(data: any) {
  return request.post('/v1/material/transfer', data)
}

export function updateMaterialTransfer(data: any) {
  return request.put(`/v1/material/transfer/${data.id}`, data)
}

export function deleteMaterialTransfer(id: number) {
  return request.delete(`/v1/material/transfer/${id}`)
}

export function submitMaterialTransfer(id: number) {
  return request.put(`/v1/material/transfer/${id}/submit`)
}

// ======================== 盘点管理 ========================
export function getMaterialCheckPage(params: any) {
  return request.get('/v1/material/inventory/page', { params })
}

export function createMaterialCheck(data: any) {
  return request.post('/v1/material/inventory', data)
}

export function updateMaterialCheck(data: any) {
  return request.put(`/v1/material/inventory/${data.id}`, data)
}

export function deleteMaterialCheck(id: number) {
  return request.delete(`/v1/material/inventory/${id}`)
}

// ======================== 库存查询 ========================
export function getMaterialStockPage(params: any) {
  return request.get('/v1/material/stock/page', { params })
}

export function getMaterialStockDetail(id: number) {
  return request.get(`/v1/material/stock/${id}`)
}
