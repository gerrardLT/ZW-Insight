/**
 * finance/retention.vue 质保金管理组件测试（2026-08-15 P3 方向1 第五批）
 *
 * 双表单页（新增质保金 + 返还登记）+ 到期预警预载（30 天），定制 7 用例。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockPage, mockCreate, mockExpiring, mockReturn, mockProjectList } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockExpiring: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockReturn: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))

vi.mock('@/api/finance', () => ({
  getRetentionPage: mockPage,
  createRetention: mockCreate,
  getExpiringRetention: mockExpiring,
  createRetentionReturn: mockReturn,
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

import Retention from '@/views/finance/retention.vue'

const RECORDS: any[] = [
  { id: 1, projectName: '滨江花园一期', retentionAmount: 500000, retentionRate: 3, status: 'ACTIVE', expireDate: '2027-01-01' },
  { id: 2, projectName: '城南市政', retentionAmount: 200000, retentionRate: 5, status: 'EXPIRED', expireDate: '2026-01-01' },
  { id: 3, projectName: '高新园区', retentionAmount: 100000, retentionRate: 3, status: 'RETURNED', expireDate: '2026-06-01' },
]

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage(records: any[] = RECORDS) {
  mockPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(Retention, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('retention.vue 质保金管理', () => {
  it('挂载三路预载：列表 + 30 天到期预警 + 项目下拉', async () => {
    const w = await mountPage()
    expect(mockPage).toHaveBeenCalled()
    expect(mockExpiring).toHaveBeenCalledWith(30) // 到期预警窗口 30 天钉住
    expect(mockProjectList).toHaveBeenCalledWith({ projectName: '' })
    expect(w.findAll('.el-table__row')).toHaveLength(3)
  })

  it('状态标签映射：ACTIVE=有效 / EXPIRED=已到期 / RETURNED=已返还', async () => {
    const w = await mountPage()
    expect(w.text()).toContain('有效')
    expect(w.text()).toContain('已到期')
    expect(w.text()).toContain('已返还')
  })

  it('双表单必填规则：新增（项目+金额）与返还（金额+日期）', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    const formMsgs = Object.values(st.formRules).flat().map((r: any) => r.message)
    expect(formMsgs).toContain('请选择项目')
    expect(formMsgs).toContain('请输入质保金金额')
    const returnMsgs = Object.values(st.returnRules).flat().map((r: any) => r.message)
    expect(returnMsgs).toContain('请输入返还金额')
    expect(returnMsgs).toContain('请选择返还日期')
  })

  it('新增质保金：组装 formData 调 createRetention 并刷新', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    expect(st.dialogVisible).toBe(true)
    st.formData.projectId = 4
    st.formData.retentionAmount = 300000
    st.formData.retentionRate = 3
    mockPage.mockClear()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockCreate).toHaveBeenCalledTimes(1)
    expect((mockCreate.mock.calls as any)[0][0]).toMatchObject({ projectId: 4, retentionAmount: 300000, retentionRate: 3 })
    expect(mockPage).toHaveBeenCalled()
    expect(st.dialogVisible).toBe(false)
  })

  it('返还登记：handleReturn 回显 retentionId 并打开弹窗', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.handleReturn(RECORDS[0])
    await flushPromises()
    expect(st.returnVisible).toBe(true)
    expect(st.returnForm.retentionId).toBe(1) // 关联行 id 钉住
    expect(st.returnForm.returnAmount).toBe(0)
  })

  it('返还提交：组装 returnForm 调 createRetentionReturn 并刷新', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.handleReturn(RECORDS[0])
    await flushPromises()
    st.returnForm.returnAmount = 500000
    st.returnForm.returnDate = '2026-08-15'
    mockPage.mockClear()
    await st.handleReturnSubmit()
    await flushPromises()
    expect(mockReturn).toHaveBeenCalledTimes(1)
    expect((mockReturn.mock.calls as any)[0][0]).toMatchObject({ retentionId: 1, returnAmount: 500000 })
    expect(mockPage).toHaveBeenCalled()
    expect(st.returnVisible).toBe(false)
  })

  it('搜索重置 page、重置清空 projectId', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.queryParams.page = 2
    st.queryParams.projectId = 6
    mockPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect(st.queryParams.page).toBe(1)
    st.handleReset()
    await flushPromises()
    expect(st.queryParams).toEqual({ page: 1, size: 10, projectId: undefined })
  })
})
