/**
 * budget 域预算/变更单/控制配置页组件测试（2026-08-15 P3 收尾批 10a）
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockBudgetPage, mockBudgetCreate, mockBudgetUpdate, mockBudgetDelete, mockBudgetSubmit,
  mockChangeList, mockChangeCreate, mockChangeUpdate, mockChangeDelete, mockChangeSubmit, mockChangeWithdraw,
  mockConfigList, mockConfigCreate, mockConfigUpdate, mockConfigDelete,
  mockProjectList,
} = vi.hoisted(() => {
  const page = () => vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } }))
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  return {
    mockBudgetPage: page(), mockBudgetCreate: ok(), mockBudgetUpdate: ok(), mockBudgetDelete: ok(), mockBudgetSubmit: ok(),
    mockChangeList: page(), mockChangeCreate: ok(), mockChangeUpdate: ok(), mockChangeDelete: ok(), mockChangeSubmit: ok(), mockChangeWithdraw: ok(),
    mockConfigList: page(), mockConfigCreate: ok(), mockConfigUpdate: ok(), mockConfigDelete: ok(),
    mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  }
})

vi.mock('@/api/budget', () => ({
  getBudgetPage: mockBudgetPage, createBudget: mockBudgetCreate, updateBudget: mockBudgetUpdate,
  deleteBudget: mockBudgetDelete, submitBudget: mockBudgetSubmit,
}))
vi.mock('@/api/budget-change', () => ({
  listBudgetChanges: mockChangeList, createBudgetChange: mockChangeCreate, updateBudgetChange: mockChangeUpdate,
  deleteBudgetChange: mockChangeDelete, submitBudgetChange: mockChangeSubmit, withdrawBudgetChange: mockChangeWithdraw,
}))
vi.mock('@/api/budget-control-config', () => ({
  listBudgetControlConfigs: mockConfigList, createBudgetControlConfig: mockConfigCreate,
  updateBudgetControlConfig: mockConfigUpdate, deleteBudgetControlConfig: mockConfigDelete,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockProjectList,
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {}, params: {} }),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Budget from '@/views/budget/index.vue'
import BudgetChange from '@/views/budget/change/index.vue'
import ControlConfig from '@/views/budget/control-config/index.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

describe('budget/index.vue 预算管理', () => {
  async function mountPage(records: any[] = []) {
    mockBudgetPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(Budget, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载并渲染行', async () => {
    const w = await mountPage([{ id: 1, projectName: 'P1', totalAmount: 100, status: 'DRAFT' }])
    expect(mockBudgetPage).toHaveBeenCalled()
    expect(w.findAll('.el-table__row')).toHaveLength(1)
  })

  it('新增走 create、编辑走 update', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockBudgetCreate).toHaveBeenCalledTimes(1)
    expect(mockBudgetUpdate).not.toHaveBeenCalled()
    st.handleEdit({ id: 6, projectId: 1, projectName: 'P1', totalAmount: 100 })
    await flushPromises()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockBudgetUpdate).toHaveBeenCalledTimes(1)
  })

  it('行提交审批调 submitBudget、删除调 deleteBudget', async () => {
    await mountPage([{ id: 2, status: 'DRAFT' }])
    await wrapper.vm.$.setupState.handleSubmit({ id: 2 })
    await flushPromises()
    expect(mockBudgetSubmit).toHaveBeenCalledWith(2)
    await wrapper.vm.$.setupState.handleDelete({ id: 2 })
    await flushPromises()
    expect(mockBudgetDelete).toHaveBeenCalledWith(2)
  })

  it('搜索重置页码、重置清空条件', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.queryParams.pageNum = 3
    mockBudgetPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
    st.handleReset()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
  })
})

describe('budget/change/index.vue 预算变更单', () => {
  async function mountPage(records: any[] = []) {
    mockChangeList.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(BudgetChange, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载变更单列表', async () => {
    const w = await mountPage([{ id: 1, changeNo: 'BG-001', status: 'DRAFT' }])
    expect(mockChangeList).toHaveBeenCalled()
    expect(w.findAll('.el-table__row')).toHaveLength(1)
  })

  it('行提交调 submitBudgetChange、撤回调 withdrawBudgetChange', async () => {
    await mountPage([{ id: 3, status: 'DRAFT' }])
    const st = wrapper.vm.$.setupState
    await st.handleSubmit({ id: 3 })
    await flushPromises()
    expect(mockChangeSubmit).toHaveBeenCalledWith(3)
    await st.handleWithdraw({ id: 3 })
    await flushPromises()
    expect(mockChangeWithdraw).toHaveBeenCalledWith(3)
  })

  it('删除调 deleteBudgetChange 并刷新', async () => {
    await mountPage([{ id: 4 }])
    mockChangeList.mockClear()
    await wrapper.vm.$.setupState.handleDelete({ id: 4 })
    await flushPromises()
    expect(mockChangeDelete).toHaveBeenCalledWith(4)
    expect(mockChangeList).toHaveBeenCalled()
  })
})

describe('budget/control-config/index.vue 预算控制配置', () => {
  async function mountPage(records: any[] = []) {
    mockConfigList.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(ControlConfig, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载配置列表', async () => {
    const w = await mountPage([{ id: 1, projectName: 'P1', controlMode: 'BLOCK', warningThreshold: 80 }])
    expect(mockConfigList).toHaveBeenCalled()
    expect(w.findAll('.el-table__row')).toHaveLength(1)
  })

  it('项目选择联动回填 projectName', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    st.handleProjectChange(7, { projectName: '滨江花园一期' })
    expect(st.formData.projectId ?? st.formData.projectName).toBeTruthy()
    expect(st.formData.projectName).toBe('滨江花园一期')
  })

  it('提交组装 payload（projectId/controlMode/warningThreshold）', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    st.formData.projectId = 7
    st.formData.controlMode = 'BLOCK'
    st.formData.warningThreshold = 90
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockConfigCreate).toHaveBeenCalledTimes(1)
    expect((mockConfigCreate.mock.calls as any)[0][0]).toMatchObject({ controlMode: 'BLOCK', warningThreshold: 90 })
  })

  it('删除调 deleteBudgetControlConfig', async () => {
    await mountPage([{ id: 9 }])
    await wrapper.vm.$.setupState.handleDelete({ id: 9 })
    await flushPromises()
    expect(mockConfigDelete).toHaveBeenCalledWith(9)
  })
})
