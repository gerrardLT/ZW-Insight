import request from '@/utils/request'

// ======================== 公司概览 ========================
export function getCompanyOverview() {
  return request.get('/v1/dashboard/company-overview')
}

// ======================== 预算执行 ========================
export function getBudgetExecution(params: any) {
  return request.get('/v1/dashboard/budget-execution', { params })
}

// ======================== 应收款监控 ========================
export function getReceivableMonitor() {
  return request.get('/v1/dashboard/receivable-monitor')
}

// ======================== 供应商应付监控 ========================
export function getSupplierPayableMonitor(params?: any) {
  return request.get('/v1/dashboard/supplier-payable', { params })
}

// ======================== 投标分析 ========================
export function getTenderAnalysis() {
  return request.get('/v1/dashboard/tender-analysis')
}

// ======================== 库存分析 ========================
export function getInventoryAnalysis() {
  return request.get('/v1/dashboard/inventory-analysis')
}

// ======================== 进度甘特图 ========================
export function getScheduleGantt(projectId: number) {
  return request.get(`/v1/dashboard/schedule-gantt/${projectId}`)
}

// 兼容别名已移除（2026-08-17）：getDashboardStats/getProjectStatusDistribution/getIncomeExpenseComparison
// 三别名均指向 company-overview 或 receivable-monitor，与页面实际消费语义错位，
// 致仪表盘卡片全 0/饼图空白（真实浏览器实测抓出）；页面统一改用 getCompanyOverview。

// ======================================================================
// 项目看板（独立项目维度） - P2 Business Enhance
// 后端：ProjectDashboardController
//   GET /api/v1/dashboard/project/{projectId}/budget   → BudgetExecutionDTO
//   GET /api/v1/dashboard/project/{projectId}/progress → ProgressDTO
//   GET /api/v1/dashboard/project/{projectId}/contract → ContractReceiptDTO
//   GET /api/v1/dashboard/project/{projectId}/output   → OutputTrendDTO
//   GET /api/v1/dashboard/project/{projectId}/overview → ProjectDashboardDTO
// 说明：后端 BigDecimal 在 JSON 中序列化为 number，前端统一以 number 接收。
// 注意：request 响应拦截器返回完整的 R<T> 包装对象（{ code, message, data }），
//      调用方通过 res.data 取得对应 DTO。下方 DTO 接口用于 res.data 的类型标注。
// ======================================================================

/** 预算科目明细 */
export interface SubjectDetailDTO {
  /** 科目名称 */
  subjectName: string
  /** 预算金额 */
  budget: number
  /** 已付金额 */
  paid: number
  /** 占比（保留4位小数） */
  ratio: number
}

/** 预算执行数据 */
export interface BudgetExecutionDTO {
  /** 预算总额 */
  totalBudget: number
  /** 已使用金额 */
  usedAmount: number
  /** 使用率（保留4位小数） */
  usageRate: number
  /** 各科目明细 */
  subjects: SubjectDetailDTO[]
}

/** 进度完成率数据 */
export interface ProgressDTO {
  /** 总计划任务数 */
  totalTasks: number
  /** 已完成任务数 */
  completedTasks: number
  /** 完成百分比（保留4位小数） */
  completionRate: number
}

/** 合同回款数据 */
export interface ContractReceiptDTO {
  /** 施工合同总额 */
  contractTotal: number
  /** 累计开票金额 */
  invoicedAmount: number
  /** 累计回款金额 */
  receivedAmount: number
  /** 回款率（保留4位小数） */
  receiptRate: number
}

/** 月度产值 */
export interface MonthlyOutputDTO {
  /** 月份 YYYY-MM */
  month: string
  /** 产值金额 */
  amount: number
}

/** 产值上报趋势数据 */
export interface OutputTrendDTO {
  /** 累计上报产值 */
  totalOutput: number
  /** 本月产值 */
  monthOutput: number
  /** 近12月趋势（按月份升序） */
  trend: MonthlyOutputDTO[]
}

/** 项目看板聚合数据 */
export interface ProjectDashboardDTO {
  /** 预算执行 */
  budget: BudgetExecutionDTO
  /** 进度完成率 */
  progress: ProgressDTO
  /** 合同回款 */
  contract: ContractReceiptDTO
  /** 产值趋势 */
  output: OutputTrendDTO
}

/** 查询指定项目的预算执行数据（res.data 为 BudgetExecutionDTO；id 为后端雪花 ID，真实序列化为 string） */
export function getProjectBudget(projectId: number | string) {
  return request.get(`/v1/dashboard/project/${projectId}/budget`)
}

/** 查询指定项目的进度完成率（res.data 为 ProgressDTO） */
export function getProjectProgress(projectId: number | string) {
  return request.get(`/v1/dashboard/project/${projectId}/progress`)
}

/** 查询指定项目的合同回款数据（res.data 为 ContractReceiptDTO） */
export function getProjectContract(projectId: number | string) {
  return request.get(`/v1/dashboard/project/${projectId}/contract`)
}

/** 查询指定项目的产值上报趋势（res.data 为 OutputTrendDTO） */
export function getProjectOutput(projectId: number | string) {
  return request.get(`/v1/dashboard/project/${projectId}/output`)
}

/** 查询指定项目的看板聚合数据（一次调用四维度，res.data 为 ProjectDashboardDTO） */
export function getProjectOverview(projectId: number | string) {
  return request.get(`/v1/dashboard/project/${projectId}/overview`)
}

