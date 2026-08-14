/**
 * payment-apply.vue 组件测试（2026-08-14 P0 补测）
 *
 * @matrix C-5 付款申请五分支合同路由（前端 UI 层）
 * 钉住 loadContracts 的 switch 路由逻辑（payment-apply.vue L188-203）：
 *   PURCHASE/LABOR/MACHINE/SUBCONTRACT/OTHER_EXPENSE(default) 五分支各调用
 *   对应合同分页 API；项目或合同类型变化时清空已选合同并重载下拉。
 * 与 L5 21-finance-chain.spec.ts 的数据源/落库断言互补（UI 层钉住路由逻辑）。
 *
 * 模式与 confirm-password-dialog.component.test.ts 一致：真实 Element Plus 挂载，
 * mock API 层（协作方），通过 setupState 驱动组件内部函数。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockGetPaymentApplyPage,
  mockCreatePaymentApply,
  mockDeletePaymentApply,
  mockSubmitPaymentApply,
  mockGetProjectList,
  mockGetOtherContractPage,
  mockGetPurchaseContractPage,
  mockGetLaborContractPage,
  mockGetMachineContractPage,
  mockGetSubcontractPage,
} = vi.hoisted(() => ({
  mockGetPaymentApplyPage: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreatePaymentApply: vi.fn(async (_p?: any): Promise<any> => ({ code: 200 })),
  mockDeletePaymentApply: vi.fn(async (_p?: any): Promise<any> => ({ code: 200 })),
  mockSubmitPaymentApply: vi.fn(async (_p?: any): Promise<any> => ({ code: 200 })),
  mockGetProjectList: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: [] })),
  mockGetOtherContractPage: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: { records: [] } })),
  mockGetPurchaseContractPage: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: { records: [] } })),
  mockGetLaborContractPage: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: { records: [] } })),
  mockGetMachineContractPage: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: { records: [] } })),
  mockGetSubcontractPage: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: { records: [] } })),
}))

vi.mock('@/api/finance', () => ({
  getPaymentApplyPage: mockGetPaymentApplyPage,
  createPaymentApply: mockCreatePaymentApply,
  deletePaymentApply: mockDeletePaymentApply,
  submitPaymentApply: mockSubmitPaymentApply,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockGetProjectList,
}))
vi.mock('@/api/contract', () => ({
  getOtherContractPage: mockGetOtherContractPage,
}))
vi.mock('@/api/purchase', () => ({
  getPurchaseContractPage: mockGetPurchaseContractPage,
}))
vi.mock('@/api/labor', () => ({
  getLaborContractPage: mockGetLaborContractPage,
}))
vi.mock('@/api/machine', () => ({
  getMachineContractPage: mockGetMachineContractPage,
}))
vi.mock('@/api/subcontract', () => ({
  getSubcontractPage: mockGetSubcontractPage,
}))
// SupplierSelector 涉及独立请求链，本测试聚焦合同类型路由，stub 之
// （runtime-only 构建不支持 template 字符串，用 render 函数，台账既有经验）
vi.mock('@/components/SupplierSelector.vue', () => ({
  default: {
    name: 'SupplierSelector',
    render: () => null,
  },
}))

import PaymentApply from '@/views/finance/payment-apply.vue'

async function mountPage() {
  const wrapper = mount(PaymentApply, {
    global: { plugins: [ElementPlus] },
  })
  await flushPromises()
  return wrapper
}

/** script setup 组件经实例代理访问 setup 绑定（dev 模式 setupState 暴露） */
function setupState(wrapper: any): any {
  return wrapper.vm.$.setupState
}

