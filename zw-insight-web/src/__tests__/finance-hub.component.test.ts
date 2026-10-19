/**
 * FinanceHubPage.vue 聚合入口壳测试（V2026_120 财务二级菜单收敛）
 *
 * 覆盖：Tab 按 meta.hubTabs 渲染 / ?tab= 直达激活 / 切 Tab router.replace 保留其余 query
 *（projectId 不丢）/ 首个 Tab 默认激活。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockRouteQuery, mockRouterReplace } = vi.hoisted(() => ({
  mockRouteQuery: { value: {} as Record<string, string> },
  mockRouterReplace: vi.fn(),
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ replace: mockRouterReplace, push: vi.fn() }),
    useRoute: () => ({ query: mockRouteQuery.value, params: {}, meta: mockRouteQuery.value.__meta }),
  }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return { ...actual }
})

import FinanceHubPage from '@/views/finance/hub/FinanceHubPage.vue'

const TABS = [
  { key: 'settlement', label: '工程竣工结算', loader: () => import('@/views/finance/settlement/index.vue') },
  { key: 'receivable', label: '应收工程账款台账', loader: () => import('@/views/finance/receivable/index.vue') },
]

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
  mockRouteQuery.value = {}
})

async function mountHub(query: Record<string, string> = {}) {
  mockRouteQuery.value = { ...query, __meta: { hubTabs: TABS } } as any
  wrapper = mount(FinanceHubPage, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('finance/hub/FinanceHubPage.vue 聚合入口壳', () => {
  it('按 meta.hubTabs 渲染全部 Tab 标签，默认激活第一个', async () => {
    const w = await mountHub()
    const labels = w.findAll('.hub-tab-label').map((l: any) => l.text())
    expect(labels).toEqual(['工程竣工结算', '应收工程账款台账'])
    expect(w.vm.$.setupState.activeTab).toBe('settlement')
  })

  it('?tab=receivable 直达激活指定 Tab', async () => {
    const w = await mountHub({ tab: 'receivable' })
    expect(w.vm.$.setupState.activeTab).toBe('receivable')
  })

  it('切 Tab 时 router.replace 保留其余 query（projectId 不丢）', async () => {
    mockRouteQuery.value = { projectId: '90001', __meta: { hubTabs: TABS } } as any
    wrapper = mount(FinanceHubPage, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    st.activeTab = 'receivable'
    await flushPromises()
    expect(mockRouterReplace).toHaveBeenCalled()
    const arg = (mockRouterReplace.mock.calls as any)[0][0]
    expect(arg.query).toMatchObject({ projectId: '90001', tab: 'receivable' })
  })
})
