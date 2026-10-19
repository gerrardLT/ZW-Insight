import request from '@/utils/request'
import type { R, PageResult, ID } from '@/types/api'
import type {
  Project,
  ProjectCreateRequest,
  ProjectPageQuery,
  ProjectMember,
  ProjectMemberAddRequest,
  UpdateMemberRolesRequest
} from '@/types/project'

// ======================== 项目管理 ========================
export function getProjectPage(params: ProjectPageQuery) {
  return request.get<R<PageResult<Project>>>('/v1/project/page', { params })
}

export function getProjectList(params?: Partial<ProjectPageQuery>) {
  return request.get<R<Project[]>>('/v1/project/list', { params })
}

export function getProjectDetail(id: number | string) {
  return request.get<R<Project>>(`/v1/project/${id}`)
}

export function createProject(data: ProjectCreateRequest) {
  return request.post<R<void>>('/v1/project', data)
}

export function updateProject(data: ProjectCreateRequest & { id: number }) {
  return request.put<R<void>>(`/v1/project/${data.id}`, data)
}

export function deleteProject(id: number) {
  return request.delete<R<void>>(`/v1/project/${id}`)
}

/**
 * 批量删除项目（仅 DRAFT 草稿可删；空列表拒绝，整体单事务，任一失败回滚）
 * 后端：DELETE /api/v1/project/batch（ProjectController#batchDelete）
 */
export function batchDeleteProjects(ids: number[]) {
  return request.delete<R<void>>('/v1/project/batch', { data: ids })
}

export function submitProject(id: number | string) {
  return request.post<R<void>>(`/v1/project/${id}/submit`)
}

// ======================== P1-M1 状态机事件端点 ========================

/** 撤回立项（FILED → DRAFT） */
export function withdrawProject(id: number | string) {
  return request.post<R<void>>(`/v1/project/${id}/withdraw`)
}

/** 落标归档（TENDERING → LOST，须附原因） */
export function loseBidProject(id: number | string, reason: string) {
  return request.post<R<void>>(`/v1/project/${id}/lose-bid`, null, { params: { reason } })
}

/** 暂停施工（CONSTRUCTION → PAUSED，须附原因） */
export function pauseProject(id: number | string, reason: string) {
  return request.post<R<void>>(`/v1/project/${id}/pause`, null, { params: { reason } })
}

/** 恢复施工（PAUSED → CONSTRUCTION） */
export function resumeProject(id: number | string) {
  return request.post<R<void>>(`/v1/project/${id}/resume`)
}

/** 发起终止审批（非终态 → TERMINATING） */
export function terminateProject(id: number | string, reason: string) {
  return request.post<R<void>>(`/v1/project/${id}/terminate`, null, { params: { reason } })
}

/** 查询项目流转大事记 */
export function getProjectStatusLog(id: number | string) {
  return request.get<R<import('@/types/project').ProjectStatusLog[]>>(`/v1/project/${id}/status-log`)
}

/** 查询项目关键字段变更台账 */
export function getProjectChangeLog(id: number | string) {
  return request.get<R<import('@/types/project').ProjectChangeLog[]>>(`/v1/project/${id}/change-log`)
}

// 结项条件预检（发起结项前调用，提示哪些条件未满足）
export interface CloseCheckCondition {
  name: string
  passed: boolean
  message: string
}
export interface CloseCheckResult {
  allPassed: boolean
  conditions: CloseCheckCondition[]
  failedReasons: string[]
}
export function getProjectCloseCheck(id: number | string) {
  return request.get<R<CloseCheckResult>>(`/v1/project/${id}/close-check`)
}

// 发起项目结项审批（校验通过后状态置 CLOSING，审批通过后置 CLOSED）
export function closeProject(id: number | string) {
  return request.post<R<void>>(`/v1/project/${id}/close`)
}

// ======================== 建设单位（甲方） ========================
export interface Owner {
  id: number
  ownerName: string
  contactName?: string
  contactPhone?: string
}

export function getOwnerList(params?: Record<string, unknown>) {
  return request.get<R<Owner[]>>('/v1/basedata/owner/list', { params })
}

export function getOwnerPage(params?: Record<string, unknown>) {
  return request.get<R<PageResult<Owner>>>('/v1/basedata/owner/page', { params })
}

// ======================== 签约公司 ========================
export interface Company {
  id: number
  companyName: string
}

export function getCompanyList(params?: Record<string, unknown>) {
  return request.get<R<Company[]>>('/v1/basedata/company/list', { params })
}

// ======================== 项目成员管理 ========================
export function getProjectMembers(projectId: number | string, params?: Record<string, unknown>) {
  return request.get<R<ProjectMember[]>>(`/v1/project/${projectId}/member`, { params })
}

export function addProjectMember(projectId: number | string, data: ProjectMemberAddRequest) {
  return request.post<R<void>>(`/v1/project/${projectId}/member`, data)
}

export function removeProjectMember(projectId: number | string, userId: number | string) {
  return request.delete<R<void>>(`/v1/project/${projectId}/member/${userId}`)
}

export function updateMemberRoles(projectId: number | string, userId: number | string, data: UpdateMemberRolesRequest) {
  return request.put<R<void>>(`/v1/project/${projectId}/member/${userId}/roles`, data)
}

// ======================== 项目组合看板 ========================

/** 项目组合看板（状态 × 金额分布） */
export function getProjectPortfolio() {
  return request.get<R<any>>('/v1/project/portfolio')
}


// ======================== 项目一本账（V2026_117 只读聚合） ========================

/** 一本账六条线（后端 ProjectOnebookController /v1/project/{id}/onebook） */
export interface ProjectOnebook {
  projectId: ID
  projectName: string
  status: string
  income: {
    contractAmount: number
    visaAmount: number
    outputAmount: number
    settlementAmount: number
    invoicedAmount: number
    receivedAmount: number
    unreceived: number
    receivableOpen: number
  }
  expense: {
    categories: Array<{ category: string; contractAmount: number; settled: number; paid: number; outstanding: number; invoiceReceived: number }>
    totalSettled: number
    totalPaid: number
    totalOutstanding: number
    applying: number
    reimbursementApproved: number
    reserveOutstanding: number
    wageUnpaid: number
    otherPayment: number
  }
  cost: {
    baseline: number
    currentBudget: number
    commitment: number
    actual: number
    forecast: number
    hasCbs: boolean
  }
  profit: {
    forecastProfit: number | null
    forecastBasis: string
    realizedProfit: number
    realizedBasis: string
    pocketProfit: number
    pocketBasis: string
    cashIn: number
    cashOut: number
  }
  fund: {
    cashIn: number
    cashOut: number
    advance: number
    retentionOutstanding: number
    bondOccupied: number
    billHeld: number
    payableOpen: number
    receivableOpen: number
  }
  tax: { saleTax: number; inputTax: number; vatPayable: number }
}

/** 项目一本账（只读聚合，六条线一次取全） */
export function getProjectOnebook(projectId: number | string) {
  return request.get<R<ProjectOnebook>>(`/v1/project/${projectId}/onebook`)
}
