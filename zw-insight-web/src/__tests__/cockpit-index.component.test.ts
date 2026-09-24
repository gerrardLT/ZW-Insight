/**
 * cockpit/index.vue 经营驾驶舱首页组件测试（V2026_65 第三期 P1-B）
 *
 * @matrix 驾驶舱首页：
 *   - §4 数字卡四要素渲染（当前值 / 与上期变化 / 目标·口径 / 风险状态）
 *   - 环比无基期（NO_BASELINE）时显示“—”，不得用 0 冒充“无变化”
 *   - 目标利润率取后端配置下发值，钉住前端不再硬编码“≥5%”
 *   - §14 全局筛选器：公司/项目联动、快捷筛选传参、无数据源维度置灰
 *   - §16.1 点击数字 → 项目构成弹窗；公司级指标（资金缺口）如实告知无构成
 *   - §5.1 利润趋势点击月份 → 调 profit-attribution 弹出归因
 *
 * 模式与 dashboard-index.component.test.ts 一致：真实 Element Plus 挂载，
 * mock API 层与 echarts 模块（happy-dom 无 canvas），afterEach unmount。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockOverview, mockFilterOptions, mockProfitTrend, mockAttribution,
  mockHealth, mockRiskSummary, mockRiskPage, mockScan,
  mockRollingPage, mockByDays,
  chartInstances, chartInit, mockInfo, mockError, mockPush,
} = vi.hoisted(() => {
  const instances: any[] = []
  const makeChart = () => {
    const handlers: Record<string, any[]> = {}
    return {
      setOption: vi.fn(),
      resize: vi.fn(),
      dispose: vi.fn(),
      isDisposed: vi.fn(() => false),
      off: vi.fn((evt: string) => { handlers[evt] = [] }),
      on: vi.fn((evt: string, fn: any) => {
        handlers[evt] = handlers[evt] || []
        handlers[evt].push(fn)
      }),
      /** 测试辅助：模拟图表点击（ECharts 事件） */
      emit: (evt: string, params: any) => (handlers[evt] || []).forEach(fn => fn(params)),
      handlers,
    }
  }
  const init = vi.fn(() => {
    const c = makeChart()
    instances.push(c)
    return c
  })
  return {
    mockOverview: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockFilterOptions: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockProfitTrend: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockAttribution: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockHealth: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockRiskSummary: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockRiskPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
    mockScan: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockRollingPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [] } })),
    mockByDays: vi.fn(async (): Promise<any> => ({ code: 200, data: null })),
    chartInstances: instances,
    chartInit: init,
    mockInfo: vi.fn(),
    mockError: vi.fn(),
    mockPush: vi.fn(),
  }
})

vi.mock('@/api/cockpit', () => ({
  getCockpitOverview: mockOverview,
  getCockpitFilterOptions: mockFilterOptions,
  getCockpitProfitTrend: mockProfitTrend,
  getProfitAttribution: mockAttribution,
  getProjectHealth: mockHealth,
  getRiskSummary: mockRiskSummary,
  getRiskPage: mockRiskPage,
  scanRisks: mockScan,
}))
vi.mock('@/api/fund-plan', () => ({
  getRollingForecastPage: mockRollingPage,
  getForecastByDays: mockByDays,
}))
vi.mock('@/stores/app', async () => {
  const { reactive } = await import('vue')
  const state = reactive({ isDark: false })
  return { useAppStore: () => state }
})
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mockPush }) }))
vi.mock('echarts', () => ({ init: chartInit }))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: mockError, warning: vi.fn(), info: mockInfo },
  }
})

import CockpitIndex from '@/views/cockpit/index.vue'

/** 公司级完整口径的总览样本（数值取自 UI 原型 §3 的示例量级） */
function overviewData(overrides: Record<string, any> = {}) {
  return {
    contractIncome: 280000000,
    forecastTotalCost: 235000000,
    forecastProfit: 45000000,
    forecastProfitRate: 0.161,
    realizedProfit: 1000000,
    cumulativeReceived: 120000000,
    cumulativePaid: 90000000,
    receivableOutstanding: 27000000,
    accountBalance: 5000000,
    availableFund: 8000000,
    gap90Days: 12000000,
    gapBasis: 'COMPANY_SNAPSHOT_WITH_ACCOUNT_BALANCE',
    currentMonthCashNeed: 3000000,
    threeMonthCashNeed: 9000000,
    changes: {
      basis: 'VS_LAST_MONTH_SNAPSHOT',
      baseMonth: '2026-08',
      contractIncome: 5000000,
      forecastTotalCost: 2000000,
      forecastProfit: 3000000,
      receivedThisMonth: 4000000,
    },
    targets: { forecastProfitRate: 0.08, basis: 'CONFIG:cockpit.target-profit-rate' },
    scope: { ownerCompanyId: null, projectId: null, projectCount: 4, filtered: false },
    ...overrides,
  }
}

