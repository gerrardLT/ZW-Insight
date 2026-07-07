import request from '@/utils/request'
import type { R, PageResult, PageQuery } from '@/types/api'
import type {
  ConstructionContract,
  ContractCreateRequest,
  ContractPageQuery,
  ContractDetail,
  ChangeVisa,
  OtherContract,
  OutputReport
} from '@/types/contract'

// ======================== 施工合同 ========================
export function getContractPage(params: ContractPageQuery) {
  return request.get<R<PageResult<ConstructionContract>>>('/v1/contract/page', { params })
}

export function getContractDetail(id: number) {
  return request.get<R<ConstructionContract>>(`/v1/contract/${id}`)
}

export function createContract(data: ContractCreateRequest) {
  return request.post<R<void>>('/v1/contract', data)
}

export function updateContract(data: ContractCreateRequest & { id: number }) {
  return request.put<R<void>>(`/v1/contract/${data.id}`, data)
}

export function deleteContract(id: number) {
  return request.delete<R<void>>(`/v1/contract/${id}`)
}

export function submitContract(id: number) {
  return request.put<R<void>>(`/v1/contract/${id}/submit`)
}

// ======================== 合同明细 ========================
export function getContractDetails(contractId: number) {
  return request.get<R<ContractDetail[]>>(`/v1/contract/${contractId}/details`)
}

export function saveContractDetails(contractId: number, items: ContractDetail[]) {
  return request.post<R<void>>(`/v1/contract/${contractId}/details`, items)
}

// ======================== 变更签证 ========================
export function getChangeVisaPage(params: PageQuery & { contractId?: number }) {
  return request.get<R<PageResult<ChangeVisa>>>('/v1/contract/change-visa', { params })
}

export function createChangeVisa(data: Partial<ChangeVisa>) {
  return request.post<R<void>>('/v1/contract/change-visa', data)
}

export function submitChangeVisa(id: number) {
  return request.post<R<void>>(`/v1/contract/change-visa/${id}/submit`)
}

// ======================== 其他合同 ========================
export function getOtherContractPage(params: PageQuery & { projectId?: number; contractCategory?: string }) {
  return request.get<R<PageResult<OtherContract>>>('/v1/contract/other/page', { params })
}

export function getOtherContractDetail(id: number) {
  return request.get<R<OtherContract>>(`/v1/contract/other/${id}`)
}

export function createOtherContract(data: Partial<OtherContract>) {
  return request.post<R<void>>('/v1/contract/other', data)
}

export function updateOtherContract(id: number, data: Partial<OtherContract>) {
  return request.put<R<void>>(`/v1/contract/other/${id}`, data)
}

export function deleteOtherContract(id: number) {
  return request.delete<R<void>>(`/v1/contract/other/${id}`)
}

// ======================== 工程量清单 ========================
export interface QuantityListItem {
  id?: number
  contractId: number
  itemCode?: string
  itemName: string
  unit?: string
  quantity?: number
  unitPrice?: number
  totalPrice?: number
}

export function getQuantityListPage(params: PageQuery & { contractId?: number }) {
  return request.get<R<PageResult<QuantityListItem>>>('/v1/contract/quantity', { params })
}

export function createQuantityList(data: Partial<QuantityListItem>) {
  return request.post<R<void>>('/v1/contract/quantity', data)
}

export function updateQuantityList(id: number, data: Partial<QuantityListItem>) {
  return request.put<R<void>>(`/v1/contract/quantity/${id}`, data)
}

export function deleteQuantityList(id: number) {
  return request.delete<R<void>>(`/v1/contract/quantity/${id}`)
}

export function importQuantityList(data: FormData) {
  return request.post<R<void>>('/v1/contract/quantity/import', data, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

// ======================== 竣工结算 ========================
export function getFinalSettlementPage(params: PageQuery & { contractId?: number }) {
  return request.get<R<PageResult<Record<string, unknown>>>>('/v1/contract/settlement', { params })
}

export function createFinalSettlement(data: Record<string, unknown>) {
  return request.post<R<void>>('/v1/contract/settlement', data)
}

export function submitFinalSettlement(id: number) {
  return request.post<R<void>>(`/v1/contract/settlement/${id}/submit`)
}

// ======================== 产值报告 ========================
export function getOutputReportPage(params: PageQuery & { contractId?: number }) {
  return request.get<R<PageResult<OutputReport>>>('/v1/contract/output', { params })
}

export function createOutputReport(data: Partial<OutputReport>) {
  return request.post<R<void>>('/v1/contract/output', data)
}

export function submitOutputReport(id: number) {
  return request.post<R<void>>(`/v1/contract/output/${id}/submit`)
}

// ======================== 工程量清单(BOQ) ========================
export interface BomItem {
  id?: number
  contractId: number
  itemCode?: string
  itemName: string
  parentId?: number
  level?: number
  unit?: string
  quantity?: number
  unitPrice?: number
  totalPrice?: number
}

export function getBomItems(contractId: number) {
  return request.get<R<BomItem[]>>(`/v1/contract/bom/${contractId}`)
}

export function createBomItem(data: Partial<BomItem>) {
  return request.post<R<void>>('/v1/contract/bom', data)
}

export function updateBomItem(id: number, data: Partial<BomItem>) {
  return request.put<R<void>>(`/v1/contract/bom/${id}`, data)
}

export function deleteBomItem(id: number) {
  return request.delete<R<void>>(`/v1/contract/bom/${id}`)
}

export function importBomItems(data: FormData) {
  return request.post<R<void>>('/v1/contract/bom/import', data, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
