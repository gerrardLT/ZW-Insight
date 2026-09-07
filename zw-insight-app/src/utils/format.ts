/**
 * 移动端数值格式化纯函数
 *
 * 与 PC 端 `zw-insight-web/src/utils/chart-format.ts` 的同名函数**逐字同构**。
 * 三端当前不共享 workspace（无 pnpm-workspace / lerna），故此处为镜像实现；
 * 修改任一侧必须同步另一侧，避免 fmtWan 类双实现漂移（PC 端已有 e2e
 * consistency 断言钉住其语义）。
 *
 * 存在原因：`pages/contract/change-event/index.vue` 需要万元展示，
 * 但 app 端此前无该工具模块，构建期报 MODULE_NOT_FOUND。
 */

/**
 * 金额转万元（保留两位小数）。
 *
 * `val || 0` 兜住 null / undefined / '' / 0 / NaN，与 PC 端实现一致；
 * 入参允许字符串，因后端 BigDecimal 字段经 JSON 序列化后可能为数字字符串
 * （如变更事件 costDelta）。
 *
 * @param val 原始金额（元）
 * @return 万元数值，如 50000 → 5、1234567 → 123.46
 */
export function toWan(val: number | string | null | undefined): number {
  const n = val || 0
  return Math.round((Number(n) / 10000) * 100) / 100
}
