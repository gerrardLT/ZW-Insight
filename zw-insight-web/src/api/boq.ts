import request from '@/utils/request'

/**
 * 上传工程量清单 Excel 文件
 */
export function uploadBoq(contractId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/v1/contracts/${contractId}/boq/upload`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

/**
 * 查询合同关联的清单树形结构
 */
export function getBoqTree(contractId: number) {
  return request.get(`/v1/contracts/${contractId}/boq`)
}

/**
 * 查询清单平铺列表（供产值上报使用）
 */
export function getBoqFlat(contractId: number) {
  return request.get(`/v1/contracts/${contractId}/boq/flat`)
}

/**
 * 清除合同清单数据
 */
export function deleteBoq(contractId: number) {
  return request.delete(`/v1/contracts/${contractId}/boq`)
}
