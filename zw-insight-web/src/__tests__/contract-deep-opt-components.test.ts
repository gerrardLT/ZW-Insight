/**
 * P1-M3 施工合同深挖组件单元测试
 * 覆盖 ContractDetailDrawer（四率计算/明细/变更签证/产值/结算）与 index 撤回/抽屉调用
 * V2026_108 追加：过程结算页签（加载/新建/提交）与签证关联变更事件选择
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockGetDetails,
  mockGetChangeVisaPage,
  mockCreateChangeVisa,
  mockGetOutputReportPage,
  mockGetFinalSettlementPage,
  mockCreateFinalSettlement,
  mockSubmitFinalSettlement,
  mockGetAvailableSources,
  mockListChangeEvents,
  mockGetPayTerms,
  mockCreatePayTerm
} = vi.hoisted(() => ({
  mockGetDetails: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockGetChangeVisaPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreateChangeVisa: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockGetOutputReportPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockGetFinalSettlementPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreateFinalSettlement: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmitFinalSettlement: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockGetAvailableSources: vi.fn(async (): Promise<any> => ({
    code: 200,
    data: {
      outputReports: [{ sourceId: 301, label: '产值报告 2026-09（本期 100000）', suggestedAmount: 100000 }],
      visas: [{ sourceId: 401, label: '签证 SITE_VISA（30000）', suggestedAmount: 30000 }]
    }
  })),
  mockListChangeEvents: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockGetPayTerms: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockCreatePayTerm: vi.fn(async (): Promise<any> => ({ code: 200 }))
}))

vi.mock('@/api/file', () => ({
  getFileListByBusiness: vi.fn(async () => ({ data: [] })),
  uploadFile: vi.fn(async () => ({ data: { id: '123' } }))
}))

vi.mock('@/api/contract', () => ({
  getContractSealedFiles: vi.fn(async () => ({ data: [] })),
  uploadContractSealedFile: vi.fn(async () => ({ data: { id: '123' } })),
  getContractDetails: mockGetDetails,
  getChangeVisaPage: mockGetChangeVisaPage,
  createChangeVisa: mockCreateChangeVisa,
  submitChangeVisa: vi.fn(async (): Promise<any> => ({ code: 200 })),
  getOutputReportPage: mockGetOutputReportPage,
  getFinalSettlementPage: mockGetFinalSettlementPage,
  getFinalSettlementDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  createFinalSettlement: mockCreateFinalSettlement,
  submitFinalSettlement: mockSubmitFinalSettlement,
  getAvailableSettlementSources: mockGetAvailableSources,
  getPayTerms: mockGetPayTerms,
  createPayTerm: mockCreatePayTerm,
  updatePayTerm: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deletePayTerm: vi.fn(async (): Promise<any> => ({ code: 200 }))
}))

vi.mock('@/api/change-event', () => ({
  listChangeEvents: mockListChangeEvents
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

  it('打开抽屉时自动加载明细、变更签证、产值记录与结算（FINAL 与 PROCESS 分开取）', async () => {
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
    // 竣工结算页签只取 FINAL 型；过程结算页签单独取 PROCESS 型
    expect(mockGetFinalSettlementPage).toHaveBeenCalledWith({ contractId: 91001, settlementType: 'FINAL', pageNum: 1, pageSize: 1 })
    expect(mockGetFinalSettlementPage).toHaveBeenCalledWith({ contractId: 91001, settlementType: 'PROCESS', pageNum: 1, pageSize: 50 })
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
    expect(vm.calcRate(100, 0)).toBeNull()
    expect(vm.calcRate(100, undefined)).toBeNull()
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
    vm.visaForm.visaDate = '2026-10-15'
    vm.visaForm.taxRate = 9
    vm.visaForm.changeReason = '土方换填'
    vm.visaForm.changeContent = '现场签证确认换填工程量'

    await vm.submitVisa()
    await flushPromises()

    expect(mockCreateChangeVisa).toHaveBeenCalledWith({
      projectId: 90001,
      contractId: 91001,
      changeType: 'SITE_VISA',
      changeAmount: 30000,
      visaDate: '2026-10-15',
      taxRate: 9,
      changeReason: '土方换填',
      changeContent: '现场签证确认换填工程量'
    })
  })

  it('V2026_108 - 打开签证弹窗时加载可选关联的已审批变更事件', async () => {
    mockListChangeEvents.mockResolvedValue({
      code: 200,
      data: {
        records: [
          { id: 501, eventNumber: 'CHG20260001', title: '基坑支护变更', costDelta: 80000, contractId: 91001 },
          { id: 502, eventNumber: 'CHG20260002', title: '他合同事件', costDelta: 100, contractId: 999 }
        ],
        total: 2
      }
    })
    wrapper = mount(ContractDetailDrawer, {
      props: { modelValue: true, contract: { id: 91001, projectId: 90001, status: 'EFFECTIVE' } },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.handleAddVisa()
    await flushPromises()

    expect(mockListChangeEvents).toHaveBeenCalledWith({ page: 1, size: 50, projectId: 90001, status: 'APPROVED' })
    // 非本合同的事件（已显式指定 contractId）不进入候选
    expect(vm.visaEventOptions).toHaveLength(1)
    expect(vm.visaEventOptions[0].id).toBe(501)
  })

  it('V2026_108 - 新建过程结算：拉取可选来源并保存草稿（含期间/扣款/来源明细）', async () => {
    wrapper = mount(ContractDetailDrawer, {
      props: { modelValue: true, contract: { id: 91001, projectId: 90001, status: 'EFFECTIVE' } },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    vm.openProcessSettlementDialog()
    await flushPromises()

    expect(mockGetAvailableSources).toHaveBeenCalledWith(91001)
    expect(vm.psAvailable.outputReports).toHaveLength(1)

    vm.psForm.periodStart = '2026-09-01'
    vm.psForm.periodEnd = '2026-09-30'
    vm.psForm.retentionDeduct = 5000
    vm.psForm.sources = [
      { sourceType: 'OUTPUT_REPORT', sourceId: 301, declaredAmount: 100000, confirmedAmount: 95000, disputedAmount: 5000, reduceReason: '计量核减' }
    ]

    await vm.submitProcessSettlementCreate()
    await flushPromises()

    expect(mockCreateFinalSettlement).toHaveBeenCalledWith({
      projectId: 90001,
      contractId: 91001,
      settlementType: 'PROCESS',
      periodStart: '2026-09-01',
      periodEnd: '2026-09-30',
      settlementDate: undefined,
      prepayDeduct: 0,
      retentionDeduct: 5000,
      otherDeduct: 0,
      sources: [
        { sourceType: 'OUTPUT_REPORT', sourceId: 301, declaredAmount: 100000, confirmedAmount: 95000, disputedAmount: 5000, reduceReason: '计量核减' }
      ]
    })
    // 净额预览 = 95000 − 5000
    expect(vm.psNetPreview).toBe(90000)
  })

  it('V2026_108 - 提交过程结算审批调用 submitFinalSettlement 并刷新列表', async () => {
    wrapper = mount(ContractDetailDrawer, {
      props: { modelValue: true, contract: { id: 91001, projectId: 90001, status: 'EFFECTIVE' } },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.submitProcessSettlement({ id: 77, status: 'DRAFT' })
    await flushPromises()

    expect(mockSubmitFinalSettlement).toHaveBeenCalledWith(77)
    expect(mockGetFinalSettlementPage).toHaveBeenCalledWith({ contractId: 91001, settlementType: 'PROCESS', pageNum: 1, pageSize: 50 })
  })

  it('V2026_109 - 打开抽屉加载收付款条款；新增条款调用 createPayTerm', async () => {
    mockGetPayTerms.mockResolvedValue({
      code: 200,
      data: [
        { id: 61, termType: 'PREPAY', ratio: 20, amount: 1000000, deductRule: 'AFTER_80' },
        { id: 62, termType: 'RETENTION', ratio: 3, retentionMonths: 24 }
      ]
    })
    wrapper = mount(ContractDetailDrawer, {
      props: { modelValue: true, contract: { id: 91001, projectId: 90001, status: 'EFFECTIVE' } },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    expect(mockGetPayTerms).toHaveBeenCalledWith('CONSTRUCTION', 91001)
    const vm = wrapper.vm as any
    expect(vm.payTerms).toHaveLength(2)

    vm.openTermDialog()
    vm.termForm.termType = 'RETENTION'
    vm.termForm.ratio = 3
    vm.termForm.retentionMonths = 24
    await vm.submitTerm()
    await flushPromises()

    expect(mockCreatePayTerm).toHaveBeenCalledWith({
      contractCategory: 'CONSTRUCTION',
      contractId: 91001,
      termType: 'RETENTION',
      ratio: 3,
      amount: undefined,
      triggerCondition: '',
      planDate: undefined,
      retentionMonths: 24,
      deductRule: undefined
    })
  })

  it('V2026_109 - 过程结算弹窗按 PREPAY 条款预填扣回建议', async () => {
    mockGetPayTerms.mockResolvedValue({
      code: 200,
      data: [{ id: 61, termType: 'PREPAY', ratio: 20, amount: 200000 }]
    })
    wrapper = mount(ContractDetailDrawer, {
      props: { modelValue: true, contract: { id: 91001, projectId: 90001, status: 'EFFECTIVE' } },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    const vm = wrapper.vm as any
    await vm.openProcessSettlementDialog()
    await flushPromises()

    expect(vm.psForm.prepayDeduct).toBe(200000)
  })
})
