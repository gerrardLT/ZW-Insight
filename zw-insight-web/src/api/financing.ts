// 融资借贷 API（借款台账 + 还款计划，54_V2026_52）
import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'

export interface BizFinancing {
  id?: number
  financingType: 'BANK_LOAN' | 'OTHER'
  contractNo: string
  lenderName: string
  principal: number
  annualRate: number
  startDate: string
  endDate?: string
  termMonths: number
  repaymentMethod: 'EQUAL_INSTALLMENT' | 'EQUAL_PRINCIPAL' | 'BULLET'
  totalInterest?: number
  totalRepaid?: number
  status?: string
  remark?: string
}

export interface BizFinancingRepayment {
  id?: number
  financingId: number
  periodNo: number
  dueDate: string
  principalDue: number
  interestDue: number
  principalPaid?: number
  interestPaid?: number
  paidDate?: string
  status?: string
}

export function getFinancingPage(params: { page?: number; size?: number; status?: string; financingType?: string }) {
  return request.get<R<PageResult<BizFinancing>>>('/v1/finance/financing/page', { params })
}

export function registerFinancing(data: BizFinancing) {
  return request.post<R<void>>('/v1/finance/financing', data)
}

export function getRepayments(id: number) {
  return request.get<R<BizFinancingRepayment[]>>(`/v1/finance/financing/${id}/repayments`)
}

export function recordRepayment(
  id: number,
  periodNo: number,
  interestPaid?: number,
  principalPaid?: number,
  paidDate?: string
) {
  return request.post<R<void>>(`/v1/finance/financing/${id}/repay`, null, {
    params: { periodNo, interestPaid, principalPaid, paidDate }
  })
}

export function getFinancingStatistics() {
  return request.get<R<Record<string, unknown>>>('/v1/finance/financing/statistics')
}
