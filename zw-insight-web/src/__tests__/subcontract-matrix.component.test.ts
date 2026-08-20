/**
 * M4 账本补齐：B-4 分包域页面矩阵用例（合同/结算）
 * views/subcontract/{contract,settlement}.vue
 *
 * 既有覆盖（不重复）：CRUD 标准 6 例与 D1/D3/D6 审计缺陷钉住见
 * subcontract-contract-crud.component.test.ts。本文件补矩阵级行为差异与现状钉住。
 *
 * @matrix B-20-4/B-20-8
 * @matrix B-21-1/B-21-2/B-21-3/B-21-4/B-21-5/B-21-6/B-21-7/B-21-10
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockContractPage, mockSettlePage, mockSettleDetail, mockSettleCreate,
  mockSettleUpdate, mockSettleSubmit, mockSettleDelete,
  mockWarning, mockSuccess, mockConfirm,
} = vi.hoisted(() => ({
  mockContractPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockSettlePage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockSettleDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockSettleCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSettleUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSettleSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSettleDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockWarning: vi.fn(),
  mockSuccess: vi.fn(),
  mockConfirm: vi.fn(async () => 'confirm'),
}))

vi.mock('@/api/subcontract', () => ({
  getSubcontractPage: mockContractPage,
  createSubcontract: vi.fn(async (): Promise<any> => ({ code: 200 })),
  updateSubcontract: vi.fn(async (): Promise<any> => ({ code: 200 })),
  deleteSubcontract: vi.fn(async (): Promise<any> => ({ code: 200 })),
  submitSubcontract: vi.fn(async (): Promise<any> => ({ code: 200 })),
  getSubcontractSettlementPage: mockSettlePage,
  getSubcontractSettlementDetail: mockSettleDetail,
  createSubcontractSettlement: mockSettleCreate,
  updateSubcontractSettlement: mockSettleUpdate,
  deleteSubcontractSettlement: mockSettleDelete,
  submitSubcontractSettlement: mockSettleSubmit,
}))
vi.mock('@/api/project', () => ({
  getProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: mockSuccess, error: vi.fn(), warning: mockWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mockConfirm },
  }
})

import Subcontract from '@/views/subcontract/contract.vue'
import Settlement from '@/views/subcontract/settlement.vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))
const contractSrc = readFileSync(resolve(__testDir, '../views/subcontract/contract.vue'), 'utf-8')
const settlementSrc = readFileSync(resolve(__testDir, '../views/subcontract/settlement.vue'), 'utf-8')

const stubs = {
  ProjectSelector: { template: '<div class="stub-project-selector" />', props: ['modelValue'] },
  SubcontractSelector: { template: '<div class="stub-subcontract-selector" />', props: ['modelValue', 'projectId'] },
}

let wrapper: any = null
beforeEach(() => { vi.clearAllMocks() })
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

// ─── B20 分包合同 ───
describe('subcontract/contract.vue B20 矩阵', () => {
  async function mountContract(records: any[] = []) {
    mockContractPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(Subcontract, { global: { plugins: [ElementPlus], stubs } })
    await flushPromises()
    return wrapper
  }

  it('B-20-4 钉住现状：查询区无状态筛选（queryParams 仅 contractName/subcontractor）', async () => {
    const w = await mountContract()
    expect(w.vm.$.setupState.queryParams).toEqual({ pageNum: 1, pageSize: 10, contractName: '', subcontractor: '' })
    expect('status' in w.vm.$.setupState.queryParams).toBe(false)
  })

  it('B-20-8 分页为 pageNum/pageSize 口径（与结算页 page/size 不一致，钉住）', async () => {
    const w = await mountContract()
    expect(w.vm.$.setupState.queryParams).toMatchObject({ pageNum: 1, pageSize: 10 })
    expect(contractSrc).toContain('v-model:current-page="queryParams.pageNum"')
  })
})

// ─── B21 分包结算 ───
describe('subcontract/settlement.vue B21 矩阵', () => {
  async function mountSettlement(records: any[] = []) {
    mockSettlePage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(Settlement, { global: { plugins: [ElementPlus], stubs } })
    await flushPromises()
    return wrapper
  }

  it('B-21-1 formRules 必填 3 条：projectId/contractId/details（含明细规则）', async () => {
    const w = await mountSettlement()
    const rules = w.vm.$.setupState.formRules
    expect((rules.projectId as any[])[0]).toMatchObject({ required: true, message: '请选择项目' })
    expect((rules.contractId as any[])[0]).toMatchObject({ required: true, message: '请选择分包合同' })
    expect((rules.details as any[])[0]).toMatchObject({ required: true, message: '请至少添加一条结算明细' })
  })

  it('B-21-2 空明细二次守卫：validate 通过后仍拦截，warning「请至少添加一条结算明细」', async () => {
    const w = await mountSettlement()
    const st = w.vm.$.setupState
    st.formRef = { validate: async () => true }
    st.formData.projectId = 1
    st.formData.contractId = 2
    st.formData.details = []
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockWarning).toHaveBeenCalledWith('请至少添加一条结算明细')
    expect(mockSettleCreate).not.toHaveBeenCalled()
  })

  it('B-21-3 totalAmount computed = Σ(数量×单价)', async () => {
    const w = await mountSettlement()
    const st = w.vm.$.setupState
    st.formData.details = [
      { itemName: '土方', unit: 'm³', quantity: 2.5, unitPrice: 100, remark: '' },
      { itemName: '钢筋', unit: '吨', quantity: 1, unitPrice: 4500.5, remark: '' },
    ]
    expect(st.totalAmount).toBeCloseTo(4750.5)
  })

  it('B-21-4 handleProjectChange 清空已选分包合同', async () => {
    const w = await mountSettlement()
    const st = w.vm.$.setupState
    st.formData.projectId = 1
    st.formData.contractId = 9
    st.handleProjectChange()
    expect(st.formData.contractId).toBeUndefined()
  })

  it('B-21-5 创建 payload：details 注入 sortOrder=i+1，金额透传', async () => {
    const w = await mountSettlement()
    const st = w.vm.$.setupState
    st.formRef = { validate: async () => true }
    st.formData.projectId = 1
    st.formData.contractId = 2
    st.formData.details = [
      { itemName: '土方', unit: 'm³', quantity: 2, unitPrice: 100, remark: '' },
      { itemName: '钢筋', unit: '吨', quantity: 1, unitPrice: 50, remark: 'r' },
    ]
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockSettleCreate).toHaveBeenCalledTimes(1)
    const payload: any = (mockSettleCreate as any).mock.calls[0][0]
    expect(payload.projectId).toBe(1)
    expect(payload.contractId).toBe(2)
    expect(payload.details.map((d: any) => d.sortOrder)).toEqual([1, 2])
    expect(mockSuccess).toHaveBeenCalledWith('新增成功')
  })

  it('B-21-6 编辑拉详情回显：字符串金额 Number 转换，缺省字段兜底', async () => {
    mockSettleDetail.mockResolvedValue({
      code: 200,
      data: {
        id: 8, projectId: 1, contractId: 2,
        details: [{ itemName: '土方', quantity: '2.50', unitPrice: '100.00' }],
      },
    })
    const w = await mountSettlement()
    const st = w.vm.$.setupState
    await st.handleEdit({ id: 8 })
    await flushPromises()
    expect(mockSettleDetail).toHaveBeenCalledWith(8)
    expect(st.formData.id).toBe(8)
    expect(st.formData.details[0]).toEqual({ itemName: '土方', unit: '', quantity: 2.5, unitPrice: 100, remark: '' })
  })

  it('B-21-7 编辑/提交/删除按钮全部仅 DRAFT 行渲染', async () => {
    const w = await mountSettlement([
      { id: 1, settlementAmount: 100, status: 'DRAFT' },
      { id: 2, settlementAmount: 200, status: 'APPROVED' },
    ])
    const texts: string[] = w.findAll('button').map((b: any) => b.text())
    expect(texts.filter(t => t === '编辑')).toHaveLength(1)
    expect(texts.filter(t => t === '提交')).toHaveLength(1)
    expect(texts.filter(t => t === '删除')).toHaveLength(1)
  })

  it('B-21-10 分页为 page/size 口径（与分包合同页 pageNum/pageSize 不一致，源码钉住）', async () => {
    const w = await mountSettlement()
    expect(w.vm.$.setupState.queryParams).toMatchObject({ page: 1, size: 10 })
    expect(settlementSrc).toContain('v-model:current-page="queryParams.page"')
  })
})
