/**
 * contract/form.vue 只读查看模式测试（2026-08-14 P0 盲点 3 钉住 @matrix 盲点 3）
 *
 * 修复前：列表「查看」与「编辑」同跳 /contract/edit/:id，非草稿合同也可进编辑表单保存。
 * 修复后：「查看」携 ?mode=view，表单整体 disabled + 隐藏保存/新增明细 + handleSubmit 守卫。
 * 本测试钉住只读语义防回归；编辑缺省行为不变的回归一并覆盖。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockGetContractDetail,
  mockCreateContract,
  mockUpdateContract,
  mockGetContractDetails,
  mockSaveContractDetails,
  mockGetProjectList,
  mockRouteQuery,
} = vi.hoisted(() => ({
  mockGetContractDetail: vi.fn(async (_id?: any): Promise<any> => ({
    code: 200,
    data: { id: '90001', projectId: 1, projectName: 'P', contractCode: 'C1', contractAmount: 100 },
  })),
  mockCreateContract: vi.fn(async (_d?: any): Promise<any> => ({ code: 200, data: { id: 1 } })),
  mockUpdateContract: vi.fn(async (_d?: any): Promise<any> => ({ code: 200 })),
  mockGetContractDetails: vi.fn(async (_id?: any): Promise<any> => ({ code: 200, data: [] })),
  mockSaveContractDetails: vi.fn(async (_id?: any, _d?: any): Promise<any> => ({ code: 200 })),
  mockGetProjectList: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: [{ id: 1, projectName: 'P' }] })),
  mockRouteQuery: { value: {} as Record<string, any> },
}))

vi.mock('@/api/contract', () => ({
  getContractDetail: mockGetContractDetail,
  createContract: mockCreateContract,
  updateContract: mockUpdateContract,
  getContractDetails: mockGetContractDetails,
  saveContractDetails: mockSaveContractDetails,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockGetProjectList,
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '90001' }, query: mockRouteQuery.value }),
  useRouter: () => ({ push: vi.fn() }),
}))

import ContractForm from '@/views/contract/form.vue'

beforeEach(() => {
  vi.clearAllMocks()
  mockRouteQuery.value = {}
})

describe('contract/form.vue 只读查看模式（@matrix 盲点 3）', () => {
  it('mode=view：标题为详情、表单禁用、无保存按钮、无新增明细按钮', { timeout: 20_000 }, async () => {
    mockRouteQuery.value = { mode: 'view' }
    const wrapper = mount(ContractForm, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('施工合同详情')
    // 保存/取消操作区被隐藏
    expect(wrapper.findAll('button').map((b) => b.text()).join(' ')).not.toContain('保存')
    // 新增明细入口被隐藏
    expect(wrapper.text()).not.toContain('新增明细')
    // 表单控件被禁用（el-form :disabled 经 provide 传递到内部控件）
    const inputs = wrapper.findAll('input')
    expect(inputs.length).toBeGreaterThan(0)
    expect(inputs.every((i) => i.attributes('disabled') !== undefined)).toBe(true)
  })

  // contract/form.vue 为重型页（BOQ 明细表+多子组件），全量并行挂载下默认 5s 超时
  // 实证不够（单跑 3/3 绿、全量并行 timeout，2026-08-15 第三批实跑），放宽到 20s
  it('mode=view：handleSubmit 守卫直接返回，不触发任何保存 API', { timeout: 20_000 }, async () => {
    mockRouteQuery.value = { mode: 'view' }
    const wrapper = mount(ContractForm, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const state: any = wrapper.vm.$.setupState

    await state.handleSubmit()
    await flushPromises()
    expect(mockUpdateContract).not.toHaveBeenCalled()
    expect(mockCreateContract).not.toHaveBeenCalled()
  })

  it('缺省（编辑模式）行为不变：标题为编辑、保存按钮存在', { timeout: 20_000 }, async () => {
    mockRouteQuery.value = {}
    const wrapper = mount(ContractForm, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('编辑施工合同')
    expect(wrapper.findAll('button').map((b) => b.text()).join(' ')).toContain('保存')
    expect(wrapper.text()).toContain('新增明细')
  })
})
