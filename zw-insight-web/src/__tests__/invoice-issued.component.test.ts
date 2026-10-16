/**
 * V2026_110 C3：销项发票台账页面组件测试
 * 覆盖：登记弹窗（蓝/红字切换与必填）、红字须挂原票、查验/入账状态流转调用。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockGetPage, mockCreate, mockVerify, mockBookkeep, mockDelete } = vi.hoisted(() => ({
  mockGetPage: vi.fn(async (): Promise<any> => ({
    code: 200,
    data: {
      records: [
        { id: 1, direction: 'BLUE', invoiceNumber: '24331000000012345678', amount: 109000, taxRate: 9, taxAmount: 9000, verifyStatus: 'UNVERIFIED', bookkeepStatus: 'UNBOOKED' },
        { id: 2, direction: 'RED', invoiceNumber: '24331000000012345999', amount: -5000, originalId: 1, verifyStatus: 'VERIFIED', bookkeepStatus: 'BOOKED' }
      ],
      total: 2
    }
  })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockVerify: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockBookkeep: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 }))
}))

vi.mock('@/api/finance', () => ({
  getInvoiceIssuedPage: mockGetPage,
  createInvoiceIssued: mockCreate,
  verifyInvoiceIssued: mockVerify,
  markInvoiceIssuedBookkeep: mockBookkeep,
  deleteInvoiceIssued: mockDelete
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') }
  }
})

import InvoiceIssuedPage from '@/views/finance/invoice-issued.vue'

describe('销项发票台账页面 (V2026_110)', () => {
  let wrapper: any

  beforeEach(() => {
    vi.clearAllMocks()
    mockGetPage.mockImplementation(async () => ({
      code: 200,
      data: {
        records: [
          { id: 1, direction: 'BLUE', invoiceNumber: '24331000000012345678', amount: 109000, taxRate: 9, taxAmount: 9000, verifyStatus: 'UNVERIFIED', bookkeepStatus: 'UNBOOKED' },
          { id: 2, direction: 'RED', invoiceNumber: '24331000000012345999', amount: -5000, originalId: 1, verifyStatus: 'VERIFIED', bookkeepStatus: 'BOOKED' }
        ],
        total: 2
      }
    }))
  })

  it('加载列表并展示蓝/红字与查验入账状态', async () => {
    wrapper = mount(InvoiceIssuedPage, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(mockGetPage).toHaveBeenCalledWith({ page: 1, size: 10, direction: undefined })
    expect(wrapper.vm.tableData).toHaveLength(2)
    expect(wrapper.vm.tableData[1].direction).toBe('RED')
  })

  it('登记弹窗：蓝字提交不带原票；红字提交携带原票ID', async () => {
    wrapper = mount(InvoiceIssuedPage, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.openDialog()
    await flushPromises()
    vm.formData.invoiceNumber = '24331000000012345678'
    vm.formData.issueDate = '2026-10-16'
    vm.formData.amount = 109000
    vm.formData.taxRate = 9
    vm.formData.projectId = 10
    vm.formData.contractId = 20
    await vm.submit()
    await flushPromises()
    expect(mockCreate).toHaveBeenCalledWith(expect.objectContaining({
      direction: 'BLUE', invoiceNumber: '24331000000012345678', originalId: undefined
    }))

    vi.clearAllMocks()
    mockGetPage.mockImplementation(async () => ({ code: 200, data: { records: [], total: 0 } }))
    await vm.openDialog()
    vm.formData.direction = 'RED'
    vm.formData.invoiceNumber = '24331000000012345999'
    vm.formData.issueDate = '2026-10-20'
    vm.formData.amount = -5000
    vm.formData.originalId = 1
    await vm.submit()
    await flushPromises()
    expect(mockCreate).toHaveBeenCalledWith(expect.objectContaining({
      direction: 'RED', amount: -5000, originalId: 1
    }))
  })

  it('查验与入账流转调用对应 API', async () => {
    wrapper = mount(InvoiceIssuedPage, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.verify({ id: 1, verifyStatus: 'UNVERIFIED' }, 'VERIFIED')
    expect(mockVerify).toHaveBeenCalledWith(1, 'VERIFIED')

    await vm.toggleBookkeep({ id: 1, bookkeepStatus: 'UNBOOKED' })
    expect(mockBookkeep).toHaveBeenCalledWith(1, true)
  })
})
