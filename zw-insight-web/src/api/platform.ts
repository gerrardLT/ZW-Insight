import request from '@/utils/request'

// ======================== 租户管理 ========================

/** 租户分页列表 */
export function getTenantPage(params: any) {
  return request.get('/v1/platform/tenant', { params })
}

/** 创建租户 */
export function createTenant(data: any) {
  return request.post('/v1/platform/tenant', data)
}

/** 更新租户 */
export function updateTenant(id: number, data: any) {
  return request.put(`/v1/platform/tenant/${id}`, data)
}

/** 停用租户 */
export function disableTenant(id: number) {
  return request.post(`/v1/platform/tenant/${id}/disable`)
}

/** 启用租户 */
export function enableTenant(id: number) {
  return request.post(`/v1/platform/tenant/${id}/enable`)
}

/** 租户续期 */
export function renewTenant(id: number, durationDays: number) {
  return request.post(`/v1/platform/tenant/${id}/renew`, { durationDays })
}

/** 配置租户功能模块 */
export function updateTenantModules(id: number, modules: string[]) {
  return request.put(`/v1/platform/tenant/${id}/modules`, { modules })
}
