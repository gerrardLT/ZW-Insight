/**
 * 业务标签唯一源（术语基准的机器可读版）。
 *
 * 规则（docs/术语表.md）：
 * - 全站状态/类别枚举的中文显示与徽章色一律取本文件，禁止在 .vue 模板内手写 statusMap；
 * - 状态只改显示层：DB 枚举值（FILED/TENDERING/...）不变；
 * - 新增枚举先改 docs/术语表.md 与本文件，再写页面；
 * - 校验：npm run check:labels（warn-only）。
 */

export type BadgeType = 'primary' | 'success' | 'warning' | 'danger' | 'info'

export interface LabelSpec {
  label: string
  badgeType: BadgeType
}

const spec = (label: string, badgeType: BadgeType): LabelSpec => ({ label, badgeType })

/** 项目状态（biz_project.status，DB 枚举值不变，仅显示名） */
export const PROJECT_STATUS: Readonly<Record<string, LabelSpec>> = {
  DRAFT: spec('草稿', 'info'),
  FILED: spec('已立项', 'primary'),
  TENDERING: spec('投标中', 'warning'),
  WON: spec('待开工', 'success'),
  LOST: spec('未中标', 'info'),
  CONSTRUCTION: spec('在建', 'primary'),
  PAUSED: spec('停工', 'warning'),
  COMPLETED: spec('已竣工', 'success'),
  CLOSING: spec('结项审批中', 'warning'),
  CLOSED: spec('已结项', 'danger'),
  TERMINATING: spec('终止审批中', 'warning'),
  TERMINATED: spec('已终止', 'danger'),
}

/** 项目状态事件（biz_project_status_log.event，大事记时间线显示名） */
export const PROJECT_EVENT: Readonly<Record<string, string>> = {
  SUBMIT: '提交立项',
  WITHDRAW: '撤回立项',
  GO_TENDER: '进入投标',
  WIN_BID: '中标',
  LOSE_BID: '未中标归档',
  START_CONSTRUCTION: '开工',
  PAUSE: '停工',
  RESUME: '复工',
  COMPLETE: '竣工验收',
  APPLY_CLOSE: '发起结项',
  APPROVE_CLOSE: '结项审批通过',
  REJECT_CLOSE: '结项审批驳回',
  TERMINATE_APPLY: '发起终止',
  TERMINATE_APPROVED: '终止审批通过',
  TERMINATE_REJECTED: '终止审批驳回',
}

/** 施工合同状态（biz_construction_contract.status） */
export const CONTRACT_STATUS: Readonly<Record<string, LabelSpec>> = {
  DRAFT: spec('草稿', 'info'),
  SUBMITTED: spec('审批中', 'warning'),
  EFFECTIVE: spec('已生效', 'success'),
  SETTLED: spec('已结算', 'primary'),
  CLOSED: spec('已结项', 'danger'),
}

/** 单据审批状态（status: DRAFT/SUBMITTED/APPROVED/REJECTED，付款/开票/结算/报销等通用） */
export const APPROVAL_STATUS: Readonly<Record<string, LabelSpec>> = {
  DRAFT: spec('草稿', 'info'),
  SUBMITTED: spec('审批中', 'warning'),
  APPROVED: spec('已通过', 'success'),
  REJECTED: spec('已驳回', 'danger'),
}

/** 支付状态（biz_payment_apply.pay_status，现金口径；术语表：未付/部分支付/已付） */
export const PAY_STATUS: Readonly<Record<string, LabelSpec>> = {
  UNPAID: spec('未付', 'info'),
  PARTIAL_PAID: spec('部分支付', 'warning'),
  PAID: spec('已付', 'success'),
}

/** 回款认领状态（biz_payment_received.claim_status） */
export const CLAIM_STATUS: Readonly<Record<string, LabelSpec>> = {
  UNCLAIMED: spec('待认领', 'warning'),
  CLAIMED: spec('已认领', 'primary'),
  WRITTEN_OFF: spec('已核销', 'success'),
}

/** 成本类别（CBS 六类；术语表禁用"费用类别"） */
export const COST_CATEGORY: Readonly<Record<string, string>> = {
  MATERIAL: '材料',
  LABOR: '人工',
  MACHINE: '机械',
  SUBCONTRACT: '分包',
  INDIRECT: '间接费',
  OTHER: '其他',
}

/** 支出合同类别（付款申请/结算的 contractCategory 五类） */
export const CONTRACT_CATEGORY: Readonly<Record<string, string>> = {
  SUBCONTRACT: '分包合同',
  PURCHASE: '采购合同',
  LABOR: '劳务合同',
  MACHINE: '机械合同',
  OTHER: '其他支出合同',
}

/** 应收/应付台账状态 */
export const LEDGER_STATUS: Readonly<Record<string, LabelSpec>> = {
  OPEN: spec('未结清', 'warning'),
  CLOSED: spec('已结清', 'success'),
}

export function labelOf(map: Readonly<Record<string, LabelSpec>>, key?: string | null): string {
  if (!key) return ''
  return map[key]?.label ?? key
}

export function typeOf(map: Readonly<Record<string, LabelSpec>>, key?: string | null): BadgeType {
  if (!key) return 'info'
  return map[key]?.badgeType ?? 'info'
}
