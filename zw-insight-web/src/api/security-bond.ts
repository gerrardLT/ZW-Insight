// 保证金台账 API（四类全生命周期，53_V2026_51）
import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'

export interface SecurityBond {
  id?: number
  projectId: number
  contractId?: number
  tenderId?: number
  bondType: 'TENDER' | 'PERFORMANCE' | 'QUALITY' | 'WAGE'
  amount: number
  contractAmount?: number
  depositDate: string
  dueDate?: string
  refundStatus?: string
  refundApplyDate?: string
  refundActualDate?: string
  refundAmount?: number
  bondForm?: 'CASH' | 'BANK_GUARANTEE' | 'INSURANCE'
  guaranteeFile?: string
  exemptReason?: string
  remark?: string
}

export interface BondPageQuery {
  page?: number
  size?: number
  projectId?: number
  bondType?: string
  refundStatus?: string
}

export function getSecurityBondPage(params: BondPageQuery) {
  return request.get<R<PageResult<SecurityBond>>>('/v1/finance/security-bond/page', { params })
}

export function saveSecurityBond(data: SecurityBond) {
  return request.post<R<void>>('/v1/finance/security-bond', data)
}

export function refundApplyBond(id: number) {
  return request.post<R<void>>(`/v1/finance/security-bond/${id}/refund-apply`)
}

export function refundConfirmBond(id: number, refundAmount: number, refundDate?: string) {
  return request.post<R<void>>(`/v1/finance/security-bond/${id}/refund-confirm`, null, {
    params: { refundAmount, refundDate }
  })
}

export function markBondUsed(id: number) {
  return request.post<R<void>>(`/v1/finance/security-bond/${id}/mark-used`)
}

export function getExpiringBonds(days?: number) {
  return request.get<R<SecurityBond[]>>('/v1/finance/security-bond/expiring', { params: { days: days || 30 } })
}

export function getBondStatistics(projectId?: number) {
  return request.get<R<Record<string, unknown>>>('/v1/finance/security-bond/statistics', { params: { projectId } })
}
