// 票据台账 API（应收/应付承兑汇票，54_V2026_52）
import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'

export interface BizBill {
  id?: number
  projectId?: number
  contractId?: number
  billNo: string
  direction: 'RECEIVABLE' | 'PAYABLE'
  billType: 'BANK_ACCEPTANCE' | 'COMMERCIAL_ACCEPTANCE'
  faceAmount: number
  issueDate: string
  dueDate: string
  drawerName?: string
  payeeName?: string
  acceptorName?: string
  status?: string
  endorseeName?: string
  endorseDate?: string
  discountDate?: string
  discountRate?: number
  discountInterest?: number
  discountNetAmount?: number
  remark?: string
}

export interface BillPageQuery {
  page?: number
  size?: number
  direction?: string
  status?: string
  projectId?: number
}

export function getBillPage(params: BillPageQuery) {
  return request.get<R<PageResult<BizBill>>>('/v1/finance/bill/page', { params })
}

export function registerBill(data: BizBill) {
  return request.post<R<void>>('/v1/finance/bill', data)
}

export function endorseBill(id: number, endorseeName: string, endorseDate?: string) {
  return request.post<R<void>>(`/v1/finance/bill/${id}/endorse`, null, {
    params: { endorseeName, endorseDate }
  })
}

export function discountBill(id: number, annualRate: number, discountDate?: string) {
  return request.post<R<void>>(`/v1/finance/bill/${id}/discount`, null, {
    params: { annualRate, discountDate }
  })
}

export function redeemBill(id: number, redeemDate?: string) {
  return request.post<R<void>>(`/v1/finance/bill/${id}/redeem`, null, { params: { redeemDate } })
}

export function payOutBill(id: number, payDate?: string) {
  return request.post<R<void>>(`/v1/finance/bill/${id}/pay-out`, null, { params: { payDate } })
}

export function getExpiringBills(days?: number) {
  return request.get<R<BizBill[]>>('/v1/finance/bill/expiring', { params: { days: days || 30 } })
}

export function getBillStatistics(projectId?: number) {
  return request.get<R<Record<string, unknown>>>('/v1/finance/bill/statistics', { params: { projectId } })
}
