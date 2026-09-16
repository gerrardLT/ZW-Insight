/**
 * DefaultLayout 首登三步引导（Phase 1.3）组件测试
 * 覆盖：无标记自动弹出、有标记不弹、finish/close 写入 zw-tour-done 标记、
 * 顶栏帮助按钮跳转 /help、命令面板含帮助命令。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'

// vi.mock 工厂被 hoisting，依赖的 mockRouter 必须用 vi.hoisted 声明
const mockRouter = vi.hoisted(() => ({
  push: vi.fn(),
  options: { routes: [] }
}))

// 模板 $route 与 setup useRoute 共用的最小 route stub
const mockRoute = {
  path: '/dashboard',
  fullPath: '/dashboard',
  name: 'Dashboard',
  meta: {},
  matched: [],
  query: {},
  params: {}
}

// 部分替换：保留其余导出（AppBreadcrumb/TagsView 还依赖 useRoute 等），
// 仅替 useRouter（布局/子组件统一拿到 mockRouter）与 useRoute（最小 route stub）
vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => mockRouter,
    useRoute: () => mockRoute
  }
})
vi.mock('@/api/system', () => ({
  getUserMenus: () => Promise.resolve({ data: [] })
}))

import DefaultLayout from '@/layouts/DefaultLayout.vue'

const TOUR_KEY = 'zw-tour-done'

let wrapper: any = null

function mountLayout() {
  const pinia = createPinia()
  setActivePinia(pinia)
  wrapper = mount(DefaultLayout, {
    global: {
      plugins: [ElementPlus, pinia],
      // 模板中 $route.path（el-menu default-active）需注入 mock
      mocks: { $route: mockRoute },
      // 测试环境无 unplugin-vue-components：router-view 需 stub（否则 v-slot 解构报错）；
      // Monitor 为存量未显式 import 图标（生产由 unplugin 兜住），stub 消除告警噪音
      stubs: {
        'router-view': { template: '<div class="router-view-stub" />' },
        Monitor: { template: '<i />' }
      }
    },
    attachTo: document.body
  })
  return flushPromises().then(() => wrapper)
}

beforeEach(() => {
  localStorage.removeItem(TOUR_KEY)
  mockRouter.push.mockClear()
})

afterEach(() => {
  if (wrapper) {
    try { wrapper.unmount() } catch { /* 忽略 */ }
    wrapper = null
  }
  localStorage.removeItem(TOUR_KEY)
})

describe('DefaultLayout 首登引导（el-tour）', () => {
  it('无 zw-tour-done 标记时自动弹出三步引导', async () => {
    const w = await mountLayout()
    const tour = w.findComponent({ name: 'ElTour' })
    expect(tour.exists()).toBe(true)
    expect(tour.props('modelValue')).toBe(true)
  })

  it('已有 zw-tour-done 标记时不弹出（不打扰老用户）', async () => {
    localStorage.setItem(TOUR_KEY, '1')
    const w = await mountLayout()
    const tour = w.findComponent({ name: 'ElTour' })
    expect(tour.props('modelValue')).toBe(false)
  })

  it('引导 finish 后写入标记（下次不再弹出）', async () => {
    const w = await mountLayout()
    const tour = w.findComponent({ name: 'ElTour' })
    tour.vm.$emit('finish')
    expect(localStorage.getItem(TOUR_KEY)).toBe('1')
  })

  it('引导中途 close 也写入标记（不重复骚扰）', async () => {
    const w = await mountLayout()
    const tour = w.findComponent({ name: 'ElTour' })
    tour.vm.$emit('close', 0)
    expect(localStorage.getItem(TOUR_KEY)).toBe('1')
  })

  it('顶栏帮助按钮存在并可点击跳转 /help', async () => {
    const w = await mountLayout()
    const btn = w.find('[aria-label="打开帮助中心"]')
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    expect(mockRouter.push).toHaveBeenCalledWith('/help')
  })

  it('命令面板命令表包含「打开帮助中心」', async () => {
    const w = await mountLayout()
    // paletteCommands 是 script setup 内部 computed，通过 CommandPalette props 间接断言
    const palette = w.findComponent({ name: 'CommandPalette' })
    expect(palette.exists()).toBe(true)
    const titles = (palette.props('commands') as any[]).map((c) => c.title)
    expect(titles).toContain('打开帮助中心')
  })
})
