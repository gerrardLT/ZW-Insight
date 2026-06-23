import request from '@/utils/request'

// 获取看板统计数据
export function getDashboardStats() {
  return request.get('/v1/dashboard/stats')
}

// 获取项目状态分布
export function getProjectStatusDistribution() {
  return request.get('/v1/dashboard/project-status')
}

// 获取收支对比数据
export function getIncomeExpenseComparison() {
  return request.get('/v1/dashboard/income-expense')
}

// 获取待办事项
export function getTodoList() {
  return request.get('/v1/dashboard/todo')
}
