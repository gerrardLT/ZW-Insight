/**
 * M2 账本补齐：A13 目标成本变更单表单 views/budget/change/form.vue 矩阵用例
 * （P3 批 budget-pages 已有调度级覆盖；本文件补必填/明细守卫/APPROVED 预算
 * 级联/金额计算/草稿与提交组合/编辑回显/查看只读等字段级断言）
 *
 * @matrix A13-01/A13-02/A13-03/A13-04/A13-05/A13-06/A13-07/A13-08/A13-10/A13-11/A13-12/A13-13/A13-14/A13-15
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockGetChange, mockGetChangeDetails, mockCreateChange, mockUpdateChange, mockSubmitChange,
  mockBudgetPage, mockBudgetDetails,
  mockRouteQuery, mockPush, mockWarning, mockConfirm,
} = vi.hoisted(() => ({
  mockGetChange: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockGetChangeDetails: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockCreateChange: vi.fn(async (): Promise<any> => ({ code: 200, data: { id: 999 } })),
  mockUpdateChange: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmitChange: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockBudgetPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockBudgetDetails: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockRouteQuery: {} as Record<string, string>,
  mockPush: vi.fn(),
  mockWarning: vi.fn(),
  mockConfirm: vi.fn(async () => 'confirm'),
}))

vi.mock('@/api/budget-change', () => ({
  getBudgetChange: mockGetChange,
  getBudgetChangeDetails: mockGetChangeDetails,
  createBudgetChange: mockCreateChange,
  updateBudgetChange: mockUpdateChange,
  submitBudgetChange: mockSubmitChange,
}))
vi.mock('@/api/budget', () => ({
  getBudgetPage: mockBudgetPage,
  getBudgetDetailsByBudgetId: mockBudgetDetails,
}))
vi.mock('@/components/ProjectSelector.vue', () => ({
  default: { name: 'ProjectSelector', props: ['modelValue', 'width'], render: () => null },
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: mockRouteQuery, params: {} }),
  useRouter: () => ({ push: mockPush }),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: mockWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mockConfirm },
  }
})

import ChangeForm from '@/views/budget/change/form.vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))
const formVueSrc = readFileSync(resolve(__testDir, '../views/budget/change/form.vue'), 'utf-8')

let wrapper: any = null
beforeEach(() => {
  vi.clearAllMocks()
  delete (mockRouteQuery as any).id
  delete (mockRouteQuery as any).mode
})
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

async function mountForm() {
  wrapper = mount(ChangeForm, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

/** 合法表单：必填已填 + 一条已选科目的明细 */
function fillValid(st: any) {
  st.formData.projectId = 1
  st.formData.budgetId = 66
  st.formData.changeReason = '钢材涨价'
  st.formData.details = [{
    budgetDetailId: 21, costCategory: 'MATERIAL', costSubcategory: '钢材',
    itemName: '钢材', originalAmount: 5000, adjustAmount: 500, adjustedAmount: 5500,
  }]
}

