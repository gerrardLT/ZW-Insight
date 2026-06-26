import request from '@/utils/request'

// ======================== 类型定义 ========================

/** 封账类型 */
export type FinanceLockType = 'MONTHLY' | 'QUARTERLY'

/** 封账状态 */
export type FinanceLockStatus = 'LOCKED' | 'UNLOCKED'

/** 财务封账记录 */
export interface FinanceLockDTO {
  id: number
  /** 封账期间，格式 YYYY-MM */
  period: string
  /** 封账类型：MONTHLY / QUARTERLY */
  lockType: FinanceLockType
  /** 状态：LOCKED / UNLOCKED */
  status: FinanceLockStatus
  /** 封账操作人ID */
  lockBy?: number
  /** 封账时间 */
  lockTime?: string
  /** 解封操作人ID */
  unlockBy?: number
  /** 解封时间 */
  unlockTime?: string
}

/** 封账创建请求 */
export interface FinanceLockCreateRequest {
  /** 封账期间，格式 YYYY-MM */
  period: string
  /** 封账类型：MONTHLY / QUARTERLY */
  lockType: FinanceLockType
}

/** 封账分页查询参数 */
export interface FinanceLockPageParams {
  pageNum: number
  pageSize: number
}

// ======================== 封账管理 API ========================

/** 创建封账记录（季度封账会展开为多个月） */
export function createLock(data: FinanceLockCreateRequest) {
  return request.post('/v1/finance/lock', data)
}

/** 解封指定封账记录 */
export function unlockPeriod(id: number) {
  return request.delete(`/v1/finance/lock/${id}/unlock`)
}

/** 封账记录分页查询 */
export function getLockPage(params: FinanceLockPageParams) {
  return request.get('/v1/finance/lock/page', { params })
}

/** 查询指定年月封账状态 */
export function getLockStatus(period: string) {
  return request.get('/v1/finance/lock/status', { params: { period } })
}
