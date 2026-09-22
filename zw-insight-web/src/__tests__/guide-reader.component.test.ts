/**
 * 使用文档三栏阅读器组件测试。
 *
 * 覆盖：默认章与导航完整性、点导航切章并同步 hash、深链打开对应章、
 * 检索过滤 + 当前章归位命中集、navLabel 去重规则、prev/next 翻页边界。
 * mermaid 依赖真实浏览器渲染，予以 mock（与 guide-markdown.test.ts 的分工：
 * 纯渲染函数在那边，这里只测阅读器状态机）。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia } from 'pinia'
import { nextTick } from 'vue'
import Guide from '@/views/help/guide.vue'
import router from '@/router'

vi.mock('mermaid', () => ({
  default: {
    initialize: vi.fn(),
    render: vi.fn(async () => ({ svg: '<svg aria-label="mock"></svg>' })),
  },
}))

let wrapper: any = null

/** 等待 transition out-in 切换完成（happy-dom 下需一个短宏任务） */
const tick = (ms = 120) => new Promise((r) => setTimeout(r, ms))

async function mountGuide() {
  await router.push('/help/guide')
  await router.isReady()
  wrapper = mount(Guide, {
    // happy-dom 不触发 transitionend，out-in 切章会卡在离场阶段；stub 掉 transition 使切换同步
    global: { plugins: [ElementPlus, router, createPinia()], stubs: { transition: true } },
    attachTo: document.body,
  })
  await flushPromises()
  await nextTick()
  return wrapper
}

beforeEach(() => {
  localStorage.setItem('zw-tour-done', '1')
  // 全局路由守卫按 localStorage token 存在性放行（/help/guide 无 permission meta，占位即可通过）
  localStorage.setItem('token', 'unit-test-token')
})
afterEach(() => {
  try { wrapper?.unmount() } catch { /* 忽略 */ }
  wrapper = null
  localStorage.removeItem('token')
})

describe('guide.vue 三栏阅读器', () => {
  it('默认打开首章（快速上手），左栏渲染 19 个导航项', async () => {
    const w = await mountGuide()
    expect(w.find('.doc-title').text()).toContain('快速上手')
    expect(w.findAll('.nav-item')).toHaveLength(19)
    expect(w.find('.nav-item.active').text()).toContain('快速上手')
  })

  it('点击导航切章：正文更换且路由 hash 同步', async () => {
    const w = await mountGuide()
    await w.find('[data-testid="guide-toc-finance"]').trigger('click')
    await flushPromises()
    await tick()
    expect(w.find('.doc-title').text()).toContain('财务管理')
    expect(router.currentRoute.value.hash).toBe('#finance')
  })

  it('深链 /help/guide#budget 直接打开预算章', async () => {
    await router.push('/help/guide#budget')
    await router.isReady()
    wrapper = mount(Guide, { global: { plugins: [ElementPlus, router, createPinia()], stubs: { transition: true } }, attachTo: document.body })
    await flushPromises()
    await tick()
    expect(wrapper.find('.doc-title').text()).toContain('预算管理')
  })

  it('检索「可付上限」：导航过滤出命中章、当前章归位到命中集、显示命中数', async () => {
    const w = await mountGuide()
    await w.find('.guide-search input').setValue('可付上限')
    await flushPromises()
    const items = w.findAll('.nav-item')
    expect(items.length).toBeGreaterThan(0)
    expect(items.length).toBeLessThan(19)
    // 首章 quickstart 不含该词 → 应自动切到某个命中章
    expect(w.find('.nav-item.active').exists()).toBe(true)
    expect(w.findAll('.nav-item-hits').length).toBe(items.length)
  })

  it('navLabel：条目主干与分组名重复时改用冒号后内容', async () => {
    const w = await mountGuide()
    const texts = w.findAll('.nav-item-text').map((n: any) => n.text())
    // 看板章 title=「看板：首页概览…」module=「看板」→ 条目不应再显示「看板」
    const dash = texts.find((t: string) => t.includes('首页概览'))
    expect(dash).toBeTruthy()
    expect(texts).not.toContain('看板')
  })

  it('翻页边界：首章无「上一章」（仅 ghost 占位），切到末章（基础数据）后无「下一章」', async () => {
    const w = await mountGuide()
    expect(w.find('button.pager-btn:not(.pager-next)').exists()).toBe(false)
    await w.find('[data-testid="guide-toc-basedata"]').trigger('click')
    await flushPromises()
    await tick()
    expect(w.find('.doc-title').text()).toContain('基础数据')
    expect(w.find('.pager-next').exists()).toBe(false)
  })
})
