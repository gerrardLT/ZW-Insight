// 老板资金看板 API（核心三大指标，53_V2026_51）
import request from '@/utils/request'
import type { R } from '@/types/api'

export interface FundGapItem {
  month: string
  expectedReceipts?: number
  expectedPayments?: number
  /**
   * 其中：已逾期未付金额（V2026_63）
   * 已包含在 expectedPayments 内，属构成项而非另计项（两者不可相加）；
   * 仅当月快照非 0。展示时必须标明“其中”，避免用户误以为需叠加。
   */
  overdueUnpaid?: number
  netGap?: number
  riskLevel?: string
}

export interface FundDashboard {
  projectId?: number
  cumulativeOutput?: number
  cumulativeReceived?: number
  advanceAmount?: number
  advanceRate?: number
  cashInflow?: number
  cashOutflow?: number
  operatingCashFlow?: number
  receivableTotal?: number
  collectionRate?: number
  bondOccupying?: number
  guaranteeRatio?: number
  wageAccountWarnings?: number
  rollingGaps?: FundGapItem[]
}

export function getFundDashboard(projectId?: number) {
  return request.get<R<FundDashboard>>('/v1/finance/fund-dashboard', { params: { projectId } })
}
