/**
 * 档案域四页 + 看板两页矩阵组件测试（账本全量补齐 M7 C-4/C-5，2026-08）
 *
 * 覆盖账本 C25 项目档案 / C26 用品档案 / C27 支出合同档案 / C28 收入合同档案 /
 * C29 首页驾驶舱 / C30 项目看板 的纯前端守卫增量。与既有测试边界（不重复）：
 *   - archive-pages.component.test.ts：聚合加载/重置/失败提示/formatMoney/三列表页 keyword
 *   - dashboard-index.component.test.ts：C-29-1/2/5/7/8/10 + 饼图/柱图绑定
 *   - project-dashboard.component.test.ts：C-30-1/2/3/4/5/9/10 + dispose
 *
 * @matrix C-25-1/C-25-4/C-25-8/C-25-9
 * @matrix C-26-1/C-26-3/C-26-6/C-26-7
 * @matrix C-27-1/C-27-3/C-27-4/C-27-7
 * @matrix C-28-1/C-28-4/C-28-7
 * @matrix C-29-11
 * @matrix C-30-6/C-30-7
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const norm = (s: string) => s.replace(/\r\n/g, '\n')
const arcIndexSrc = norm(readFileSync(resolve(__dirname, '../views/archive/index.vue'), 'utf-8'))
const arcSupplySrc = norm(readFileSync(resolve(__dirname, '../views/archive/office-supply.vue'), 'utf-8'))
const arcExpenseSrc = norm(readFileSync(resolve(__dirname, '../views/archive/other-expense-contract.vue'), 'utf-8'))
const arcIncomeSrc = norm(readFileSync(resolve(__dirname, '../views/archive/other-income-contract.vue'), 'utf-8'))

const mocks = vi.hoisted(() => {
  const instances: any[] = []
  const chartInit = vi.fn(() => {
    const c = { setOption: vi.fn(), resize: vi.fn(), dispose: vi.fn(), isDisposed: vi.fn(() => false) }
    instances.push(c)
    return c
  })
  return {
    chartInstances: instances,
    chartInit,
    mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockProjectArchive: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockSupplyArchive: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
    mockExpenseArchive: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
    mockIncomeArchive: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
    mockCompanyOverview: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockProjectBudget: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockProjectProgress: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockProjectContract: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockProjectOutput: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockError: vi.fn(),
  }
})

vi.mock('@/api/project', () => ({ getProjectList: mocks.mockProjectList }))
vi.mock('@/api/archive', () => ({
  getProjectArchive: mocks.mockProjectArchive,
  getOfficeSupplyArchive: mocks.mockSupplyArchive,
  getOtherExpenseContractArchive: mocks.mockExpenseArchive,
  getOtherIncomeContractArchive: mocks.mockIncomeArchive,
}))
vi.mock('@/api/dashboard', () => ({
  getCompanyOverview: mocks.mockCompanyOverview,
  getProjectBudget: mocks.mockProjectBudget,
  getProjectProgress: mocks.mockProjectProgress,
  getProjectContract: mocks.mockProjectContract,
  getProjectOutput: mocks.mockProjectOutput,
}))
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ userInfo: { realName: '测试管理员' } }),
}))
vi.mock('@/stores/app', () => ({
  useAppStore: () => ({ isDark: false }),
}))
// dashboard 逾期卡经 usePermission 读 finance:view：本文件不挂 pinia 且未 mock @/api/finance，
// 置 false 使逾期卡不渲染、不发起真实请求（C-29-11 仅断言统计卡）
vi.mock('@/composables/usePermission', () => ({
  usePermission: () => ({ hasPermission: () => false }),
}))
vi.mock('@/components/ProjectSelector.vue', () => ({
  default: { name: 'ProjectSelector', render: () => null },
}))
vi.mock('echarts', () => ({ init: mocks.chartInit }))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: mocks.mockError, warning: vi.fn(), info: vi.fn() },
  }
})

import ArchiveIndex from '@/views/archive/index.vue'
import ArchiveOfficeSupply from '@/views/archive/office-supply.vue'
import ArchiveOtherExpense from '@/views/archive/other-expense-contract.vue'
import DashboardIndex from '@/views/dashboard/index.vue'
import ProjectDashboard from '@/views/dashboard/project-dashboard.vue'
import ElementPlus from 'element-plus'

// 统一登记 wrapper，afterEach 卸载并清空 body，防 element-plus 弹层累积拖慢后续挂载
const liveWrappers: any[] = []
function track(w: any) { liveWrappers.push(w); return w }
afterEach(() => {
  while (liveWrappers.length) liveWrappers.pop()!.unmount()
  document.body.innerHTML = ''
})

beforeEach(() => {
  vi.clearAllMocks()
  mocks.chartInstances.length = 0
  mocks.mockCompanyOverview.mockResolvedValue({ code: 200, data: {} })
})

async function mountOf(comp: any) {
  const w = track(mount(comp, { global: { plugins: [ElementPlus] } }))
  await flushPromises()
  return w
}

describe('archive 四页只读档案增量（C25-C28）', () => {
  it('@matrix C-25-1/C-26-1/C-27-1/C-28-1 四页页首只读 alert 文案源码钉住', () => {
    expect(arcIndexSrc).toContain('项目档案为只读聚合视图')
    expect(arcSupplySrc).toContain('办公用品档案为只读聚合视图')
    expect(arcExpenseSrc).toContain('其它支出合同档案为只读聚合视图')
    expect(arcIncomeSrc).toContain('其它收入合同档案为只读聚合视图')
  })

  it('@matrix C-25-4 六 tabs 数量徽标与数组长度绑定源码钉住', () => {
    expect(arcIndexSrc).toContain(':label="`项目成员 (${members.length})`"')
    expect(arcIndexSrc).toContain(':label="`施工合同 (${constructionContracts.length})`"')
    expect(arcIndexSrc).toContain(':label="`付款记录 (${payments.length})`"')
    expect(arcIndexSrc).toContain(':label="`收款记录 (${receivedPayments.length})`"')
    expect(arcIndexSrc).toContain(':label="`分包合同 (${subcontracts.length})`"')
    expect(arcIndexSrc).toContain(':label="`机械合同 (${machineContracts.length})`"')
  })

  it('@matrix C-25-9 未选项目：空态提示且不发起档案请求', async () => {
    const w = await mountOf(ArchiveIndex)
    expect(mocks.mockProjectArchive).not.toHaveBeenCalled()
    expect(w.html()).toContain('请先选择一个项目以查看其档案')
  })

  it('@matrix C-25-8 切换项目重新加载：旧数据被整体覆盖不残留', async () => {
    mocks.mockProjectArchive
      .mockResolvedValueOnce({ code: 200, data: { project: { projectName: '项目A' }, members: [{ id: 1 }, { id: 2 }], payments: [{ id: 9 }] } })
      .mockResolvedValueOnce({ code: 200, data: { project: { projectName: '项目B' }, members: [{ id: 3 }] } })
    const w = await mountOf(ArchiveIndex)
    const st: any = w.vm.$.setupState
    await st.handleProjectChange(1)
    await flushPromises()
    expect(st.project.projectName).toBe('项目A')
    expect(st.members).toHaveLength(2)
    expect(st.payments).toHaveLength(1)
    await st.handleProjectChange(2)
    await flushPromises()
    expect(st.project.projectName).toBe('项目B')
    expect(st.members).toHaveLength(1)
    // B 响应未携带 payments → 重置为 []，不残留 A 的记录
    expect(st.payments).toEqual([])
  })

  it('@matrix C-26-3 用品档案五列绑定 + C-26-6 数值列 0 值显示 0（非 "-"）', async () => {
    for (const prop of ['supplyName', 'currentStock', 'totalInbound', 'totalIssued', 'lastInboundDate']) {
      expect(arcSupplySrc).toContain(`prop="${prop}"`)
    }
    mocks.mockSupplyArchive.mockResolvedValue({ code: 200, data: { records: [{ supplyName: 'A4纸', currentStock: 0, totalInbound: 5, totalIssued: 5, lastInboundDate: '2026-08-01' }], total: 1 } })
    const w = await mountOf(ArchiveOfficeSupply)
    const cells = w.findAll('.el-table__row td')
    expect(cells[1].text()).toBe('0')
    expect(cells[2].text()).toBe('5')
  })

  it('@matrix C-26-7/C-27-7/C-28-7 现状钉住：三只读列表页 loadData 无 catch，失败无 ElMessage 提示（盲点）', () => {
    for (const src of [arcSupplySrc, arcExpenseSrc, arcIncomeSrc]) {
      expect(src).not.toContain('catch')
      expect(src).not.toContain('ElMessage')
    }
  })

  it('@matrix C-27-3 合同档案 formatMoney：空值 "-"、0 值 0.00、千分位两位小数', async () => {
    const w = await mountOf(ArchiveOtherExpense)
    const st: any = w.vm.$.setupState
    expect(st.formatMoney(null)).toBe('-')
    expect(st.formatMoney(undefined)).toBe('-')
    expect(st.formatMoney(0)).toBe('0.00')
    expect(st.formatMoney(1234.5)).toBe('1,234.50')
  })

  it('@matrix C-27-4/C-28-4 status 原始 code 直出（prop 绑定无翻译模板）源码钉住', () => {
    expect(arcExpenseSrc).toContain('<el-table-column prop="status" label="状态" width="100" align="center" />')
    expect(arcIncomeSrc).toContain('<el-table-column prop="status" label="状态" width="100" align="center" />')
  })
})

describe('dashboard 两页看板增量（C29-C30）', () => {
  it('@matrix C-29-11 overview 空数据：项目总数 0、三金额卡 formatWan→"0" 不显示 NaN', async () => {
    const w = await mountOf(DashboardIndex)
    const st: any = w.vm.$.setupState
    expect(st.statCards[0].value).toBe(0)
    expect(st.statCards[1].value).toBe('0')
    expect(st.statCards[2].value).toBe('0')
    expect(st.statCards[3].value).toBe('0')
    expect(w.text()).not.toContain('NaN')
    expect(mocks.mockError).not.toHaveBeenCalled()
  })

  it('@matrix C-30-6 合同回款双柱 toWan：5000000→500 / 1234567→123.46', async () => {
    mocks.mockProjectBudget.mockResolvedValue({ code: 200, data: { totalBudget: 100000, usedAmount: 30000 } })
    mocks.mockProjectProgress.mockResolvedValue({ code: 200, data: { completionRate: 0.85 } })
    mocks.mockProjectContract.mockResolvedValue({ code: 200, data: { contractTotal: 5000000, receivedAmount: 1234567 } })
    mocks.mockProjectOutput.mockResolvedValue({ code: 200, data: { trend: [] } })
    const w = await mountOf(ProjectDashboard)
    const st: any = w.vm.$.setupState
    st.handleProjectChange(1001)
    await flushPromises()
    await nextTick()
    const lastOption = (c: any) => {
      const calls = c.setOption.mock.calls
      return calls.length > 0 ? calls[calls.length - 1][0] : undefined
    }
    const contractChart = mocks.chartInstances.find((c: any) => lastOption(c)?.series?.[0]?.name === '合同金额')
    expect(contractChart, '合同回款柱图应已渲染').toBeDefined()
    const opt = lastOption(contractChart)
    expect(opt.series[0].data).toEqual([500])
    expect(opt.series[1].name).toBe('回款金额')
    expect(opt.series[1].data).toEqual([123.46])
  })

  it('@matrix C-30-7 月度产值折线 toWan：100000→10 / 250000→25，X 轴为月份', async () => {
    mocks.mockProjectBudget.mockResolvedValue({ code: 200, data: {} })
    mocks.mockProjectProgress.mockResolvedValue({ code: 200, data: {} })
    mocks.mockProjectContract.mockResolvedValue({ code: 200, data: {} })
    mocks.mockProjectOutput.mockResolvedValue({ code: 200, data: { trend: [{ month: '2026-01', amount: 100000 }, { month: '2026-02', amount: 250000 }] } })
    const w = await mountOf(ProjectDashboard)
    const st: any = w.vm.$.setupState
    st.handleProjectChange(2002)
    await flushPromises()
    await nextTick()
    const lastOption = (c: any) => {
      const calls = c.setOption.mock.calls
      return calls.length > 0 ? calls[calls.length - 1][0] : undefined
    }
    const outputChart = mocks.chartInstances.find((c: any) => lastOption(c)?.series?.[0]?.type === 'line')
    expect(outputChart, '产值折线图应已渲染').toBeDefined()
    const opt = lastOption(outputChart)
    expect(opt.xAxis.data).toEqual(['2026-01', '2026-02'])
    expect(opt.series[0].data).toEqual([10, 25])
  })
})
