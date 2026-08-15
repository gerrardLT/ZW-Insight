/**
 * hr/resign-apply.vue（离职申请：新增+行提交）与 basedata/supplier-evaluation.vue
 *（供应商评价：新增+删除，无编辑）组件测试（2026-08-15 P3 收尾批）
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockResignPage, mockResignCreate, mockResignSubmit,
  mockEvalPage, mockEvalCreate, mockEvalDelete,
} = vi.hoisted(() => ({
  mockResignPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockResignCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockResignSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockEvalPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockEvalCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockEvalDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/api/hr', () => ({
  getResignApplyPage: mockResignPage, createResignApply: mockResignCreate, submitResignApply: mockResignSubmit,
}))
vi.mock('@/api/basedata', () => ({
  getSupplierEvaluationPage: mockEvalPage, createSupplierEvaluation: mockEvalCreate, deleteSupplierEvaluation: mockEvalDelete,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import ResignApply from '@/views/hr/resign-apply.vue'
import SupplierEvaluation from '@/views/basedata/supplier-evaluation.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

describe('hr/resign-apply.vue 离职申请', () => {
  async function mountPage(records: any[] = []) {
    mockResignPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(ResignApply, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载并渲染行、状态映射', async () => {
    const w = await mountPage([
      { id: 1, userName: '张三', status: 'DRAFT', resignDate: '2026-09-01' },
      { id: 2, userName: '李四', status: 'APPROVING', resignDate: '2026-09-05' },
      { id: 3, userName: '王五', status: 'APPROVED', resignDate: '2026-09-10' },
    ])
    expect(w.findAll('.el-table__row')).toHaveLength(3)
    expect(w.text()).toContain('草稿')
    expect(w.text()).toContain('审批中')
    expect(w.text()).toContain('已通过')
  })

  it('必填规则配置：用户ID/姓名/离职日期', async () => {
    await mountPage()
    const msgs = Object.values(wrapper.vm.$.setupState.formRules).flat().map((r: any) => r.message)
    expect(msgs).toContain('请输入用户ID')
    expect(msgs).toContain('请输入姓名')
    expect(msgs).toContain('请选择离职日期')
  })

  it('新增离职申请：组装 formData 调 create 并刷新', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    expect(st.dialogVisible).toBe(true)
    st.formData.userId = 9
    st.formData.userName = '赵六'
    st.formData.resignDate = '2026-10-01'
    mockResignPage.mockClear()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockResignCreate).toHaveBeenCalledTimes(1)
    expect((mockResignCreate.mock.calls as any)[0][0]).toMatchObject({ userId: 9, userName: '赵六' })
    expect(mockResignPage).toHaveBeenCalled()
  })

  it('行提交 → 调 submitResignApply', async () => {
    await mountPage([{ id: 21, status: 'DRAFT' }])
    await wrapper.vm.$.setupState.handleSubmitRow({ id: 21 })
    await flushPromises()
    expect(mockResignSubmit).toHaveBeenCalledWith(21)
  })
})

describe('basedata/supplier-evaluation.vue 供应商评价（无编辑）', () => {
  async function mountPage(records: any[] = []) {
    mockEvalPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(SupplierEvaluation, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载并渲染行', async () => {
    const w = await mountPage([
      { id: 1, supplierName: '供应商A', score: 90 },
      { id: 2, supplierName: '供应商B', score: 75 },
    ])
    expect(w.findAll('.el-table__row')).toHaveLength(2)
    expect(w.text()).toContain('供应商A')
  })

  it('必填规则配置：供应商名称 + 评分', async () => {
    await mountPage()
    const msgs = Object.values(wrapper.vm.$.setupState.formRules).flat().map((r: any) => r.message)
    expect(msgs).toContain('请输入供应商名称')
    expect(msgs).toContain('请输入评分')
  })

  it('新增评价：组装 formData 调 create 并刷新', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    st.formData.supplierName = '供应商C'
    st.formData.score = 88
    mockEvalPage.mockClear()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockEvalCreate).toHaveBeenCalledTimes(1)
    expect((mockEvalCreate.mock.calls as any)[0][0]).toMatchObject({ supplierName: '供应商C', score: 88 })
    expect(mockEvalPage).toHaveBeenCalled()
  })

  it('删除评价：确认后调 delete 并刷新', async () => {
    await mountPage([{ id: 31, supplierName: '供应商D' }])
    mockEvalPage.mockClear()
    await wrapper.vm.$.setupState.handleDelete({ id: 31 })
    await flushPromises()
    expect(mockEvalDelete).toHaveBeenCalledWith(31)
    expect(mockEvalPage).toHaveBeenCalled()
  })

  it('搜索重置页码、重置清空条件', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.queryParams.pageNum = 3
    mockEvalPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
    st.handleReset()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
  })
})
