// 资金收支分类科目 API（53_V2026_51）
import request from '@/utils/request'
import type { R } from '@/types/api'

export interface FundCategory {
  id?: number
  code: string
  name: string
  direction: 'INCOME' | 'EXPENSE'
  parentId?: number
  level?: number
  sortOrder?: number
  status?: string
  isSystem?: number
  children?: FundCategory[]
}

export function getFundCategoryTree(direction?: string) {
  return request.get<R<FundCategory[]>>('/v1/finance/fund-category/tree', { params: { direction } })
}

export function getEnabledCategories(direction?: string) {
  return request.get<R<FundCategory[]>>('/v1/finance/fund-category/enabled', { params: { direction } })
}

export function saveFundCategory(data: FundCategory) {
  return request.post<R<void>>('/v1/finance/fund-category', data)
}

export function updateFundCategory(id: number, data: FundCategory) {
  return request.put<R<void>>(`/v1/finance/fund-category/${id}`, data)
}

export function disableFundCategory(id: number) {
  return request.put<R<void>>(`/v1/finance/fund-category/${id}/disable`)
}

export function deleteFundCategory(id: number) {
  return request.delete<R<void>>(`/v1/finance/fund-category/${id}`)
}