describe('budget/change/form.vue A13 矩阵', () => {
  it('A13-01 必填规则文案钉住：所属项目/变更原因', async () => {
    await mountForm()
    const rules = wrapper.vm.$.setupState.formRules
    expect(rules.projectId[0].message).toBe('请选择所属项目')
    expect(rules.changeReason[0].message).toBe('请输入变更原因')
  })

  it('A13-02 空明细拦截：「请至少添加一条变更明细」且不发创建请求', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    st.formData.projectId = 1
    st.formData.changeReason = '原因'
    mockCreateChange.mockClear()
    await st.handleSaveDraft()
    await flushPromises()
    expect(mockWarning).toHaveBeenCalledWith('请至少添加一条变更明细')
    expect(mockCreateChange).not.toHaveBeenCalled()
  })

  it('A13-03 明细未选科目拦截：「请为每一行选择原预算明细」', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    st.formData.projectId = 1
    st.formData.changeReason = '原因'
    st.handleAddDetail() // 默认行 budgetDetailId=undefined
    mockCreateChange.mockClear()
    await st.handleSaveDraft()
    await flushPromises()
    expect(mockWarning).toHaveBeenCalledWith('请为每一行选择原预算明细')
    expect(mockCreateChange).not.toHaveBeenCalled()
  })

  it('A13-04 原预算下拉仅拉 APPROVED（pageSize=100）；未选项目不发请求并清空选项', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    st.formData.budgetId = 77
    mockBudgetPage.mockClear()
    await st.handleProjectChange(5)
    expect(mockBudgetPage).toHaveBeenCalledWith({ projectId: 5, status: 'APPROVED', pageNum: 1, pageSize: 100 })
    expect(st.formData.budgetId).toBeUndefined()
    mockBudgetPage.mockClear()
    await st.handleProjectChange(undefined)
    expect(mockBudgetPage).not.toHaveBeenCalled()
  })

  it('A13-05 选原预算明细带出科目/名称/原金额（budgetTotalPrice），并同步调整后金额', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    st.budgetDetailOptions = [{ id: 21, costCategory: 'MATERIAL', costSubcategory: '钢材', itemName: '钢材', budgetTotalPrice: 5000 }]
    st.handleAddDetail()
    const row = st.formData.details[0]
    st.handleSelectBudgetDetail(row, 21)
    expect(row.costCategory).toBe('MATERIAL')
    expect(row.costSubcategory).toBe('钢材')
    expect(row.itemName).toBe('钢材')
    expect(row.originalAmount).toBe(5000)
    expect(row.adjustedAmount).toBe(5000) // 原金额 + 调整 0
  })

  it('A13-06 调整后金额=原+调整 toFixed(2)：含浮点与负调整', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    const r1 = { originalAmount: 10.1, adjustAmount: 0.2, adjustedAmount: 0 }
    st.calcAdjustedAmount(r1)
    expect(r1.adjustedAmount).toBe(10.3) // 无 toFixed 则 10.299999...
    const r2 = { originalAmount: 100, adjustAmount: -30.55, adjustedAmount: 0 }
    st.calcAdjustedAmount(r2)
    expect(r2.adjustedAmount).toBe(69.45)
  })

  it('A13-07 调整总额聚合 ΣadjustAmount（含正负混合）', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    st.formData.details = [
      { adjustAmount: 100 }, { adjustAmount: -30.5 }, { adjustAmount: 0.2 },
    ] as any[]
    expect(st.totalAdjustAmount).toBe(69.7)
  })

  it('A13-08 保存草稿：create 携带 totalAdjustAmount+details 后回列表', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    fillValid(st)
    mockCreateChange.mockClear()
    await st.handleSaveDraft()
    await flushPromises()
    expect(mockCreateChange).toHaveBeenCalledTimes(1)
    const payload = (mockCreateChange.mock.calls as any)[0][0]
    expect(payload.totalAdjustAmount).toBe(500)
    expect(payload.details).toHaveLength(1)
    expect(payload.details[0].budgetDetailId).toBe(21)
    expect(mockPush).toHaveBeenCalledWith('/budget/change')
  })

  it('A13-10 提交审批确认框取消：无 create/submit 请求', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    fillValid(st)
    mockConfirm.mockRejectedValueOnce('cancel')
    mockCreateChange.mockClear()
    await st.handleSubmitApproval().catch(() => { /* 取消即 reject */ })
    await flushPromises()
    expect(mockCreateChange).not.toHaveBeenCalled()
    expect(mockSubmitChange).not.toHaveBeenCalled()
  })

  it('A13-11 编辑回显：budgetId 在 handleProjectChange 清空后二次回填，明细完整映射', async () => {
    mockRouteQuery.id = '123'
    mockGetChange.mockResolvedValue({ code: 200, data: { id: 123, projectId: 5, budgetId: 66, changeReason: '回填测试' } })
    mockGetChangeDetails.mockResolvedValue({ code: 200, data: [
      { budgetDetailId: 21, costCategory: 'MATERIAL', costSubcategory: '钢材', itemName: '钢材', originalAmount: 100, adjustAmount: 10, adjustedAmount: 110 },
    ] })
    await mountForm()
    const st = wrapper.vm.$.setupState
    expect(mockGetChange).toHaveBeenCalledWith(123)
    expect(st.pageTitle).toBe('编辑变更单')
    expect(st.formData.projectId).toBe(5)
    expect(st.formData.budgetId).toBe(66) // 二次回填成功
    expect(st.formData.changeReason).toBe('回填测试')
    expect(st.formData.details).toHaveLength(1)
    expect(st.formData.details[0].budgetDetailId).toBe(21)
    expect(mockBudgetPage).toHaveBeenCalledWith({ projectId: 5, status: 'APPROVED', pageNum: 1, pageSize: 100 })
    expect(mockBudgetDetails).toHaveBeenCalledWith(66)
  })

  it('A13-12 查看模式只读：标题/表单 disabled/底部按钮隐藏', async () => {
    mockRouteQuery.id = '123'
    mockRouteQuery.mode = 'view'
    mockGetChange.mockResolvedValue({ code: 200, data: { id: 123, projectId: 5, budgetId: 66, changeReason: '查看' } })
    await mountForm()
    const st = wrapper.vm.$.setupState
    expect(st.isViewMode).toBe(true)
    expect(st.pageTitle).toBe('查看变更单')
    expect(formVueSrc).toContain(':disabled="isViewMode"')
    expect(formVueSrc).toContain('v-if="!isViewMode" class="form-actions"')
  })

  it('A13-13 变更原因 maxlength=500 与字数统计钉住', async () => {
    await mountForm()
    expect(formVueSrc).toContain('maxlength="500"')
    expect(formVueSrc).toContain('show-word-limit')
  })

  it('A13-14 项目无已批准预算：原预算下拉为空', async () => {
    await mountForm()
    const st = wrapper.vm.$.setupState
    mockBudgetPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    await st.handleProjectChange(8)
    expect(st.budgetOptions).toEqual([])
  })

  it('A13-15 编辑模式保存走 updateBudgetChange(id, data)', async () => {
    mockRouteQuery.id = '123'
    mockGetChange.mockResolvedValue({ code: 200, data: { id: 123, projectId: 5, budgetId: 66, changeReason: '原原因' } })
    await mountForm()
    const st = wrapper.vm.$.setupState
    fillValid(st)
    st.formData.id = 123
    mockUpdateChange.mockClear()
    await st.handleSaveDraft()
    await flushPromises()
    expect(mockUpdateChange).toHaveBeenCalledTimes(1)
    expect((mockUpdateChange.mock.calls as any)[0][0]).toBe(123)
    expect(mockCreateChange).not.toHaveBeenCalled()
    expect(mockPush).toHaveBeenCalledWith('/budget/change')
  })
})
