/**
 * finance/invoice-apply.vue 开票申请组件测试（2026-08-15 P3 方向1 第五批）
 *
 * 「新增+行提交审批+删除」模式，表单含 TaxRateSelector/ContractSelector 子组件
 * （经 vi.mock stub 隔离，避免子组件级联请求），定制 7 用例。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockPage, mockDetail, mockCreate, mockDelete, mockSubmit, mockProjectList } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: null })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))

vi.mock('@/api/finance', () => ({
  getInvoiceApplyPage: mockPage,
  getInvoiceApplyDetail: mockDetail,
  createInvoiceApply: mockCreate,
  deleteInvoiceApply: mockDelete,
  submitInvoiceApply: mockSubmit,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockProjectList,
}))
// 子组件 stub：隔离级联请求，仅保留占位渲染
vi.mock('@/components/TaxRateSelector.vue', () => ({ default: { name: 'TaxRateSelector', template: '<div class="tax-rate-stub" />' } }))
vi.mock('@/components/ContractSelector.vue', () => ({ default: { name: 'ContractSelector', template: '<div class="contract-stub" />' } }))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import InvoiceApply from '@/views/finance/invoice-apply.vue'

const RECORDS = [
  { id: 1, projectName: '滨江花园一期', invoiceAmount: 100000, invoiceType: '增值税专用发票', status: 'DRAFT', applyDate: '2026-08-01' },
  { id: 2, projectName: '城南市政', invoiceAmount: 50000, invoiceType: '增值税普通发票', status: 'SUBMITTED', applyDate: '2026-08-02' },
  { id: 3, projectName: '高新园区', invoiceAmount: 80000, invoiceType: '增值税专用发票', status: 'REJECTED', applyDate: '2026-08-03' },
]

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage(records: any[] = RECORDS) {
  mockPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(InvoiceApply, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('invoice-apply.vue 开票申请', () => {
  it('挂载加载列表并预载项目下拉', async () => {
    const w = await mountPage()
    expect(mockPage).toHaveBeenCalled()
    expect(mockProjectList).toHaveBeenCalledWith({ projectName: '' })
    expect(w.findAll('.el-table__row')).toHaveLength(3)
    expect(w.text()).toContain('100,000.00')
  })

  it('状态标签映射：SUBMITTED/APPROVING 均「审批中」、REJECTED「已驳回」、未知透传', async () => {
    const w = await mountPage([
      ...RECORDS,
      { id: 4, projectName: 'X', invoiceAmount: 1, status: 'APPROVING', applyDate: '2026-08-04' },
      { id: 5, projectName: 'Y', invoiceAmount: 1, status: 'CUSTOM', applyDate: '2026-08-04' },
    ])
    expect(w.text()).toContain('草稿')
    expect(w.text()).toContain('审批中')
    expect(w.text()).toContain('已驳回')
    expect(w.text()).toContain('CUSTOM')
  })

  it('必填规则配置：项目/合同/金额/日期四项', async () => {
    const w = await mountPage([])
    const msgs = Object.values(w.vm.$.setupState.formRules).flat().map((r: any) => r.message)
    expect(msgs).toContain('请选择项目')
    expect(msgs).toContain('请选择关联合同')
    expect(msgs).toContain('请输入开票金额')
    expect(msgs).toContain('请选择申请日期')
  })

  it('新增开票：组装 formData 调 create（invoiceType 默认增值税专用发票）', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    expect(st.dialogVisible).toBe(true)
    expect(st.formData.invoiceType).toBe('增值税专用发票') // 默认值钉住
    st.formData.projectId = 3
    st.formData.contractId = 77
    st.formData.invoiceAmount = 12345
    st.formData.applyDate = '2026-08-15'
    mockPage.mockClear()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockCreate).toHaveBeenCalledTimes(1)
    expect((mockCreate.mock.calls as any)[0][0]).toMatchObject({ projectId: 3, contractId: 77, invoiceAmount: 12345 })
    expect(mockPage).toHaveBeenCalled()
  })

  it('行提交审批：确认后调 submitInvoiceApply 并刷新', async () => {
    const w = await mountPage()
    mockPage.mockClear()
    await w.vm.$.setupState.handleSubmitApply(RECORDS[0])
    await flushPromises()
    expect(mockSubmit).toHaveBeenCalledWith(1)
    expect(mockPage).toHaveBeenCalled()
  })

  it('删除：确认后调 deleteInvoiceApply 并刷新', async () => {
    const w = await mountPage()
    mockPage.mockClear()
    await w.vm.$.setupState.handleDelete(RECORDS[0])
    await flushPromises()
    expect(mockDelete).toHaveBeenCalledWith(1)
    expect(mockPage).toHaveBeenCalled()
  })

  it('搜索重置 pageNum、重置清空 projectId/status', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.queryParams.pageNum = 3
    st.queryParams.projectId = 5
    st.queryParams.status = 'DRAFT'
    mockPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.pageNum).toBe(1)
    expect((mockPage.mock.calls as any)[0][0]).toMatchObject({ pageNum: 1, projectId: 5, status: 'DRAFT' })
    st.handleReset()
    await flushPromises()
    expect(st.queryParams).toEqual({ pageNum: 1, pageSize: 10, projectId: undefined, status: '' })
  })

  it('查看详情：调 getInvoiceApplyDetail(row.id) 打开抽屉并回显详情（真实接口非 stub）', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    mockDetail.mockResolvedValueOnce({
      code: 200,
      data: {
        id: 1, projectName: '滨江花园一期', invoiceAmount: 100000, taxRate: 9,
        invoiceType: '增值税专用发票', invoiceTitle: '甲方公司', taxpayerId: '91330000X',
        status: 'DRAFT', applyDate: '2026-08-01',
        contractAmountSnapshot: 5000000, settlementAmountSnapshot: 3000000,
        historicalInvoicedSnapshot: 500000, createdAt: '2026-08-01 09:00:00'
      }
    })
    await st.handleView(RECORDS[0])
    await flushPromises()
    expect(mockDetail).toHaveBeenCalledWith(1)
    expect(st.detailVisible).toBe(true)
    expect(st.detailData.invoiceTitle).toBe('甲方公司')
    expect(st.detailError).toBe('')
  })

  it('详情加载失败不静默：错误写入 detailError，抽屉内提供重试入口', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    mockDetail.mockRejectedValueOnce(new Error('invoice detail down'))
    await st.handleView(RECORDS[0])
    await flushPromises()
    expect(st.detailVisible).toBe(true)
    expect(st.detailData).toBeNull()
    expect(st.detailError).toContain('invoice detail down')
    // 重试入口重新发起请求
    mockDetail.mockResolvedValueOnce({ code: 200, data: { id: 1, invoiceAmount: 100000, status: 'DRAFT' } })
    await st.retryDetail()
    await flushPromises()
    expect(mockDetail).toHaveBeenCalledTimes(2)
    expect(st.detailData.invoiceAmount).toBe(100000)
    expect(st.detailError).toBe('')
  })
})
