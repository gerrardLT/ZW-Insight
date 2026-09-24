// 应收台账 API（V2026_57：结算驱动生成，回款 FIFO 核销，账龄分析）
// 后端：ReceivableController @RequestMapping("/api/v1/finance/receivable")
import request from '@/utils/request'
import type { R, PageResult, ID } from '@/types/api'

/** 应收台账记录（biz_receivable） */
export interface Receivable {
  id: ID
  projectId: ID
  projectName?: string
  contractId?: ID
  /** 来源类型（SETTLEMENT-项目结算/RETENTION-质保金到期） */
  sourceType: string
  sourceId: ID
  receivableAmount: number
  /** 约定收款到期日 */
  dueDate: string
  writtenOffAmount: number
  /** OPEN-未结清 / CLOSED-已结清 */
  status: 'OPEN' | 'CLOSED'
  remark?: string
  createdAt?: string
}

/** 账龄桶（NOT_DUE 未到期 / D0_30 / D31_60 / D61_90 / OVER_90） */
export type AgingBucket = 'NOT_DUE' | 'D0_30' | 'D31_60' | 'D61_90' | 'OVER_90'

/** 项目账龄行 */
export interface ReceivableAgingProject {
  projectId: ID
  projectName?: string
  openBalance: number
  overdueBalance: number
  maxOverdueDays: number
  buckets: Record<AgingBucket, number>
}

/** 账龄分析结果 */
export interface ReceivableAging {
  totalOpen: number
  totalOverdue: number
  projects: ReceivableAgingProject[]
}

export function getReceivablePage(params: {
  page?: number
  size?: number
  projectId?: number
  status?: string
}) {
  return request.get<R<PageResult<Receivable>>>('/v1/finance/receivable/page', { params })
}

export function getReceivableAging(projectId?: number) {
  return request.get<R<ReceivableAging>>('/v1/finance/receivable/aging', { params: { projectId } })
}

// ======================================================================
// §10 下钻 8 级链（V2026_69）
// 项目 → 应收款 → 对应工程节点 → 应收日期 → 实际申请日期
// → 甲方审核状态 → 负责人 → 下一步动作
// ======================================================================

/** 下钻链单级 */
export interface ReceivableDrillLevel {
  level: number
  label: string
  /**
   * 本级值。**registered=false 时为 null**（未登记），
   * 前端必须显示「未登记」并提供编辑入口，不得用「待审核」「未知」等默认词冒充。
   */
  value: string | number | null
  registered: boolean
  refId?: number | null
}

/** 甲方审核状态值域（与后端 BizReceivable.REVIEW_* 一致） */
export type OwnerReviewStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'CONFIRMED' | 'DISPUTED'

/** 下钻链查询结果 */
export interface ReceivableDrillChain {
  receivableId: number
  projectId?: number | null
  projectName?: string | null
  sourceType?: string
  sourceId?: number
  status: string
  receivableAmount: number
  writtenOffAmount: number
  /** 未结清余额（下限 0，超收事实由回款单据体现） */
  openBalance: number
  overdue: boolean
  overdueDays: number
  /** 账龄分档（NOT_DUE/D0_30/D31_60/D61_90/OVER_90）；未逾期时 null */
  agingBucket?: string | null
  /** 甲方停留天数（申请日→审核日，未审核时算至今）；未登记申请日时 null */
  ownerStayDays?: number | null
  chain: ReceivableDrillLevel[]
  /**
   * 原始登记值（chain 里的甲方审核状态已转中文标签，编辑表单回填需要原码）。
   * 未登记项为 null，表单据此留空（不预填推算值）。
   */
  drillInfo?: {
    milestoneNode?: string | null
    applyDate?: string | null
    ownerReviewStatus?: OwnerReviewStatus | null
    ownerReviewDate?: string | null
    ownerId?: number | null
    ownerName?: string | null
    nextAction?: string | null
  }
  /** 未登记的级数 */
  unregisteredCount: number
  /** 八级是否齐备 */
  complete: boolean
}

/**
 * 下钻信息维护请求。
 * **字符串字段：null/undefined = 不修改，空串 = 清空登记**；
 * **日期字段：null = 不修改，且不支持清空**（LocalDate 无法区分两者），
 * 登记错了只能改成正确日期。
 */
export interface ReceivableDrillInfoRequest {
  milestoneNode?: string | null
  applyDate?: string | null
  ownerReviewStatus?: OwnerReviewStatus | '' | null
  ownerReviewDate?: string | null
  ownerId?: number | null
  ownerName?: string | null
  nextAction?: string | null
}

/** §10 下钻 8 级链（路径为 /drill/{id}，避开与 /page /aging 的字面量路径歧义） */
export function getReceivableDrill(id: number) {
  return request.get<R<ReceivableDrillChain>>(`/v1/finance/receivable/drill/${id}`)
}

/** 维护下钻信息（人工登记项；后端 @OperLog 强制留痕） */
export function updateReceivableDrill(id: number, data: ReceivableDrillInfoRequest) {
  return request.put<R<Receivable>>(`/v1/finance/receivable/drill/${id}`, data)
}
