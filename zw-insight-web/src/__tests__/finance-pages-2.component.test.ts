/**
 * finance 域封账/收票/收款/项目报销/结算/税率页组件测试（2026-08-15 P3 收尾批 9）
 * payment-apply 已有既有测试（payment-apply.component.test.ts），本文件补齐其余 6 页。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'

const {
  mockLockPage, mockCreateLock, mockUnlock,
  mockInvoiceReceivedPage, mockCreateInvoiceReceived,
  mockPaymentReceivedPage, mockCreatePaymentReceived, mockUpdatePaymentReceived, mockDeletePaymentReceived,
  mockReimbPage, mockCreateReimb, mockSubmitReimb,
  mockSettlePage, mockCreateSettle, mockSubmitSettle, mockExportSettle,
  mockTaxRates, mockCreateTaxRate, mockUpdateTaxRate, mockDeleteTaxRate,
  mockProjectList, mockError,
} = vi.hoisted(() => {
  const page = () => vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } }))
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  return {
    mockLockPage: page(), mockCreateLock: ok(), mockUnlock: ok(),
    mockInvoiceReceivedPage: page(), mockCreateInvoiceReceived: ok(),
    mockPaymentReceivedPage: page(), mockCreatePaymentReceived: ok(), mockUpdatePaymentReceived: ok(), mockDeletePaymentReceived: ok(),
    mockReimbPage: page(), mockCreateReimb: ok(), mockSubmitReimb: ok(),
    mockSettlePage: page(), mockCreateSettle: vi.fn(async (): Promise<any> => ({ code: 200, data: { id: 777 } })), mockSubmitSettle: ok(),
    mockExportSettle: vi.fn(async (): Promise<any> => new Blob(['x'])),
    mockTaxRates: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockCreateTaxRate: ok(), mockUpdateTaxRate: ok(), mockDeleteTaxRate: ok(),
    mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockError: vi.fn(),
  }
})

vi.mock('@/api/finance', () => ({
  getInvoiceReceivedPage: mockInvoiceReceivedPage, createInvoiceReceived: mockCreateInvoiceReceived,
  getPaymentReceivedPage: mockPaymentReceivedPage, createPaymentReceived: mockCreatePaymentReceived,
  updatePaymentReceived: mockUpdatePaymentReceived, deletePaymentReceived: mockDeletePaymentReceived,
  getProjectReimbursementPage: mockReimbPage, createProjectReimbursement: mockCreateReimb, submitProjectReimbursement: mockSubmitReimb,
}))
vi.mock('@/api/finance-lock', () => ({
  getLockPage: mockLockPage, createLock: mockCreateLock, unlockPeriod: mockUnlock,
}))
vi.mock('@/api/settlement', () => ({
  getSettlementPage: mockSettlePage, createSettlement: mockCreateSettle, submitSettlement: mockSubmitSettle, exportSettlement: mockExportSettle,
}))
vi.mock('@/api/tax-rate', () => ({
  getAllTaxRates: mockTaxRates, createTaxRate: mockCreateTaxRate, updateTaxRate: mockUpdateTaxRate, deleteTaxRate: mockDeleteTaxRate,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockProjectList,
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: mockError, warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import FinanceLock from '@/views/finance/finance-lock/index.vue'
import InvoiceReceived from '@/views/finance/invoice-received.vue'
import PaymentReceived from '@/views/finance/payment-received.vue'
import ProjectReimbursement from '@/views/finance/project-reimbursement.vue'
import Settlement from '@/views/finance/settlement/index.vue'
import TaxRate from '@/views/finance/tax-rate/index.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

function mountOpts() {
  setActivePinia(createPinia())
  return { global: { plugins: [ElementPlus] } }
}

describe('finance/finance-lock/index.vue 财务封账', () => {
  async function mountPage(records: any[] = []) {
    mockLockPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(FinanceLock, mountOpts())
    await flushPromises()
    return wrapper
  }

  it('挂载加载封账记录', async () => {
    const w = await mountPage([{ id: 1, period: '2026-07', lockType: 'FULL', status: 'LOCKED' }])
    expect(mockLockPage).toHaveBeenCalled()
    expect(w.findAll('.el-table__row')).toHaveLength(1)
  })

  it('封账：组装 period+lockType 调 createLock', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    st.formData.period = '2026-08'
    st.formData.lockType = 'FULL'
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockCreateLock).toHaveBeenCalledWith(expect.objectContaining({ period: '2026-08', lockType: 'FULL' }))
  })

  it('解封：确认后调 unlockPeriod 并刷新', async () => {
    await mountPage([{ id: 3, period: '2026-07', status: 'LOCKED' }])
    mockLockPage.mockClear()
    await wrapper.vm.$.setupState.handleUnlock({ id: 3, period: '2026-07' })
    await flushPromises()
    expect(mockUnlock).toHaveBeenCalledWith(3)
    expect(mockLockPage).toHaveBeenCalled()
  })
})

describe('finance/invoice-received.vue 收票登记', () => {
  async function mountPage() {
    mockInvoiceReceivedPage.mockResolvedValue({ code: 200, data: { records: [{ id: 1 }], total: 1 } })
    wrapper = mount(InvoiceReceived, mountOpts())
    await flushPromises()
    return wrapper
  }

  it('挂载加载、搜索重置页码、重置清空', async () => {
    const w = await mountPage()
    expect(mockInvoiceReceivedPage).toHaveBeenCalled()
    const st = w.vm.$.setupState
    st.queryParams.pageNum = 3
    mockInvoiceReceivedPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
    st.handleReset()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
  })

  it('新增收票调 createInvoiceReceived 并刷新', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    st.formData.invoiceAmount = 1000
    mockInvoiceReceivedPage.mockClear()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockCreateInvoiceReceived).toHaveBeenCalledTimes(1)
    expect(mockInvoiceReceivedPage).toHaveBeenCalled()
  })
})

describe('finance/payment-received.vue 收款管理', () => {
  async function mountPage() {
    mockPaymentReceivedPage.mockResolvedValue({ code: 200, data: { records: [{ id: 1 }], total: 1 } })
    wrapper = mount(PaymentReceived, mountOpts())
    await flushPromises()
    return wrapper
  }

  it('新增走 create、编辑走 update（按 id 分流）', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    st.formData.amount = 500
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockCreatePaymentReceived).toHaveBeenCalledTimes(1)
    expect(mockUpdatePaymentReceived).not.toHaveBeenCalled()
    st.handleEdit({ id: 8, amount: 500 })
    await flushPromises()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockUpdatePaymentReceived).toHaveBeenCalledTimes(1)
    expect((mockUpdatePaymentReceived.mock.calls as any)[0][0].id).toBe(8)
  })

  it('删除：确认后调 deletePaymentReceived 并刷新', async () => {
    await mountPage()
    mockPaymentReceivedPage.mockClear()
    await wrapper.vm.$.setupState.handleDelete({ id: 9 })
    await flushPromises()
    expect(mockDeletePaymentReceived).toHaveBeenCalledWith(9)
    expect(mockPaymentReceivedPage).toHaveBeenCalled()
  })
})

describe('finance/project-reimbursement.vue 项目报销', () => {
  async function mountPage() {
    mockReimbPage.mockResolvedValue({ code: 200, data: { records: [{ id: 1, status: 'DRAFT' }], total: 1 } })
    wrapper = mount(ProjectReimbursement, mountOpts())
    await flushPromises()
    return wrapper
  }

  it('挂载加载、新增调 create', async () => {
    await mountPage()
    expect(mockReimbPage).toHaveBeenCalled()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    st.formData.amount = 200
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockCreateReimb).toHaveBeenCalledTimes(1)
  })

  it('行提交审批调 submitProjectReimbursement', async () => {
    await mountPage()
    await wrapper.vm.$.setupState.handleSubmitRow({ id: 12 })
    await flushPromises()
    expect(mockSubmitReimb).toHaveBeenCalledWith(12)
  })
})

describe('finance/settlement/index.vue 竣工结算', () => {
  async function mountPage() {
    mockSettlePage.mockResolvedValue({ code: 200, data: { records: [{ id: 1, settlementCode: 'JS-001', status: 'DRAFT' }], total: 1 } })
    wrapper = mount(Settlement, mountOpts())
    await flushPromises()
    return wrapper
  }

  it('挂载加载结算列表与项目下拉', async () => {
    await mountPage()
    expect(mockSettlePage).toHaveBeenCalled()
    expect(mockProjectList).toHaveBeenCalled()
  })

  it('创建结算：按 projectId 调 createSettlement', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleCreate()
    await flushPromises()
    expect(st.createDialogVisible).toBe(true)
    st.createForm.projectId = 5
    await st.handleCreateSubmit()
    await flushPromises()
    expect(mockCreateSettle).toHaveBeenCalledWith(5)
  })

  it('行提交调 submitSettlement', async () => {
    await mountPage()
    await wrapper.vm.$.setupState.handleSubmit({ id: 21 })
    await flushPromises()
    expect(mockSubmitSettle).toHaveBeenCalledWith(21)
  })
})

describe('finance/tax-rate/index.vue 税率管理', () => {
  async function mountPage() {
    mockTaxRates.mockResolvedValue({ code: 200, data: [{ id: 1, name: '增值税', rateValue: 13 }] })
    wrapper = mount(TaxRate, mountOpts())
    await flushPromises()
    return wrapper
  }

  it('挂载加载税率列表', async () => {
    const w = await mountPage()
    expect(mockTaxRates).toHaveBeenCalled()
    expect(w.text()).toContain('增值税')
  })

  it('新增走 create、编辑走 update 双参 (id, payload)', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    st.formData.name = '附加税'
    st.formData.rateValue = 5
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockCreateTaxRate).toHaveBeenCalledTimes(1)
    expect((mockCreateTaxRate.mock.calls as any)[0][0]).toMatchObject({ name: '附加税', rateValue: 5 })
    st.handleEdit({ id: 2, name: '增值税', rateValue: 13 })
    await flushPromises()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockUpdateTaxRate).toHaveBeenCalledTimes(1)
    expect((mockUpdateTaxRate.mock.calls as any)[0][0]).toBe(2)
  })

  it('删除：确认后调 deleteTaxRate 并刷新', async () => {
    await mountPage()
    mockTaxRates.mockClear()
    await wrapper.vm.$.setupState.handleDelete({ id: 4 })
    await flushPromises()
    expect(mockDeleteTaxRate).toHaveBeenCalledWith(4)
    expect(mockTaxRates).toHaveBeenCalled()
  })
})
