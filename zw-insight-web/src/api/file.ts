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

// ======================== 通用文件服务（FileController） ========================

export interface FileInfo {
  id: number
  originalName: string
  fileName: string
  filePath: string
  fileSize: number
  fileType: string
  storageType: string
  businessType?: string
  businessId?: number
  projectId?: number
  createdAt?: string
}

/** 通用文件上传 */
export function uploadFile(file: File, businessType?: string, businessId?: number | string, projectId?: number | string) {
  const formData = new FormData()
  formData.append('file', file)
  if (businessType) formData.append('businessType', businessType)
  if (businessId) formData.append('businessId', String(businessId))
  if (projectId) formData.append('projectId', String(projectId))
  return request.post<any, { code: number; data: FileInfo }>('/v1/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 删除文件 */
export function deleteFile(id: number | string) {
  return request.delete(`/v1/file/${id}`)
}

/** 根据业务类型与业务ID查询文件列表 */
export function getFileListByBusiness(businessType: string, businessId: number | string) {
  return request.get<any, { code: number; data: FileInfo[] }>('/v1/file/list', {
    params: { businessType, businessId }
  })
}

/** 批量回填绑定业务主体 */
export function bindBusinessFiles(data: {
  fileIds: (number | string)[]
  businessType: string
  businessId: number | string
  projectId?: number | string
}) {
  return request.put<any, { code: number; data: number }>('/v1/file/bind-business', data)
}
