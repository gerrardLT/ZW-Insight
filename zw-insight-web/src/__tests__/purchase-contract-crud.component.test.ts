/**
 * purchase/contract.vue 采购合同页组件测试（2026-08-15 P3 收尾批）
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

vi.mock('@/api/purchase', () => ({
  getPurchaseContractPage: mockPage, createPurchaseContract: mockCreate, updatePurchaseContract: mockUpdate,
  deletePurchaseContract: mockDelete, submitPurchaseContract: mockSubmit,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import PurchaseContract from '@/views/purchase/contract.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'purchase/contract.vue 采购合同',
  component: PurchaseContract,
  pageMock: mockPage, createMock: mockCreate, updateMock: mockUpdate, deleteMock: mockDelete,
  addButtonText: '新增采购合同',
  requiredError: '请输入合同名称',
  records: [
    { id: 1, contractName: '钢筋采购合同', contractCode: 'PC-001', supplierName: '钢贸公司甲', contractAmount: 2000000, status: 'DRAFT', signingDate: '2026-01-15' },
    { id: 2, contractName: '水泥采购合同', contractCode: 'PC-002', supplierName: '建材公司乙', contractAmount: 800000, status: 'EFFECTIVE', signingDate: '2026-02-20' },
  ],
})

describe('purchase/contract.vue 行提交审批', () => {
  let wrapper: any = null
  afterEach(() => {
    if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
    vi.clearAllMocks()
  })

  it('DRAFT 行提交 → 调 submitPurchaseContract', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: [{ id: 41, contractName: 'C', status: 'DRAFT' }], total: 1 } })
    wrapper = mount(PurchaseContract, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.vm.$.setupState.handleSubmit({ id: 41 })
    await flushPromises()
    expect(mockSubmit).toHaveBeenCalledWith(41)
  })
})
