import { ref, type Component } from 'vue'

/**
 * 命令面板条目（Phase 1.1）。
 * - group：分组标签，如「导航」「操作」
 * - run：执行动作（路由跳转 / 主题切换 / 登出等），真实调用，无空操作
 */
export interface PaletteCommand {
  id: string
  title: string
  group: string
  /** 额外检索关键词（拼音/别名/路径片段），提升子序列匹配命中 */
  keywords?: string
  /** 右侧浅色提示，如路由路径 */
  hint?: string
  /** 可选图标组件 */
  icon?: Component
  /** 执行动作 */
  run: () => void
}

/**
 * 模块级单例开合状态：命令面板在布局挂载一次，
 * 全局（快捷键 / 顶栏按钮 / 其它组件）共享同一 visible，避免多处 prop 透传。
 */
const visible = ref(false)

export function useCommandPalette() {
  function open() {
    visible.value = true
  }
  function close() {
    visible.value = false
  }
  function toggle() {
    visible.value = !visible.value
  }
  return { visible, open, close, toggle }
}

/**
 * 轻量子序列打分（不引 fuse.js，保持依赖最小；命令量 < 200 无需外部库）。
 * - 空查询：返回 1（全部命中，保持原顺序）
 * - 连续子串：高分，且越靠前越高（前缀最优）
 * - 分散子序列：按字符间跨度扣分
 * - 不命中：返回 0
 */
export function fuzzyScore(text: string, query: string): number {
  const q = query.trim().toLowerCase()
  if (!q) return 1
  const t = (text || '').toLowerCase()
  if (!t) return 0
  const idx = t.indexOf(q)
  if (idx >= 0) return 1000 - idx
  // 子序列匹配：统计跨度
  let cursor = 0
  let gaps = 0
  let last = -1
  for (const ch of q) {
    const found = t.indexOf(ch, cursor)
    if (found === -1) return 0
    if (last >= 0) gaps += found - last - 1
    last = found
    cursor = found + 1
  }
  return Math.max(1, 100 - gaps)
}
