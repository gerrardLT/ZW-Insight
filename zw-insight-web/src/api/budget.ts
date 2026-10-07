import request from '@/utils/request'
import type { R, PageResult, PageQuery } from '@/types/api'
import type {
  Budget,
  BudgetCreateRequest,
  BudgetPageQuery,
  BudgetSubcategory
} from '@/types/budget'

// ======================== 预算编制 ========================
export function getBudgetPage(params: BudgetPageQuery) {
  return request.get<R<PageResult<Budget>>>('/v1/budget/page', { params })
}

export function getBudgetDetail(id: number) {
  return request.get<R<Budget>>(`/v1/budget/${id}`)
}

/** 查询指定预算下的全部明细（供变更表单选择原预算明细） */
export function getBudgetDetailsByBudgetId(budgetId: number) {
  return request.get<R<any[]>>(`/v1/budget/${budgetId}/details`)
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
  return request.post<R<void>>(`/v1/budget/${id}/submit`)
}

// ======================== 二级科目（P2-M4：待接入 CBS 表单下拉，端点合法保留） ========================
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
