import request from '@/utils/request'

/**
 * 获取检查方案列表（按检查类型筛选已启用方案）
 */
export function listInspectionSchemes(params: { inspectionType?: string; pageNum?: number; pageSize?: number }) {
  return request.get('/v1/inspection-schemes', { params })
}

/**
 * 获取方案下的检查项列表
 */
export function getSchemeItems(schemeId: number) {
  return request.get(`/v1/inspection-schemes/${schemeId}/items`)
}

/**
 * 将方案关联到检查记录（自动填充检查明细）
 */
export function applyScheme(inspectionId: number, schemeId: number) {
  return request.post(`/v1/inspections/${inspectionId}/apply-scheme`, { schemeId })
}

/**
 * 获取检查记录详情（含方案快照）
 */
export function getInspectionDetail(id: number) {
  return request.get(`/v1/site/inspection/${id}`)
}

/**
 * 更新检查明细
 */
export function updateInspectionDetails(inspectionId: number, details: any[]) {
  return request.put(`/v1/site/inspection/${inspectionId}/details`, details)
}
