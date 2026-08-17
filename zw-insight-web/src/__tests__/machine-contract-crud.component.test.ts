/**
 * machine/contract.vue 机械合同页组件测试（2026-08-15 P3 收尾批）
 * 工厂 6 标准用例 + 行提交审批定制例（待决策 #7 落地）。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockPage, mockCreate, mockUpdate, mockDelete, mockSubmit } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/api/machine', () => ({
  getMachineContractPage: mockPage, createMachineContract: mockCreate, updateMachineContract: mockUpdate,
  deleteMachineContract: mockDelete, submitMachineContract: mockSubmit,
}))
// 内嵌 ProjectSelector 子组件使用，mock 防真实请求
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

import MachineContract from '@/views/machine/contract.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'machine/contract.vue 机械合同',
  component: MachineContract,
  pageMock: mockPage, createMock: mockCreate, updateMock: mockUpdate, deleteMock: mockDelete,
  addButtonText: '新增机械合同',
  requiredError: '请输入合同名称',
  records: [
    { id: 1, contractName: '塔吊租赁合同', machineName: '塔吊A', supplierName: '租赁公司甲', rentalType: 'SHIFT', contractAmount: 300000, status: 'DRAFT' },
    { id: 2, contractName: '挖机租赁合同', machineName: '挖掘机B', supplierName: '租赁公司乙', rentalType: 'MONTHLY', contractAmount: 200000, status: 'EFFECTIVE' },
  ],
})

describe('machine/contract.vue 行提交审批', () => {
  let wrapper: any = null
  afterEach(() => {
    if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
    vi.clearAllMocks()
  })

  it('DRAFT 行提交 → 调 submitMachineContract', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: [{ id: 21, contractName: 'C', status: 'DRAFT' }], total: 1 } })
    wrapper = mount(MachineContract, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.vm.$.setupState.handleSubmit({ id: 21 })
    await flushPromises()
    expect(mockSubmit).toHaveBeenCalledWith(21)
  })
})
