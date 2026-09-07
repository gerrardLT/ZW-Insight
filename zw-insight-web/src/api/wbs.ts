import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'

// ======================================================================
// WBS 工作分解结构 API
// 后端：ProjectWbsNodeController @RequestMapping("/api/v1/project/{projectId}/wbs/nodes")
//   GET    /page         分页
//   GET    /tree         全树
//   GET    /select-list  下拉扁平列表（仅 ACTIVE）
//   GET    /{id}         详情
//   GET    /{id}/children 直接子节点
//   POST   （根）         新增
//   PUT    /{id}         更新
//   DELETE /{id}         删除
//   POST   /batch-delete 批量删除
//
// 说明：
// 1. 路径一律写成完整模板字面量，不经 helper 函数间接拼接——
//    一致性审计工具（tools/consistency-audit）做静态匹配，
//    间接拼接会被识别为「后端无对应 Controller」的 Critical 误报。
// 2. nodeLevel 由后端按父节点推导，前端不传（传了也会被忽略）。
// ======================================================================

/** WBS 节点 */
export interface WbsNode {
  id?: number
  projectId?: number
  parentId?: number | null
  /** 层级（1-阶段 2-工作包 3-任务），后端推导 */
  nodeLevel?: number
  nodeCode: string
  nodeName: string
  description?: string
  startDate?: string | null
  endDate?: string | null
  /** ACTIVE / INACTIVE / CLOSED */
  status?: string
  sortOrder?: number
  projectName?: string
  /** 树查询时由后端装配 */
  children?: WbsNode[]
  createdAt?: string
  updatedAt?: string
}

/** WBS 分页查询参数 */
export interface WbsPageQuery {
  page?: number
  size?: number
  parentId?: number | null
  status?: string
  keyword?: string
}

export function getWbsPage(projectId: number, params: WbsPageQuery) {
  return request.get<R<PageResult<WbsNode>>>(`/v1/project/${projectId}/wbs/nodes/page`, { params })
}

export function getWbsTree(projectId: number) {
  return request.get<R<WbsNode[]>>(`/v1/project/${projectId}/wbs/nodes/tree`)
}

export function getWbsSelectList(projectId: number) {
  return request.get<R<WbsNode[]>>(`/v1/project/${projectId}/wbs/nodes/select-list`)
}

export function getWbsNode(projectId: number, id: number) {
  return request.get<R<WbsNode>>(`/v1/project/${projectId}/wbs/nodes/${id}`)
}

export function getWbsChildren(projectId: number, id: number) {
  return request.get<R<WbsNode[]>>(`/v1/project/${projectId}/wbs/nodes/${id}/children`)
}

export function createWbsNode(projectId: number, data: Partial<WbsNode>) {
  return request.post<R<WbsNode>>(`/v1/project/${projectId}/wbs/nodes`, data)
}

export function updateWbsNode(projectId: number, id: number, data: Partial<WbsNode>) {
  return request.put<R<WbsNode>>(`/v1/project/${projectId}/wbs/nodes/${id}`, data)
}

export function deleteWbsNode(projectId: number, id: number) {
  return request.delete<R<void>>(`/v1/project/${projectId}/wbs/nodes/${id}`)
}

export function batchDeleteWbsNodes(projectId: number, ids: number[]) {
  return request.post<R<number>>(`/v1/project/${projectId}/wbs/nodes/batch-delete`, ids)
}
