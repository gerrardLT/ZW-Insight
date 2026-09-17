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

/**
 * 列表时间短格式（降噪）：今日 → HH:mm，非今日 → MM-DD。
 *
 * **app 端专用**，不属 PC 镜像范围（PC 列表宽屏容得下完整时间戳）。
 * 原为 `pages/home/index.vue` 内联函数，Stage 3.1 信息中心 ZwiCell 化时
 * 提取至此，避免同一语义在 home / message-center 双实现漂移。
 *
 * 纯字符位切片，不走 `new Date(str)` 解析——iOS Safari 与微信小程序对
 * 'YYYY-MM-DD HH:mm:ss'（空格分隔、无时区）会返回 Invalid Date。
 *
 * @param t 后端时间字符串（'YYYY-MM-DD HH:mm:ss' 或 'YYYY-MM-DD'）
 * @return 今日 '14:05'；非今日 '08-16'；空值 ''；长度不足 10 或今日无时分时原样返回
 */
export function shortTime(t?: string | null): string {
  if (!t) return ''
  const s = String(t).trim()
  if (s.length < 10) return s
  const now = new Date()
  const mm = String(now.getMonth() + 1).padStart(2, '0')
  const dd = String(now.getDate()).padStart(2, '0')
  const ymd = `${now.getFullYear()}-${mm}-${dd}`
  if (s.startsWith(ymd)) return s.length >= 16 ? s.slice(11, 16) : s
  return s.slice(5, 10)
}
