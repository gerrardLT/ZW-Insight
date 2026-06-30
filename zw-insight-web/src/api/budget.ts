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
  return request.put(`/v1/budget/change/${data.id}`, data)
}

export function deleteBudgetChange(id: number) {
  return request.delete(`/v1/budget/change/${id}`)
}

export function submitBudgetChange(id: number) {
  return request.post(`/v1/budget/change/${id}/submit`)
}

// ======================== 预算配置 ========================
export function getBudgetConfigList(params: any) {
  return request.get('/v1/budget/config/list', { params })
}

export function saveBudgetConfig(data: any) {
  return request.post('/v1/budget/config', data)
}

export function updateBudgetConfig(data: any) {
  return request.put(`/v1/budget/config/${data.id}`, data)
}

export function deleteBudgetConfig(id: number) {
  return request.delete(`/v1/budget/config/${id}`)
}

// ======================== 二级科目 ========================
export function getBudgetSubcategoryList(costCategory: string) {
  return request.get(`/v1/budget/subcategory/${costCategory}`)
}

export function createBudgetSubcategory(data: any) {
  return request.post('/v1/budget/subcategory', data)
}

export function updateBudgetSubcategory(id: number, data: any) {
  return request.put(`/v1/budget/subcategory/${id}`, data)
}

export function deleteBudgetSubcategory(id: number) {
  return request.delete(`/v1/budget/subcategory/${id}`)
}