function filterOptionsData() {
  return {
    companies: [{ companyId: 10, companyName: '华东建设' }],
    projects: [
      { projectId: 1, projectName: '滨江花园一期', ownerCompanyId: 10 },
      { projectId: 2, projectName: '城南市政道路', ownerCompanyId: null },
    ],
    quickFilters: [
      { code: 'ALL', label: '全部项目' }, { code: 'HIGH_RISK', label: '高风险' },
      { code: 'LOSS', label: '亏损' }, { code: 'FUND_TIGHT', label: '资金紧张' },
      { code: 'PROFIT_DOWN', label: '利润下降' }, { code: 'MONTH_ABNORMAL', label: '本月异常' },
    ],
    unsupportedDimensions: [
      { code: 'REGION', reason: 'biz_project 无区域列' },
      { code: 'PROJECT_MANAGER', reason: '全仓无项目经理字段' },
    ],
  }
}

async function mountPage(overview: Record<string, any> = overviewData()) {
  mockOverview.mockResolvedValue({ code: 200, data: overview })
  mockFilterOptions.mockResolvedValue({ code: 200, data: filterOptionsData() })
  mockProfitTrend.mockResolvedValue({
    code: 200,
    data: {
      snapshots: [{ snapshotMonth: '2026-09', forecastProfit: 45000000, categoryBreakdown: null }],
      realized: { year: 2026, months: [] },
    },
  })
  mockHealth.mockResolvedValue({ code: 200, data: [] })
  mockRiskSummary.mockResolvedValue({
    code: 200,
    data: { redCount: 0, yellowCount: 0, infoCount: 0, activeTotal: 0 },
  })
  mockRiskPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
  mockRollingPage.mockResolvedValue({ code: 200, data: { records: [] } })

  const wrapper = mount(CockpitIndex, {
    global: {
      plugins: [ElementPlus],
      stubs: { IconArrowRight: true },
      mocks: { $router: { push: mockPush } },
    },
  })
  await flushPromises()
  return wrapper
}

