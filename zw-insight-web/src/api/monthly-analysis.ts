// 月度经营分析表 API（V2026_67 后端 + V2026_68 前端页面）
// 后端：MonthlyAnalysisController @RequestMapping("/api/v1/finance/monthly-analysis")
// 对标 docs/资金流转流程.md §9「每月必须形成一个项目经营表」（10 类费用 × 6 列）
import request from '@/utils/request'
import type { R } from '@/types/api'

/** 月度经营分析行（项目 × 月份 × 费用类别） */
export interface MonthlyAnalysisRow {
  id?: number
  projectId: number
  /** 分析月份 yyyy-MM */
  analysisMonth: string
  /** 类别码：LABOR/MATERIAL/MACHINE/SUBCONTRACT/MEASURE/ADMIN/ENTERTAIN/TRAVEL_VEHICLE/PROFESSIONAL/TAX/UNCLASSIFIED */
  categoryCode: string
  categoryName: string
  /** 预算（CBS baseline，目标成本口径） */
  budgetAmount: number
  /**
   * 本月发生 = 本月末累计 − 上月末累计。
   * **首次生成无上月行时为 null**，前端显示「—」而非 0（不把「无法计算」伪装成「本月无发生」）。
   */
  currentMonthOccurred: number | null
  /** 累计发生（CBS actual） */
  cumulativeOccurred: number
  /**
   * 累计支付：仅直接费四类（人工/材料/机械/分包）有合同 cumulative_paid 权威来源；
   * 间接费六类**为 null**（付款申请只到 OTHER_EXPENSE 粒度，无法按十类细分），不用 0 冒充已付清。
   */
  cumulativePaid: number | null
  /** 应付未付 = 累计发生 − 累计支付；累计支付为 null 时同为 null */
  payableOutstanding: number | null
  /** 预计最终（CBS forecast，EAC 口径） */
  forecastFinal: number
  /** 本月发生口径：VS_LAST_MONTH / NO_BASELINE（首次生成）/ NO_DATA_SOURCE（该类未建 CBS 账户） */
  occurredBasis: string
  /** 累计支付口径：APPROVAL_WRITEBACK（审批口径，非现金）/ NO_PAYMENT_SOURCE */
  paidBasis: string
  /** 归入本类的 CBS 账户数（0 = 该类未建账户，金额为 0 而非数据缺失） */
  accountCount: number
  generatedAt?: string
}

/** 合计（注意 paidScope：累计支付/应付未付合计仅覆盖直接费四类） */
export interface MonthlyAnalysisTotals {
  budgetAmount: number
  currentMonthOccurred: number | null
  cumulativeOccurred: number
  cumulativePaid: number
  /** 有付款数据源的类的累计发生合计（应付未付合计 = 本值 − cumulativePaid） */
  directOccurredTotal: number
  payableOutstanding: number | null
  forecastFinal: number
  /** 本月发生是否所有类都有值（false 时合计只含已知项） */
  currentMonthComplete: boolean
  /** 固定为 DIRECT_FOUR_CATEGORIES：告知合计口径范围 */
  paidScope: string
}

/** 查询结果 */
export interface MonthlyAnalysisResult {
  projectId: number
  month: string
  rows: MonthlyAnalysisRow[]
  totals: MonthlyAnalysisTotals
  /** 本月发生为空的类别名（用于口径提示） */
  occurredNullCategories: string[]
  /** 累计支付为空的类别名 */
  paidNullCategories: string[]
  generatedAt?: string | null
  /** 口径说明（后端下发，前端必须展示，不得静默隐藏） */
  notes: string[]
}

/** 生成报告 */
export interface MonthlyAnalysisReport {
  projectId?: number
  month: string
  projectCount?: number
  successCount?: number
  inserted?: number
  updated?: number
  rows?: number
  occurredNullCategories?: string[]
  skippedUnclassified?: boolean
  failedProjects?: string[]
}

/**
 * 查询月度经营分析表。
 * @param params.month 不传则后端取该项目已生成的最新月份（无数据时回退当月）
 */
export function getMonthlyAnalysis(params: { projectId: number; month?: string }) {
  return request.get<R<MonthlyAnalysisResult>>('/v1/finance/monthly-analysis', { params })
}

/** §9 十类的中文名与固定行序（表头与 CSV 导出共用，不在前端硬编码） */
export function getMonthlyAnalysisCategories() {
  return request.get<R<Record<string, string>>>('/v1/finance/monthly-analysis/categories')
}

/** 手工生成/重跑覆盖某项目某月（同月重跑为 upsert，不重复插行） */
export function generateMonthlyAnalysis(projectId: number, month: string) {
  return request.post<R<MonthlyAnalysisReport>>('/v1/finance/monthly-analysis/generate', null, {
    params: { projectId, month }
  })
}

/** 批量生成全部已建 CBS 账户项目的某月分析表（运维补跑；失败明细随响应返回） */
export function generateAllMonthlyAnalysis(month: string) {
  return request.post<R<MonthlyAnalysisReport>>('/v1/finance/monthly-analysis/generate-all', null, {
    params: { month }
  })
}
