/**
 * P1-M3 施工合同深挖组件单元测试
 * 覆盖 ContractDetailDrawer（四率计算/明细/变更签证/产值/结算）与 index 撤回/抽屉调用
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockGetDetails,
  mockGetChangeVisaPage,
  mockCreateChangeVisa,
  mockGetOutputReportPage,
  mockGetFinalSettlementPage
} = vi.hoisted(() => ({
  mockGetDetails: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockGetChangeVisaPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreateChangeVisa: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockGetOutputReportPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockGetFinalSettlementPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } }))
}))

vi.mock('@/api/contract', () => ({
  getContractDetails: mockGetDetails,
  getChangeVisaPage: mockGetChangeVisaPage,
  createChangeVisa: mockCreateChangeVisa,
  getOutputReportPage: mockGetOutputReportPage,
  getFinalSettlementPage: mockGetFinalSettlementPage
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') }
  }
})

import ContractDetailDrawer from '@/views/contract/components/ContractDetailDrawer.vue'

describe('ContractDetailDrawer 合同履约全息抽屉 (P1-M3 A1/B1)', () => {
  let wrapper: any = null

  beforeEach(() => {
    vi.clearAllMocks()
    mockGetDetails.mockResolvedValue({
      code: 200,
      data: [{ id: 1, itemName: '桩基工程', quantity: 100, unitPrice: 500, totalPrice: 50000 }]
    })
    mockGetChangeVisaPage.mockResolvedValue({
      code: 200,
      data: { records: [{ id: 11, changeType: 'DESIGN_CHANGE', changeAmount: 100000, status: 'APPROVED' }], total: 1 }
    })
    mockGetOutputReportPage.mockResolvedValue({
      code: 200,
      data: { records: [{ id: 21, reportPeriod: '2026-08', currentOutput: 200000, status: 'APPROVED' }], total: 1 }
    })
    mockGetFinalSettlementPage.mockResolvedValue({
      code: 200,
      data: { records: [{ id: 31, settlementAmount: 4800000 }], total: 1 }
    })
  })

  afterEach(() => {
    if (wrapper) {
      wrapper.unmount()
      wrapper = null
    }
  })

  it('打开抽屉时自动加载明细、变更签证、产值记录与结算', async () => {
    wrapper = mount(ContractDetailDrawer, {
      props: {
        modelValue: true,
        contract: {
          id: 91001,
          contractCode: 'HT-2026-001',
          partyAName: '城市建投',
          contractAmount: 5000000,
          cumulativeOutput: 2500000,
          cumulativeInvoiceAmount: 2000000,
          cumulativeReceivedAmount: 1500000,
          status: 'EFFECTIVE'
        }
      },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    expect(mockGetDetails).toHaveBeenCalledWith(91001)
    expect(mockGetChangeVisaPage).toHaveBeenCalledWith({ contractId: 91001, pageNum: 1, pageSize: 50 })
    expect(mockGetOutputReportPage).toHaveBeenCalledWith({ contractId: 91001, pageNum: 1, pageSize: 50 })
    expect(mockGetFinalSettlementPage).toHaveBeenCalledWith({ contractId: 91001, pageNum: 1, pageSize: 1 })
  })

  it('履约四率计算逻辑正确', async () => {
    wrapper = mount(ContractDetailDrawer, {
      props: {
        modelValue: true,
        contract: { id: 91001, contractAmount: 1000000, cumulativeOutput: 500000 }
      },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    expect(vm.calcRate(500000, 1000000)).toBe(50)
    expect(vm.calcRate(0, 1000000)).toBe(0)
    expect(vm.calcRate(1200000, 1000000)).toBe(100) // 上限截断
  })

  it('登记变更签证提交调用 createChangeVisa (B1)', async () => {
    wrapper = mount(ContractDetailDrawer, {
      props: {
        modelValue: true,
        contract: { id: 91001, projectId: 90001, status: 'EFFECTIVE' }
      },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.visaForm.changeType = 'SITE_VISA'
    vm.visaForm.changeAmount = 30000
    vm.visaForm.changeReason = '土方换填'
    vm.visaForm.changeContent = '现场签证确认换填工程量'

    await vm.submitVisa()
    await flushPromises()

    expect(mockCreateChangeVisa).toHaveBeenCalledWith({
      projectId: 90001,
      contractId: 91001,
      changeType: 'SITE_VISA',
      changeAmount: 30000,
      changeReason: '土方换填',
      changeContent: '现场签证确认换填工程量',
      status: 'APPROVED'
    })
  })
})
