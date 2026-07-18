import request from '@/utils/request'

// ======================== 采购合同 ========================
export function getPurchaseContractPage(params: any) {
  return request.get('/v1/purchase/contract/page', { params })
}

export function getPurchaseContractDetail(id: number) {
  return request.get(`/v1/purchase/contract/${id}`)
}

export function createPurchaseContract(data: any) {
  return request.post('/v1/purchase/contract', data)
}

export function updatePurchaseContract(data: any) {
  return request.put(`/v1/purchase/contract/${data.id}`, data)
}

export function deletePurchaseContract(id: number) {
  return request.delete(`/v1/purchase/contract/${id}`)
}

export function submitPurchaseContract(id: number) {
  return request.put(`/v1/purchase/contract/${id}/submit`)
}

// ======================== 采购结算 ========================
export function getPurchaseSettlementPage(params: any) {
  return request.get('/v1/purchase/settlement/page', { params })
}

export function getPurchaseSettlementDetail(id: number) {
  return request.get(`/v1/purchase/settlement/${id}`)
}

export function createPurchaseSettlement(data: any) {
  return request.post('/v1/purchase/settlement', data)
}

export function updatePurchaseSettlement(data: any) {
  return request.put(`/v1/purchase/settlement/${data.id}`, data)
}

export function deletePurchaseSettlement(id: number) {
  return request.delete(`/v1/purchase/settlement/${id}`)
}

export function submitPurchaseSettlement(id: number) {
  return request.put(`/v1/purchase/settlement/${id}/submit`)
}

// 查询指定合同下可结算的入库单（已审批且未结算）
export function getAvailableInbounds(contractId: number) {
  return request.get('/v1/purchase/settlement/available-inbounds', { params: { contractId } })
}

// ======================== 三方比价 - 询价 ========================
export function getInquiryPage(params: any) {
  return request.get('/v1/purchase/inquiry/page', { params })
}

export function getInquiryDetail(id: number) {
  return request.get(`/v1/purchase/inquiry/${id}`)
}

export function createInquiry(data: any) {
  return request.post('/v1/purchase/inquiry', data)
}

export function updateInquiry(data: any) {
  return request.put(`/v1/purchase/inquiry/${data.id}`, data)
}

export function deleteInquiry(id: number) {
  return request.delete(`/v1/purchase/inquiry/${id}`)
}

export function publishInquiry(id: number) {
  return request.put(`/v1/purchase/inquiry/${id}/publish`)
}

// ======================== 三方比价 - 报价 ========================
export function getQuotationList(inquiryId: number) {
  return request.get(`/v1/purchase/inquiry/${inquiryId}/quotations`)
}


export function submitQuotation(data: any) {
  return request.post('/v1/purchase/quotation/submit', data)
}

// ======================== 三方比价 - 定标 ========================
export function confirmBid(data: any) {
  return request.post('/v1/purchase/inquiry/confirm-bid', data)
}

// ======================== 定标结果（中标结果管理） ========================
// 后端：BidResultController @RequestMapping("/api/v1/purchase/bid")
// 计算报价排名
export function calculateBidRanking(inquiryId: number) {
  return request.post(`/v1/purchase/bid/calculate/${inquiryId}`)
}

// 确认中标供应商
export function confirmBidWinner(data: { inquiryId: number; supplierId: number }) {
  return request.post('/v1/purchase/bid/confirm', data)
}

// 查询询价单的定标结果
export function getBidResultByInquiry(inquiryId: number) {
  return request.get(`/v1/purchase/bid/${inquiryId}`)
}
