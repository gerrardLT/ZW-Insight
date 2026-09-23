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
