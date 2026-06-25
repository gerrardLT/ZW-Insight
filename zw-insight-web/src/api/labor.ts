import request from '@/utils/request'

// ======================== 劳务合同 ========================
export function getLaborContractPage(params: any) {
  return request.get('/v1/labor/contract/page', { params })
}

export function getLaborContractDetail(id: number) {
  return request.get(`/v1/labor/contract/${id}`)
}

export function createLaborContract(data: any) {
  return request.post('/v1/labor/contract', data)
}

export function updateLaborContract(data: any) {
  return request.put(`/v1/labor/contract/${data.id}`, data)
}

export function deleteLaborContract(id: number) {
  return request.delete(`/v1/labor/contract/${id}`)
}

export function submitLaborContract(id: number) {
  return request.put(`/v1/labor/contract/${id}/submit`)
}

// ======================== 产值上报 ========================
export function getLaborOutputPage(params: any) {
  return request.get('/v1/labor/output-report/page', { params })
}

export function createLaborOutput(data: any) {
  return request.post('/v1/labor/output-report', data)
}

export function updateLaborOutput(data: any) {
  return request.put(`/v1/labor/output-report/${data.id}`, data)
}

export function deleteLaborOutput(id: number) {
  return request.delete(`/v1/labor/output-report/${id}`)
}

// ======================== 劳务结算 ========================
export function getLaborSettlementPage(params: any) {
  return request.get('/v1/labor/settlement/page', { params })
}

export function createLaborSettlement(data: any) {
  return request.post('/v1/labor/settlement', data)
}

export function updateLaborSettlement(data: any) {
  return request.put(`/v1/labor/settlement/${data.id}`, data)
}

export function deleteLaborSettlement(id: number) {
  return request.delete(`/v1/labor/settlement/${id}`)
}

export function submitLaborSettlement(id: number) {
  return request.put(`/v1/labor/settlement/${id}/submit`)
}

// ======================== 奖惩 ========================
export function getLaborRewardPage(params: any) {
  return request.get('/v1/labor/reward-punish/page', { params })
}

export function createLaborReward(data: any) {
  return request.post('/v1/labor/reward-punish', data)
}

export function deleteLaborReward(id: number) {
  return request.delete(`/v1/labor/reward-punish/${id}`)
}

// ======================== 班组管理 ========================
export function getLaborTeamPage(params: any) {
  return request.get('/v1/labor/team/page', { params })
}

export function createLaborTeam(data: any) {
  return request.post('/v1/labor/team', data)
}

export function updateLaborTeam(data: any) {
  return request.put(`/v1/labor/team/${data.id}`, data)
}

export function deleteLaborTeam(id: number) {
  return request.delete(`/v1/labor/team/${id}`)
}

// ======================== 花名册 ========================
export function getLaborRosterPage(params: any) {
  return request.get('/v1/labor/roster/page', { params })
}

export function createLaborRoster(data: any) {
  return request.post('/v1/labor/roster', data)
}

export function updateLaborRoster(data: any) {
  return request.put(`/v1/labor/roster/${data.id}`, data)
}

export function deleteLaborRoster(id: number) {
  return request.delete(`/v1/labor/roster/${id}`)
}

// ======================== 用工单 ========================
export function getWorkOrderPage(params: any) {
  return request.get('/v1/labor/work-order/page', { params })
}

export function createWorkOrder(data: any) {
  return request.post('/v1/labor/work-order', data)
}

export function updateWorkOrder(data: any) {
  return request.put(`/v1/labor/work-order/${data.id}`, data)
}

export function deleteWorkOrder(id: number) {
  return request.delete(`/v1/labor/work-order/${id}`)
}

export function submitWorkOrder(id: number) {
  return request.put(`/v1/labor/work-order/${id}/submit`)
}

// ======================== 工资单 ========================
export function getPayrollPage(params: any) {
  return request.get('/v1/labor/payroll/page', { params })
}

export function createPayroll(data: any) {
  return request.post('/v1/labor/payroll', data)
}

export function updatePayroll(data: any) {
  return request.put(`/v1/labor/payroll/${data.id}`, data)
}

export function deletePayroll(id: number) {
  return request.delete(`/v1/labor/payroll/${id}`)
}

export function submitPayroll(id: number) {
  return request.put(`/v1/labor/payroll/${id}/submit`)
}

// ======================== 薪资统计 ========================
export function getSalaryStats(params: { projectId: number; month: string }) {
  return request.get('/v1/labor/salary/stats', { params })
}

export function getSalaryDetail(params: { projectId: number; month: string; teamId: number; page: number; size: number }) {
  return request.get('/v1/labor/salary/detail', { params })
}

export function getSalaryCompare(params: { projectId: number; month: string }) {
  return request.get('/v1/labor/salary/compare', { params })
}

export function exportSalaryExcel(projectId: number, month: string) {
  const token = localStorage.getItem('token')
  const baseUrl = '/api/v1/labor/salary/export'
  const params = new URLSearchParams({ projectId: String(projectId), month })
  const url = `${baseUrl}?${params.toString()}`

  // 使用 window.open 方式触发文件下载，避免响应拦截器解析 blob
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', `薪资统计_${month}.xlsx`)

  // 如果需要 token 认证，使用 fetch + blob
  return fetch(url, {
    headers: { Authorization: `Bearer ${token || ''}` }
  }).then(res => {
    if (!res.ok) throw new Error('导出失败')
    return res.blob()
  })
}
