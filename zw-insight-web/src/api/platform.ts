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

// ======================== 租户类型管理（SysTenantTypeController） ========================

/** 租户类型接口类型 */
export interface TenantType {
  id?: number
  typeName: string
  durationDays: number
  sortOrder?: number
  status?: number
}

/** 租户类型分页列表 */
export function getTenantTypePage(params: { page?: number; size?: number; typeName?: string; status?: number }) {
  return request.get('/v1/platform/tenant-type', { params })
}

/** 租户类型详情 */
export function getTenantTypeById(id: number) {
  return request.get(`/v1/platform/tenant-type/${id}`)
}

/** 新增租户类型 */
export function createTenantType(data: TenantType) {
  return request.post('/v1/platform/tenant-type', data)
}

/** 更新租户类型 */
export function updateTenantType(id: number, data: TenantType) {
  return request.put(`/v1/platform/tenant-type/${id}`, data)
}

/** 删除租户类型 */
export function deleteTenantType(id: number) {
  return request.delete(`/v1/platform/tenant-type/${id}`)
}

/** 批量删除租户类型 */
export function batchDeleteTenantType(ids: number[]) {
  return request.delete('/v1/platform/tenant-type/batch', { data: ids })
}
