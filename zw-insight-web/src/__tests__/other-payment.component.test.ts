/**
 * finance/other-payment.vue 其他付款组件测试（2026-08-15 P3 方向1 第四批）
 *
 * 财务单据页为「新增即生效」模式（无编辑/删除/行提交），定制 6 用例：
 * 渲染/状态映射/formatMoney/必填规则/新增 create/搜索重置 page。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockPage, mockCreate, mockProjectList } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))

vi.mock('@/api/finance', () => ({
  getOtherPaymentPage: mockPage,
  createOtherPayment: mockCreate,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockProjectList,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  }
})

import OtherPayment from '@/views/finance/other-payment.vue'

const RECORDS = [
  { id: 1, projectName: '滨江花园一期', payerName: '财务部', paymentAmount: 5000, paymentDate: '2026-08-01', status: 'DRAFT' },
  { id: 2, projectName: '城南市政', payerName: '财务部', paymentAmount: 3000.25, paymentDate: '2026-08-02', status: 'APPROVED' },
]

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage(records = RECORDS) {
  mockPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(OtherPayment, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('other-payment.vue 其他付款', () => {
  it('挂载加载列表并预载项目下拉数据源', async () => {
    const w = await mountPage()
    expect(mockPage).toHaveBeenCalled()
    expect(mockProjectList).toHaveBeenCalledWith({ projectName: '' })
    expect(w.findAll('.el-table__row')).toHaveLength(2)
    expect(w.text()).toContain('3,000.25')
  })

  it('状态标签映射：DRAFT=草稿 / APPROVED=已通过 / 未知透传', async () => {
    const w = await mountPage([
      ...RECORDS,
      { id: 3, projectName: 'X', payerName: 'Y', paymentAmount: 1, paymentDate: '2026-08-03', status: 'UNKNOWN_ST' },
    ])
    expect(w.text()).toContain('草稿')
    expect(w.text()).toContain('已通过')
    expect(w.text()).toContain('UNKNOWN_ST')
  })

  it('formatMoney：0 合法、空值显示 -', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    expect(st.formatMoney(0)).toBe('0.00')
    expect(st.formatMoney(null)).toBe('-')
    expect(st.formatMoney(12345.6)).toBe('12,345.60')
  })

  it('必填规则配置：项目/付款人/金额/日期四项', async () => {
    const w = await mountPage([])
    const msgs = Object.values(w.vm.$.setupState.formRules).flat().map((r: any) => r.message)
    expect(msgs).toContain('请选择项目')
    expect(msgs).toContain('请输入付款人')
    expect(msgs).toContain('请输入付款金额')
    expect(msgs).toContain('请选择付款日期')
  })

  it('新增付款：组装 formData 调 createOtherPayment 并刷新', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    expect(st.dialogVisible).toBe(true)
    st.formData.projectId = 7
    st.formData.payerName = '财务部'
    st.formData.paymentAmount = 999
    st.formData.paymentDate = '2026-08-15'
    mockPage.mockClear()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockCreate).toHaveBeenCalledTimes(1)
    expect((mockCreate.mock.calls as any)[0][0]).toMatchObject({ projectId: 7, paymentAmount: 999 })
    expect(mockPage).toHaveBeenCalled()
    expect(st.dialogVisible).toBe(false)
  })

  it('搜索重置 page（page/size 参数口径）并重新查询', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.queryParams.page = 3
    st.queryParams.projectId = 5
    mockPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.page).toBe(1)
    expect((mockPage.mock.calls as any)[0][0]).toMatchObject({ page: 1, size: 10, projectId: 5 })
    // 重置清空 projectId
    st.handleReset()
    await flushPromises()
    expect(st.queryParams).toEqual({ page: 1, size: 10, projectId: undefined })
  })
})
