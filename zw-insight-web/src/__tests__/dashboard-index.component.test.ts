/**
 * dashboard/index.vue 首页驾驶舱组件测试（2026-08-14 P2 补测）
 *
 * @matrix C-29 首页驾驶舱：greeting 时段（C-29-1）、4 卡片绑定（C-29-2，后端真实字段
 *   projectTotal/totalContractAmount/totalIncome/advanceFund，2026-08-17 真实浏览器实测修复翻转）、
 *   饼图 statusDistribution Map→[{name,value}] 转换钉住、柱图累计收支 toWan 钉住、
 *   接口失败显式 ElMessage.error + 空图兜底（C-29-5/7/8）、resize（C-29-10）、dispose
 *
 * 模式与 inspection-form.scheme.component.test.ts 一致：真实 Element Plus 挂载，
 * mock API 层与 echarts 模块（happy-dom 无 canvas），setupState 驱动，afterEach unmount。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

// vi.hoisted 先于模块 import 执行，不可引用顶部 import 的 helper，内联构造
const {
  mockGetCompanyOverview,
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
    mockGetCompanyOverview: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    chartInstances: instances,
    chartInit: init,
    mockError: vi.fn(),
  }
})

vi.mock('@/api/dashboard', () => ({
  getCompanyOverview: mockGetCompanyOverview,
}))
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ userInfo: { realName: '测试管理员' } }),
}))
vi.mock('echarts', () => ({ init: chartInit }))
// ElMessage 实例会向 happy-dom body 附加 DOM 并持定时器，partial mock 防累积拖慢
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: mockError, warning: vi.fn(), info: vi.fn() },
  }
})

import DashboardIndex from '@/views/dashboard/index.vue'

let currentWrapper: any = null

async function mountPage() {
  const wrapper = mount(DashboardIndex, {
    global: { plugins: [ElementPlus] },
  })
  await flushPromises()
  currentWrapper = wrapper
  return wrapper
}

function setupState(wrapper: any): any {
  return wrapper.vm.$.setupState
}

describe('dashboard/index.vue 首页驾驶舱（@matrix C-29）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chartInstances.length = 0
    mockGetCompanyOverview.mockResolvedValue({ code: 200, data: {} })
  })

  afterEach(() => {
    vi.useRealTimers()
    if (currentWrapper) {
      try { currentWrapper.unmount() } catch { /* 忽略卸载异常 */ }
      currentWrapper = null
    }
  })

  // @matrix C-29-1
  it.each([
    [3, '凌晨好'],
    [7, '早上好'],
    [10, '上午好'],
    [13, '中午好'],
    [15, '下午好'],
    [20, '晚上好'],
  ])('greeting 时段 %i 点 → %s', async (hour, expected) => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date(2026, 7, 14, hour, 30))
    const wrapper = await mountPage()
    expect(wrapper.text()).toContain(expected)
  })

  // @matrix C-29-2（后端真实字段钉住：防回归再读 projectCount 等错位字段）
  it('4 卡片绑定后端真实字段（formatWan 展示链）', async () => {
    mockGetCompanyOverview.mockResolvedValue({
      code: 200,
      data: { projectTotal: 12, totalContractAmount: 5000000, totalIncome: 1234567, advanceFund: null },
    })
    const wrapper = await mountPage()
    const text = wrapper.text()
    expect(text).toContain('项目总数')
    expect(text).toContain('12')
    // formatWan：5000000→'500.0'、1234567→'123.5'、空值→'0'
    expect(text).toContain('500.0')
    expect(text).toContain('123.5')
  })

  // 饼图：statusDistribution Map → [{name,value}] 转换钉住（修复前整个 overview 对象当数组致空白）
  it('饼图将 statusDistribution Map 转为 [{name,value}]', async () => {
    mockGetCompanyOverview.mockResolvedValue({
      code: 200,
      data: { statusDistribution: { '施工中': 3, '已竣工': 2, '报备中': 1 } },
    })
    await mountPage()
    const pie = chartInstances[0]
    const opt = pie.setOption.mock.calls[pie.setOption.mock.calls.length - 1][0]
    expect(opt.series[0].type).toBe('pie')
    expect(opt.series[0].data).toEqual([
      { name: '施工中', value: 3 },
      { name: '已竣工', value: 2 },
      { name: '报备中', value: 1 },
    ])
  })

  // 柱图：累计收入/支出 toWan 钉住（后端无月度端点，不再伪造 months/income/expense）
  it('柱图展示累计收入/支出（toWan 转换）', async () => {
    mockGetCompanyOverview.mockResolvedValue({
      code: 200,
      data: { totalIncome: 59900000, totalExpense: 30000000 },
    })
    await mountPage()
    const bar = chartInstances[1]
    const opt = bar.setOption.mock.calls[bar.setOption.mock.calls.length - 1][0]
    expect(opt.xAxis.data).toEqual(['收入', '支出'])
    expect(opt.series[0].data.map((d: any) => d.value)).toEqual([5990, 3000])
  })

  // @matrix C-29-5（钉住已修复行为：接口失败显式提示，不静默）
  it('loadStats 失败 → ElMessage.error 显式提示且 stats 置空', async () => {
    mockGetCompanyOverview.mockRejectedValue(new Error('stats down'))
    const wrapper = await mountPage()
    expect(mockError).toHaveBeenCalledWith(expect.stringContaining('加载统计数据失败'))
    expect(setupState(wrapper).stats).toEqual({})
  })

  // @matrix C-29-7
  it('饼图接口失败 → 显式提示 + 空图兜底 setOption', async () => {
    // 首次调用（loadStats）成功，仅饼图所用调用失败：第一次 resolve、其后 reject
    mockGetCompanyOverview
      .mockResolvedValueOnce({ code: 200, data: {} })
      .mockRejectedValue(new Error('pie down'))
    await mountPage()
    expect(mockError).toHaveBeenCalledWith(expect.stringContaining('加载项目状态分布失败'))
    const pie = chartInstances[0]
    expect(pie).toBeDefined()
    const opt = pie.setOption.mock.calls[pie.setOption.mock.calls.length - 1][0]
    expect(opt.series[0].type).toBe('pie')
    // 空图兜底：草稿/施工中/已竣工 value 全 0
    expect(opt.series[0].data.every((d: any) => d.value === 0)).toBe(true)
  })

  // @matrix C-29-8
  it('柱图接口失败 → 显式提示 + 空图兜底 setOption', async () => {
    // loadStats/饼图先成功，柱图调用失败
    mockGetCompanyOverview
      .mockResolvedValueOnce({ code: 200, data: {} })
      .mockResolvedValueOnce({ code: 200, data: {} })
      .mockRejectedValue(new Error('bar down'))
    await mountPage()
    expect(mockError).toHaveBeenCalledWith(expect.stringContaining('加载收支对比失败'))
    const bar = chartInstances[1]
    expect(bar).toBeDefined()
    const opt = bar.setOption.mock.calls[bar.setOption.mock.calls.length - 1][0]
    expect(opt.series).toHaveLength(1)
    expect(opt.series[0].data).toEqual([])
  })

  // @matrix C-29-10
  it('window resize → 两图 resize', async () => {
    await mountPage()
    window.dispatchEvent(new Event('resize'))
    expect(chartInstances[0].resize).toHaveBeenCalled()
    expect(chartInstances[1].resize).toHaveBeenCalled()
  })

  it('unmount → 两图 dispose', async () => {
    const wrapper = await mountPage()
    const [pie, bar] = chartInstances
    wrapper.unmount()
    currentWrapper = null
    expect(pie.dispose).toHaveBeenCalled()
    expect(bar.dispose).toHaveBeenCalled()
  })
})