describe('payment-apply.vue 五分支合同路由（@matrix C-5）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetPaymentApplyPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    mockGetOtherContractPage.mockResolvedValue({ code: 200, data: { records: [] } })
    mockGetPurchaseContractPage.mockResolvedValue({ code: 200, data: { records: [] } })
    mockGetLaborContractPage.mockResolvedValue({ code: 200, data: { records: [] } })
    mockGetMachineContractPage.mockResolvedValue({ code: 200, data: { records: [] } })
    mockGetSubcontractPage.mockResolvedValue({ code: 200, data: { records: [] } })
  })

  it('页面挂载加载付款申请列表', async () => {
    await mountPage()
    expect(mockGetPaymentApplyPage).toHaveBeenCalled()
  })

  it('PURCHASE 分支调用 getPurchaseContractPage 并带项目与分页参数', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.formData.projectId = 1001
    st.formData.contractCategory = 'PURCHASE'
    await st.loadContracts()
    expect(mockGetPurchaseContractPage).toHaveBeenCalledTimes(1)
    const params = mockGetPurchaseContractPage.mock.calls[0][0]
    expect(params.projectId).toBe(1001)
    expect(params.page).toBe(1)
    expect(params.size).toBe(100)
    // 其他分支 API 不应被调用
    expect(mockGetLaborContractPage).not.toHaveBeenCalled()
    expect(mockGetMachineContractPage).not.toHaveBeenCalled()
    expect(mockGetSubcontractPage).not.toHaveBeenCalled()
    expect(mockGetOtherContractPage).not.toHaveBeenCalled()
  })

  it('LABOR 分支调用 getLaborContractPage', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.formData.projectId = 1001
    st.formData.contractCategory = 'LABOR'
    await st.loadContracts()
    expect(mockGetLaborContractPage).toHaveBeenCalledTimes(1)
    expect(mockGetPurchaseContractPage).not.toHaveBeenCalled()
  })

  it('MACHINE 分支调用 getMachineContractPage', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.formData.projectId = 1001
    st.formData.contractCategory = 'MACHINE'
    await st.loadContracts()
    expect(mockGetMachineContractPage).toHaveBeenCalledTimes(1)
  })

  it('SUBCONTRACT 分支调用 getSubcontractPage', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.formData.projectId = 1001
    st.formData.contractCategory = 'SUBCONTRACT'
    await st.loadContracts()
    expect(mockGetSubcontractPage).toHaveBeenCalledTimes(1)
  })

  it('OTHER_EXPENSE 走 default 分支并携带 contractCategory 参数', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.formData.projectId = 1001
    st.formData.contractCategory = 'OTHER_EXPENSE'
    await st.loadContracts()
    expect(mockGetOtherContractPage).toHaveBeenCalledTimes(1)
    const params = mockGetOtherContractPage.mock.calls[0][0]
    expect(params.contractCategory).toBe('OTHER_EXPENSE')
  })

  it('未知合同类型回落 default（OTHER_EXPENSE 数据源）', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.formData.projectId = 1001
    st.formData.contractCategory = 'UNKNOWN_TYPE'
    await st.loadContracts()
    expect(mockGetOtherContractPage).toHaveBeenCalledTimes(1)
  })

  it('切换合同类型清空已选合同并重载下拉', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.formData.projectId = 1001
    st.formData.contractCategory = 'PURCHASE'
    mockGetPurchaseContractPage.mockResolvedValueOnce({
      code: 200, data: { records: [{ id: 555, contractName: 'C1' }] },
    })
    await st.loadContracts()
    expect(st.contractOptions.length).toBe(1)
    st.formData.contractId = 555

    // 切换类型：contractId 必须清空、下拉重新加载
    st.formData.contractCategory = 'LABOR'
    mockGetLaborContractPage.mockResolvedValueOnce({
      code: 200, data: { records: [{ id: 777, contractName: 'C2' }] },
    })
    await st.loadContracts()
    expect(st.formData.contractId).toBeUndefined()
    expect(st.contractOptions.length).toBe(1)
    expect(st.contractOptions[0].id).toBe(777)
  })

  it('未选项目时不发起合同查询', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.formData.projectId = undefined
    st.formData.contractCategory = 'PURCHASE'
    await st.loadContracts()
    expect(mockGetPurchaseContractPage).not.toHaveBeenCalled()
    expect(st.contractOptions).toEqual([])
  })

  it('接口返回数组（非分页结构）时兼容渲染（records || data 兜底）', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    st.formData.projectId = 1001
    st.formData.contractCategory = 'OTHER_EXPENSE'
    mockGetOtherContractPage.mockResolvedValueOnce({
      code: 200, data: [{ id: 1, contractName: '直出数组' }],
    })
    await st.loadContracts()
    expect(st.contractOptions.length).toBe(1)
    expect(st.contractOptions[0].contractName).toBe('直出数组')
  })
})
