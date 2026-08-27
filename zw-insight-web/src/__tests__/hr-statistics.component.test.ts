/**
 * hr/statistics.vue 人事统计总览组件测试（2026-08-14 P2 补测）
 *
 * @matrix C-20 人事统计总览：单请求加载（C-20-1）、3 卡片绑定（C-20-2）、
 *   4 图表数据绑定（C-20-3~6，setOption 负载断言：部门柱/岗位饼/司龄柱/月度趋势线）、
 *   失败显式提示（C-20-7，钉住本批修复行为：loadData 补 try/catch + ElMessage.error）、
 *   resize（C-20-8）、dispose（C-20-9/10）
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

// vi.hoisted 先于模块 import 执行，不可引用顶部 import 的 helper，内联构造
const {
  mockGetHrStatisticsOverview,
  chartInstances,
  chartInit,
  mockError,
} = vi.hoisted(() => {
  const instances: any[] = []
  const makeChart = () => ({
    setOption: vi.fn(),
    resize: vi.fn(),
    dispose: vi.fn(),
    isDisposed: vi.fn(() => false),
  })
  const init = vi.fn(() => {
    const c = makeChart()
    instances.push(c)
    return c
  })
  return {
    mockGetHrStatisticsOverview: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    chartInstances: instances,
    chartInit: init,
    mockError: vi.fn(),
  }
})

vi.mock('@/api/hr', () => ({
  getHrStatisticsOverview: mockGetHrStatisticsOverview,
}))
vi.mock('echarts', () => ({ init: chartInit }))
// ElMessage partial mock 防 happy-dom DOM/定时器累积
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: mockError, warning: vi.fn(), info: vi.fn() },
  }
})

// 暗色联动引入 useAppStore 后：无 pinia 环境的组件测试统一 mock，防 getActivePinia
vi.mock('@/stores/app', () => ({
  useAppStore: () => ({ isDark: false }),
}))
import HrStatistics from '@/views/hr/statistics.vue'

let currentWrapper: any = null

const FULL_DATA = {
  totalActive: 128,
  monthlyEntry: 6,
  monthlyResign: 2,
  byDept: [{ deptName: '工程部', count: 50 }, { deptName: '财务部', count: 10 }],
  byPost: [{ postName: '项目经理', count: 8 }],
  bySeniority: [{ range: '1年以下', count: 30 }],
  monthlyTrend: [{ month: '2026-01', entryCount: 5, resignCount: 1 }],
}

async function mountPage() {
  const wrapper = mount(HrStatistics, {
    global: { plugins: [ElementPlus] },
  })
  await flushPromises()
  currentWrapper = wrapper
  return wrapper
}

function setupState(wrapper: any): any {
  return wrapper.vm.$.setupState
}

function lastOption(chart: any): any {
  const calls = chart.setOption.mock.calls
  return calls.length > 0 ? calls[calls.length - 1][0] : undefined
}

describe('hr/statistics.vue 人事统计总览（@matrix C-20）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chartInstances.length = 0
    mockGetHrStatisticsOverview.mockResolvedValue({ code: 200, data: FULL_DATA })
  })

  afterEach(() => {
    if (currentWrapper) {
      try { currentWrapper.unmount() } catch { /* 忽略卸载异常 */ }
      currentWrapper = null
    }
  })

  // @matrix C-20-1
  it('挂载发起单次 overview 请求', async () => {
    await mountPage()
    expect(mockGetHrStatisticsOverview).toHaveBeenCalledTimes(1)
  })

  // @matrix C-20-2
  it('3 卡片绑定 overview 数据', async () => {
    const wrapper = await mountPage()
    const text = wrapper.text()
    expect(text).toContain('128')
    expect(text).toContain('6')
    expect(text).toContain('2')
    expect(setupState(wrapper).overview.totalActive).toBe(128)
  })

  // @matrix C-20-3
  it('部门分布柱图绑定 byDept（xAxis 部门名 + series 人数）', async () => {
    await mountPage()
    // 渲染顺序：dept/post/seniority/trend（renderDeptChart 先 init）
    const dept = chartInstances[0]
    const opt = lastOption(dept)
    expect(opt.xAxis.data).toEqual(['工程部', '财务部'])
    expect(opt.series[0].type).toBe('bar')
    expect(opt.series[0].data).toEqual([50, 10])
  })

  // @matrix C-20-4/5/6
  it('岗位饼/司龄柱/月度趋势三图数据绑定', async () => {
    await mountPage()
    // 岗位饼图
    const post = chartInstances[1]
    const postOpt = lastOption(post)
    expect(postOpt.series[0].type).toBe('pie')
    expect(postOpt.series[0].data).toEqual([{ name: '项目经理', value: 8 }])
    // 司龄柱图
    const seniority = chartInstances[2]
    const senOpt = lastOption(seniority)
    expect(senOpt.xAxis.data).toEqual(['1年以下'])
    expect(senOpt.series[0].data).toEqual([30])
    // 月度趋势（入职/离职双线）
    const trend = chartInstances[3]
    const trendOpt = lastOption(trend)
    expect(trendOpt.xAxis.data).toEqual(['2026-01'])
    expect(trendOpt.series).toHaveLength(2)
    expect(trendOpt.series[0].name).toBe('入职')
    expect(trendOpt.series[0].data).toEqual([5])
    expect(trendOpt.series[1].name).toBe('离职')
    expect(trendOpt.series[1].data).toEqual([1])
  })

  // @matrix C-20-7（钉住本批修复：原 loadData 无 try/catch，现显式提示）
  it('接口失败 → ElMessage.error 显式提示（不静默）', async () => {
    mockGetHrStatisticsOverview.mockRejectedValue(new Error('hr down'))
    await mountPage()
    expect(mockError).toHaveBeenCalledWith(expect.stringContaining('加载人事统计数据失败'))
  })

  // @matrix C-20-8
  it('window resize → 四图 resize', async () => {
    await mountPage()
    window.dispatchEvent(new Event('resize'))
    for (const chart of chartInstances.slice(0, 4)) {
      expect(chart.resize).toHaveBeenCalled()
    }
  })

  // @matrix C-20-9/10
  it('unmount → 四图 dispose', async () => {
    const wrapper = await mountPage()
    const charts = chartInstances.slice(0, 4)
    wrapper.unmount()
    currentWrapper = null
    for (const chart of charts) {
      expect(chart.dispose).toHaveBeenCalled()
    }
  })
})
