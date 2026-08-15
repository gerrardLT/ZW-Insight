/**
 * archive 域四个只读档案页组件测试（2026-08-15 P3 收尾批）
 *
 * index（项目档案聚合：选项目拉档案填充各 section/失败重置）+
 * office-supply / other-expense-contract / other-income-contract（只读分页查询）。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockProjectList, mockProjectArchive,
  mockOfficeSupplyArchive, mockOtherExpenseArchive, mockOtherIncomeArchive,
  mockError,
} = vi.hoisted(() => ({
  mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockProjectArchive: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockOfficeSupplyArchive: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockOtherExpenseArchive: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockOtherIncomeArchive: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockError: vi.fn(),
}))

vi.mock('@/api/project', () => ({
  getProjectList: mockProjectList,
}))
vi.mock('@/api/archive', () => ({
  getProjectArchive: mockProjectArchive,
  getOfficeSupplyArchive: mockOfficeSupplyArchive,
  getOtherExpenseContractArchive: mockOtherExpenseArchive,
  getOtherIncomeContractArchive: mockOtherIncomeArchive,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: mockError, warning: vi.fn() },
  }
})

import ArchiveIndex from '@/views/archive/index.vue'
import ArchiveOfficeSupply from '@/views/archive/office-supply.vue'
import ArchiveOtherExpense from '@/views/archive/other-expense-contract.vue'
import ArchiveOtherIncome from '@/views/archive/other-income-contract.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

describe('archive/index.vue 项目档案聚合', () => {
  async function mountPage() {
    wrapper = mount(ArchiveIndex, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载预载项目列表', async () => {
    await mountPage()
    expect(mockProjectList).toHaveBeenCalled()
  })

  it('选择项目 → 拉档案填充各 section', async () => {
    mockProjectArchive.mockResolvedValue({
      code: 200,
      data: {
        project: { id: 1, projectName: '滨江花园一期' },
        members: [{ id: 1, userName: '张三' }],
        constructionContracts: [{ id: 1 }],
        payments: [],
        receivedPayments: [],
        subcontracts: [{ id: 1 }, { id: 2 }],
        machineContracts: [],
        fundSummary: { totalIncome: 100, totalExpense: 50 },
      },
    })
    const w = await mountPage()
    const st = w.vm.$.setupState
    await st.handleProjectChange(1)
    await flushPromises()
    expect(mockProjectArchive).toHaveBeenCalledWith(1)
    expect(st.project.projectName).toBe('滨江花园一期')
    expect(st.members).toHaveLength(1)
    expect(st.subcontracts).toHaveLength(2)
    expect(st.fundSummary.totalIncome).toBe(100)
  })

  it('清空项目（id 为空）→ 重置档案', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.project = { projectName: 'X' }
    st.members = [{ id: 1 }]
    await st.handleProjectChange(undefined)
    await flushPromises()
    expect(st.project).toEqual({})
    expect(st.members).toEqual([])
  })

  it('档案接口失败 → 重置 + 错误提示（不静默）', async () => {
    mockProjectArchive.mockRejectedValue(new Error('档案服务异常'))
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.project = { projectName: 'X' }
    await st.handleProjectChange(2)
    await flushPromises()
    expect(mockError).toHaveBeenCalledWith(expect.stringContaining('加载项目档案失败'))
    expect(st.project).toEqual({})
  })

  it('formatMoney：null/空串/NaN 显 -，数值千分位两位小数', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    expect(st.formatMoney(null)).toBe('-')
    expect(st.formatMoney('')).toBe('-')
    expect(st.formatMoney('abc')).toBe('-')
    expect(st.formatMoney(1234.5)).toBe('1,234.50')
  })
})

const listPages: Array<{ name: string; comp: any; pageMock: any; label: string }> = [
  { name: 'office-supply', comp: ArchiveOfficeSupply, pageMock: mockOfficeSupplyArchive, label: '办公用品档案' },
  { name: 'other-expense-contract', comp: ArchiveOtherExpense, pageMock: mockOtherExpenseArchive, label: '其他支出合同档案' },
  { name: 'other-income-contract', comp: ArchiveOtherIncome, pageMock: mockOtherIncomeArchive, label: '其他收入合同档案' },
]

for (const p of listPages) {
  describe(`archive/${p.name}.vue ${p.label}`, () => {
    async function mountPage(records: any[] = []) {
      p.pageMock.mockResolvedValue({ code: 200, data: { records, total: records.length } })
      wrapper = mount(p.comp, { global: { plugins: [ElementPlus] } })
      await flushPromises()
      return wrapper
    }

    it('挂载加载并渲染行', async () => {
      const w = await mountPage([{ id: 1 }, { id: 2 }])
      expect(p.pageMock).toHaveBeenCalled()
      expect(w.findAll('.el-table__row')).toHaveLength(2)
    })

    it('搜索重置页码带 keyword、重置清空', async () => {
      await mountPage()
      const st = wrapper.vm.$.setupState
      st.queryParams.keyword = '钢筋'
      st.queryParams.page = 3
      p.pageMock.mockClear()
      st.handleSearch()
      await flushPromises()
      expect(st.queryParams.page).toBe(1)
      expect((p.pageMock.mock.calls as any)[0][0]).toMatchObject({ page: 1, keyword: '钢筋' })
      st.handleReset()
      await flushPromises()
      expect(st.queryParams).toEqual({ page: 1, size: 10, keyword: undefined })
    })
  })
}
