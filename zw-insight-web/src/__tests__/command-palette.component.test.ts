/**
 * CommandPalette.vue 命令面板组件测试（Phase 1.1）
 * 覆盖：挂载渲染分组、输入过滤、Enter 执行并关闭、方向键导航、空状态、fuzzyScore。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import CommandPalette from '@/components/CommandPalette.vue'
import {
  useCommandPalette,
  fuzzyScore,
  type PaletteCommand
} from '@/composables/useCommandPalette'

const { open, close, visible } = useCommandPalette()

const runA = vi.fn()
const runB = vi.fn()
const commands: PaletteCommand[] = [
  { id: 'pay', title: '付款申请', group: '导航', keywords: 'payment /finance/payment-apply', run: runA },
  { id: 'theme', title: '切换主题', group: '操作', keywords: 'theme dark 深色', run: runB }
]

afterEach(() => {
  close()
  vi.clearAllMocks()
})

function mountPalette() {
  open()
  // el-icon 在应用内由 unplugin-vue-components 自动注册；测试环境显式 stub 以消除解析告警
  return mount(CommandPalette, {
    props: { commands },
    attachTo: document.body,
    global: { stubs: { 'el-icon': { template: '<i><slot /></i>' } } }
  })
}

function getInput(): HTMLInputElement {
  return document.querySelector('.palette-input') as HTMLInputElement
}

describe('CommandPalette.vue', () => {
  it('打开后渲染命令项与分组标签', async () => {
    const w = mountPalette()
    await nextTick()
    expect(document.querySelectorAll('.palette-item').length).toBe(2)
    const labels = Array.from(document.querySelectorAll('.palette-group-label')).map((e) => e.textContent)
    expect(labels).toEqual(['导航', '操作'])
    w.unmount()
  })

  it('输入过滤后回车执行匹配命令并关闭面板', async () => {
    const w = mountPalette()
    await nextTick()
    const input = getInput()
    input.value = '付款'
    input.dispatchEvent(new Event('input'))
    await nextTick()
    expect(document.querySelectorAll('.palette-item').length).toBe(1)
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }))
    expect(runA).toHaveBeenCalledTimes(1)
    expect(runB).not.toHaveBeenCalled()
    expect(visible.value).toBe(false)
    w.unmount()
  })

  it('下箭头移动选中项', async () => {
    const w = mountPalette()
    await nextTick()
    const input = getInput()
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }))
    await nextTick()
    expect(document.querySelector('.palette-item.active')?.textContent).toContain('切换主题')
    w.unmount()
  })

  it('Esc 关闭面板', async () => {
    const w = mountPalette()
    await nextTick()
    const input = getInput()
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    expect(visible.value).toBe(false)
    w.unmount()
  })

  it('无匹配结果时显示空状态', async () => {
    const w = mountPalette()
    await nextTick()
    const input = getInput()
    input.value = 'zzz不存在的命令'
    input.dispatchEvent(new Event('input'))
    await nextTick()
    expect(document.querySelector('.palette-empty')).toBeTruthy()
    w.unmount()
  })
})

describe('fuzzyScore', () => {
  it('空查询命中全部（返回 1）', () => {
    expect(fuzzyScore('任意文本', '   ')).toBe(1)
  })
  it('越靠前的连续子串得分越高', () => {
    expect(fuzzyScore('付款申请', '付款')).toBeGreaterThan(fuzzyScore('合同付款单', '付款'))
  })
  it('分散子序列可命中', () => {
    expect(fuzzyScore('payment-apply', 'ppl')).toBeGreaterThan(0)
  })
  it('不命中返回 0', () => {
    expect(fuzzyScore('abc', 'xyz')).toBe(0)
  })
})
