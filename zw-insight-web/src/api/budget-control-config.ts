import request from '@/utils/request'

export function listBudgetControlConfigs(params: any) {
  return request.get('/v1/budget-control-configs', { params })
}

export function getBudgetControlConfig(id: number) {
  return request.get(`/v1/budget-control-configs/${id}`)
}

export function createBudgetControlConfig(data: any) {
  return request.post('/v1/budget-control-configs', data)
}

export function updateBudgetControlConfig(id: number, data: any) {
  return request.put(`/v1/budget-control-configs/${id}`, data)
}

export function deleteBudgetControlConfig(id: number) {
  return request.delete(`/v1/budget-control-configs/${id}`)
}

export function getEffectiveConfig(projectId: number) {
  return request.get(`/v1/budget-control-configs/project/${projectId}`)
}
