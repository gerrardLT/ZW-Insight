/**
 * tender/register.vue 投标报名页组件测试（2026-08-15 P3 收尾批）
 * 工厂 6 标准用例 + 行提交定制例。handleEdit 经 detail API 回显
 * （mock 返回行数据适配），page/size 口径。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const RECORDS: any[] = [
  { id: 1, projectName: '滨江花园一期', tenderProjectName: '滨江花园施工总承包', bidDate: '2026-09-01', status: 'DRAFT' },
  { id: 2, projectName: '城南市政', tenderProjectName: '市政道路改造', bidDate: '2026-09-15', status: 'SUBMITTED' },
]

const { mockPage, mockCreate, mockUpdate, mockDelete, mockSubmit, mockDetail } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
}))

vi.mock('@/api/tender', () => ({
  getTenderRegisterPage: mockPage, getTenderRegisterDetail: mockDetail, createTenderRegister: mockCreate,
  updateTenderRegister: mockUpdate, deleteTenderRegister: mockDelete, submitTenderRegister: mockSubmit,
}))
// 页内嵌 ProjectSelector 子组件，mock 防真实请求
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

import TenderRegister from '@/views/tender/register.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'tender/register.vue 投标报名',
  component: TenderRegister,
  pageMock: mockPage, createMock: mockCreate, updateMock: mockUpdate, deleteMock: mockDelete,
  addButtonText: '新增投标报名',
  requiredError: '请选择项目',
  pageKey: 'page', // page/size 口径
  skipEditCase: true, // 回显经 detail API，由下方扩展例覆盖
  records: RECORDS,
})

describe('tender/register.vue 扩展', () => {
  let wrapper: any = null
  afterEach(() => {
    if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
    vi.clearAllMocks()
  })

  it('编辑回显经 detail API（返回行数据填充 formData）', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: RECORDS, total: 2 } })
    mockDetail.mockResolvedValue({ code: 200, data: RECORDS[0] })
    wrapper = mount(TenderRegister, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.vm.$.setupState.handleEdit(RECORDS[0])
    await flushPromises()
    expect(mockDetail).toHaveBeenCalledWith(1)
    expect(wrapper.vm.$.setupState.formData.id).toBe(1)
    expect(wrapper.vm.$.setupState.isEdit).toBe(true)
  })

  it('DRAFT 行提交 → 调 submitTenderRegister', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: [{ id: 71, status: 'DRAFT' }], total: 1 } })
    wrapper = mount(TenderRegister, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.vm.$.setupState.handleSubmitApply({ id: 71 })
    await flushPromises()
    expect(mockSubmit).toHaveBeenCalledWith(71)
  })
})
