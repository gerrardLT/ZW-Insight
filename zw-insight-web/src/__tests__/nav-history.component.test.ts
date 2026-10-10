/**
 * 顶栏「最近访问」历史下拉（导航方案A）组件测试
 * 覆盖：域面板不再渲染最近访问小节（回归钉住）、顶栏下拉渲染与域标签、
 *       常驻入口（/dashboard）排除、点击跳转、空记录态。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'

const mockRouter = vi.hoisted(() => ({
  push: vi.fn(),
  options: {
    routes: [
      {
        path: '/',
        children: [
          { path: 'dashboard', meta: { title: '首页' } },
          {
            path: 'project',
            children: [{ path: 'page', meta: { title: '项目列表' } }],
          },
          { path: 'contract', meta: { title: '施工合同' } },
          {
            path: 'finance',
            children: [{ path: 'payment-apply', meta: { title: '付款申请' } }],
          },
        ],
      },
    ],
  },
}))

const mockRoute = {
  path: '/dashboard',
  fullPath: '/dashboard',
  name: 'Dashboard',
  meta: {},
  matched: [],
  query: {},
  params: {},
}

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRouter: () => mockRouter,
    useRoute: () => mockRoute,
  }
})

vi.mock('@/api/system', () => ({
  getUserMenus: () =>
    Promise.resolve({
      data: [
        { id: 1, menuType: 'MENU', path: '/dashboard', status: 1, hidden: 0 },
        { id: 2, menuType: 'MENU', path: '/project/page', status: 1, hidden: 0 },
        { id: 3, menuType: 'MENU', path: '/contract', status: 1, hidden: 0 },
        { id: 4, menuType: 'MENU', path: '/finance/payment-apply', status: 1, hidden: 0 },
      ],
    }),
}))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    userInfo: { id: '42', username: 'tester', realName: '测试员' },
    logout: vi.fn(),
  }),
}))

// 顶栏全局项目切换器（ProjectSelector）挂载即拉项目列表，测试中必须打桩，避免真实网络请求
vi.mock('@/api/project', () => ({
  getProjectList: () => Promise.resolve({ data: [] }),
}))

import DefaultLayout from '@/layouts/DefaultLayout.vue'

const RECENT_KEY = 'zw-nav-recent:42'

let wrapper: any = null

function mountLayout() {
  const pinia = createPinia()
  setActivePinia(pinia)
  wrapper = mount(DefaultLayout, {
    global: {
      plugins: [ElementPlus, pinia],
      mocks: { $route: mockRoute },
      stubs: {
        'router-view': { template: '<div class="router-view-stub" />' },
        Monitor: { template: '<i />' },
      },
    },
    attachTo: document.body,
  })
  return flushPromises().then(() => wrapper)
}

beforeEach(() => {
  localStorage.setItem(
    RECENT_KEY,
    JSON.stringify(['/finance/payment-apply', '/dashboard', '/project/page'])
  )
})

afterEach(() => {
  if (wrapper) {
    try { wrapper.unmount() } catch { /* 忽略 */ }
    wrapper = null
  }
  localStorage.removeItem(RECENT_KEY)
  localStorage.removeItem('zw-tour-done')
  localStorage.removeItem('zw-guide-banner-dismissed')
  document.body.innerHTML = ''
})

describe('导航方案A：最近访问移顶栏历史下拉', () => {
  it('域面板不再渲染「最近访问」小节（回归钉住：面板回归纯域结构）', async () => {
    const w = await mountLayout()
    const panel = w.find('.menu-scrollbar')
    expect(panel.exists()).toBe(true)
    expect(panel.text()).not.toContain('最近访问')
  })

  it('顶栏历史按钮存在，下拉含最近项与域标签，且排除常驻入口 /dashboard', async () => {
    const w = await mountLayout()
    const btn = w.find('[data-testid="nav-history-btn"]')
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    await flushPromises()

    const items = Array.from(document.querySelectorAll('[data-testid="nav-history-item"]'))
    expect(items.length).toBe(2)
    const first = items[0] as HTMLElement
    expect(first.textContent).toContain('付款申请')
    expect(first.textContent).toContain('财务资金')
    expect(items.map((el) => el.textContent)).not.toContain(expect.stringContaining('首页'))
  })

  it('点击历史条目跳转对应路径', async () => {
    const w = await mountLayout()
    await w.find('[data-testid="nav-history-btn"]').trigger('click')
    await flushPromises()
    const first = document.querySelector('[data-testid="nav-history-item"]') as HTMLElement
    first?.click()
    await flushPromises()
    expect(mockRouter.push).toHaveBeenCalledWith('/finance/payment-apply')
  })

  it('无记录时展示空态文案', async () => {
    localStorage.setItem(RECENT_KEY, '[]')
    const w = await mountLayout()
    await w.find('[data-testid="nav-history-btn"]').trigger('click')
    await flushPromises()
    const menu = document.querySelector('[data-testid="nav-history-menu"]')
    expect(menu?.textContent).toContain('暂无访问记录')
  })
})
