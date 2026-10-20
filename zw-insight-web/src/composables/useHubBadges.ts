/**
 * Hub 页签待办徽标（S2）。
 *
 * 只复用已有只读接口，不为徽标新增后端端点：
 * - 财务三色待办（待审批付款 / 已批未付 / 逾期应收）来自 S3.1 财务工作台聚合，一次请求供多个页签共用；
 * - 劳务点工单、材料入库单、机械台班单的草稿数走各自 page 接口的 status 过滤
 *   （后端已补 status 参数，V2026 后新增；DRAFT = 未提交/待确认）。
 *
 * 口径纪律：任一请求失败只隐藏对应徽标（不显示 0、不伪造），不阻断页签渲染。
 */
import { getFinanceWorkbench } from '@/api/finance'
import { getWorkOrderPage } from '@/api/labor'
import { getMaterialInboundPage } from '@/api/material'
import { getMachineWorkLogPage } from '@/api/machine'

export type HubBadgeSource =
  | 'financePendingApproval'
  | 'financeUnpaid'
  | 'financeOverdueReceivable'
  | 'laborWorkOrderDraft'
  | 'materialInboundDraft'
  | 'machineWorkLogDraft'

export interface HubBadge {
  /** 待办笔数；<=0 时调用方不渲染徽标 */
  count: number
  /** 口径说明，用于 title 提示 */
  label: string
  tone: 'warning' | 'danger'
}

const FINANCE_SOURCES: HubBadgeSource[] = [
  'financePendingApproval',
  'financeUnpaid',
  'financeOverdueReceivable'
]

/** 批量取徽标：同一数据源只请求一次，失败静默降级 */
export async function loadHubBadges(sources: HubBadgeSource[]): Promise<Record<string, HubBadge>> {
  const unique = new Set(sources)
  const badges: Record<string, HubBadge> = {}
  const jobs: Promise<void>[] = []

  if (FINANCE_SOURCES.some((source) => unique.has(source))) {
    jobs.push((async () => {
      try {
        const res: any = await getFinanceWorkbench()
        const payable = res?.data?.payable || {}
        const receivable = res?.data?.receivable || {}
        if (unique.has('financePendingApproval')) {
          badges.financePendingApproval = {
            count: Number(payable.pendingApprovalCount || 0),
            label: '待审批付款',
            tone: 'warning'
          }
        }
        if (unique.has('financeUnpaid')) {
          badges.financeUnpaid = {
            count: Number(payable.approvedUnpaidCount || 0),
            label: '已批未付',
            tone: 'danger'
          }
        }
        if (unique.has('financeOverdueReceivable')) {
          badges.financeOverdueReceivable = {
            count: Number(receivable.overdueCount || 0),
            label: '逾期应收',
            tone: 'danger'
          }
        }
      } catch {
        // 徽标失败静默降级：不显示 0，也不阻断页签
      }
    })())
  }

  if (unique.has('laborWorkOrderDraft')) {
    jobs.push((async () => {
      try {
        const res: any = await getWorkOrderPage({ page: 1, size: 1, status: 'DRAFT' })
        badges.laborWorkOrderDraft = {
          count: Number(res?.data?.total || 0),
          label: '草稿待确认',
          tone: 'warning'
        }
      } catch {
        // 同上
      }
    })())
  }

  if (unique.has('materialInboundDraft')) {
    jobs.push((async () => {
      try {
        const res: any = await getMaterialInboundPage({ page: 1, size: 1, status: 'DRAFT' })
        badges.materialInboundDraft = {
          count: Number(res?.data?.total || 0),
          label: '草稿待提交',
          tone: 'warning'
        }
      } catch {
        // 同上
      }
    })())
  }

  if (unique.has('machineWorkLogDraft')) {
    jobs.push((async () => {
      try {
        const res: any = await getMachineWorkLogPage({ page: 1, size: 1, status: 'DRAFT' })
        badges.machineWorkLogDraft = {
          count: Number(res?.data?.total || 0),
          label: '台班待确认',
          tone: 'warning'
        }
      } catch {
        // 同上
      }
    })())
  }

  await Promise.all(jobs)
  return badges
}
