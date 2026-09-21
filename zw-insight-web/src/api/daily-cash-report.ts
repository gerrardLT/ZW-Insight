// 资金日报 API（每日头寸，老板视角，55_V2026_53）
import request from '@/utils/request'
import type { R } from '@/types/api'
import type { BankFlow } from '@/api/bank-flow'

export interface DailyCashReport {
  id?: number
  reportDate: string
  totalBalance?: number
  basicBalance?: number
  generalBalance?: number
  specialBalance?: number
  inflowAmount?: number
  outflowAmount?: number
  netPosition?: number
  largeOutflowCount?: number
  largeOutflowAmount?: number
  accountCount?: number
}

export function generateDailyReport(reportDate?: string, largeThreshold?: number) {
  return request.post<R<DailyCashReport>>('/v1/finance/daily-cash-report/generate', null, {
    params: { reportDate, largeThreshold }
  })
}

export function getDailyReport(reportDate?: string) {
  return request.get<R<DailyCashReport>>('/v1/finance/daily-cash-report', { params: { reportDate } })
}

export function getDailyReportTrend(days?: number) {
  return request.get<R<DailyCashReport[]>>('/v1/finance/daily-cash-report/trend', { params: { days: days || 7 } })
}

export function getLargeOutflows(reportDate?: string, threshold?: number) {
  return request.get<R<BankFlow[]>>('/v1/finance/daily-cash-report/large-outflows', {
    params: { reportDate, threshold }
  })
}
