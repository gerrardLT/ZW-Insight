// 工程老板经营驾驶舱 API（V2026_59）
// 后端：CockpitController @RequestMapping("/api/v1/dashboard/cockpit")
// 口径纪律：forecastProfit（预计利润 = 合同收入 − CBS 完工预测总成本）与
//          realizedProfit（已实现收支差）为两套口径，禁止混用展示。
import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'

/** 经营总览 8 卡（驾驶舱 §4） */
export interface CockpitOverview {
  /** 合同收入（预计利润计算基数） */
  contractIncome: number
  /** 预计最终总成本（CBS EAC 合计） */
  forecastTotalCost: number
  /** 预计利润 = 合同收入 − 预计最终总成本 */
  forecastProfit: number
  /** 预计利润率 */
  forecastProfitRate: number
  /** 已实现利润（收入−支出，历史收付口径；与预计利润并存不混用） */
  realizedProfit: number
  /** 累计回款 */
  cumulativeReceived: number
  /** 累计支付 */
  cumulativePaid: number
  /** 应收未收 */
  receivableOutstanding: number
  /**
   * 90 天资金缺口（资金流转 §10.4：未来预计支付 − 可用资金）
   * **正数 = 缺钱**，负数 = 有富余。旧口径为“正净缺口合计”且不减可用资金，
   * 会把账面能覆盖的缺口误报为重大风险（2026-09-24 修正）。
   */
  gap90Days: number
  /** 缺口构成明细（不隐藏口径，供 tooltip 如实展示） */
  gap90DaysDetail?: {
    expectedPayments: number
    expectedReceipts: number
    accountBalance: number
    availableFund: number
    gap: number
    currentMonthPayments: number
  }
  /** 可用资金 = 账户余额快照合计 + 窗口内预计回款 */
  availableFund?: number
  /** 账户资金（各账户最新余额快照合计；biz_bank_balance 未登记时为 0） */
  accountBalance: number
  /**
   * 应付未付（已确认付款义务）= Σ合同 cumulative_settlement − cumulative_paid
   * UI §9.1 四卡要求的是本字段；与 approvedUnpaid 语义不同，**不可互替**。
   */
  payableOutstanding?: number
  /** 已批未付（已进入付款流程但银行未划款，按剩余未付额） */
  approvedUnpaid?: number
  /** 本月现金需求（资金流转 §12）= 当月预计支付（含逾期） */
  currentMonthCashNeed?: number
  /** 三个月资金需求（资金流转 §12）= 未来 3 个月预计支付合计 */
  threeMonthCashNeed?: number
}

/** 预计利润月度快照 */
export interface ProfitSnapshot {
  id?: number
  snapshotMonth: string
  projectId?: number
  contractIncome: number
  /** 收入口径（CONSTRUCTION_CONTRACT / PROJECT_CONTRACT_AMOUNT） */
  incomeBasis: string
  cumulativeOutput?: number
  actualCost?: number
  forecastTotalCost: number
  /** 成本口径（CBS_FORECAST / FALLBACK_TOTAL_EXPENSE） */
  costBasis: string
  forecastProfit: number
  /** 较上期利润变化（首期为 null，不伪造 0） */
  profitDelta?: number | null
  categoryBreakdown?: string
  snapshotDate: string
}

/** 利润归因条目（成本上升 → delta 为负） */
export interface ProfitAttributionItem {
  category: string
  forecast: number
  prevForecast: number
  delta: number
}

/** 利润归因结果 */
export interface ProfitAttribution {
  month: string
  forecastProfit: number
  profitDelta: number | null
  /** 是否有上期基线（false 时 items 为空，首期无对比基期） */
  hasBaseline: boolean
  items: ProfitAttributionItem[]
}

/** 项目健康度行（驾驶舱 §5.2） */
export interface ProjectHealth {
  projectId: number
  projectName: string
  status: string
  contractIncome: number
  incomeBasis: string
  actualCost: number
  forecastTotalCost: number
  costBasis: string
  forecastProfit: number
  profitRate: number
  /** 健康度（RED/YELLOW/GREEN，规则自动判定） */
  health: 'RED' | 'YELLOW' | 'GREEN'
  redCount: number
  yellowCount: number
  /** TOP 风险标题（最多 3 条） */
  topRisks: string[]
}

