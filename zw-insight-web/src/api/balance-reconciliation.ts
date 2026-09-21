// 银行存款余额调节表 API（55_V2026_53）
import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'

export interface BalanceReconciliation {
  id?: number
  accountId: number
  reconciliationDate: string
  bankStatementBalance: number
  bookBalance: number
  enterpriseDepositBankNot?: number
  enterprisePaymentBankNot?: number
  bankDepositEnterpriseNot?: number
  bankPaymentEnterpriseNot?: number
  adjustedBankBalance?: number
  adjustedBookBalance?: number
  balanced?: number
  remark?: string
}

export function getReconciliationPage(params: {
  page?: number
  size?: number
  accountId?: number
  start?: string
  end?: string
}) {
  return request.get<R<PageResult<BalanceReconciliation>>>('/v1/finance/balance-reconciliation/page', { params })
}

export function saveReconciliation(data: BalanceReconciliation) {
  return request.post<R<BalanceReconciliation>>('/v1/finance/balance-reconciliation', data)
}

export function getReconciliationDetail(id: number) {
  return request.get<R<BalanceReconciliation>>(`/v1/finance/balance-reconciliation/${id}`)
}
