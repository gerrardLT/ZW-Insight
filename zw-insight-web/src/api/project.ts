import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'
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

export function getProjectDetail(id: number) {
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

export function submitProject(id: number) {
  return request.put<R<void>>(`/v1/project/${id}/submit`)
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
export function getProjectMembers(projectId: number, params?: Record<string, unknown>) {
  return request.get<R<ProjectMember[]>>(`/v1/project/${projectId}/member`, { params })
}

export function addProjectMember(projectId: number, data: ProjectMemberAddRequest) {
  return request.post<R<void>>(`/v1/project/${projectId}/member`, data)
}

export function removeProjectMember(projectId: number, userId: number) {
  return request.delete<R<void>>(`/v1/project/${projectId}/member/${userId}`)
}

export function updateMemberRoles(projectId: number, userId: number, data: UpdateMemberRolesRequest) {
  return request.put<R<void>>(`/v1/project/${projectId}/member/${userId}/roles`, data)
}