describe('cockpit/index.vue 经营驾驶舱首页（§4 四要素 / §14 筛选 / §16.1 下钻 / §5.1 归因）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chartInstances.length = 0
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('D-01 数字卡四要素齐备：当前值 + 环比 + 目标/口径 + 风险状态（8 张卡）', async () => {
    const wrapper = await mountPage()
    const cards = wrapper.findAll('.metric-card')
    expect(cards.length).toBe(8)

    const income = cards[0]
    // ① 当前值（formatWan 1 位小数 + 万）
    expect(income.find('.metric-value').text()).toContain('28000.0 万')
    // ② 环比：↑ 500.0 万，百分比以上期值（2.75 亿）为基数 = +1.8%
    expect(income.find('.metric-change').text()).toContain('较上期 ↑ 500.0 万')
    expect(income.find('.metric-change').text()).toContain('+1.8%')
    // ③ 目标/口径行存在
    expect(income.find('.metric-target').text().length).toBeGreaterThan(0)
    // ④ 风险状态行存在（无判定的卡显示“—”，不伪造“正常”）
    expect(income.find('.metric-status').text()).toBe('—')

    // 四要素行在每张卡都渲染（结构一致，不缺行）
    cards.forEach(c => {
      expect(c.find('.metric-value').exists()).toBe(true)
      expect(c.find('.metric-change').exists()).toBe(true)
      expect(c.find('.metric-target').exists()).toBe(true)
      expect(c.find('.metric-status').exists()).toBe(true)
    })
    wrapper.unmount()
  })

  it('D-02 环比无基期（NO_BASELINE + delta=null）显示“—”，不得用 0 冒充“无变化”', async () => {
    const wrapper = await mountPage(overviewData({
      changes: {
        basis: 'NO_BASELINE', baseMonth: null,
        contractIncome: null, forecastTotalCost: null, forecastProfit: null,
        receivedThisMonth: 0,
      },
    }))
    const text = wrapper.findAll('.metric-change').map(e => e.text()).join('|')
    expect(text).toContain('较上期 —')
    // 关键反向断言：不得出现 0.0 万的“伪无变化”
    expect(text).not.toContain('↑ 0.0 万')
    expect(text).not.toContain('↓ 0.0 万')
    wrapper.unmount()
  })

  it('D-03 目标利润率取后端配置下发值（不再硬编码 ≥5%）', async () => {
    const wrapper = await mountPage(overviewData({
      targets: { forecastProfitRate: 0.18, basis: 'CONFIG:cockpit.target-profit-rate' },
    }))
    const text = wrapper.findAll('.metric-card').map(c => c.text()).join('|')
    expect(text).toContain('目标 ≥ 18.0%')
    expect(text).not.toContain('目标 ≥ 5%')
    wrapper.unmount()
  })

  it('D-04 风险状态由真实判定产生：缺口为正→需安排资金；预计亏损→亏损态', async () => {
    const wrapper = await mountPage(overviewData({
      forecastProfit: -3200000, forecastProfitRate: -0.04, gap90Days: 5800000,
    }))
    const text = wrapper.findAll('.metric-card').map(c => c.text()).join('|')
    expect(text).toContain('🔴 预计亏损')
    expect(text).toContain('🔴 需安排资金')
    // 亏损卡带告警样式（metric-alert）
    expect(wrapper.findAll('.metric-card.metric-alert').length).toBeGreaterThan(0)
    wrapper.unmount()
  })

  it('D-05 §14 筛选器：6 个快捷筛选由接口下发渲染，无数据源维度置灰且说明原因', async () => {
    const wrapper = await mountPage()
    const quickButtons = wrapper.findAll('.quick-filter-bar .el-radio-button')
    expect(quickButtons.length).toBe(6)
    expect(wrapper.find('.quick-filter-bar').text()).toContain('高风险')
    expect(wrapper.find('.quick-filter-bar').text()).toContain('本月异常')

    // 区域 / 项目经理：渲染为 disabled 下拉（不做选了不生效的假下拉）
    const disabledSelects = wrapper.findAll('.header-actions .el-select')
      .filter(s => s.classes().join(' ').includes('is-disabled')
        || s.find('.is-disabled').exists() || s.html().includes('is-disabled'))
    expect(disabledSelects.length).toBeGreaterThanOrEqual(2)
    wrapper.unmount()
  })

  it('D-06 §14 快捷筛选切换时以 quickFilter 调用 project-health（ALL 不传参）', async () => {
    const wrapper = await mountPage()
    const vm: any = wrapper.vm
    mockHealth.mockClear()

    vm.quickFilter = 'LOSS'
    await vm.loadHealth()
    expect(mockHealth).toHaveBeenCalledWith(
      expect.objectContaining({ quickFilter: 'LOSS' }))

    mockHealth.mockClear()
    vm.quickFilter = 'ALL'
    await vm.loadHealth()
    // ALL 等同不筛选：不传 quickFilter（后端对非法值报 400，避免误传）
    // mock 定义为无参函数，mock.calls 被推为 [] 元组，故经 any 取实参
    const arg: any = (mockHealth as any).mock.calls[0]?.[0] || {}
    expect(arg.quickFilter).toBeUndefined()
    wrapper.unmount()
  })

  it('D-07 §14 公司筛选切换时清空已选项目，并把 ownerCompanyId 传给 overview', async () => {
    const wrapper = await mountPage()
    const vm: any = wrapper.vm
    vm.projectFilter = 2
    mockOverview.mockClear()

    vm.companyFilter = 10
    await vm.onCompanyChange()

    expect(vm.projectFilter).toBeUndefined()
    expect(mockOverview).toHaveBeenCalledWith(
      expect.objectContaining({ ownerCompanyId: 10, projectId: undefined }))
    wrapper.unmount()
  })

  it('D-08 项目级口径提示：gapBasis 为项目快照时必须显式告知未含账户余额', async () => {
    const wrapper = await mountPage(overviewData({
      gapBasis: 'PROJECT_SNAPSHOT_WITHOUT_ACCOUNT_BALANCE',
      scope: { ownerCompanyId: 10, projectId: null, projectCount: 3, filtered: true },
    }))
    const bar = wrapper.find('.quick-filter-bar').text()
    expect(bar).toContain('未含公司账户余额')
    expect(bar).toContain('当前统计 3 个项目')
    wrapper.unmount()
  })

  it('D-09 §16.1 点击数字卡 → 拉取项目构成并展示合计（不带快捷筛选）', async () => {
    const wrapper = await mountPage()
    const vm: any = wrapper.vm
    mockHealth.mockResolvedValueOnce({
      code: 200,
      data: [
        { projectId: 1, projectName: '滨江花园一期', forecastProfit: 30000000 },
        { projectId: 2, projectName: '城南市政道路', forecastProfit: 15000000 },
      ],
    })

    await vm.openCardDrill({ label: '预计利润', drillField: 'forecastProfit', drillIsRate: false })
    await flushPromises()

    expect(mockHealth).toHaveBeenCalledWith(expect.not.objectContaining({ quickFilter: expect.anything() }))
    expect(vm.drillRows.length).toBe(2)
    // 降序排列，合计 = 4500 万（与卡片值一致 → 无差额提示）
    expect(vm.drillRows[0].projectName).toBe('滨江花园一期')
    expect(vm.drillTotal).toBe(45000000)
    expect(vm.drillMismatch).toBeNull()
    expect(vm.drillVisible).toBe(true)
    wrapper.unmount()
  })

  it('D-10 §16.1 构成合计与卡片值不一致时给出差额提示（不静默展示对不上的账）', async () => {
    const wrapper = await mountPage()
    const vm: any = wrapper.vm
    mockHealth.mockResolvedValueOnce({
      code: 200,
      data: [{ projectId: 1, projectName: '仅一个项目', forecastProfit: 40000000 }],
    })

    await vm.openCardDrill({ label: '预计利润', drillField: 'forecastProfit', drillIsRate: false })
    await flushPromises()

    // 卡片值 4500 万 vs 构成合计 4000 万 → 差额 −500 万，必须提示
    expect(vm.drillMismatch).toBe(-5000000)
    wrapper.unmount()
  })

  it('D-11 §16.1 公司级指标（资金缺口）点击时如实告知无项目构成，不硬摊也不开弹窗', async () => {
    const wrapper = await mountPage()
    const vm: any = wrapper.vm
    mockHealth.mockClear()

    await vm.openCardDrill({
      label: '90天资金缺口', drillField: undefined,
      drillNote: '资金缺口为公司级现金流口径，无逐项目构成',
    })
    await flushPromises()

    expect(mockInfo).toHaveBeenCalled()
    expect(mockHealth).not.toHaveBeenCalled()
    expect(vm.drillVisible).toBe(false)
    wrapper.unmount()
  })

  it('D-12 §5.1 利润趋势点击月份 → 调 profit-attribution 并展示归因（无上期基线时如实告知）', async () => {
    const wrapper = await mountPage()
    const vm: any = wrapper.vm
    mockAttribution.mockResolvedValueOnce({
      code: 200,
      data: {
        month: '2026-09', forecastProfit: 45000000, profitDelta: -3200000, hasBaseline: true,
        items: [{ category: 'MATERIAL', forecast: 120000000, prevForecast: 116800000, delta: -3200000 }],
      },
    })

    // 模拟图表点击（profitChart 为第一个初始化的实例）
    const profitChart = chartInstances[0]
    expect(profitChart).toBeTruthy()
    profitChart.emit('click', { name: '2026-09' })
    await flushPromises()

    expect(mockAttribution).toHaveBeenCalledWith(expect.objectContaining({ month: '2026-09' }))
    expect(vm.attributionVisible).toBe(true)
    expect(vm.attribution.items.length).toBe(1)
    expect(vm.attribution.hasBaseline).toBe(true)

    // 无上期基线：hasBaseline=false 时前端如实告知“无法归因”，不拿本期数充当变化量
    mockAttribution.mockResolvedValueOnce({
      code: 200,
      data: { month: '2026-04', forecastProfit: 100, profitDelta: null, hasBaseline: false, items: [] },
    })
    await vm.openAttribution('2026-04')
    await flushPromises()
    expect(vm.attribution.hasBaseline).toBe(false)
    expect(vm.attribution.items.length).toBe(0)
    wrapper.unmount()
  })

  it('D-13 接口失败显式提示，不静默降级为空数据', async () => {
    mockOverview.mockRejectedValue(new Error('500 服务异常'))
    mockFilterOptions.mockResolvedValue({ code: 200, data: filterOptionsData() })
    mockProfitTrend.mockResolvedValue({ code: 200, data: { snapshots: [], realized: null } })
    mockHealth.mockResolvedValue({ code: 200, data: [] })
    mockRiskSummary.mockResolvedValue({ code: 200, data: { activeTotal: 1 } })
    mockRiskPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    mockRollingPage.mockResolvedValue({ code: 200, data: { records: [] } })

    const wrapper = mount(CockpitIndex, {
      global: {
        plugins: [ElementPlus],
        stubs: { IconArrowRight: true },
        mocks: { $router: { push: mockPush } },
      },
    })
    await flushPromises()

    expect(mockError).toHaveBeenCalled()
    const msg = mockError.mock.calls.map(c => String(c[0])).join('|')
    expect(msg).toContain('加载经营总览失败')
    wrapper.unmount()
  })
})
