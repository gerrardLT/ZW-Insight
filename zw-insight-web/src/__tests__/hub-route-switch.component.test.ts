/**
 * HubPage 跨 Hub 复用回归（S2 审核 Critical）：
 * 多个 hub 路由共用同一个 HubPage 实例（router-view 不换组件类型），而
 * contract / settlement / output 等页签 key 在不同 Hub 间重名。
 * 本用例用真实 vue-router 复现「劳务 → 机械」切换，钉住：
 * 1）页签组件缓存键带路由 path，不会把上一个 Hub 的页面挂到当前 Hub；
 * 2）切 Hub 后按新 Hub 的首 Tab 规则重选，而不是沿用旧 Tab key。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { defineComponent, h } from 'vue'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'

vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ permissions: [], roles: [] }),
}))
vi.mock('@/api/finance', () => ({ getFinanceWorkbench: vi.fn(async () => ({ data: {} })) }))
vi.mock('@/api/labor', () => ({ getWorkOrderPage: vi.fn(async () => ({ data: { total: 0 } })) }))

import HubPage from '@/components/HubPage.vue'

const pageStub = (name: string) =>
  defineComponent({ name, setup: () => () => h('div', { class: `${name}-stub` }) })

const LABOR_TABS = [
  { key: 'contract', label: '劳务分包合同', loader: () => Promise.resolve(pageStub('laborContract')) },
  { key: 'roster', label: '现场劳务花名册', loader: () => Promise.resolve(pageStub('laborRoster')) },
]
const MACHINE_TABS = [
  { key: 'contract', label: '机械租赁合同', loader: () => Promise.resolve(pageStub('machineContract')) },
  { key: 'ledger', label: '机械设备台账', loader: () => Promise.resolve(pageStub('machineLedger')) },
]

const Root = defineComponent({ setup: () => () => h(RouterView) })

function buildRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/labor/hub/labor-group', component: HubPage, meta: { hubTabs: LABOR_TABS } },
      { path: '/machine/hub/machine-group', component: HubPage, meta: { hubTabs: MACHINE_TABS } },
    ],
  })
}

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  localStorage.clear()
})

describe('HubPage 跨 Hub 切换', () => {
  it('同名页签 key 不复用上一个 Hub 的组件，且按新 Hub 重选首 Tab', async () => {
    const router = buildRouter()
    router.push('/labor/hub/labor-group')
    await router.isReady()
    wrapper = mount(Root, { global: { plugins: [router, ElementPlus] } })
    await flushPromises()

    expect(wrapper.find('.laborContract-stub').exists()).toBe(true)

    // 切到机械 Hub：两个 Hub 都存在 key=contract 的页签
    await router.push('/machine/hub/machine-group')
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('.machineContract-stub').exists()).toBe(true)
    expect(wrapper.find('.laborContract-stub').exists()).toBe(false)
  })
})
