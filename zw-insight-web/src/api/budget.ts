import request from '@/utils/request'

// ======================== 预算编制 ========================
export function getBudgetPage(params: any) {
  return request.get('/v1/budget/page', { params })
}

export function getBudgetDetail(id: number) {
  return request.get(`/v1/budget/${id}`)
}

export function createBudget(data: any) {
  return request.post('/v1/budget', data)
}

export function updateBudget(data: any) {
  return request.put('/v1/budget', data)
}

export function deleteBudget(id: number) {
  return request.delete(`/v1/budget/${id}`)
}

export function submitBudget(id: number) {
  return request.put(`/v1/budget/${id}/submit`)
}

// ======================== 预算变更 ========================
export function getBudgetChangePage(params: any) {
  return request.get('/v1/budget/change/page', { params })
}

export function createBudgetChange(data: any) {
  return request.post('/v1/budget/change', data)
}

export function updateBudgetChange(data: any) {
  return request.put('/v1/budget/change', data)
}

export function deleteBudgetChange(id: number) {
  return request.delete(`/v1/budget/change/${id}`)
}

export function submitBudgetChange(id: number) {
  return request.put(`/v1/budget/change/${id}/submit`)
}

// ======================== 预算配置 ========================
export function getBudgetConfigList(params: any) {
  return request.get('/v1/budget/config/list', { params })
}

export function saveBudgetConfig(data: any) {
  return request.post('/v1/budget/config', data)
}

export function updateBudgetConfig(data: any) {
  return request.put('/v1/budget/config', data)
}

export function deleteBudgetConfig(id: number) {
  return request.delete(`/v1/budget/config/${id}`)
}

// ======================== 二级科目 ========================
export function getBudgetSubjectList(params: any) {
  return request.get('/v1/budget/subject/list', { params })
}

export function createBudgetSubject(data: any) {
  return request.post('/v1/budget/subject', data)
}

export function updateBudgetSubject(data: any) {
  return request.put('/v1/budget/subject', data)
}

export function deleteBudgetSubject(id: number) {
  return request.delete(`/v1/budget/subject/${id}`)
}
