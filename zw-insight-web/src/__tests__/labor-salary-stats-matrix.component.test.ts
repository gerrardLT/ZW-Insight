/**
 * M4 账本补齐：B-3 劳务域薪资统计页矩阵用例
 * views/labor/salary/stats.vue
 *
 * 既有覆盖（不重复）：盲点 9 工人姓名本地过滤见 material-salary-defects.component.test.ts。
 *
 * @matrix B-19-1/B-19-2/B-19-3/B-19-4/B-19-5/B-19-6/B-19-7/B-19-9/B-19-10/B-19-11/B-19-12
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockStats, mockDetail, mockCompare, mockExport, mockWarning, mockSuccess, mockError } = vi.hoisted(() => ({
  mockStats: vi.fn(async (): Promise<any> => ({ code: 200, data: null })),
  mockDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCompare: vi.fn(async (): Promise<any> => ({ code: 200, data: null })),
  mockExport: vi.fn(async (): Promise<any> => new Blob()),
  mockWarning: vi.fn(),
  mockSuccess: vi.fn(),
  mockError: vi.fn(),
}))

vi.mock('@/api/labor', () => ({
  getSalaryStats: mockStats,
  getSalaryDetail: mockDetail,
  getSalaryCompare: mockCompare,
  exportSalaryExcel: mockExport,
}))
vi.mock('@/api/project', () => ({
  getProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: mockSuccess, error: mockError, warning: mockWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import SalaryStats from '@/views/labor/salary/stats.vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))
const statsSrc = readFileSync(resolve(__testDir, '../views/labor/salary/stats.vue'), 'utf-8')

const stubs = {
  ProjectSelector: { template: '<div class="stub-project-selector" />', props: ['modelValue'] },
}

const teamListFixture = [
  { teamId: 1, teamName: '木工一班', leaderName: '张', headCount: 10, totalPayable: 100, totalDeduction: 0, totalActual: 100, orderType: 'FIXED' },
  { teamId: 2, teamName: '临工二班', leaderName: '李', headCount: 5, totalPayable: 50, totalDeduction: 0, totalActual: 50, orderType: 'TEMPORARY' },
]

let wrapper: any = null
beforeEach(() => { vi.clearAllMocks() })
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

async function mountStats() {
  wrapper = mount(SalaryStats, { global: { plugins: [ElementPlus], stubs } })
  await flushPromises()
  return wrapper
}

describe('labor/salary/stats.vue B19 矩阵', () => {
  it('B-19-1 查询前置拦截：无项目→「请选择项目」，无月份→「请选择月份」，均不发请求', async () => {
    const w = await mountStats()
    const st = w.vm.$.setupState
    await st.handleSearch()
    expect(mockWarning).toHaveBeenCalledWith('请选择项目')
    st.queryParams.projectId = 1
    await st.handleSearch()
    expect(mockWarning).toHaveBeenCalledWith('请选择月份')
    expect(mockStats).not.toHaveBeenCalled()
  })

  it('B-19-2 getRateClass：正→rate-up、负→rate-down、0/null→空串', async () => {
    const w = await mountStats()
    const st = w.vm.$.setupState
    expect(st.getRateClass(12.5)).toBe('rate-up')
    expect(st.getRateClass(-3.2)).toBe('rate-down')
    expect(st.getRateClass(0)).toBe('')
    expect(st.getRateClass(null)).toBe('')
  })

  it('B-19-3 Tab 过滤：ALL 全量 / FIXED 仅自有劳务 / TEMPORARY 仅零星用工', async () => {
    mockStats.mockResolvedValue({ code: 200, data: { teamCount: 2, totalHeadCount: 15, totalPayable: 150, totalDeduction: 0, totalActual: 150, fixedPayable: 100, temporaryPayable: 50, teamList: teamListFixture } })
    const w = await mountStats()
    const st = w.vm.$.setupState
    st.queryParams.projectId = 1
    st.queryParams.month = '2026-07'
    await st.handleSearch()
    await flushPromises()
    expect(st.filteredTeamList).toHaveLength(2)
    st.activeTab = 'FIXED'
    expect(st.filteredTeamList.map((t: any) => t.teamName)).toEqual(['木工一班'])
    st.activeTab = 'TEMPORARY'
    expect(st.filteredTeamList.map((t: any) => t.teamName)).toEqual(['临工二班'])
  })

  it('B-19-4 班组名称本地 includes 过滤（与 Tab 组合生效）', async () => {
    mockStats.mockResolvedValue({ code: 200, data: { teamList: teamListFixture } })
    const w = await mountStats()
    const st = w.vm.$.setupState
    st.queryParams.projectId = 1
    st.queryParams.month = '2026-07'
    await st.handleSearch()
    await flushPromises()
    st.queryParams.teamName = '一班'
    expect(st.filteredTeamList.map((t: any) => t.teamId)).toEqual([1])
    st.activeTab = 'TEMPORARY'
    expect(st.filteredTeamList).toHaveLength(0)
  })

  it('B-19-5 formatAmount：null/undefined→0.00，千分位格式化 1234567.8→1,234,567.80', async () => {
    const w = await mountStats()
    const st = w.vm.$.setupState
    expect(st.formatAmount(null)).toBe('0.00')
    expect(st.formatAmount(undefined)).toBe('0.00')
    expect(st.formatAmount(1234567.8)).toBe('1,234,567.80')
    expect(st.formatAmount(0)).toBe('0.00')
  })

  it('B-19-6 handleReset 清空查询条件与全部结果数据', async () => {
    mockStats.mockResolvedValue({ code: 200, data: { teamList: teamListFixture } })
    const w = await mountStats()
    const st = w.vm.$.setupState
    st.queryParams.projectId = 1
    st.queryParams.month = '2026-07'
    await st.handleSearch()
    await flushPromises()
    expect(st.statsData).toBeTruthy()
    st.handleReset()
    expect(st.queryParams).toEqual({ projectId: undefined, month: '', teamName: '', workerName: '' })
    expect(st.statsData).toBeNull()
    expect(st.compareData).toBeNull()
    expect(st.searched).toBe(false)
  })

  it('B-19-7 handleProjectChange 清空已加载数据与查询标记', async () => {
    mockStats.mockResolvedValue({ code: 200, data: { teamList: teamListFixture } })
    const w = await mountStats()
    const st = w.vm.$.setupState
    st.queryParams.projectId = 1
    st.queryParams.month = '2026-07'
    await st.handleSearch()
    await flushPromises()
    st.handleProjectChange()
    expect(st.statsData).toBeNull()
    expect(st.compareData).toBeNull()
    expect(st.searched).toBe(false)
  })

  it('B-19-9 导出按钮未查询时禁用、文件名模板钉住（源码钉住）', async () => {
    await mountStats()
    expect(statsSrc).toContain(':disabled="!statsData"')
    expect(statsSrc).toContain('`薪资统计_${queryParams.value.month}.xlsx`')
  })

  it('B-19-10 导出函数级守卫：未选项目/月份→「请先选择项目和月份」，不调导出接口', async () => {
    const w = await mountStats()
    const st = w.vm.$.setupState
    await st.handleExport()
    expect(mockWarning).toHaveBeenCalledWith('请先选择项目和月份')
    expect(mockExport).not.toHaveBeenCalled()
  })

  it('B-19-11 明细分页阈值：_detailTotal>10 才渲染分页、明细 size=10（源码钉住）', async () => {
    await mountStats()
    expect(statsSrc).toContain('v-if="row._detailTotal > 10"')
    expect(statsSrc).toContain('size: 10')
  })

  it('B-19-12 空数据文案钉住：「该月份暂无已审批的薪资数据」', async () => {
    await mountStats()
    expect(statsSrc).toContain('该月份暂无已审批的薪资数据')
  })
})
