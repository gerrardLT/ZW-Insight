import request from '@/utils/request'

// ======================== 目标成本变更 ========================

/** 变更单分页列表 */
export function listBudgetChanges(params: any) {
  return request.get('/v1/budget/change/page', { params })
}

/** 变更单详情 */
export function getBudgetChange(id: number) {
  return request.get(`/v1/budget/change/${id}`)
}

/** 变更单明细列表 */
export function getBudgetChangeDetails(id: number) {
  return request.get(`/v1/budget/change/${id}/details`)
}

/** 新建变更单 */
export function createBudgetChange(data: any) {
  return request.post('/v1/budget/change', data)
}

/** 更新变更单 */
export function updateBudgetChange(id: number, data: any) {
  return request.put(`/v1/budget/change/${id}`, data)
}

/** 删除变更单 */
export function deleteBudgetChange(id: number) {
  return request.delete(`/v1/budget/change/${id}`)
}

/** 提交审批 */
export function submitBudgetChange(id: number) {
  return request.post(`/v1/budget/change/${id}/submit`)
}

/** 撤回变更单 */
export function withdrawBudgetChange(id: number) {
  return request.post(`/v1/budget/change/${id}/withdraw`)
}

/** 变更轨迹查询（按项目） */
export function getBudgetChangeTrace(projectId: number) {
  return request.get('/v1/budget/change/trace', { params: { projectId } })
}
