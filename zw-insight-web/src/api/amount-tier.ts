// 金额分级审批配置 API（54_V2026_52）
import request from '@/utils/request'
import type { R } from '@/types/api'

export interface AmountTierConfig {
  id?: number
  module: string
  tierLevel: number
  tierName: string
  minAmount: number
  maxAmount?: number | null
  enabled?: number
}

export function getTierList(module: string) {
  return request.get<R<AmountTierConfig[]>>('/v1/finance/amount-tier/list', { params: { module } })
}

export function matchTier(module: string, amount: number) {
  return request.get<R<AmountTierConfig | null>>('/v1/finance/amount-tier/match', {
    params: { module, amount }
  })
}

export function saveTier(data: AmountTierConfig) {
  return request.post<R<void>>('/v1/finance/amount-tier', data)
}

export function updateTier(id: number, data: AmountTierConfig) {
  return request.put<R<void>>(`/v1/finance/amount-tier/${id}`, data)
}
