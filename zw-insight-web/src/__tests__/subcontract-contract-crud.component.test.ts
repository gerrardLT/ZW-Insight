/**
 * subcontract/contract.vue 分包合同页组件测试（2026-08-15 P3 收尾批）
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

vi.mock('@/api/subcontract', () => ({
  getSubcontractPage: mockPage, createSubcontract: mockCreate, updateSubcontract: mockUpdate,
  deleteSubcontract: mockDelete, submitSubcontract: mockSubmit,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Subcontract from '@/views/subcontract/contract.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'subcontract/contract.vue 分包合同',
  component: Subcontract,
  pageMock: mockPage, createMock: mockCreate, updateMock: mockUpdate, deleteMock: mockDelete,
  addButtonText: '新增分包合同',
  requiredError: '请输入合同名称',
  records: [
    { id: 1, contractName: '幕墙分包合同', subcontractorName: '幕墙公司甲', contractAmount: 1200000, status: 'DRAFT', startDate: '2026-02-01', endDate: '2026-10-31' },
    { id: 2, contractName: '消防分包合同', subcontractorName: '消防公司乙', contractAmount: 600000, status: 'EFFECTIVE', startDate: '2026-03-01', endDate: '2026-09-30' },
  ],
})

describe('subcontract/contract.vue 行提交审批', () => {
  let wrapper: any = null
  afterEach(() => {
    if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
    vi.clearAllMocks()
  })

  it('DRAFT 行提交 → 调 submitSubcontract', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: [{ id: 31, contractName: 'C', status: 'DRAFT' }], total: 1 } })
    wrapper = mount(Subcontract, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.vm.$.setupState.handleSubmit({ id: 31 })
    await flushPromises()
    expect(mockSubmit).toHaveBeenCalledWith(31)
  })
})
