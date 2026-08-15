/**
 * hr/entry.vue 入职申请页组件测试（2026-08-15 P3 收尾批）
 * 工厂标准用例（回显经 detail API 跳过）+ 回显/行提交扩展例。page/size 口径。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const RECORDS: any[] = [
  { id: 1, realName: '张三', phone: '13800000001', position: '施工员', status: 'DRAFT', entryDate: '2026-09-01' },
  { id: 2, realName: '李四', phone: '13800000002', position: '资料员', status: 'APPROVED', entryDate: '2026-09-05' },
]

const { mockPage, mockCreate, mockUpdate, mockDelete, mockSubmit, mockDetail } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
}))

vi.mock('@/api/hr', () => ({
  getHrEntryPage: mockPage, getHrEntryDetail: mockDetail, createHrEntry: mockCreate,
  updateHrEntry: mockUpdate, deleteHrEntry: mockDelete, submitHrEntry: mockSubmit,
}))
// 页内嵌 OrgSelector/PostSelector 子组件，mock 防真实请求
vi.mock('@/api/system', () => ({
  getOrgTree: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  getPostList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import HrEntry from '@/views/hr/entry.vue'
import { crudPageSuite } from './helpers/crud-page-tests'

crudPageSuite({
  title: 'hr/entry.vue 入职申请',
  component: HrEntry,
  pageMock: mockPage, createMock: mockCreate, updateMock: mockUpdate, deleteMock: mockDelete,
  addButtonText: '新增入职申请',
  requiredError: '请输入真实姓名',
  pageKey: 'page', // page/size 口径
  skipEditCase: true, // 回显经 detail API，由下方扩展例覆盖
  records: RECORDS,
})

describe('hr/entry.vue 扩展', () => {
  let wrapper: any = null
  afterEach(() => {
    if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
    vi.clearAllMocks()
  })

  it('编辑回显经 detail API（返回行数据填充 formData）', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: RECORDS, total: 2 } })
    mockDetail.mockResolvedValue({ code: 200, data: RECORDS[0] })
    wrapper = mount(HrEntry, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.vm.$.setupState.handleEdit(RECORDS[0])
    await flushPromises()
    expect(mockDetail).toHaveBeenCalledWith(1)
    expect(wrapper.vm.$.setupState.formData.id).toBe(1)
    expect(wrapper.vm.$.setupState.isEdit).toBe(true)
  })

  it('DRAFT 行提交 → 调 submitHrEntry', async () => {
    mockPage.mockResolvedValue({ code: 200, data: { records: [{ id: 81, realName: '王五', status: 'DRAFT' }], total: 1 } })
    wrapper = mount(HrEntry, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    await wrapper.vm.$.setupState.handleSubmitApply({ id: 81 })
    await flushPromises()
    expect(mockSubmit).toHaveBeenCalledWith(81)
  })
})
