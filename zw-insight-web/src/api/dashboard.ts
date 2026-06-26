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

// ======================== 兼容别名（页面引用） ========================
export function getDashboardStats() {
  return getCompanyOverview()
}

export function getProjectStatusDistribution() {
  return request.get('/v1/dashboard/company-overview')
}

export function getIncomeExpenseComparison() {
  return request.get('/v1/dashboard/receivable-monitor')
}

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

/** 查询指定项目的预算执行数据（res.data 为 BudgetExecutionDTO） */
export function getProjectBudget(projectId: number) {
  return request.get(`/v1/dashboard/project/${projectId}/budget`)
}

/** 查询指定项目的进度完成率（res.data 为 ProgressDTO） */
export function getProjectProgress(projectId: number) {
  return request.get(`/v1/dashboard/project/${projectId}/progress`)
}

/** 查询指定项目的合同回款数据（res.data 为 ContractReceiptDTO） */
export function getProjectContract(projectId: number) {
  return request.get(`/v1/dashboard/project/${projectId}/contract`)
}

/** 查询指定项目的产值上报趋势（res.data 为 OutputTrendDTO） */
export function getProjectOutput(projectId: number) {
  return request.get(`/v1/dashboard/project/${projectId}/output`)
}

/** 查询指定项目的看板聚合数据（一次调用四维度，res.data 为 ProjectDashboardDTO） */
export function getProjectOverview(projectId: number) {
  return request.get(`/v1/dashboard/project/${projectId}/overview`)
}
