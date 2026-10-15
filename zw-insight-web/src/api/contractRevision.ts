import request from '@/utils/request'
import type { R } from '@/types/api'
export interface ContractRevision {
  id: string | number
  contractId: string | number
  baseVersion: number
  beforeAmount: number
  afterAmount: number
  deltaAmount: number
  beforeSnapshot: string
  afterSnapshot: string
  reason: string
  status: string
}
export const getContractRevisions = (contractId: string | number) => request.get<R<ContractRevision[]>>('/v1/contract/revision', { params: { contractId } })
export const getContractRevision = (id: string | number) => request.get<R<ContractRevision>>(`/v1/contract/revision/${id}`)
export const createContractRevision = (data: { contractId: string | number; baseVersion: number; afterAmount: number; reason: string }) => request.post<R<string | number>>('/v1/contract/revision', data)
export const cancelContractRevision = (id: string | number) => request.post<R<void>>(`/v1/contract/revision/${id}/cancel`)
export const submitContractRevision = (id: string | number) => request.post<R<void>>(`/v1/contract/revision/${id}/submit`)
