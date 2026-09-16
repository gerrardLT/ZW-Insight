/**
 * useShortcuts 全局键盘快捷键组合式函数单测（Phase 0.2）
 * 验证：键位映射、事件总线派发、输入态不拦截守卫、preventDefault。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import {
  useShortcuts,
  isEditableTarget,
  ZW_SHORTCUT_EVENTS
} from '@/composables/useShortcuts'

const Host = defineComponent({
  setup() {
    useShortcuts()
    return () => h('div')
  }
})

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

function mountHost() {
  wrapper = mount(Host, { attachTo: document.body })
}

/** 在指定目标上派发 keydown（冒泡到 document 触发组合式函数监听） */
function fireKey(init: KeyboardEventInit, target: HTMLElement = document.body): KeyboardEvent {
  const evt = new KeyboardEvent('keydown', { cancelable: true, bubbles: true, ...init })
  target.dispatchEvent(evt)
  return evt
}

/** 监听 window 自定义事件总线 */
function listen(eventType: string) {
  const handler = vi.fn()
  window.addEventListener(eventType, handler)
  return { handler, stop: () => window.removeEventListener(eventType, handler) }
}

describe('useShortcuts', () => {
  it('Ctrl+K / ⌘K 唤起命令面板并 preventDefault', () => {
    mountHost()
    const { handler, stop } = listen(ZW_SHORTCUT_EVENTS.togglePalette)
    const evt = fireKey({ key: 'k', ctrlKey: true })
    expect(handler).toHaveBeenCalledTimes(1)
    expect(evt.defaultPrevented).toBe(true)
    stop()
  })

  it('`/`（非输入态）唤起命令面板', () => {
    mountHost()
    const { handler, stop } = listen(ZW_SHORTCUT_EVENTS.togglePalette)
    fireKey({ key: '/' }, document.body)
    expect(handler).toHaveBeenCalledTimes(1)
    stop()
  })

  it('`/`（输入框内）不拦截：不唤起命令面板', () => {
    mountHost()
    const input = document.createElement('input')
    document.body.appendChild(input)
    const { handler, stop } = listen(ZW_SHORTCUT_EVENTS.togglePalette)
    fireKey({ key: '/' }, input)
    expect(handler).not.toHaveBeenCalled()
    input.remove()
    stop()
  })

  it('Ctrl+S 派发 save 并 preventDefault（输入框内仍生效）', () => {
    mountHost()
    const input = document.createElement('input')
    document.body.appendChild(input)
    const { handler, stop } = listen(ZW_SHORTCUT_EVENTS.save)
    const evt = fireKey({ key: 's', ctrlKey: true }, input)
    expect(handler).toHaveBeenCalledTimes(1)
    expect(evt.defaultPrevented).toBe(true)
    input.remove()
    stop()
  })

  it('Ctrl+Enter 派发 save（弹窗提交约定）', () => {
    mountHost()
    const { handler, stop } = listen(ZW_SHORTCUT_EVENTS.save)
    fireKey({ key: 'Enter', metaKey: true })
    expect(handler).toHaveBeenCalledTimes(1)
    stop()
  })

  it('Esc 派发 escape', () => {
    mountHost()
    const { handler, stop } = listen(ZW_SHORTCUT_EVENTS.escape)
    fireKey({ key: 'Escape' })
    expect(handler).toHaveBeenCalledTimes(1)
    stop()
  })

  it('卸载后不再响应（onBeforeUnmount 注销监听）', () => {
    mountHost()
    wrapper.unmount()
    wrapper = null
    const { handler, stop } = listen(ZW_SHORTCUT_EVENTS.togglePalette)
    fireKey({ key: '/' }, document.body)
    expect(handler).not.toHaveBeenCalled()
    stop()
  })
})

describe('isEditableTarget', () => {
  it('识别 INPUT/TEXTAREA/SELECT/contenteditable', () => {
    expect(isEditableTarget(document.createElement('input'))).toBe(true)
    expect(isEditableTarget(document.createElement('textarea'))).toBe(true)
    expect(isEditableTarget(document.createElement('select'))).toBe(true)
    const editable = document.createElement('div')
    editable.contentEditable = 'true'
    expect(isEditableTarget(editable)).toBe(true)
  })

  it('普通元素与非元素返回 false', () => {
    expect(isEditableTarget(document.createElement('div'))).toBe(false)
    expect(isEditableTarget(document.body)).toBe(false)
    expect(isEditableTarget(null)).toBe(false)
  })
})
