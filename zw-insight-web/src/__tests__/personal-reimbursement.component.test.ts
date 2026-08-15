/**
 * finance/personal-reimbursement.vue 个人报销组件测试（2026-08-15 P3 方向1 第四批）
 *
 * 财务单据页为「新增+行提交审批」模式（无编辑/删除），定制 6 用例：
 * 渲染/状态标签映射/formatMoney/必填规则/新增 create/行提交 submit。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockPage, mockCreate, mockSubmit } = vi.hoisted(() => ({
  mockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
}))

vi.mock('@/api/finance', () => ({
  getPersonalReimbursementPage: mockPage,
  createPersonalReimbursement: mockCreate,
  submitPersonalReimbursement: mockSubmit,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import PersonalReimbursement from '@/views/finance/personal-reimbursement.vue'

const RECORDS = [
  { id: 1, totalAmount: 1234.5, reimbursementDate: '2026-08-01', status: 'DRAFT', remark: '差旅' },
  { id: 2, totalAmount: 500, reimbursementDate: '2026-08-02', status: 'APPROVING', remark: '' },
  { id: 3, totalAmount: 800, reimbursementDate: '2026-08-03', status: 'APPROVED', remark: '' },
]

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage(records = RECORDS) {
  mockPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(PersonalReimbursement, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('personal-reimbursement.vue 个人报销', () => {
  it('挂载加载列表并渲染金额（千分位两位小数）', async () => {
    const w = await mountPage()
    expect(mockPage).toHaveBeenCalled()
    expect(w.findAll('.el-table__row')).toHaveLength(3)
    expect(w.text()).toContain('1,234.50') // formatMoney zh-CN 千分位
  })

  it('状态标签映射：DRAFT=草稿 / APPROVING=审批中 / APPROVED=已通过 / 未知透传', async () => {
    const w = await mountPage([
      ...RECORDS,
      { id: 4, totalAmount: 1, reimbursementDate: '2026-08-04', status: 'REJECTED', remark: '' },
    ])
    expect(w.text()).toContain('草稿')
    expect(w.text()).toContain('审批中')
    expect(w.text()).toContain('已通过')
    expect(w.text()).toContain('REJECTED') // 未知状态透传原值
  })

  it('formatMoney：0 合法展示，null/undefined 显示 -', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    expect(st.formatMoney(0)).toBe('0.00')
    expect(st.formatMoney(null)).toBe('-')
    expect(st.formatMoney(undefined)).toBe('-')
    expect(st.formatMoney(2000)).toBe('2,000.00')
  })

  it('必填规则配置：总金额 + 报销日期', async () => {
    const w = await mountPage([])
    const msgs = Object.values(w.vm.$.setupState.formRules).flat().map((r: any) => r.message)
    expect(msgs).toContain('请输入报销总金额')
    expect(msgs).toContain('请选择报销日期')
  })

  it('新增报销：组装 formData 调 create 并刷新', async () => {
    const w = await mountPage([])
    const st = w.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    expect(st.dialogVisible).toBe(true)
    st.formData.totalAmount = 888
    st.formData.reimbursementDate = '2026-08-15'
    mockPage.mockClear()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockCreate).toHaveBeenCalledTimes(1)
    expect((mockCreate.mock.calls as any)[0][0]).toMatchObject({ totalAmount: 888, reimbursementDate: '2026-08-15' })
    expect(mockPage).toHaveBeenCalled()
    expect(st.dialogVisible).toBe(false)
  })

  it('行提交审批：确认后调 submitPersonalReimbursement 并刷新', async () => {
    const w = await mountPage()
    mockPage.mockClear()
    await w.vm.$.setupState.handleSubmitRow(RECORDS[0])
    await flushPromises()
    expect(mockSubmit).toHaveBeenCalledWith(1)
    expect(mockPage).toHaveBeenCalled()
  })
})
