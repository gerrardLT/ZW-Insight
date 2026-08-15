/**
 * finance/invoice-summary.vue 开票汇总组件测试（2026-08-15 P3 方向1 第四批）
 *
 * 只读汇总页，核心逻辑为合计行计算（getSummaries：金额列千分位合计、
 * 笔数列求和、非统计列空串）与项目+日期区间查询参数组装，定制 5 用例。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockSummary, mockProjectList } = vi.hoisted(() => ({
  mockSummary: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))

vi.mock('@/api/finance', () => ({
  getInvoiceSummary: mockSummary,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockProjectList,
}))

import InvoiceSummary from '@/views/finance/invoice-summary.vue'

const ROWS = [
  { projectName: 'P1', invoicedCount: 2, invoicedAmount: 1000, invoicedTaxAmount: 90, receivedCount: 1, receivedAmount: 800, receivedTaxAmount: 72 },
  { projectName: 'P2', invoicedCount: 3, invoicedAmount: 2000.5, invoicedTaxAmount: 180.5, receivedCount: 2, receivedAmount: 1500, receivedTaxAmount: 135 },
]

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage(data = ROWS) {
  mockSummary.mockResolvedValue({ code: 200, data })
  wrapper = mount(InvoiceSummary, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('invoice-summary.vue 开票汇总', () => {
  it('挂载并行发起汇总查询与项目列表预载', async () => {
    await mountPage()
    expect(mockSummary).toHaveBeenCalled()
    expect(mockProjectList).toHaveBeenCalledWith({ projectName: '' }) // onMounted searchProject('')
  })

  it('getSummaries：首列「合计」+ 金额列千分位合计 + 笔数列求和 + 非统计列空串', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    const columns = [
      { property: 'projectName' },
      { property: 'invoicedCount' },
      { property: 'invoicedAmount' },
      { property: 'invoicedTaxAmount' },
      { property: 'receivedCount' },
      { property: 'receivedAmount' },
      { property: 'receivedTaxAmount' },
      { property: 'remark' }, // 非统计列
    ] as any[]
    const sums = st.getSummaries({ columns, data: ROWS })
    expect(sums[0]).toBe('合计')
    expect(sums[1]).toBe('5') // 笔数求和（字符串）
    expect(sums[2]).toBe('3,000.50') // 金额千分位两位小数
    expect(sums[3]).toBe('270.50')
    expect(sums[4]).toBe('3')
    expect(sums[5]).toBe('2,300.00')
    expect(sums[6]).toBe('207.00')
    expect(sums[7]).toBe('') // 非统计列空串
  })

  it('getSummaries：空数据合计为零值', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    const sums = st.getSummaries({
      columns: [{ property: 'projectName' }, { property: 'invoicedAmount' }] as any[],
      data: [],
    })
    expect(sums[0]).toBe('合计')
    expect(sums[1]).toBe('0.00')
  })

  it('搜索带项目与日期区间参数（startDate/endDate 来自 dateRange）', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.queryParams.projectId = 9
    st.dateRange = ['2026-01-01', '2026-06-30']
    mockSummary.mockClear()
    st.handleSearch()
    await flushPromises()
    expect((mockSummary.mock.calls as any)[0][0]).toEqual({
      projectId: 9, startDate: '2026-01-01', endDate: '2026-06-30',
    })
  })

  it('重置清空项目与日期区间后重新查询', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.queryParams.projectId = 9
    st.dateRange = ['2026-01-01', '2026-06-30']
    mockSummary.mockClear()
    st.handleReset()
    await flushPromises()
    expect(st.queryParams).toEqual({ projectId: undefined })
    expect(st.dateRange).toBeNull()
    expect((mockSummary.mock.calls as any)[0][0]).toEqual({ projectId: undefined, startDate: undefined, endDate: undefined })
  })
})
