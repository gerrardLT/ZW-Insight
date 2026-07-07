import request from '@/utils/request'
import type { R, PageResult, PageQuery } from '@/types/api'
import type {
  Budget,
  BudgetCreateRequest,
  BudgetPageQuery,
  BudgetChangeRequest,
  BudgetControlConfig,
  BudgetSubcategory
} from '@/types/budget'

// ======================== 预算编制 ========================
export function getBudgetPage(params: BudgetPageQuery) {
  return request.get<R<PageResult<Budget>>>('/v1/budget/page', { params })
}

export function getBudgetDetail(id: number) {
  return request.get<R<Budget>>(`/v1/budget/${id}`)
}

export function createBudget(data: BudgetCreateRequest) {
  return request.post<R<void>>('/v1/budget', data)
}

export function updateBudget(data: BudgetCreateRequest & { id: number }) {
  return request.put<R<void>>(`/v1/budget/${data.id}`, data)
}

export function deleteBudget(id: number) {
  return request.delete<R<void>>(`/v1/budget/${id}`)
}

export function submitBudget(id: number) {
  return request.put<R<void>>(`/v1/budget/${id}/submit`)
}

// ======================== 预算变更 ========================
export interface BudgetChange {
  id: number
  projectId: number
  budgetId: number
  changeReason: string
  totalAdjustAmount?: number
  status: string
  createdAt?: string
}

export function getBudgetChangePage(params: PageQuery & { projectId?: number; budgetId?: number }) {
  return request.get<R<PageResult<BudgetChange>>>('/v1/budget/change/page', { params })
}

export function createBudgetChange(data: BudgetChangeRequest) {
  return request.post<R<void>>('/v1/budget/change', data)
}

export function updateBudgetChange(data: BudgetChangeRequest & { id: number }) {
  return request.put<R<void>>(`/v1/budget/change/${data.id}`, data)
}

export function deleteBudgetChange(id: number) {
  return request.delete<R<void>>(`/v1/budget/change/${id}`)
}

export function submitBudgetChange(id: number) {
  return request.post<R<void>>(`/v1/budget/change/${id}/submit`)
}

// ======================== 预算配置 ========================
export function getBudgetConfigList(params: { projectId?: number }) {
  return request.get<R<BudgetControlConfig[]>>('/v1/budget/config/list', { params })
}

export function saveBudgetConfig(data: Partial<BudgetControlConfig>) {
  return request.post<R<void>>('/v1/budget/config', data)
}

export function updateBudgetConfig(data: BudgetControlConfig) {
  return request.put<R<void>>(`/v1/budget/config/${data.id}`, data)
}

export function deleteBudgetConfig(id: number) {
  return request.delete<R<void>>(`/v1/budget/config/${id}`)
}

// ======================== 二级科目 ========================
export function getBudgetSubcategoryList(costCategory: string) {
  return request.get<R<BudgetSubcategory[]>>(`/v1/budget/subcategory/${costCategory}`)
}

export function createBudgetSubcategory(data: Partial<BudgetSubcategory>) {
  return request.post<R<void>>('/v1/budget/subcategory', data)
}

export function updateBudgetSubcategory(id: number, data: Partial<BudgetSubcategory>) {
  return request.put<R<void>>(`/v1/budget/subcategory/${id}`, data)
}

export function deleteBudgetSubcategory(id: number) {
  return request.delete<R<void>>(`/v1/budget/subcategory/${id}`)
}
