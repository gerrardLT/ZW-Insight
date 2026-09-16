import { onMounted, onBeforeUnmount } from 'vue'

/**
 * 全局快捷键事件总线事件名（集中式，供命令面板 / 表单弹窗消费）。
 * - togglePalette：⌘K / Ctrl+K 或 `/` 唤起命令面板（Phase 1.1 订阅）
 * - save：Ctrl+S 或 Ctrl+Enter 通知当前表单/弹窗执行保存（复用 payment-apply 原有 Ctrl+Enter 约定）
 * - escape：Esc 通知命令面板等浮层关闭最上层（Element 弹窗由 close-on-press-escape 自理）
 */
export const ZW_SHORTCUT_EVENTS = {
  togglePalette: 'zw:toggle-palette',
  save: 'zw:save',
  escape: 'zw:escape'
} as const

/**
 * 「输入框/文本域内不拦截」守卫（复用 payment-apply 原判断逻辑并扩展 contenteditable）：
 * 单字符快捷键（如 `/`）在可编辑元素聚焦时不触发，避免打断正常输入。
 */
export function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  const tag = target.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true
  return target.isContentEditable === true
}

/**
 * 集中式全局键盘快捷键组合式函数。
 *
 * 键位（尊重「输入态不拦截」守卫；带修饰键的组合在输入框内仍生效）：
 * - ⌘K / Ctrl+K：唤起命令面板（全局，任意上下文）
 * - `/`：唤起命令面板（仅非输入态）
 * - Ctrl+S / ⌘+S：保存当前表单/弹窗（拦截浏览器默认「保存页面」）
 * - Ctrl+Enter / ⌘+Enter：提交当前弹窗表单
 * - Esc：通知命令面板等自绘浮层关闭
 *
 * 仅依赖 DOM 与 window CustomEvent，不引第三方库，可独立单测。
 *
 * @example
 * // 在布局根组件 setup 中调用一次即全局生效
 * useShortcuts()
 */
export function useShortcuts() {
  function handleKeydown(event: KeyboardEvent) {
    const mod = event.ctrlKey || event.metaKey
    const key = event.key

    // Esc：交由订阅方（命令面板）判断是否关闭最上层；不阻断 Element 弹窗自身 Esc 逻辑
    if (key === 'Escape') {
      window.dispatchEvent(new CustomEvent(ZW_SHORTCUT_EVENTS.escape))
      return
    }

    // ⌘K / Ctrl+K：任意上下文唤起命令面板
    if (mod && key.toLowerCase() === 'k') {
      event.preventDefault()
      window.dispatchEvent(new CustomEvent(ZW_SHORTCUT_EVENTS.togglePalette))
      return
    }

    // Ctrl+S：保存（即使在输入框内也应生效，并拦截浏览器默认保存对话框）
    if (mod && key.toLowerCase() === 's') {
      event.preventDefault()
      window.dispatchEvent(new CustomEvent(ZW_SHORTCUT_EVENTS.save))
      return
    }

    // Ctrl+Enter：提交当前弹窗表单（与既有 payment-apply 表单提示一致）
    if (mod && key === 'Enter') {
      window.dispatchEvent(new CustomEvent(ZW_SHORTCUT_EVENTS.save))
      return
    }

    // 以下单字符快捷键：输入态不拦截
    if (isEditableTarget(event.target)) return

    // `/`：唤起命令面板（对齐 macOS / 现代工具站惯例）
    if (key === '/') {
      event.preventDefault()
      window.dispatchEvent(new CustomEvent(ZW_SHORTCUT_EVENTS.togglePalette))
    }
  }

  onMounted(() => document.addEventListener('keydown', handleKeydown))
  onBeforeUnmount(() => document.removeEventListener('keydown', handleKeydown))

  return { handleKeydown }
}
