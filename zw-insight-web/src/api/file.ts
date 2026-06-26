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
  return request.get<any, any>('/api/v1/file/serial')
}

/** 新增编号规则 */
export function createSerialNumber(data: SerialNumberRule) {
  return request.post('/api/v1/file/serial', data)
}

/** 更新编号规则 */
export function updateSerialNumber(id: number, data: SerialNumberRule) {
  return request.put(`/api/v1/file/serial/${id}`, data)
}

/** 删除编号规则 */
export function deleteSerialNumber(id: number) {
  return request.delete(`/api/v1/file/serial/${id}`)
}

/** 预览生成编号 */
export function generateSerialNumber(businessType: string) {
  return request.post<any, any>(`/api/v1/file/serial/generate/${businessType}`)
}
