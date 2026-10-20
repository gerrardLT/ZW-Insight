/**
 * HubPage.vue S2 首屏体验补测：
 * - 首 Tab 选择优先级：URL ?tab= > 上次访问记忆 > 角色默认 > 首个可见
 * - 待办徽标（财务三色待办 + 劳务点工草稿）渲染/计数为 0 隐藏/接口失败隐藏
 * - 长 Hub（>6 页签）溢出收敛到「更多」，下拉内保留全部页签
 *
 * 与 finance-hub.component.test.ts 的分工：后者锁权限过滤与 Tab 基础行为，
 * 本文件只覆盖 S2 新增的三段能力。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { defineComponent, h } from 'vue'

const {
  mockRouteQuery, mockRoutePath, mockRouterReplace,
  mockPermissions, mockRoles, mockFinanceWorkbench, mockWorkOrderPage,
  mockMaterialInboundPage, mockMachineWorkLogPage,
} = vi.hoisted(() => ({
  mockRouteQuery: { value: {} as Record<string, any> },
  mockRoutePath: { value: '' },
  mockRouterReplace: vi.fn(),
  mockPermissions: { value: [] as string[] },
  mockRoles: { value: [] as string[] },
  mockFinanceWorkbench: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockWorkOrderPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockMaterialInboundPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockMachineWorkLogPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ replace: mockRouterReplace, push: vi.fn() }),
    useRoute: () => ({ query: mockRouteQuery.value, params: {}, path: mockRoutePath.value, meta: mockRouteQuery.value.__meta }),
  }
})
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ permissions: mockPermissions.value, roles: mockRoles.value }),
}))
vi.mock('@/api/finance', () => ({ getFinanceWorkbench: mockFinanceWorkbench }))
vi.mock('@/api/labor', () => ({ getWorkOrderPage: mockWorkOrderPage }))
vi.mock('@/api/material', () => ({ getMaterialInboundPage: mockMaterialInboundPage }))
vi.mock('@/api/machine', () => ({ getMachineWorkLogPage: mockMachineWorkLogPage }))

import HubPage from '@/components/HubPage.vue'

const pageStub = (name: string) =>
  defineComponent({ name, setup: () => () => h('div', { class: `${name}-stub` }) })

function tab(key: string, extra: Record<string, any> = {}) {
  return { key, label: `T-${key}`, loader: () => Promise.resolve({ default: pageStub(`${key}Stub`) }), ...extra }
}

const MEMORY_KEY = 'zw-hub-tab:/labor/hub/labor-group'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
  localStorage.clear()
  mockPermissions.value = []
  mockRoles.value = []
  mockRoutePath.value = ''
  mockRouteQuery.value = {}
  mockFinanceWorkbench.mockResolvedValue({ code: 200, data: {} })
  mockWorkOrderPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
})

async function mountHub(tabs: any[], options: { query?: Record<string, any>; path?: string; perms?: string[]; roles?: string[] } = {}) {
  mockPermissions.value = options.perms || []
  mockRoles.value = options.roles || []
  mockRoutePath.value = options.path || ''
  mockRouteQuery.value = { ...(options.query || {}), __meta: { hubTabs: tabs } }
  wrapper = mount(HubPage, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('HubPage S2 首 Tab 选择', () => {
  const TABS = [tab('a'), tab('b'), tab('c')]

  it('无 URL 参数时恢复上次访问的 Tab（按路由 path 记忆）', async () => {
    localStorage.setItem(MEMORY_KEY, 'b')
    const w = await mountHub(TABS, { path: '/labor/hub/labor-group' })
    expect(w.vm.$.setupState.activeTab).toBe('b')
  })

  it('URL ?tab= 优先于访问记忆', async () => {
    localStorage.setItem(MEMORY_KEY, 'b')
    const w = await mountHub(TABS, { path: '/labor/hub/labor-group', query: { tab: 'a' } })
    expect(w.vm.$.setupState.activeTab).toBe('a')
  })

  it('切 Tab 后写回记忆（下次进入直接恢复）', async () => {
    const w = await mountHub(TABS, { path: '/labor/hub/labor-group' })
    w.vm.$.setupState.activate('c')
    await flushPromises()
    expect(localStorage.getItem(MEMORY_KEY)).toBe('c')
  })

  it('无记忆时按角色命中 defaultForRoles', async () => {
    const roleTabs = [tab('contract'), tab('work-order', { defaultForRoles: ['PROJECT_MANAGER'] })]
    const w = await mountHub(roleTabs, { path: '/labor/hub/labor-group', roles: ['PROJECT_MANAGER'] })
    expect(w.vm.$.setupState.activeTab).toBe('work-order')
  })

  it('记忆内容已不存在时回落角色默认，不进入失效页', async () => {
    localStorage.setItem(MEMORY_KEY, 'gone')
    const roleTabs = [tab('contract'), tab('work-order', { defaultForRoles: ['PROJECT_MANAGER'] })]
    const w = await mountHub(roleTabs, { path: '/labor/hub/labor-group', roles: ['PROJECT_MANAGER'] })
    expect(w.vm.$.setupState.activeTab).toBe('work-order')
  })
})

describe('HubPage S2 待办徽标', () => {
  it('财务三色待办按口径渲染，计数为 0 的页签不显示徽标', async () => {
    mockFinanceWorkbench.mockResolvedValue({
      code: 200,
      data: {
        payable: { pendingApprovalCount: 3, approvedUnpaidCount: 0 },
        receivable: { overdueCount: 12 },
      },
    })
    const tabs = [
      tab('payment-apply', { badge: 'financePendingApproval' }),
      tab('payable', { badge: 'financeUnpaid' }),
      tab('receivable', { badge: 'financeOverdueReceivable' }),
    ]
    const w = await mountHub(tabs, { perms: ['finance:view'] })
    const badgeEls = w.findAll('.hub-tab-badge')
    expect(badgeEls).toHaveLength(2)
    expect(badgeEls[0].text()).toBe('3')
    expect(badgeEls[0].attributes('title')).toContain('待审批付款')
    expect(badgeEls[1].text()).toBe('12')
  })

  it('财务徽标只发一次聚合请求（多页签共用同一数据源）', async () => {
    const tabs = [
      tab('payment-apply', { badge: 'financePendingApproval' }),
      tab('payable', { badge: 'financeUnpaid' }),
    ]
    await mountHub(tabs, { perms: ['finance:view'] })
    expect(mockFinanceWorkbench).toHaveBeenCalledTimes(1)
  })

  it('徽标接口失败只隐藏徽标，页签仍可用', async () => {
    mockWorkOrderPage.mockRejectedValueOnce(new Error('500'))
    const tabs = [tab('contract'), tab('work-order', { badge: 'laborWorkOrderDraft' })]
    const w = await mountHub(tabs, { perms: ['labor:view'] })
    expect(w.findAll('.hub-tab-label').map((l: any) => l.text())).toEqual(['T-contract', 'T-work-order'])
    expect(w.findAll('.hub-tab-badge')).toHaveLength(0)
  })

  it('点工草稿徽标走 status=DRAFT 过滤', async () => {
    mockWorkOrderPage.mockResolvedValue({ code: 200, data: { records: [], total: 7 } })
    const w = await mountHub([tab('work-order', { badge: 'laborWorkOrderDraft' })], { perms: ['labor:view'] })
    expect(mockWorkOrderPage).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 1, status: 'DRAFT' }))
    expect(w.find('.hub-tab-badge').text()).toBe('7')
  })

  it('材料入库草稿徽标走 status=DRAFT 过滤', async () => {
    mockMaterialInboundPage.mockResolvedValue({ code: 200, data: { records: [], total: 4 } })
    const w = await mountHub([tab('inbound', { badge: 'materialInboundDraft' })], { perms: ['material:view'] })
    expect(mockMaterialInboundPage).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 1, status: 'DRAFT' }))
    expect(w.find('.hub-tab-badge').text()).toBe('4')
    expect(w.find('.hub-tab-badge').attributes('title')).toContain('草稿待提交')
  })

  it('机械台班待确认徽标走 status=DRAFT 过滤', async () => {
    mockMachineWorkLogPage.mockResolvedValue({ code: 200, data: { records: [], total: 2 } })
    const w = await mountHub([tab('work-log', { badge: 'machineWorkLogDraft' })], { perms: ['machine:view'] })
    expect(mockMachineWorkLogPage).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 1, status: 'DRAFT' }))
    expect(w.find('.hub-tab-badge').text()).toBe('2')
    expect(w.find('.hub-tab-badge').attributes('title')).toContain('台班待确认')
  })
})

describe('HubPage S2 长 Hub 收敛', () => {
  const LONG_TABS = Array.from({ length: 9 }, (_, i) =>
    tab(`t${i + 1}`, i >= 6 ? { group: '结算与奖惩' } : {})
  )

  it('超过 6 个页签：主区渲染 6 个，其余进入「更多」', async () => {
    const w = await mountHub(LONG_TABS)
    const st = w.vm.$.setupState
    expect(st.primaryTabs).toHaveLength(6)
    expect(st.overflowTabs.map((t: any) => t.key)).toEqual(['t7', 't8', 't9'])
    // 主区按钮（6）+ 更多按钮（1）
    expect(w.findAll('.hub-tab')).toHaveLength(7)
    expect(w.find('.hub-tab--more').text()).toContain('更多')
  })

  it('溢出页签按 group 连续分组', async () => {
    const w = await mountHub(LONG_TABS)
    const groups = w.vm.$.setupState.overflowGroups
    expect(groups).toHaveLength(1)
    expect(groups[0].group).toBe('结算与奖惩')
    expect(groups[0].items).toHaveLength(3)
  })

  it('激活溢出页签时「更多」显示当前页签名并保持可用', async () => {
    const w = await mountHub(LONG_TABS)
    w.vm.$.setupState.activate('t8')
    await flushPromises()
    await w.vm.$nextTick()
    const st = w.vm.$.setupState
    expect(st.activeTab).toBe('t8')
    expect(st.overflowActive).toBe(true)
    expect(w.find('.hub-tab--more').text()).toContain('T-t8')
  })

  it('页签不超过 6 个时不渲染「更多」', async () => {
    const w = await mountHub(LONG_TABS.slice(0, 6))
    expect(w.find('.hub-tab--more').exists()).toBe(false)
  })
})
