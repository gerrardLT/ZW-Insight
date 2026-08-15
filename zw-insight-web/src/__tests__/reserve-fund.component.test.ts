/**
 * finance/reserve-fund.vue 备用金管理组件测试（2026-08-15 P3 方向1 第五批）
 *
 * 双表单页（申请 + 归还登记）+ 行提交审批，定制 7 用例。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockPage, mockApply, mockSubmit, mockReturn, mockProjectList } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockApply: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockReturn: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))

vi.mock('@/api/finance', () => ({
  getReserveFundApplyPage: mockPage,
  createReserveFundApply: mockApply,
  submitReserveFundApply: mockSubmit,
  createReserveFundReturn: mockReturn,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockProjectList,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import ReserveFund from '@/views/finance/reserve-fund.vue'

const RECORDS: any[] = [
  { id: 1, projectName: '滨江花园一期', applicant: '张三', applyAmount: 20000, status: 'DRAFT', applyDate: '2026-08-01' },
  { id: 2, projectName: '城南市政', applicant: '李四', applyAmount: 50000, status: 'APPROVING', applyDate: '2026-08-02' },
  { id: 3, projectName: '高新园区', applicant: '王五', applyAmount: 10000, status: 'APPROVED', applyDate: '2026-08-03' },
]

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage(records: any[] = RECORDS) {
  mockPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(ReserveFund, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('reserve-fund.vue 备用金管理', () => {
  it('挂载加载列表并预载项目下拉', async () => {
    const w = await mountPage()
    expect(mockPage).toHaveBeenCalled()
    expect(mockProjectList).toHaveBeenCalledWith({ projectName: '' })
    expect(w.findAll('.el-table__row')).toHaveLength(3)
    expect(w.text()).toContain('20,000.00')
  })

  it('状态标签映射：DRAFT=草稿 / APPROVING=审批中 / APPROVED=已通过 / 未知透传', async () => {
    const w = await mountPage([...RECORDS, { id: 4, projectName: 'X', applicant: 'Y', applyAmount: 1, status: 'RETURNED', applyDate: '2026-08-04' }])
    expect(w.text()).toContain('草稿')
    expect(w.text()).toContain('审批中')
    expect(w.text()).toContain('已通过')
    expect(w.text()).toContain('RETURNED')
  })

  it('双表单必填规则：申请（项目/申请人/金额/日期）与归还（金额/日期）', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    const applyMsgs = Object.values(st.applyRules).flat().map((r: any) => r.message)
    expect(applyMsgs).toContain('请选择项目')
    expect(applyMsgs).toContain('请输入申请人')
    expect(applyMsgs).toContain('请输入申请金额')
    const returnMsgs = Object.values(st.returnRules).flat().map((r: any) => r.message)
    expect(returnMsgs).toContain('请输入归还金额')
    expect(returnMsgs).toContain('请选择归还日期')
  })

  it('新增申请：组装 applyForm 调 createReserveFundApply 并刷新', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    st.handleAddApply()
    await flushPromises()
    expect(st.applyVisible).toBe(true)
    st.applyForm.projectId = 5
    st.applyForm.applicant = '张三'
    st.applyForm.applyAmount = 8000
    st.applyForm.applyDate = '2026-08-15'
    mockPage.mockClear()
    await st.handleApplySubmit()
    await flushPromises()
    expect(mockApply).toHaveBeenCalledTimes(1)
    expect((mockApply.mock.calls as any)[0][0]).toMatchObject({ projectId: 5, applicant: '张三', applyAmount: 8000 })
    expect(mockPage).toHaveBeenCalled()
    expect(st.applyVisible).toBe(false)
  })

  it('行提交审批：确认后调 submitReserveFundApply 并刷新', async () => {
    const w = await mountPage()
    mockPage.mockClear()
    await w.vm.$.setupState.handleSubmitRow(RECORDS[0])
    await flushPromises()
    expect(mockSubmit).toHaveBeenCalledWith(1)
    expect(mockPage).toHaveBeenCalled()
  })

  it('归还登记：回显 reserveApplyId + 提交调 createReserveFundReturn', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.handleReturn(RECORDS[2])
    await flushPromises()
    expect(st.returnVisible).toBe(true)
    expect(st.returnForm.reserveApplyId).toBe(3) // 关联申请单 id 钉住
    st.returnForm.returnAmount = 10000
    st.returnForm.returnDate = '2026-08-20'
    mockPage.mockClear()
    await st.handleReturnSubmit()
    await flushPromises()
    expect(mockReturn).toHaveBeenCalledTimes(1)
    expect((mockReturn.mock.calls as any)[0][0]).toMatchObject({ reserveApplyId: 3, returnAmount: 10000 })
    expect(mockPage).toHaveBeenCalled()
    expect(st.returnVisible).toBe(false)
  })

  it('搜索重置 page、重置清空 projectId', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.queryParams.page = 2
    st.queryParams.projectId = 7
    mockPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.page).toBe(1)
    st.handleReset()
    await flushPromises()
    expect(st.queryParams).toEqual({ page: 1, size: 10, projectId: undefined })
  })
})