/** 风险台账记录（驾驶舱 §11-12 六要素） */
export interface RiskRegister {
  id: number
  riskCode: string
  riskType: string
  projectId?: number
  projectName?: string
  severity: 'RED' | 'YELLOW' | 'INFO'
  /** 发生了什么 */
  title: string
  /** 影响多少钱 */
  impactAmount?: number
  /** 为什么发生（结构化 JSON 字符串） */
  reasonDetail?: string
  /** 谁负责 */
  ownerId?: number
  ownerName?: string
  /** 下一步动作 */
  nextAction?: string
  /** 当前处理状态 */
  handleStatus: 'OPEN' | 'PROCESSING' | 'RESOLVED' | 'IGNORED'
  handledBy?: number
  handledAt?: string
  handleNote?: string
  bizRefType?: string
  bizRefId?: number
  ruleParams?: string
  lastScanAt?: string
  createdAt?: string
}

/** 风险分级汇总 */
export interface RiskSummary {
  redCount: number
  redImpact: number
  yellowCount: number
  yellowImpact: number
  infoCount: number
  infoImpact: number
  activeTotal: number
}

/** 风险扫描统计 */
export interface RiskScanResult {
  scannedRules: number
  findings: number
  inserted: number
  updated: number
  resolved: number
  failedRules: string[]
}

/** 利润趋势（快照曲线 + 已实现月度曲线） */
export interface CockpitProfitTrend {
  snapshots: ProfitSnapshot[]
  realized: {
    year: number
    months: { month: number; income: number; expense: number; profit: number }[]
    totalIncome: number
    totalExpense: number
    totalProfit: number
  }
}

export function getCockpitOverview() {
  return request.get<R<CockpitOverview>>('/v1/dashboard/cockpit/overview')
}

export function getCockpitProfitTrend(params: { projectId?: number; months?: number; year?: number }) {
  return request.get<R<CockpitProfitTrend>>('/v1/dashboard/cockpit/profit-trend', { params })
}

export function getProfitAttribution(params: { month: string; projectId?: number }) {
  return request.get<R<ProfitAttribution>>('/v1/dashboard/cockpit/profit-attribution', { params })
}

export function getProjectHealth() {
  return request.get<R<ProjectHealth[]>>('/v1/dashboard/cockpit/project-health')
}

export function getProjectForecasts() {
  return request.get<R<ProjectHealth[]>>('/v1/dashboard/cockpit/project-forecasts')
}

export function getProfitSnapshots(params: { projectId?: number; months?: number }) {
  return request.get<R<ProfitSnapshot[]>>('/v1/dashboard/cockpit/profit-snapshots', { params })
}

export function getRiskSummary(projectId?: number) {
  return request.get<R<RiskSummary>>('/v1/dashboard/cockpit/risk/summary', { params: { projectId } })
}

export function getRiskPage(params: {
  page?: number
  size?: number
  severity?: string
  handleStatus?: string
  riskType?: string
  projectId?: number
}) {
  return request.get<R<PageResult<RiskRegister>>>('/v1/dashboard/cockpit/risk/page', { params })
}

/**
 * 风险处理流转（认领 PROCESSING / 解决 RESOLVED / 忽略 IGNORED / 重开 OPEN）
 * 参数走请求体（后端 RiskHandleRequest），与移动端保持一致
 */
export function handleRisk(id: number, action: string, handleNote?: string) {
  return request.put<R<void>>(`/v1/dashboard/cockpit/risk/${id}/handle`, { action, handleNote })
}

/** 手动触发风险扫描（定时任务每日 02:00 自动执行，此处供首屏初始化） */
export function scanRisks() {
  return request.put<R<RiskScanResult>>('/v1/dashboard/cockpit/risk/scan')
}
