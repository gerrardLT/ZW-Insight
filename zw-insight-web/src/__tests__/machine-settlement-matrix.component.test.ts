/**
 * M3 账本补齐：B-2 机械结算三页矩阵用例
 * views/machine/settlement/index.vue（B11）+ create.vue（B12）+ detail.vue（B13）
 *
 * @matrix B-11-2/B-11-3/B-11-4/B-11-5/B-11-6/B-11-7/B-11-8/B-11-9/B-11-10
 * @matrix B-12-1/B-12-2/B-12-3/B-12-4/B-12-5/B-12-8/B-12-9
 * @matrix B-13-1/B-13-2/B-13-3/B-13-4/B-13-7/B-13-8
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockSettlementPage, mockSubmitSettlement, mockSummary, mockExport,
  mockCreateSettlement, mockUsagePage, mockSettlementDetail,
  mockProjectList, mockRouteParams, mockPush, mockBack,
  mockSuccess, mockError, mockWarning, mockConfirm,
} = vi.hoisted(() => ({
  mockSettlementPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockSubmitSettlement: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSummary: vi.fn(async (): Promise<any> => ({ code: 200, data: { totalSettledAmount: 0, totalPaidAmount: 0, unpaidAmount: 0, settlementCount: 0 } })),
  mockExport: vi.fn(async (): Promise<any> => new ArrayBuffer(8)),
  mockCreateSettlement: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUsagePage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockSettlementDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockRouteParams: {} as Record<string, string>,
  mockPush: vi.fn(),
  mockBack: vi.fn(),
  mockSuccess: vi.fn(),
  mockError: vi.fn(),
  mockWarning: vi.fn(),
  mockConfirm: vi.fn(async () => 'confirm'),
}))

vi.mock('@/api/machine', () => ({
  getMachineSettlementPage: mockSettlementPage,
  submitMachineSettlement: mockSubmitSettlement,
  getMachineSettlementSummary: mockSummary,
  exportMachineSettlement: mockExport,
  createMachineSettlement: mockCreateSettlement,
  getMachineUsagePage: mockUsagePage,
  getMachineSettlementDetail: mockSettlementDetail,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mockProjectList,
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: mockRouteParams, query: {} }),
  useRouter: () => ({ push: mockPush, back: mockBack }),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: mockSuccess, error: mockError, warning: mockWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mockConfirm },
  }
})

import SettlementIndex from '@/views/machine/settlement/index.vue'
import SettlementCreate from '@/views/machine/settlement/create.vue'
import SettlementDetail from '@/views/machine/settlement/detail.vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))
const indexSrc = readFileSync(resolve(__testDir, '../views/machine/settlement/index.vue'), 'utf-8')
const detailSrc = readFileSync(resolve(__testDir, '../views/machine/settlement/detail.vue'), 'utf-8')

let wrapper: any = null
beforeEach(() => {
  vi.clearAllMocks()
  delete (mockRouteParams as any).id
})
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

async function mountIndex(records: any[] = []) {
  mockSettlementPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(SettlementIndex, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}
async function mountCreate() {
  wrapper = mount(SettlementCreate, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}
async function mountDetail() {
  wrapper = mount(SettlementDetail, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('settlement/index.vue B11 矩阵', () => {
  it('B-11-2 summary 接口失败不阻断：catch 静默，列表仍渲染', async () => {
    mockSummary.mockRejectedValue(new Error('500'))
    const w = await mountIndex([{ id: 1, settlementCode: 'JXJS-1', status: 0 }])
    expect(mockSettlementPage).toHaveBeenCalled()
    expect(w.findAll('.el-table__row')).toHaveLength(1)
  })

  it('B-11-3 状态筛选数字枚举 0/1/2/3：选项钉住 + 参数透传', async () => {
    const w = await mountIndex()
    expect(indexSrc).toContain('<el-option label="草稿" :value="0" />')
    expect(indexSrc).toContain('<el-option label="审批中" :value="1" />')
    expect(indexSrc).toContain('<el-option label="已审批" :value="2" />')
    expect(indexSrc).toContain('<el-option label="已驳回" :value="3" />')
    const st = w.vm.$.setupState
    st.queryParams.status = 3
    mockSettlementPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect((mockSettlementPage.mock.calls as any)[0][0].status).toBe(3)
  })

  it('B-11-4 结算周期 dateRange 组装：length===2 才附加 periodStart/periodEnd', async () => {
    const w = await mountIndex()
    const st = w.vm.$.setupState
    st.queryParams.dateRange = ['2026-08-01', '2026-08-31']
    mockSettlementPage.mockClear()
    st.handleSearch()
    await flushPromises()
    let params = (mockSettlementPage.mock.calls as any)[0][0]
    expect(params.periodStart).toBe('2026-08-01')
    expect(params.periodEnd).toBe('2026-08-31')
    st.queryParams.dateRange = null
    mockSettlementPage.mockClear()
    st.handleSearch()
    await flushPromises()
    params = (mockSettlementPage.mock.calls as any)[0][0]
    expect(params.periodStart).toBeUndefined()
    expect(params.periodEnd).toBeUndefined()
  })

  it('B-11-5 提交审批仅 status 0/3 行渲染', async () => {
    const w = await mountIndex([
      { id: 1, status: 0, settlementCode: 'A' },
      { id: 2, status: 1, settlementCode: 'B' },
      { id: 3, status: 2, settlementCode: 'C' },
      { id: 4, status: 3, settlementCode: 'D' },
    ])
    const rows = w.findAll('.el-table__row')
    expect(rows[0].text()).toContain('提交审批')
    expect(rows[1].text()).not.toContain('提交审批')
    expect(rows[2].text()).not.toContain('提交审批')
    expect(rows[3].text()).toContain('提交审批')
  })

  it('B-11-6 提交审批确认框+成功后提示「提交成功」并刷新', async () => {
    const w = await mountIndex()
    const st = w.vm.$.setupState
    await st.handleSubmit({ id: 9 })
    await flushPromises()
    expect(mockConfirm).toHaveBeenCalled()
    expect(mockSubmitSettlement).toHaveBeenCalledWith(9)
    expect(mockSuccess).toHaveBeenCalledWith('提交成功')
    expect(mockSettlementPage).toHaveBeenCalled()
  })

  it('B-11-7 导出 Excel blob 下载：文件名 机械结算单_{code}.xlsx + revokeObjectURL', async () => {
    const createSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock')
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const w = await mountIndex()
    await w.vm.$.setupState.handleExport({ id: 5, settlementCode: 'JXJS-202608-0001' })
    await flushPromises()
    expect(mockExport).toHaveBeenCalledWith(5)
    expect(createSpy).toHaveBeenCalled()
    expect(revokeSpy).toHaveBeenCalledWith('blob:mock')
    expect(indexSrc).toContain('`机械结算单_${row.settlementCode}.xlsx`')
    createSpy.mockRestore()
    revokeSpy.mockRestore()
  })

  it('B-11-8 导出失败提示 ElMessage.error("导出失败")', async () => {
    mockExport.mockRejectedValue(new Error('500'))
    const w = await mountIndex()
    await w.vm.$.setupState.handleExport({ id: 5, settlementCode: 'X' })
    await flushPromises()
    expect(mockError).toHaveBeenCalledWith('导出失败')
  })

  it('B-11-9 新建跳转 create 页', async () => {
    const w = await mountIndex()
    w.vm.$.setupState.handleCreate()
    expect(mockPush).toHaveBeenCalledWith('/machine/settlement/create')
  })

  it('B-11-10 查看跳转 detail/:id（雪花 ID 直传字符串）', async () => {
    const w = await mountIndex()
    w.vm.$.setupState.handleView({ id: '2089728215595675650' })
    expect(mockPush).toHaveBeenCalledWith('/machine/settlement/detail/2089728215595675650')
  })
})

describe('settlement/create.vue B12 矩阵', () => {
  it('B-12-1 必填 projectId/period 两条提示文案', async () => {
    const w = await mountCreate()
    const rules = w.vm.$.setupState.formRules
    expect(rules.projectId[0].message).toBe('请选择项目')
    expect(rules.period[0].message).toBe('请选择结算周期')
  })

  it('B-12-2 预览仅双条件齐备时加载：只选项目 previewVisible=false', async () => {
    const w = await mountCreate()
    const st = w.vm.$.setupState
    st.formData.projectId = 1
    st.formData.period = null
    await st.loadPreview()
    expect(st.previewVisible).toBe(false)
    expect(mockUsagePage).not.toHaveBeenCalled()
  })

  it('B-12-3 合计金额 computed = Σ previewData.amount', async () => {
    const w = await mountCreate()
    const st = w.vm.$.setupState
    st.previewData = [
      { machineName: '挖机', workDays: 2, unitPrice: 100, amount: 200 },
      { machineName: '吊车', workDays: 1, unitPrice: 300, amount: 300 },
    ]
    expect(st.totalAmount).toBe(500)
    st.previewData = []
    expect(st.totalAmount).toBe(0)
  })

  it('B-12-4 切换项目/周期自动重载预览：handlePeriodChange 触发 usage 请求', async () => {
    const w = await mountCreate()
    const st = w.vm.$.setupState
    st.formData.projectId = 1
    st.formData.period = ['2026-08-01', '2026-08-31']
    await st.handlePeriodChange()
    await flushPromises()
    expect(mockUsagePage).toHaveBeenCalledTimes(1)
    const params = (mockUsagePage.mock.calls as any)[0][0]
    expect(params.projectId).toBe(1)
    expect(params.startDate).toBe('2026-08-01')
    expect(params.endDate).toBe('2026-08-31')
    expect(st.previewVisible).toBe(true)
  })

  it('B-12-5 空预览拦截保存（盲点 12 守卫，2026-08-24 翻转）：canSave=false + handleSave 不发请求仅 warning', async () => {
    const w = await mountCreate()
    const st = w.vm.$.setupState
    mockUsagePage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    st.formData.projectId = 1
    st.formData.period = ['2026-08-01', '2026-08-31']
    await st.loadPreview()
    expect(st.canSave, '空预览应禁用保存').toBe(false)
    await st.handleSave()
    await flushPromises()
    expect(mockCreateSettlement, '空预览不得发起创建请求').not.toHaveBeenCalled()
    expect(mockWarning).toHaveBeenCalledWith('当前周期内无可结算的机械使用明细，无法保存')
    // 有明细后 canSave 翻转为 true（正常路径不受影响）
    st.previewData = [{ machineName: '挖机', amount: 200 }]
    expect(st.canSave).toBe(true)
  })

  it('B-12-8 usage 接口失败降级：previewData=[] 不阻断', async () => {
    const w = await mountCreate()
    const st = w.vm.$.setupState
    mockUsagePage.mockRejectedValue(new Error('500'))
    st.formData.projectId = 1
    st.formData.period = ['2026-08-01', '2026-08-31']
    await st.loadPreview()
    await flushPromises()
    expect(st.previewData).toEqual([])
    expect(st.previewVisible).toBe(true)
  })

  it('B-12-9 取消/返回 router.back()', async () => {
    await mountCreate()
    const backBtns = wrapper.findAll('button').filter((b: any) => b.text() === '返回' || b.text() === '取消')
    expect(backBtns.length).toBeGreaterThanOrEqual(2)
    await backBtns[0].trigger('click')
    expect(mockBack).toHaveBeenCalled()
  })
})

describe('settlement/detail.vue B13 矩阵', () => {
  it('B-13-1 雪花 ID 字符串传递：route.params.id 原样传给详情 API（源码注释纪律实证）', async () => {
    mockRouteParams.id = '2089728215595675650'
    mockSettlementDetail.mockResolvedValue({ code: 200, data: { id: '2089728215595675650', status: 0 } })
    await mountDetail()
    expect(mockSettlementDetail).toHaveBeenCalledWith('2089728215595675650')
    expect(detailSrc).toContain('雪花 ID 超出 Number 安全整数范围，必须以字符串传递避免精度丢失')
  })

  it('B-13-2 提交审批按钮仅 status 0/3：草稿渲染、已审批不渲染', async () => {
    mockRouteParams.id = '1'
    mockSettlementDetail.mockResolvedValue({ code: 200, data: { id: '1', status: 0, settlementCode: 'J1' } })
    await mountDetail()
    expect(wrapper.text()).toContain('提交审批')
    mockSettlementDetail.mockResolvedValue({ code: 200, data: { id: '1', status: 2, settlementCode: 'J1' } })
    await mountDetail()
    expect(wrapper.text()).not.toContain('提交审批')
  })

  it('B-13-3 基本信息五项渲染：编号/周期/金额/状态/流程ID（缺省「-」）', async () => {
    mockRouteParams.id = '1'
    mockSettlementDetail.mockResolvedValue({ code: 200, data: { id: '1', status: 0, settlementCode: 'J1', periodStart: '2026-08-01', periodEnd: '2026-08-31', totalAmount: 100 } })
    await mountDetail()
    expect(detailSrc).toContain('label="结算编号"')
    expect(detailSrc).toContain('label="结算周期"')
    expect(detailSrc).toContain('label="结算金额"')
    expect(detailSrc).toContain('label="当前状态"')
    expect(detailSrc).toContain('label="审批流程ID"')
    expect(detailSrc).toContain("detail.workflowInstanceId || '-'")
    expect(wrapper.text()).toContain('J1')
    expect(wrapper.text()).toContain('-')
  })

  it('B-13-4 明细表渲染：与创建预览同构（机械名称/工作天数/单价/金额）', async () => {
    mockRouteParams.id = '1'
    mockSettlementDetail.mockResolvedValue({ code: 200, data: { id: '1', status: 0, details: [{ machineName: '挖机', workDays: 2, unitPrice: 100, amount: 200 }] } })
    await mountDetail()
    expect(detailSrc).toContain('prop="machineName" label="机械名称"')
    expect(detailSrc).toContain('prop="workDays" label="工作天数"')
    expect(wrapper.text()).toContain('挖机')
  })

  it('B-13-7 无 id 打开：loadDetail 直接 return，不发请求不崩溃', async () => {
    delete (mockRouteParams as any).id
    await mountDetail()
    expect(mockSettlementDetail).not.toHaveBeenCalled()
    expect(wrapper.vm.$.setupState.detail).toEqual({})
  })

  it('B-13-8 数字状态映射与列表页一致：同一 statusMap 0/1/2/3', async () => {
    mockRouteParams.id = '1'
    mockSettlementDetail.mockResolvedValue({ code: 200, data: { id: '1', status: 0 } })
    const w = await mountDetail()
    const st = w.vm.$.setupState
    expect(st.statusLabel(0)).toBe('草稿')
    expect(st.statusLabel(1)).toBe('审批中')
    expect(st.statusLabel(2)).toBe('已审批')
    expect(st.statusLabel(3)).toBe('已驳回')
    expect(st.statusTagType(1)).toBe('primary')
    expect(indexSrc).toContain("0: { label: '草稿', type: 'info' }")
    expect(detailSrc).toContain("0: { label: '草稿', type: 'info' }")
  })
})
