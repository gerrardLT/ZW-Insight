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
// 页内新增 ProjectSelector 子组件（审计缺陷 D1 修复）会调 getProjectList，mock 防真实请求
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

import Subcontract from '@/views/subcontract/contract.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'subcontract/contract.vue 分包合同',
  component: Subcontract,
  pageMock: mockPage, createMock: mockCreate, updateMock: mockUpdate, deleteMock: mockDelete,
  addButtonText: '新增分包合同',
  requiredError: '请输入合同名称',
  records: [
    // 夹具字段与后端实体 BizSubcontract 对齐（审计缺陷 D2 修复，2026-08-17）：
    // 原 subcontractorName/startDate/endDate 在实体上不存在，页面列 prop="subcontractor" 会渲染为空
    { id: 1, projectId: 101, contractName: '幕墙分包合同', subcontractor: '幕墙公司甲', contractAmount: 1200000, status: 'DRAFT', signingDate: '2026-02-01' },
    { id: 2, projectId: 101, contractName: '消防分包合同', subcontractor: '消防公司乙', contractAmount: 600000, status: 'EFFECTIVE', signingDate: '2026-03-01' },
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

describe('subcontract/contract.vue 审计缺陷修复钉住（D1/D3/D6，2026-08-17）', () => {
  let wrapper: any = null
  afterEach(() => {
    if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
    vi.clearAllMocks()
  })

  it('D1：formRules 含 projectId 必填规则，handleAdd 重置 projectId', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    wrapper = mount(Subcontract, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    const projectRules = (st.formRules.projectId || []) as any[]
    expect(projectRules.some((r: any) => r.required && r.message === '请选择项目')).toBe(true)
    // formData 含 projectId 且新增时重置为 undefined
    expect('projectId' in st.formData).toBe(true)
    st.formData.projectId = 999
    st.handleAdd()
    expect(st.formData.projectId).toBeUndefined()
  })

  it('D3：contractAmount 规则拒绝 0/null/负数，放行正数（配置层断言，happy-dom validate 受限）', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    wrapper = mount(Subcontract, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st = wrapper.vm.$.setupState
    const amountRules = (st.formRules.contractAmount || []) as any[]
    const validator = amountRules.find((r: any) => typeof r.validator === 'function')?.validator
    expect(validator, 'contractAmount 应配置 validator 规则').toBeTruthy()
    const run = (v: any) => new Promise<string | null>((resolve) => {
      validator(null, v, (err?: Error) => resolve(err ? err.message : null))
    })
    expect(await run(0)).toBe('合同金额必须大于 0')
    expect(await run(null)).toBe('合同金额必须大于 0')
    expect(await run(-100)).toBe('合同金额必须大于 0')
    expect(await run(100)).toBeNull()
  })

  it('D6：EFFECTIVE 行不渲染编辑/删除按钮，DRAFT 行全部渲染', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: [
      { id: 1, contractName: '草稿合同', subcontractor: '甲', status: 'DRAFT' },
      { id: 2, contractName: '生效合同', subcontractor: '乙', status: 'EFFECTIVE' },
    ], total: 2 } })
    wrapper = mount(Subcontract, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const buttons = wrapper.findAll('button')
    const texts: string[] = buttons.map((b: any) => b.text())
    // DRAFT 行：编辑/提交审批/删除 各 1；EFFECTIVE 行：零操作按钮
    expect(texts.filter((t: string) => t.includes('编辑'))).toHaveLength(1)
    expect(texts.filter((t: string) => t.includes('删除'))).toHaveLength(1)
    expect(texts.filter((t: string) => t.includes('提交审批'))).toHaveLength(1)
  })
})
