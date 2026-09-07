import request from '@/utils/request'
import type { R, PageResult } from '@/types/api'

// ======================================================================
// CBS 成本账户 API（成本主线归集口径）
// 后端：CostAccountController
//   GET    /api/v1/budget/cost-account/page   分页
//   GET    /api/v1/budget/cost-account/tree   全树
//   GET    /api/v1/budget/cost-account/{id}   详情
//   POST   /api/v1/budget/cost-account        新增
//   PUT    /api/v1/budget/cost-account/{id}   更新
//   DELETE /api/v1/budget/cost-account/{id}   删除
//   POST   /api/v1/budget/cost-account/{id}/lock   锁定
//   POST   /api/v1/budget/cost-account/{id}/close  关闭
//   POST   /api/v1/budget/cost-account/{id}/sync   金额同步
//
// 成本主线六维：baseline(目标成本) → current(当前预算) → commitment(已承诺)
//              → actual(实际成本) → forecast(完工预测EAC) → variance(偏差)
// ======================================================================

/** CBS 成本账户 */
export interface CostAccount {
  id?: number
  projectId?: number
  parentId?: number | null
  wbsNodeId?: number | null
  accountCode: string
  accountName: string
  /** MATERIAL / LABOR / MACHINE / SUBCONTRACT / INDIRECT / OTHER */
  costCategory: string
  costSubcategory?: string
  baselineAmount?: number
  currentAmount?: number
  commitmentAmount?: number
  actualAmount?: number
  forecastAmount?: number
  /** ACTIVE / LOCKED / CLOSED */
  status?: string
  remark?: string
  projectName?: string
  wbsNodeCode?: string
  wbsNodeName?: string
  children?: CostAccount[]
  createdAt?: string
  updatedAt?: string
}

/** 成本账户分页查询参数 */
export interface CostAccountPageQuery {
  page?: number
  size?: number
  projectId?: number
  parentId?: number | null
  wbsNodeId?: number | null
  costCategory?: string
  status?: string
}

/** 成本流水（金额变动台账） */
export interface CostAccountTxn {
  id?: number
  projectId?: number
  accountId?: number
  /** BASELINE / CURRENT / COMMITMENT / ACTUAL / FORECAST */
  amountType?: string
  deltaAmount?: number
  balanceAfter?: number
  /** CHANGE_EVENT / CONTRACT / PURCHASE / SETTLEMENT / PAYMENT / MATERIAL / MANUAL */
  sourceType?: string
  sourceId?: string
  sourceNumber?: string
  occurredAt?: string
  remark?: string
  createdAt?: string
}

export function getCostAccountPage(params: CostAccountPageQuery) {
  return request.get<R<PageResult<CostAccount>>>('/v1/budget/cost-account/page', { params })
}

export function getCostAccountTree(projectId: number) {
  return request.get<R<CostAccount[]>>('/v1/budget/cost-account/tree', { params: { projectId } })
}

export function getCostAccount(id: number) {
  return request.get<R<CostAccount>>(`/v1/budget/cost-account/${id}`)
}

export function createCostAccount(data: Partial<CostAccount>) {
  return request.post<R<CostAccount>>('/v1/budget/cost-account', data)
}

export function updateCostAccount(id: number, data: Partial<CostAccount>) {
  return request.put<R<CostAccount>>(`/v1/budget/cost-account/${id}`, data)
}

export function deleteCostAccount(id: number) {
  return request.delete<R<void>>(`/v1/budget/cost-account/${id}`)
}

export function lockCostAccount(id: number) {
  return request.post<R<void>>(`/v1/budget/cost-account/${id}/lock`)
}

export function closeCostAccount(id: number) {
  return request.post<R<void>>(`/v1/budget/cost-account/${id}/close`)
}

/** 从源模块同步金额（合同承诺 / 结算实际） */
export function syncCostAccount(
  id: number,
  params: { commitmentDelta?: number; actualDelta?: number }
) {
  return request.post<R<void>>(`/v1/budget/cost-account/${id}/sync`, null, { params })
}

