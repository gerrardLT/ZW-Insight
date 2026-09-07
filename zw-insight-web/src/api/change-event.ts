import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'

// ======================================================================
// 变更事件 API
// 后端：ChangeEventController @RequestMapping("/api/v1/contract/change-event")
//   GET    /page                  分页（page,size,projectId,status,sourceType,category,keyword）
//   GET    /{id}                  详情
//   GET    /open-count            项目下待处理数（工作台角标）
//   GET    /approved-cost-delta   项目下已批准变更累计成本影响
//   POST   （根）                  登记（草稿）
//   PUT    /{id}                  更新（仅 DRAFT/ASSESSING 可改）
//   POST   /{id}/start-assessment 转入评估中
//   POST   /{id}/assessment       提交影响评估 → APPROVING
//   POST   /{id}/approve          批准（?comment 可选）
//   POST   /{id}/reject           驳回（?rejectionReason 必填）
//   POST   /{id}/re-assess        退回重新评估（?reason 可选）
//   POST   /{id}/cancel           作废（?reason 可选）
//   DELETE /{id}                  删除（仅 DRAFT/CANCELLED）
//   POST   /list-by-ids           批量查询
//
// 身份约定：评估人/批准人一律由后端从登录态取，前端不传、也无法传
// （允许客户端指定「我是谁」等于放弃审计可信度）。
// ======================================================================

/**
 * 变更事件业务状态（与后端 ChangeEventStatus 枚举严格一致）。
 * 业务状态机 ≠ BPMN 审批流：status 描述事件自身生命周期，
 * 「谁审批、几级审批」由 Flowable 流程定义决定。
 */
export type ChangeEventStatus =
  | 'DRAFT'        // 草稿：现场登记完成，尚未评估
  | 'ASSESSING'    // 评估中：商务/造价测算成本与工期影响
  | 'APPROVING'    // 审批中：评估完成，进入审批
  | 'APPROVED'     // 已批准（终态，触发下游 CBS/合同传导）
  | 'REJECTED'     // 已驳回（终态）
  | 'CANCELLED'    // 已作废（终态）

/** 来源类型（后端白名单校验，传其他值会被拒） */
export type ChangeEventSourceType =
  | 'FIELD_EVENT'      // 现场事件
  | 'DESIGN_CHANGE'    // 设计变更
  | 'OWNER_REQUEST'    // 业主指令
  | 'VARIATION_ORDER'  // 清单变更
  | 'OTHER'

/** 影响类别 */
export type ChangeEventCategory =
  | 'COST_IMPACT'      // 成本影响
  | 'SCOPE_CHANGE'     // 范围变更
  | 'SCHEDULE_DELAY'   // 工期延误
  | 'QUALITY_ISSUE'    // 质量问题
  | 'OTHER'

/** 受影响的成本账户调整指令 */
export interface AffectedAccount {
  /** 成本账户ID */
  accountId: number
  /** 调整方向 */
  deltaType: 'INCREASE' | 'DECREASE'
  /** 调整金额（绝对值，方向由 deltaType 决定） */
  deltaAmount: number
}

/** 佐证附件（复用 file_info 体系，只存引用） */
export interface SupportingDoc {
  fileId?: number
  url?: string
  name?: string
  type?: string
}

/** 影响评估 */
export interface ImpactAssessment {
  /** 成本影响（正=增加成本，负=节约；无影响须显式填 0） */
  costDelta: number
  /** 工期影响天数（正=延误，负=提前） */
  scheduleDelayDays?: number
  /** 评估理由（必填，审批人据此判断） */
  rationale: string
  /** 评估备注 */
  assessmentNotes?: string
}

/** 变更事件 */
export interface BizChangeEvent {
  id?: number
  projectId: number
  projectName?: string
  /** 编号（后端按 CHANGE_EVENT 规则生成，前端不传） */
  eventNumber?: string
  sourceType: ChangeEventSourceType | string
  /** 来源引用（签证ID等）；填写后同号不可重复登记，防业务事实重复录入 */
  sourceRef?: string
  title: string
  description?: string
  category?: ChangeEventCategory | string
  affectedWbsIds?: number[]
  affectedAccounts?: AffectedAccount[]
  supportingDocs?: SupportingDoc[]
  impactAssessment?: ImpactAssessment
  /** 成本影响（冗余列，列表排序/统计用） */
  costDelta?: number
  /** 工期影响天数（冗余列） */
  scheduleDelayDays?: number
  status?: ChangeEventStatus | string
  assessedBy?: number
  assessedAt?: string
  approvedBy?: number
  approvedAt?: string
  rejectionReason?: string
  workflowInstanceId?: string
  createdByName?: string
  createdAt?: string
  updatedAt?: string
}

/** 分页查询参数 */
export interface ChangeEventPageQuery {
  page?: number
  size?: number
  projectId?: number
  status?: string
  sourceType?: string
  category?: string
  keyword?: string
}

export function listChangeEvents(params: ChangeEventPageQuery) {
  return request.get<R<PageResult<BizChangeEvent>>>('/v1/contract/change-event/page', { params })
}

export function getChangeEvent(id: number) {
  return request.get<R<BizChangeEvent>>(`/v1/contract/change-event/${id}`)
}

/** 项目下待处理（非终态）事件数，用于工作台角标 */
export function getChangeEventOpenCount(projectId: number) {
  return request.get<R<number>>('/v1/contract/change-event/open-count', { params: { projectId } })
}

