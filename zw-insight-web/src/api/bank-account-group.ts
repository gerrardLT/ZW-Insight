// 银行账户分组 API（多级树，55_V2026_53）
import request from '@/utils/request'
import type { R } from '@/types/api'

export interface BankAccountGroup {
  id?: number
  groupName: string
  groupCode: string
  parentId?: number
  level?: number
  sortOrder?: number
  status?: string
  remark?: string
  children?: BankAccountGroup[]
}

export function getGroupTree() {
  return request.get<R<BankAccountGroup[]>>('/v1/finance/bank-account-group/tree')
}

export function getGroupList(params?: { groupName?: string; groupCode?: string; level?: number }) {
  return request.get<R<BankAccountGroup[]>>('/v1/finance/bank-account-group/list', { params })
}

export function saveGroup(data: BankAccountGroup) {
  return request.post<R<void>>('/v1/finance/bank-account-group', data)
}

export function updateGroup(id: number, data: BankAccountGroup) {
  return request.put<R<void>>(`/v1/finance/bank-account-group/${id}`, data)
}

export function deleteGroup(id: number) {
  return request.delete<R<void>>(`/v1/finance/bank-account-group/${id}`)
}

export function toggleGroupStatus(id: number, enabled: boolean) {
  return request.put<R<void>>(`/v1/finance/bank-account-group/${id}/status`, null, { params: { enabled } })
}
