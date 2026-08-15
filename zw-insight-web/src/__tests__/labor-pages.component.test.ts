/**
 * labor 域合同与派工单页组件测试（2026-08-15 P3 收尾批）
 *
 * labor/contract（劳务合同，含提交审批按钮——2026-08-15 待决策 #7 落地）
 * labor/work-order（派工单，提交后汇总进工资单）。
 * 工厂 6 标准用例 + 各 1 行提交审批定制例。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockContractPage, mockContractCreate, mockContractUpdate, mockContractDelete, mockContractSubmit,
  mockWorkOrderPage, mockWorkOrderCreate, mockWorkOrderUpdate, mockWorkOrderDelete, mockWorkOrderSubmit,
} = vi.hoisted(() => {
  const page = () => vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } }))
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  return {
    mockContractPage: page(), mockContractCreate: ok(), mockContractUpdate: ok(), mockContractDelete: ok(), mockContractSubmit: ok(),
    mockWorkOrderPage: page(), mockWorkOrderCreate: ok(), mockWorkOrderUpdate: ok(), mockWorkOrderDelete: ok(), mockWorkOrderSubmit: ok(),
  }
})

vi.mock('@/api/labor', () => ({
  getLaborContractPage: mockContractPage, createLaborContract: mockContractCreate, updateLaborContract: mockContractUpdate,
  deleteLaborContract: mockContractDelete, submitLaborContract: mockContractSubmit,
  getWorkOrderPage: mockWorkOrderPage, createWorkOrder: mockWorkOrderCreate, updateWorkOrder: mockWorkOrderUpdate,
  deleteWorkOrder: mockWorkOrderDelete, submitWorkOrder: mockWorkOrderSubmit,
  // work-order 内嵌 TeamSelector 子组件使用，mock 防真实请求
  getLaborTeamPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
}))
// 内嵌 ProjectSelector 子组件使用
vi.mock('@/api/project', () => ({
  getProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import LaborContract from '@/views/labor/contract.vue'
import WorkOrder from '@/views/labor/work-order.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'labor/contract.vue 劳务合同',
  component: LaborContract,
  pageMock: mockContractPage, createMock: mockContractCreate, updateMock: mockContractUpdate, deleteMock: mockContractDelete,
  addButtonText: '新增劳务合同',
  requiredError: '请输入合同名称',
  records: [
    { id: 1, contractName: '木工劳务合同', teamName: '木工一班', contractAmount: 500000, status: 'DRAFT', startDate: '2026-01-01', endDate: '2026-12-31' },
    { id: 2, contractName: '钢筋劳务合同', teamName: '钢筋一班', contractAmount: 800000, status: 'EFFECTIVE', startDate: '2026-01-01', endDate: '2026-12-31' },
  ],
})

crudPageSuite({
  title: 'labor/work-order.vue 派工单',
  component: WorkOrder,
  pageMock: mockWorkOrderPage, createMock: mockWorkOrderCreate, updateMock: mockWorkOrderUpdate, deleteMock: mockWorkOrderDelete,
  addButtonText: '新增派工单',
  requiredError: '请选择项目',
  pageKey: 'page', // work-order 为 page/size 口径（contract 为 pageNum/pageSize）
  records: [
    { id: 1, projectName: '滨江花园一期', teamName: '木工一班', workDate: '2026-08-01', workerCount: 12, status: 'DRAFT' },
    { id: 2, projectName: '城南市政', teamName: '钢筋一班', workDate: '2026-08-02', workerCount: 8, status: 'APPROVED' },
  ],
})

// ─── 行提交审批定制例（待决策 #7 落地后 UI 闭环补充） ───
describe('labor 合同/派工单 行提交审批', () => {
  let wrapper: any = null
  afterEach(() => {
    if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
    vi.clearAllMocks()
  })

  it('劳务合同 DRAFT 行提交 → 调 submitLaborContract', async () => {
    mockContractPage.mockResolvedValue({ code: 200, data: { records: [{ id: 9, contractName: 'C', status: 'DRAFT' }], total: 1 } })
    wrapper = mount(LaborContract, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.vm.$.setupState.handleSubmit({ id: 9 })
    await flushPromises()
    expect(mockContractSubmit).toHaveBeenCalledWith(9)
  })

  it('派工单行提交 → 调 submitWorkOrder', async () => {
    mockWorkOrderPage.mockResolvedValue({ code: 200, data: { records: [{ id: 11, projectName: 'P', status: 'DRAFT' }], total: 1 } })
    wrapper = mount(WorkOrder, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.vm.$.setupState.handleSubmit({ id: 11 })
    await flushPromises()
    expect(mockWorkOrderSubmit).toHaveBeenCalledWith(11)
  })
})
