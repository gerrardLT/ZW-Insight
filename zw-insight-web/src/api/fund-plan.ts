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
  /** 科目明细（V2026_58；合计须与总额一致，空=不维护明细） */
  details?: FundPlanDetail[]
}

/** 月度计划科目明细（biz_fund_plan_detail，V2026_58） */
export interface FundPlanDetail {
  id?: number
  planId?: number
  direction: 'INCOME' | 'EXPENSE'
  categoryCode: string
  amount: number
  remark?: string
}

/** 待支付大额支出按科目聚合行（/rolling/top-expenses，V2026_56；V2026_63 含已逾期） */
export interface FutureExpenseRow {
  categoryCode: string
  categoryName: string
  amount: number
  count: number
  /**
   * 其中：已逾期未付金额（payment_date 已过但仍未支付，V2026_63）
   * 已包含在 amount 内，属构成项；原口径下界为今天会把这部分排除，
   * 导致待付款在页面上完全消失（线上实测曾返回空数组）。
   */
  overdueAmount?: number
}

export interface FundRollingForecast {
  id?: number
  projectId?: number
  forecastMonth: string
  expectedReceipts?: number
  expectedPayments?: number
  /** 其中：已逾期未付（V2026_63，已含在 expectedPayments 内，不可相加） */
  overdueUnpaid?: number
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

export function getPlanDetails(planId: number) {
  return request.get<R<FundPlanDetail[]>>(`/v1/finance/fund-plan/monthly/${planId}/details`)
}

export function generateRollingForecast(projectId?: number, months?: number) {
  return request.post<R<FundRollingForecast[]>>('/v1/finance/fund-plan/rolling/generate', null, {
    params: { projectId, months: months || 6 }
  })
}

export function getRollingForecastPage(params: { page?: number; size?: number; projectId?: number }) {
  return request.get<R<PageResult<FundRollingForecast>>>('/v1/finance/fund-plan/rolling/page', { params })
}

/** 未来大额支出 TOP（已批未付按科目聚合；驾驶舱资金中心 §9.3） */
export function getFutureExpenseTop(params: { projectId?: number; days?: number }) {
  return request.get<R<FutureExpenseRow[]>>('/v1/finance/fund-plan/rolling/top-expenses', { params })
}

/**
 * 未来 N 天资金预测（UI §9.2：30/60/90 天三档，累计窗口）。
 * netFlow 为 §9.2 表格口径（回款−付款，负数=净流出）；
 * gap 为 §10.4 口径（付款−可用资金，正数=缺钱）。两者不可混用。
 */
export interface DayForecast {
  days: number
  windowEnd: string
  expectedReceipts: number
  expectedPayments: number
  /** 其中：已逾期未付（构成项，已含在 expectedPayments 内） */
  overdueUnpaid: number
  /** §9.2 资金差额 = 回款 − 付款 */
  netFlow: number
  accountBalance: number
  /** 可用资金 = 账户余额 + 窗口内预计回款 */
  availableFund: number
  /** §10.4 缺口 = 付款 − 可用资金（正数=缺钱） */
  gap: number
  /** §15：可用资金能否覆盖（true → 黄色关注而非红色严重） */
  coverable: boolean
}

export function getForecastByDays(params: { projectId?: number; days?: number }) {
  return request.get<R<DayForecast>>('/v1/finance/fund-plan/rolling/days', { params })
}

/** 资金缺口归因（UI §9.2：哪个项目导致 + 主要付款对象） */
export interface GapAttribution {
  days: number
  byProject: {
    projectId: number
    projectName: string
    expectedPayments: number
    expectedReceipts: number
    netGap: number
  }[]
  byPayee: {
    /** 无供应商名称时为「（未登记收款方）」，不隐藏 */
    supplierName: string
    amount: number
    count: number
  }[]
}

export function getGapAttribution(params: { projectId?: number; days?: number; topN?: number }) {
  return request.get<R<GapAttribution>>('/v1/finance/fund-plan/rolling/gap-attribution', { params })
}
