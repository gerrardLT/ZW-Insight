// 应付账款台账 API（V2026_115：支出结算驱动生成，付款 FIFO 核销，账龄分析）
// 后端：PayableController @RequestMapping("/api/v1/finance/payable")
import request from '@/utils/request'
import type { R, PageResult, ID } from '@/types/api'

/** 应付台账行（biz_payable） */
export interface Payable {
  id: ID
  projectId: ID
  projectName?: string
  contractId: ID
  /** 合同类别（SUBCONTRACT/PURCHASE/LABOR/MACHINE/OTHER） */
  contractCategory: string
  supplierName?: string
  /** 来源类型（*_SETTLEMENT / BACKFILL-迁移回填） */
  sourceType: string
  sourceId: ID
  payableAmount: number
  /** 已核销金额（付款生效时 FIFO 冲减） */
  paidAmount: number
  /** 约定付款到期日 */
  dueDate: string
  /** OPEN-未结清 / CLOSED-已结清 */
  status: 'OPEN' | 'CLOSED'
  remark?: string
  createdAt?: string
}

/** 账龄桶（NOT_DUE 未到期 / D0_30 / D31_60 / D61_90 / OVER_90） */
export type PayableAgingBucket = 'NOT_DUE' | 'D0_30' | 'D31_60' | 'D61_90' | 'OVER_90'

/** 项目账龄行 */
export interface PayableAgingProject {
  projectId: ID
  projectName?: string
  openBalance: number
  overdueBalance: number
  maxOverdueDays: number
  buckets: Record<PayableAgingBucket, number>
}

/** 账龄汇总 */
export interface PayableAging {
  totalOpen: number
  totalOverdue: number
  projects: PayableAgingProject[]
}

export function getPayablePage(params: {
  page: number
  size: number
  projectId?: ID
  status?: string
  contractCategory?: string
}) {
  return request.get<R<PageResult<Payable>>>('/v1/finance/payable/page', { params })
}

export function getPayableAging(projectId?: ID) {
  return request.get<R<PayableAging>>('/v1/finance/payable/aging', { params: { projectId } })
}
