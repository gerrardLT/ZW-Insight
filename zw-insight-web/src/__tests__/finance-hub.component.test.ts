/**
 * HubPage.vue 聚合入口壳测试（V2026_120/121/122 二级菜单收敛）
 *
 * 覆盖：Tab 按 meta.hubTabs 渲染 / ?tab= 直达激活 / 切 Tab router.replace 保留其余 query
 *（projectId 不丢）/ 首个 Tab 默认激活 / V2026_122 Tab 级权限过滤（无码 Tab 不渲染、
 * 超管全过、全无权限空态）。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockRouteQuery, mockRouterReplace, mockPermissions } = vi.hoisted(() => ({
  mockRouteQuery: { value: {} as Record<string, string> },
  mockRouterReplace: vi.fn(),
  mockPermissions: { value: [] as string[] },
}))

vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ replace: mockRouterReplace, push: vi.fn() }),
    useRoute: () => ({ query: mockRouteQuery.value, params: {}, meta: mockRouteQuery.value.__meta }),
  }
})
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ permissions: mockPermissions.value }),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return { ...actual }
})

import HubPage from '@/components/HubPage.vue'

const TABS = [
  { key: 'settlement', label: '工程竣工结算', loader: () => import('@/views/finance/settlement/index.vue') },
  { key: 'receivable', label: '应收工程账款台账', loader: () => import('@/views/finance/receivable/index.vue') },
]

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
  mockPermissions.value = []
  mockRouteQuery.value = {}
})

async function mountHub(query: Record<string, string> = {}) {
  mockRouteQuery.value = { ...query, __meta: { hubTabs: TABS } } as any
  wrapper = mount(HubPage, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('components/HubPage.vue 聚合入口壳', () => {
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
    wrapper = mount(HubPage, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    st.activeTab = 'receivable'
    await flushPromises()
    expect(mockRouterReplace).toHaveBeenCalled()
    const arg = (mockRouterReplace.mock.calls as any)[0][0]
    expect(arg.query).toMatchObject({ projectId: '90001', tab: 'receivable' })
  })
})

describe('components/HubPage.vue Tab 级权限过滤（V2026_122 权限补强）', () => {
  const SECURE_TABS = [
    { key: 'list', label: '项目台账', loader: () => import('@/views/project/index.vue') },
    { key: 'wbs', label: '工程分解与任务节点（WBS）', loader: () => import('@/views/project/wbs/index.vue'), permission: 'project:wbs:view' },
  ]

  async function mountSecure(perms: string[], query: Record<string, string> = {}) {
    mockPermissions.value = perms
    mockRouteQuery.value = { ...query, __meta: { hubTabs: SECURE_TABS } } as any
    wrapper = mount(HubPage, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('无权限码的 Tab 不渲染（细粒度守卫在 Tab 内挂载时不失效）', async () => {
    const w = await mountSecure([])
    const labels = w.findAll('.hub-tab-label').map((l: any) => l.text())
    expect(labels).toEqual(['项目台账'])
    expect(labels).not.toContain('工程分解与任务节点（WBS）')
  })

  it('持有权限码的 Tab 正常渲染；无权限用户 ?tab= 指向受限 Tab 时回落首个可见', async () => {
    const w = await mountSecure(['project:wbs:view'], { tab: 'wbs' })
    const labels = w.findAll('.hub-tab-label').map((l: any) => l.text())
    expect(labels).toEqual(['项目台账', '工程分解与任务节点（WBS）'])
    expect(w.vm.$.setupState.activeTab).toBe('wbs')
    const w2 = await mountSecure([], { tab: 'wbs' })
    expect(w2.vm.$.setupState.activeTab).toBe('list')
  })

  it('超管（*:*:*）全量放行', async () => {
    const w = await mountSecure(['*:*:*'])
    expect(w.findAll('.hub-tab-label')).toHaveLength(2)
  })

  it('全部 Tab 均无权限时渲染空态提示（不伪装成空列表）', async () => {
    mockPermissions.value = []
    mockRouteQuery.value = { __meta: { hubTabs: [SECURE_TABS[1]] } } as any
    wrapper = mount(HubPage, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.find('.el-empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('无该模块的功能权限')
  })
})
