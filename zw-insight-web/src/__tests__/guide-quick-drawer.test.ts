/**
 * 核心链路速览（指南内置系统）组件测试
 * 覆盖：
 *   - registry：core-chains 章存在、置于首位、正文含 2 个 mermaid 块与关键小节
 *   - guide store：openQuick/closeQuick 状态翻转
 *   - GuideQuickDrawer：渲染章节 HTML 与小节导航、完整文档入口、mermaid 占位块存在
 *   - dashboard 引导条：默认可见、打开按钮唤起抽屉、不再显示写 localStorage 后隐藏
 */
import { describe, it, expect, afterEach, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ElementPlus from 'element-plus'

import { USER_GUIDE, getChapter } from '@/docs/help/registry'
import { renderChapter } from '@/docs/help/markdown'
import { useGuideStore } from '@/stores/guide'
import GuideQuickDrawer from '@/components/GuideQuickDrawer.vue'
import Dashboard from '@/views/dashboard/index.vue'

let wrapper: any = null

function mountWith(obj: any, extraGlobal: Record<string, unknown> = {}) {
  return mount(obj, {
    global: {
      plugins: [ElementPlus, createPinia()],
      ...(Object.keys(extraGlobal).length ? extraGlobal : {}),
    },
  })
}

afterEach(() => {
  if (wrapper) {
    try { wrapper.unmount() } catch { /* 忽略 */ }
    wrapper = null
  }
  localStorage.removeItem('zw-guide-banner-dismissed')
})

describe('registry：core-chains 章节', () => {
  it('章节存在且排在首位（新用户第一入口）', () => {
    expect(USER_GUIDE[0]?.id).toBe('core-chains')
    const ch = getChapter('core-chains')
    expect(ch).toBeTruthy()
    expect(ch!.title).toContain('核心链路')
    expect(ch!.summary.length).toBeGreaterThan(10)
  })

  it('正文含 2 个 mermaid 全景/资金图与核心小节', () => {
    const body = getChapter('core-chains')!.body
    expect((body.match(/```mermaid/g) || []).length).toBe(2)
    const { html, sections } = renderChapter(body)
    expect(sections.length).toBeGreaterThanOrEqual(6)
    expect(html).toContain('mermaid-block')
    for (const kw of ['生命周期', '双口径', '红线', '角色']) {
      expect(body).toContain(kw)
    }
  })
})

describe('stores/guide', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('openQuick/closeQuick 翻转 quickVisible', () => {
    const s = useGuideStore()
    expect(s.quickVisible).toBe(false)
    s.openQuick()
    expect(s.quickVisible).toBe(true)
    s.closeQuick()
    expect(s.quickVisible).toBe(false)
  })
})

describe('GuideQuickDrawer', () => {
  it('打开态渲染章节正文、小节导航与完整文档入口', async () => {
    // el-drawer 关闭态懒渲染：先经 store 打开再挂载，断言打开后的真实 DOM
    const pinia = createPinia()
    setActivePinia(pinia)
    useGuideStore().openQuick()
    wrapper = mount(GuideQuickDrawer, { global: { plugins: [ElementPlus, pinia] } })
    await flushPromises()
    const root = document.body
    expect(root.querySelector('[data-testid="guide-quick-toc"]') !== null).toBe(true)
    const body = root.querySelector('[data-testid="guide-quick-body"]')
    expect(body !== null).toBe(true)
    expect(body!.innerHTML).toContain('mermaid-block')
    expect(body!.textContent).toContain('项目全生命周期全景')
    expect(root.querySelector('[data-testid="guide-quick-full"]') !== null).toBe(true)
  })
})

describe('dashboard 引导条', () => {
  it('默认可见；打开按钮唤起抽屉；不再显示持久化并隐藏', async () => {
    wrapper = mountWith(Dashboard)
    await flushPromises()

    const banner = () => wrapper.find('[data-testid="guide-banner"]')
    expect(banner().exists()).toBe(true)

    // 打开按钮 → guide store 翻转（抽屉本体挂在布局上，这里只验证唤起通道）
    const store = useGuideStore()
    expect(store.quickVisible).toBe(false)
    await wrapper.find('[data-testid="guide-banner-open"]').trigger('click')
    expect(store.quickVisible).toBe(true)

    // 不再显示：localStorage 置位 + 条隐藏
    await wrapper.find('[data-testid="guide-banner-dismiss"]').trigger('click')
    expect(localStorage.getItem('zw-guide-banner-dismissed')).toBe('1')
    await wrapper.vm.$nextTick()
    expect(banner().exists()).toBe(false)
  })

  it('localStorage 已置位时不再渲染', async () => {
    localStorage.setItem('zw-guide-banner-dismissed', '1')
    wrapper = mountWith(Dashboard)
    await flushPromises()
    expect(wrapper.find('[data-testid="guide-banner"]').exists()).toBe(false)
  })
})