/** 成本流水：某个账户的金额是怎么一步步变成现在这样的 */
export function getCostAccountLedger(
  id: number,
  params?: { amountType?: string; from?: string; to?: string }
) {
  return request.get<R<CostAccountTxn[]>>(`/v1/budget/cost-account/${id}/ledger`, { params })
}

// ======================================================================
// 成本归集（源单据 → CBS 账户自动对账）
// 后端：CostAccountController#rollup → CostRollUpService
//   POST /api/v1/budget/cost-account/rollup?projectId=
//
// 口径：承诺=有效合同金额；实际=已审批结算 + 材料出库消耗（非付款！）
// 幂等：以「状态跃迁 from→to」为键，可重复执行/定时执行，结果始终收敛
// ======================================================================

/** 未能归集的源单据（需人工显式绑定账户） */
export interface UnmappedDoc {
  /** 费用类别 */
  category: string
  /** 来源类型（PURCHASE_CONTRACT / LABOR_SETTLEMENT / MATERIAL_OUTBOUND 等） */
  sourceType: string
  /** 来源业务ID */
  sourceId: string
  /** 来源单据编号 */
  sourceNumber?: string
  /** 金额 */
  amount: number
  /** 未能归集的原因（无同科目账户 / 同科目多账户需绑定 / 绑定指向失效账户） */
  reason: string
}

/** 单账户单维度的对账结果 */
export interface AccountSync {
  accountId: number
  accountCode: string
  accountName: string
  amountType: string
  from: number
  to: number
  delta: number
}

/** 记账失败明细（如账户已锁定/关闭） */
export interface FailedSync {
  accountId: number
  accountCode: string
  amountType: string
  delta: number
  reason: string
}

/** 归集报告 */
export interface RollupReport {
  projectId: number
  /** 汇总说明 */
  message: string
  /** 参与归集的源单据数 */
  sourceDocCount: number
  /** 实际记账笔数 */
  postedCount: number
  /** 幂等命中跳过笔数 */
  duplicateCount: number
  /** 成功对账明细 */
  synced: AccountSync[]
  /** 未能归集的单据（需人工绑定） */
  unmapped: UnmappedDoc[]
  /** 记账失败明细 */
  failed: FailedSync[]
}

/** 源单据 → 成本账户 的显式绑定 */
export interface CostAccountLink {
  id?: number
  projectId?: number
  accountId: number
  /** PURCHASE_CONTRACT / LABOR_CONTRACT / MACHINE_CONTRACT / SUBCONTRACT_CONTRACT
   *  / PURCHASE_SETTLEMENT / LABOR_SETTLEMENT / MACHINE_WORK_SETTLEMENT
   *  / SUBCONTRACT_SETTLEMENT / MATERIAL_OUTBOUND */
  sourceType: string
  sourceId: string
  remark?: string
}

/** 执行成本归集（可重复调用，幂等收敛） */
export function costRollUp(projectId: number) {
  return request.post<R<RollupReport>>('/v1/budget/cost-account/rollup', null, {
    params: { projectId }
  })
}

/** 查询项目的显式绑定关系 */
export function listCostAccountLinks(projectId: number) {
  return request.get<R<CostAccountLink[]>>('/v1/budget/cost-account/link/list', {
    params: { projectId }
  })
}

/** 查询某账户的绑定明细（这个账户的钱来自哪些单据） */
export function listLinksByAccount(accountId: number) {
  return request.get<R<CostAccountLink[]>>(`/v1/budget/cost-account/${accountId}/link`)
}

/** 新增绑定（幂等：已存在则返回既有记录） */
export function bindCostAccountLink(data: CostAccountLink) {
  return request.post<R<CostAccountLink>>('/v1/budget/cost-account/link', data)
}

/** 批量绑定（归集报告里一次性处理多张待绑定单据） */
export function bindCostAccountLinkBatch(data: CostAccountLink[]) {
  return request.post<R<number>>('/v1/budget/cost-account/link/batch', data)
}

/** 解除绑定（仅影响下次归集分配，不自动冲销已记账） */
export function unbindCostAccountLink(id: number) {
  return request.delete<R<void>>(`/v1/budget/cost-account/link/${id}`)
}
