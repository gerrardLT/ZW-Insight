// 农民工工资专户 API（条例合规，53_V2026_51）
import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'

export interface WageSpecialAccount {
  id?: number
  projectId: number
  accountNo: string
  accountName?: string
  bankName: string
  bankBranch?: string
  status?: string
  wageBudget?: number
  totalReceived?: number
  totalPaid?: number
  currentBalance?: number
  workerCount?: number
  lastDepositDate?: string
  complianceFlag?: string
  remark?: string
}

export interface WageDeposit {
  accountId?: number
  projectId?: number
  depositDate: string
  amount: number
  payerName?: string
  voucherNo?: string
  remark?: string
}

export interface WageAccountPageQuery {
  page?: number
  size?: number
  projectId?: number
  status?: string
}

export function getWageAccountPage(params: WageAccountPageQuery) {
  return request.get<R<PageResult<WageSpecialAccount>>>('/v1/finance/wage-account/page', { params })
}

export function saveWageAccount(data: WageSpecialAccount) {
  return request.post<R<void>>('/v1/finance/wage-account', data)
}

export function recordWageDeposit(id: number, deposit: WageDeposit) {
  return request.post<R<void>>(`/v1/finance/wage-account/${id}/deposit`, deposit)
}

export function recordWagePayment(id: number, amount: number, payDate?: string) {
  return request.post<R<void>>(`/v1/finance/wage-account/${id}/wage-payment`, null, {
    params: { amount, payDate }
  })
}

export function getWageDeposits(id: number) {
  return request.get<R<WageDeposit[]>>(`/v1/finance/wage-account/${id}/deposits`)
}

export function getArrivalRate(id: number) {
  return request.get<R<number>>(`/v1/finance/wage-account/${id}/arrival-rate`)
}

export function complianceScan() {
  return request.post<R<WageSpecialAccount[]>>('/v1/finance/wage-account/compliance-scan')
}

export function cancelWageAccount(id: number) {
  return request.post<R<void>>(`/v1/finance/wage-account/${id}/cancel`)
}
