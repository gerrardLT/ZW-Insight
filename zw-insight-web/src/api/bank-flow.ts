// 银行流水与余额登记 API（55_V2026_53）
import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'

export interface BankFlow {
  id?: number
  accountId: number
  flowDate: string
  direction: 'IN' | 'OUT'
  amount: number
  balanceAfter?: number
  transactionNo?: string
  description?: string
  counterpartyName?: string
  reconciled?: number
  matchedType?: string
  matchedId?: number
  /** 本次勾稽金额（空=按流水整笔金额勾稽；V2026_56 部分勾稽） */
  matchAmount?: number
  source?: string
}

export interface AccountBalanceRow {
  accountId: number
  accountName: string
  accountType: string
  bankName?: string
  balance: number
  balanceDate?: string
}

export function recordBalance(accountId: number, snapshotDate: string, balance: number, remark?: string) {
  return request.post<R<void>>('/v1/finance/bank-flow/balance', null, {
    params: { accountId, snapshotDate, balance, remark }
  })
}

export function getLatestBalances(asOfDate?: string) {
  return request.get<R<AccountBalanceRow[]>>('/v1/finance/bank-flow/latest-balances', { params: { asOfDate } })
}

export function getFlowPage(params: {
  page?: number
  size?: number
  accountId?: number
  start?: string
  end?: string
  reconciled?: number
}) {
  return request.get<R<PageResult<BankFlow>>>('/v1/finance/bank-flow/page', { params })
}

export function addFlow(data: BankFlow) {
  return request.post<R<void>>('/v1/finance/bank-flow', data)
}

export function importFlows(flows: BankFlow[]) {
  return request.post<R<Record<string, number>>>('/v1/finance/bank-flow/import', flows)
}

export function matchFlow(id: number, matchedType: string, matchedId: number, matchAmount?: number) {
  return request.post<R<void>>(`/v1/finance/bank-flow/${id}/match`, null, {
    params: { matchedType, matchedId, matchAmount }
  })
}

export function unmatchFlow(id: number) {
  return request.post<R<void>>(`/v1/finance/bank-flow/${id}/unmatch`)
}