/** 项目下已批准变更的累计成本影响（成本主线看板「累计变更额」） */
export function getApprovedCostDelta(projectId: number) {
  return request.get<R<number>>('/v1/contract/change-event/approved-cost-delta', { params: { projectId } })
}

/** 登记变更事件（草稿）。现场只需说清「发生了什么」，不要求填影响评估 */
export function createChangeEvent(data: Partial<BizChangeEvent>) {
  return request.post<R<BizChangeEvent>>('/v1/contract/change-event', data)
}

/** 更新（仅 DRAFT/ASSESSING 可改；终态只读以保证已批准变更可追溯） */
export function updateChangeEvent(id: number, data: Partial<BizChangeEvent>) {
  return request.put<R<BizChangeEvent>>(`/v1/contract/change-event/${id}`, data)
}

/** 转入评估中（现场登记完成，交商务测算） */
export function startChangeEventAssessment(id: number) {
  return request.post<R<BizChangeEvent>>(`/v1/contract/change-event/${id}/start-assessment`)
}

/**
 * 提交影响评估 → 流转至 APPROVING。
 * 成本影响非 0 时必须已在 affectedAccounts 指明账户，否则后端拒绝
 * （否则批准后无法传导到 CBS，主链断裂）。
 */
export function submitChangeEventAssessment(id: number, assessment: ImpactAssessment) {
  return request.post<R<BizChangeEvent>>(`/v1/contract/change-event/${id}/assessment`, assessment)
}

/** 批准（触发 Outbox → CBS 当前预算调整） */
export function approveChangeEvent(id: number, comment?: string) {
  return request.post<R<BizChangeEvent>>(`/v1/contract/change-event/${id}/approve`, null, {
    params: comment ? { comment } : undefined
  })
}

/** 驳回（必须留原因，作为复盘与知识沉淀输入） */
export function rejectChangeEvent(id: number, rejectionReason: string) {
  return request.post<R<BizChangeEvent>>(`/v1/contract/change-event/${id}/reject`, null, {
    params: { rejectionReason }
  })
}

/** 退回重新评估（保留事件连续性，不作废重登） */
export function reAssessChangeEvent(id: number, reason?: string) {
  return request.post<R<BizChangeEvent>>(`/v1/contract/change-event/${id}/re-assess`, null, {
    params: reason ? { reason } : undefined
  })
}

/** 作废（已批准的不可作废，须登记反向变更冲销） */
export function cancelChangeEvent(id: number, reason?: string) {
  return request.post<R<BizChangeEvent>>(`/v1/contract/change-event/${id}/cancel`, null, {
    params: reason ? { reason } : undefined
  })
}

/** 删除（仅 DRAFT/CANCELLED；进入审批链的必须留痕） */
export function deleteChangeEvent(id: number) {
  return request.delete<R<void>>(`/v1/contract/change-event/${id}`)
}

/** 批量查询（跨模块回填展示字段用） */
export function listChangeEventsByIds(ids: number[]) {
  return request.post<R<BizChangeEvent[]>>('/v1/contract/change-event/list-by-ids', ids)
}

// ======================================================================
// 状态展示映射：单一事实源，避免各页面各写一套导致标签不一致
// ======================================================================

/** 状态中文 */
export const CHANGE_EVENT_STATUS_LABELS: Record<string, string> = {
  DRAFT: '草稿',
  ASSESSING: '评估中',
  APPROVING: '审批中',
  APPROVED: '已批准',
  REJECTED: '已驳回',
  CANCELLED: '已作废'
}

/** 状态标签色（Element Plus tag type） */
export const CHANGE_EVENT_STATUS_TAG_TYPES: Record<string, string> = {
  DRAFT: 'info',
  ASSESSING: 'warning',
  APPROVING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  CANCELLED: 'info'
}

/** 来源类型中文 */
export const CHANGE_EVENT_SOURCE_LABELS: Record<string, string> = {
  FIELD_EVENT: '现场事件',
  DESIGN_CHANGE: '设计变更',
  OWNER_REQUEST: '业主指令',
  VARIATION_ORDER: '清单变更',
  OTHER: '其他'
}

/** 影响类别中文 */
export const CHANGE_EVENT_CATEGORY_LABELS: Record<string, string> = {
  COST_IMPACT: '成本影响',
  SCOPE_CHANGE: '范围变更',
  SCHEDULE_DELAY: '工期延误',
  QUALITY_ISSUE: '质量问题',
  OTHER: '其他'
}

/** 影响类别标签色 */
export const CHANGE_EVENT_CATEGORY_TAG_TYPES: Record<string, string> = {
  COST_IMPACT: 'danger',
  SCOPE_CHANGE: 'warning',
  SCHEDULE_DELAY: 'info',
  QUALITY_ISSUE: 'danger',
  OTHER: 'info'
}

/** 状态下拉选项（筛选表单用） */
export const CHANGE_EVENT_STATUS_OPTIONS = Object.entries(CHANGE_EVENT_STATUS_LABELS).map(
  ([value, label]) => ({ value, label })
)

/** 来源类型下拉选项 */
export const CHANGE_EVENT_SOURCE_OPTIONS = Object.entries(CHANGE_EVENT_SOURCE_LABELS).map(
  ([value, label]) => ({ value, label })
)

/** 影响类别下拉选项 */
export const CHANGE_EVENT_CATEGORY_OPTIONS = Object.entries(CHANGE_EVENT_CATEGORY_LABELS).map(
  ([value, label]) => ({ value, label })
)
