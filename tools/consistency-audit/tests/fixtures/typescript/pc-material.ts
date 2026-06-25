import request from '@/utils/request'

// ======================== 入库管理 ========================
export function getInboundPage(params: any) {
  return request.get('/v1/material/inbound/page', { params })
}

export function getInboundDetail(id: number) {
  return request.get(`/v1/material/inbound/${id}`)
}

export function createInbound(data: any) {
  return request.post('/v1/material/inbound', data)
}

export function updateInbound(id: number, data: any) {
  return request.put(`/v1/material/inbound/${id}`, data)
}

export function deleteInbound(id: number) {
  return request.delete(`/v1/material/inbound/${id}`)
}

// ======================== 出库管理 ========================
export function getOutboundPage(params: any) {
  return request.get('/v1/material/outbound/page', { params })
}

export function createOutbound(data: any) {
  return request.post('/v1/material/outbound', data)
}

export function deleteOutbound(id: number) {
  return request.delete(`/v1/material/outbound/${id}`)
}
