/**
 * 顶栏消息铃铛未读角标组件测试
 * 覆盖：挂载即拉取并显示未读数、0 条隐藏角标、接口失败静默保留旧值、
 *       30s 轮询刷新、卸载后停止轮询（防定时器叠加泄漏）。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'

const mockGetUnreadCount = vi.hoisted(() => vi.fn())

const mockRouter = vi.hoisted(() => ({
  push: vi.fn(),
  options: { routes: [{ path: '/', children: [{ path: 'dashboard', meta: { title: '首页' } }] }] },
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
  return { ...actual, useRouter: () => mockRouter, useRoute: () => mockRoute }
})

vi.mock('@/api/system', () => ({
  getUserMenus: () =>
    Promise.resolve({ data: [{ id: 1, menuType: 'MENU', path: '/dashboard', status: 1, hidden: 0 }] }),
}))

vi.mock('@/api/message', () => ({ getUnreadCount: mockGetUnreadCount }))

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({
    userInfo: { id: '42', username: 'tester', realName: '测试员' },
    logout: vi.fn(),
  }),
}))

import DefaultLayout from '@/layouts/DefaultLayout.vue'

let wrapper: any = null

async function mountLayout() {
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
  await flushPromises()
  return wrapper
}

/** 角标节点：el-badge 的 content 在 hidden 时以 display:none 隐藏（节点仍在 DOM） */
function badgeContent(w: any) {
  return w.find('[data-testid="layout-unread-badge"] .el-badge__content')
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] })
  mockGetUnreadCount.mockReset()
})

afterEach(() => {
  if (wrapper) {
    try { wrapper.unmount() } catch { /* 忽略 */ }
    wrapper = null
  }
  vi.useRealTimers()
  localStorage.removeItem('zw-tour-done')
  localStorage.removeItem('zw-guide-banner-dismissed')
  document.body.innerHTML = ''
})

describe('顶栏消息未读角标', () => {
  it('挂载即拉取未读数并显示在铃铛上', async () => {
    mockGetUnreadCount.mockResolvedValue({ data: 5 })

    const w = await mountLayout()

    expect(mockGetUnreadCount).toHaveBeenCalledTimes(1)
    const badge = badgeContent(w)
    expect(badge.exists()).toBe(true)
    expect(badge.text()).toBe('5')
    expect(badge.attributes('style') || '').not.toContain('display: none')
  })

  it('超过 99 条显示 99+', async () => {
    mockGetUnreadCount.mockResolvedValue({ data: 250 })

    const w = await mountLayout()

    expect(badgeContent(w).text()).toBe('99+')
  })

  it('未读数为 0 时隐藏角标', async () => {
    mockGetUnreadCount.mockResolvedValue({ data: 0 })

    const w = await mountLayout()

    const badge = badgeContent(w)
    expect(!badge.exists() || (badge.attributes('style') || '').includes('display: none')).toBe(true)
  })

  it('接口失败静默：不抛错、不显示角标', async () => {
    mockGetUnreadCount.mockRejectedValue(new Error('network down'))

    const w = await mountLayout()

    const badge = badgeContent(w)
    expect(!badge.exists() || (badge.attributes('style') || '').includes('display: none')).toBe(true)
  })

  it('每 30 秒轮询一次并刷新数字', async () => {
    mockGetUnreadCount.mockResolvedValueOnce({ data: 1 }).mockResolvedValue({ data: 7 })

    const w = await mountLayout()
    expect(badgeContent(w).text()).toBe('1')

    await vi.advanceTimersByTimeAsync(30000)
    await flushPromises()

    expect(mockGetUnreadCount).toHaveBeenCalledTimes(2)
    expect(badgeContent(w).text()).toBe('7')
  })

  it('卸载后停止轮询（不再请求，防定时器泄漏）', async () => {
    mockGetUnreadCount.mockResolvedValue({ data: 3 })

    const w = await mountLayout()
    const callsAfterMount = mockGetUnreadCount.mock.calls.length
    w.unmount()
    wrapper = null

    await vi.advanceTimersByTimeAsync(120000)

    expect(mockGetUnreadCount.mock.calls.length).toBe(callsAfterMount)
  })
})
