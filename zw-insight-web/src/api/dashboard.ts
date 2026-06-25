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
