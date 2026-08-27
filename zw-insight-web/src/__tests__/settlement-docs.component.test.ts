/**
 * 结算单据组页组件测试（2026-08-15 P3 收尾批 12）
 * machine/settlement 三页 + purchase/settlement + labor/payroll。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockMSettlePage, mockMSettleDetail, mockMSettleCreate, mockMSettleSubmit, mockMSettleSummary, mockMSettleExport, mockUsagePage,
  mockPSettlePage, mockPSettleCreate, mockPSettleUpdate, mockPSettleDelete, mockPSettleSubmit, mockPContractPage, mockAvailInbounds,
  mockPayrollPage, mockPayrollCreate, mockPayrollDelete, mockPayrollSubmit, mockTeamPage,
  mockProjectList,
} = vi.hoisted(() => {
  const page = () => vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } }))
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  return {
    mockMSettlePage: page(), mockMSettleDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockMSettleCreate: ok(), mockMSettleSubmit: ok(),
    mockMSettleSummary: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockMSettleExport: vi.fn(async (): Promise<any> => new Blob(['x'])),
    mockUsagePage: page(),
    mockPSettlePage: page(), mockPSettleCreate: ok(), mockPSettleUpdate: ok(), mockPSettleDelete: ok(), mockPSettleSubmit: ok(),
    mockPContractPage: page(), mockAvailInbounds: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockPayrollPage: page(), mockPayrollCreate: ok(), mockPayrollDelete: ok(), mockPayrollSubmit: ok(),
    mockTeamPage: page(),
    mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [{ id: 1, projectName: 'P1' }] })),
  }
})

vi.mock('@/api/machine', () => ({
  getMachineSettlementPage: mockMSettlePage, getMachineSettlementDetail: mockMSettleDetail,
  createMachineSettlement: mockMSettleCreate, submitMachineSettlement: mockMSettleSubmit,
  getMachineSettlementSummary: mockMSettleSummary, exportMachineSettlement: mockMSettleExport,
  getMachineUsagePage: mockUsagePage,
}))
vi.mock('@/api/purchase', () => ({
  getPurchaseSettlementPage: mockPSettlePage, createPurchaseSettlement: mockPSettleCreate,
  updatePurchaseSettlement: mockPSettleUpdate, deletePurchaseSettlement: mockPSettleDelete,
  submitPurchaseSettlement: mockPSettleSubmit, getPurchaseContractPage: mockPContractPage,
  getAvailableInbounds: mockAvailInbounds,
}))
vi.mock('@/api/labor', () => ({
  getPayrollPage: mockPayrollPage, createPayroll: mockPayrollCreate, deletePayroll: mockPayrollDelete,
  submitPayroll: mockPayrollSubmit, getLaborTeamPage: mockTeamPage,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockProjectList,
}))
vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ push: vi.fn() }),
    useRoute: () => ({ query: {}, params: { id: '1' } }),
  }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

// 暗色联动引入 useAppStore 后：无 pinia 环境的组件测试统一 mock，防 getActivePinia
vi.mock('@/stores/app', () => ({
  useAppStore: () => ({ isDark: false }),
}))
import MSettleIndex from '@/views/machine/settlement/index.vue'
import MSettleCreate from '@/views/machine/settlement/create.vue'
import MSettleDetail from '@/views/machine/settlement/detail.vue'
import PSettlement from '@/views/purchase/settlement.vue'
import Payroll from '@/views/labor/payroll.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

describe('machine/settlement/index.vue 机械结算列表', () => {
  async function mountPage(records: any[] = []) {
    mockMSettlePage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(MSettleIndex, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载并行加载列表/汇总/项目下拉', async () => {
    await mountPage([{ id: 1, settlementNo: 'JS-1', status: 'DRAFT' }])
    expect(mockMSettlePage).toHaveBeenCalled()
    expect(mockMSettleSummary).toHaveBeenCalled()
    expect(mockProjectList).toHaveBeenCalled()
  })

  it('行提交调 submitMachineSettlement', async () => {
    await mountPage([{ id: 3, status: 'DRAFT' }])
    await wrapper.vm.$.setupState.handleSubmit({ id: 3 })
    await flushPromises()
    expect(mockMSettleSubmit).toHaveBeenCalledWith(3)
  })
})

describe('machine/settlement/create.vue 机械结算创建', () => {
  async function mountPage() {
    wrapper = mount(MSettleCreate, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载预载项目列表', async () => {
    await mountPage()
    expect(mockProjectList).toHaveBeenCalled()
  })

  it('项目+周期变更触发使用明细预览查询（startDate/endDate 组装）', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.formData.projectId = 1
    st.formData.period = ['2026-08-01', '2026-08-31']
    mockUsagePage.mockClear()
    await st.handlePeriodChange()
    await flushPromises()
    expect(mockUsagePage).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1, startDate: '2026-08-01', endDate: '2026-08-31',
    }))
    expect(st.previewVisible).toBe(true)
  })

  it('项目或周期缺失时不查询且预览隐藏', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.formData.projectId = undefined
    st.formData.period = ['2026-08-01', '2026-08-31']
    mockUsagePage.mockClear()
    await st.handleProjectChange()
    await flushPromises()
    expect(mockUsagePage).not.toHaveBeenCalled()
    expect(st.previewVisible).toBe(false)
  })

  it('保存组装 projectId+periodStart/periodEnd 调 createMachineSettlement', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.formRef = { validate: vi.fn(async () => true) }
    st.formData.projectId = 1
    st.formData.period = ['2026-08-01', '2026-08-31']
    // 盲点 12 守卫（2026-08-24）：正常保存需预览齐备且有明细
    st.previewVisible = true
    st.previewData = [{ machineName: '挖机', amount: 500 }]
    await st.handleSave()
    await flushPromises()
    expect(mockMSettleCreate).toHaveBeenCalledWith({
      projectId: 1, periodStart: '2026-08-01', periodEnd: '2026-08-31',
    })
  })
})

describe('machine/settlement/detail.vue 机械结算详情', () => {
  async function mountPage() {
    mockMSettleDetail.mockResolvedValue({ code: 200, data: { id: 1, settlementNo: 'JS-1', status: 'DRAFT', items: [] } })
    wrapper = mount(MSettleDetail, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载按路由 id 拉详情', async () => {
    await mountPage()
    expect(mockMSettleDetail).toHaveBeenCalled()
  })

  it('行提交调 submitMachineSettlement', async () => {
    await mountPage()
    await wrapper.vm.$.setupState.handleSubmit()
    await flushPromises()
    expect(mockMSettleSubmit).toHaveBeenCalled()
  })
})

describe('purchase/settlement.vue 采购结算', () => {
  async function mountPage(records: any[] = []) {
    mockPSettlePage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    mockPContractPage.mockResolvedValue({ code: 200, data: { records: [{ id: 1, contractName: 'C1' }], total: 1 } })
    wrapper = mount(PSettlement, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载结算列表与合同下拉数据源', async () => {
    await mountPage([{ id: 1, status: 'DRAFT' }])
    expect(mockPSettlePage).toHaveBeenCalled()
    expect(mockPContractPage).toHaveBeenCalled()
  })

  it('选择合同触发候选入库单查询并重置入库单选项（available-inbounds 联动）', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    st.formData.contractId = 1
    st.formData.inboundId = 99 // 应被重置
    mockAvailInbounds.mockClear()
    await st.handleContractChange()
    await flushPromises()
    expect(mockAvailInbounds).toHaveBeenCalledWith(1)
    expect(st.formData.inboundId).toBeUndefined() // 切换合同重置入库单
  })

  it('未选合同时不查候选入库单', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    st.formData.contractId = undefined
    mockAvailInbounds.mockClear()
    await st.handleContractChange()
    await flushPromises()
    expect(mockAvailInbounds).not.toHaveBeenCalled()
  })

  it('行提交/删除调对应 API', async () => {
    await mountPage([{ id: 5, status: 'DRAFT' }])
    const st = wrapper.vm.$.setupState
    await st.handleSubmit({ id: 5 })
    await flushPromises()
    expect(mockPSettleSubmit).toHaveBeenCalledWith(5)
    await st.handleDelete({ id: 5 })
    await flushPromises()
    expect(mockPSettleDelete).toHaveBeenCalledWith(5)
  })
})

describe('labor/payroll.vue 工资单', () => {
  async function mountPage(records: any[] = []) {
    mockPayrollPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    mockTeamPage.mockResolvedValue({ code: 200, data: { records: [{ id: 1, teamName: '木工一班' }], total: 1 } })
    wrapper = mount(Payroll, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载工资单列表与班组下拉数据源', async () => {
    await mountPage([{ id: 1, status: 'DRAFT' }])
    expect(mockPayrollPage).toHaveBeenCalled()
    expect(mockTeamPage).toHaveBeenCalled()
  })

  it('行提交调 submitPayroll、删除调 deletePayroll', async () => {
    await mountPage([{ id: 7, status: 'DRAFT' }])
    const st = wrapper.vm.$.setupState
    await st.handleSubmitPayroll({ id: 7 })
    await flushPromises()
    expect(mockPayrollSubmit).toHaveBeenCalledWith(7)
    await st.handleDelete({ id: 7 })
    await flushPromises()
    expect(mockPayrollDelete).toHaveBeenCalledWith(7)
  })
})
