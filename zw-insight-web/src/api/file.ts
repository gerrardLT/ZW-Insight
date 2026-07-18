import request from '@/utils/request'

// ======================== 编号规则管理 ========================

/** 编号规则接口类型 */
export interface SerialNumberRule {
  id?: number
  businessType: string
  rulePrefix: string
  dateFormat: string
  seqLength: number
  resetPeriod: string
  description?: string
}

/** 获取编号规则列表 */
export function getSerialNumberList() {
  return request.get<any, any>('/v1/file/serial')
}

/** 新增编号规则 */
export function createSerialNumber(data: SerialNumberRule) {
  return request.post('/v1/file/serial', data)
}

/** 更新编号规则 */
export function updateSerialNumber(id: number, data: SerialNumberRule) {
  return request.put(`/v1/file/serial/${id}`, data)
}

/** 删除编号规则 */
export function deleteSerialNumber(id: number) {
  return request.delete(`/v1/file/serial/${id}`)
}

/** 预览生成编号 */
export function generateSerialNumber(businessType: string) {
  return request.post<any, any>(`/v1/file/serial/generate/${businessType}`)
}

// ======================== 存储配置管理（StorageController） ========================

/** 存储配置接口类型 */
export interface FileStorage {
  id?: number
  storageType: string
  endpoint?: string
  accessKey?: string
  secretKey?: string
  bucket?: string
  basePath?: string
  status?: number
}

/** 存储配置分页列表 */
export function getStoragePage(params: { page?: number; size?: number }) {
  return request.get('/v1/file/storage', { params })
}

/** 存储配置详情 */
export function getStorageById(id: number) {
  return request.get(`/v1/file/storage/${id}`)
}

/** 新增存储配置 */
export function createStorage(data: FileStorage) {
  return request.post('/v1/file/storage', data)
}

/** 更新存储配置 */
export function updateStorage(id: number, data: FileStorage) {
  return request.put(`/v1/file/storage/${id}`, data)
}

/** 删除存储配置 */
export function deleteStorage(id: number) {
  return request.delete(`/v1/file/storage/${id}`)
}
