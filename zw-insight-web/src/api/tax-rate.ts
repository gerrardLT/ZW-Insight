import request from '@/utils/request'

// ======================== 税率管理 ========================

/** 税率数据传输对象 */
export interface TaxRateDTO {
  id: number
  /** 税率名称 */
  name: string
  /** 税率数值（如 13.00 表示 13%） */
  rateValue: number
  /** 状态：ENABLED 启用 / DISABLED 停用 */
  status: 'ENABLED' | 'DISABLED'
  /** 创建时间 */
  createTime: string
}

/** 新增/修改税率请求体 */
export interface TaxRateRequest {
  name: string
  rateValue: number
}

/** 新增税率 */
export function createTaxRate(data: TaxRateRequest) {
  return request.post('/v1/finance/tax-rate', data)
}

/** 修改税率 */
export function updateTaxRate(id: number, data: TaxRateRequest) {
  return request.put(`/v1/finance/tax-rate/${id}`, data)
}

/** 停用税率（逻辑删除） */
export function deleteTaxRate(id: number) {
  return request.delete(`/v1/finance/tax-rate/${id}`)
}

/** 查询启用税率列表 */
export function getTaxRateList() {
  return request.get('/v1/finance/tax-rate/list')
}

/** 查询全部税率（含停用） */
export function getAllTaxRates() {
  return request.get('/v1/finance/tax-rate/all')
}
