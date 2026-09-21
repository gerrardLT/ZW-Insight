// 资金计划 API（三层联动，53_V2026_51）
import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'

export interface FundAnnualBudget {
  id?: number
  budgetYear: number
  projectId?: number
  incomePlan: number
  expensePlan: number
  remark?: string
  status?: string
}

export interface FundMonthlyPlan {
  id?: number
  planYear: number
  planMonth: number
  projectId?: number
  incomePlan: number
  expensePlan: number
  actualIncome?: number
  actualExpense?: number
  remark?: string
  status?: string
}

export interface FundRollingForecast {
  id?: number
  projectId?: number
  forecastMonth: string
  expectedReceipts?: number
  expectedPayments?: number
  netGap?: number
  riskLevel?: string
  snapshotDate?: string
}

export function saveAnnualBudget(data: FundAnnualBudget) {
  return request.post<R<void>>('/v1/finance/fund-plan/annual', data)
}

export function getAnnualBudgetPage(params: { page?: number; size?: number; budgetYear?: number; projectId?: number }) {
  return request.get<R<PageResult<FundAnnualBudget>>>('/v1/finance/fund-plan/annual/page', { params })
}

export function saveMonthlyPlan(data: FundMonthlyPlan) {
  return request.post<R<void>>('/v1/finance/fund-plan/monthly', data)
}

export function getMonthlyPlanPage(params: { page?: number; size?: number; planYear?: number; projectId?: number }) {
  return request.get<R<PageResult<FundMonthlyPlan>>>('/v1/finance/fund-plan/monthly/page', { params })
}

export function fillMonthlyActual(year: number, month: number, projectId?: number) {
  return request.post<R<void>>('/v1/finance/fund-plan/monthly/fill-actual', null, {
    params: { year, month, projectId }
  })
}

export function generateRollingForecast(projectId?: number, months?: number) {
  return request.post<R<FundRollingForecast[]>>('/v1/finance/fund-plan/rolling/generate', null, {
    params: { projectId, months: months || 6 }
  })
}

export function getRollingForecastPage(params: { page?: number; size?: number; projectId?: number }) {
  return request.get<R<PageResult<FundRollingForecast>>>('/v1/finance/fund-plan/rolling/page', { params })
}