/** 查询指定项目的成本控制数据（Project Cost 360） */
export function getProjectCostControl(projectId: number | string) {
  return request.get(`/v1/dashboard/project/${projectId}/cost-control`)
}

// ======================================================================
// 项目成本控制看板（Project Cost 360） - Cost Control Backbone
// 后端：ProjectDashboardController.getCostControl()
//   GET /api/v1/dashboard/project/{projectId}/cost-control → ProjectCostControlDTO
// ======================================================================

/** 成本汇总指标 */
export interface ProjectCostTotals {
  /** 基准预算总额 */
  baselineTotal?: number
  /** 当前预算总额 */
  currentTotal?: number
  /** 已承诺总额 */
  commitmentTotal?: number
  /** 实际成本总额 */
  actualTotal?: number
  /** 预测完工总额 (EAC) */
  forecastTotal?: number
  /** 剩余预算 */
  remainingBudget?: number
  /** 偏差金额 */
  varianceAmount?: number
  /** 偏差率 (%) */
  varianceRate?: number
  /**
   * 预计超支（UI §7.1）= forecastTotal − baselineTotal，**正数 = 预计超出目标成本**。
   * 与 varianceAmount（相对当前预算）口径不同，不可互替。
   */
  forecastOverrun?: number
  /** 预算使用率 (%) */
  usageRate?: number
  /** 承诺率 (%) */
  commitmentRate?: number
}

/** 成本账户摘要 */
export interface CostAccountSummary {
  /** 账户 ID */
  accountId: number
  /** 账户编码 */
  code: string
  /** 账户名称 */
  name: string
  /** 费用类别 */
  costCategory?: string
  /** 费用子类 */
  costSubcategory?: string
  /** WBS 编码 */
  wbsCode?: string
  /** WBS 名称 */
  wbsName?: string
  /** 基准预算 */
  baseline: number
  /** 当前预算 */
  current: number
  /** 已承诺 */
  commitment: number
  /** 实际成本 */
  actual: number
  /** 预测（EAC） */
  forecast: number
  /** 剩余预算 */
  remaining: number
  /** 偏差金额 */
  variance: number
  /**
   * 偏差率 (%) = variance / current * 100。
   * **当前预算为 0 时为 null**（无基准不可算率），前端显示“—”而非 0%。
   */
  varianceRate?: number | null
  /** 使用率 (%) */
  usageRate: number
  /** 状态 */
  status: string
}

/** 按费用类别分组汇总 */
export interface CategorySummary {
  /** 费用类别 */
  costCategory?: string
  /** 类别名称 */
  categoryName: string
  /** 基准预算 */
  baseline: number
  /** 当前预算 */
  current: number
  /** 已承诺 */
  commitment: number
  /** 实际成本 */
  actual: number
  /** 预测 */
  forecast: number
  /** 偏差 */
  variance: number
  /** 偏差率 (%)；当前预算为 0 时 null */
  varianceRate?: number | null
  /** 账户数量 */
  accountCount: number
}

/**
 * UI §7.2「七类成本结构」汇总（材料/分包/人工/机械/措施/管理/商务 + 未归类）。
 * 与 CBS 六类 {@link CategorySummary} **并存不互替**。
 */
export interface DocCategorySummary {
  /** 文档类别码 */
  code: 'MATERIAL' | 'SUBCONTRACT' | 'LABOR' | 'MACHINE' | 'MEASURE' | 'ADMIN' | 'BUSINESS' | 'OTHER'
  /** 中文名 */
  name: string
  /**
   * 归类依据（不隐藏映射规则）：
   * CBS_CATEGORY=由 cost_category 直接映射；
   * CBS_SUBCATEGORY_KEYWORD=间接费按子类名关键词细分；
   * UNCLASSIFIED=未能归类（如实单列，不并入其他类）
   */
  basis: string
  /** 预算（当前预算） */
  current: number
  /** 实际发生 */
  actual: number
  /** 预计最终（EAC） */
  forecast: number
  /** 偏差金额（current − forecast，正数=节约） */
  variance: number
  /** 偏差率 (%)；预算为 0 时 null */
  varianceRate?: number | null
  /** 风险等级：RED 超支&gt;10% / YELLOW 超支 / GREEN 未超支 / INFO 无预算基准 */
  riskLevel: 'RED' | 'YELLOW' | 'GREEN' | 'INFO'
  /** 账户数量 */
  accountCount: number
}

/** 月度趋势数据 */
export interface MonthlyTrend {
  /** 月份 YYYY-MM */
  month: string
  /** 当月实际成本 */
  monthlyActual: number
  /** 累计实际成本 */
  cumulativeActual: number
  /** 累计承诺 */
  cumulativeCommitment: number
  /** 累计预测 */
  cumulativeForecast: number
}

/** 项目成本控制看板聚合数据 */
export interface ProjectCostControlDTO {
  /** 项目 ID */
  projectId: number
  /** 项目名称 */
  projectName: string
  /** WBS 节点数量 */
  wbsCount: number
  /** 成本账户数量 */
  accountCount: number
  /** 汇总指标 */
  totals: ProjectCostTotals
  /** 成本账户明细列表 */
  accounts: CostAccountSummary[]
  /** 按费用类别分组汇总 */
  categorySummaries: CategorySummary[]
  /** 按 UI §7.2 七类口径分组汇总（恒 8 行：7 类 + 未归类） */
  docCategories?: DocCategorySummary[]
  /** 趋势数据 */
  trends: MonthlyTrend[]
}
