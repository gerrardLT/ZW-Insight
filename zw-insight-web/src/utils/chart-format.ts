/**
 * 图表/看板数值变换纯函数（2026-08-14 P2 补测提取）
 *
 * 提取自 dashboard/index.vue 与 project-dashboard.vue 的组件内函数，
 * 单一事实源：L1 单测与 e2e consistency 断言共用，消除 fmtWan 类双实现漂移。
 * 语义与原组件实现逐字对齐（迁移无行为变更）。
 */

/** 金额转万元展示（1 位小数）：空值/NaN → '0'，否则 (val/10000).toFixed(1) */
export function formatWan(val: unknown): string {
  const n = Number(val)
  if ((val === null || val === undefined || val === '') || Number.isNaN(n)) return '0'
  return (n / 10000).toFixed(1)
}

/** 金额保留两位小数（四舍五入） */
export function toAmount2(val: number): number {
  return Math.round((val || 0) * 100) / 100
}

/** 金额转万元（保留两位小数） */
export function toWan(val: number): number {
  return Math.round(((val || 0) / 10000) * 100) / 100
}

/** 完成率转百分比并裁剪到 [0, 100]（completionRate 为小数，如 0.85 → 85） */
export function clampPercent(rate: number): number {
  return Math.min(Math.max(Math.round((rate || 0) * 100), 0), 100)
}

/** 剩余预算 = max(预算 - 已用, 0)，负值归零（防图表/展示负数） */
export function nonNegativeRemaining(budget: number, used: number): number {
  return Math.max((budget || 0) - (used || 0), 0)
}
