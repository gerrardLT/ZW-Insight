/**
 * dashboard/project-dashboard.vue 项目看板组件测试（2026-08-14 P2 补测）
 *
 * @matrix C-30 项目看板：四维并行加载（C-30-1）、单维失败隔离 el-alert（C-30-2）、
 *   isEmpty 空态判定（C-30-3）、剩余预算非负（C-30-4，setOption 负载断言）、
 *   完成率裁剪（C-30-5）、toWan 换算（C-30-6）、resize 防抖 300ms（C-30-9）、
 *   未选项目不发请求（C-30-10）、dispose
 *
 * 数值变换语义在 chart-format.pure.test.ts 覆盖纯函数层，本文件钉住组件绑定链。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { nextTick } from 'vue'

// vi.hoisted 先于模块 import 执行，不可引用顶部 import 的 helper，内联构造
const {
  mockGetProjectBudget,
  mockGetProjectProgress,
  mockGetProjectContract,
  mockGetProjectOutput,
  chartInstances,
  chartInit,
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
    mockGetProjectBudget: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockGetProjectProgress: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockGetProjectContract: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    mockGetProjectOutput: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
    chartInstances: instances,
    chartInit: init,
  }
})

vi.mock('@/api/dashboard', () => ({
  getProjectBudget: mockGetProjectBudget,
  getProjectProgress: mockGetProjectProgress,
  getProjectContract: mockGetProjectContract,
  getProjectOutput: mockGetProjectOutput,
}))
vi.mock('@/components/ProjectSelector.vue', () => ({
  default: { name: 'ProjectSelector', render: () => null },
}))
vi.mock('echarts', () => ({ init: chartInit }))

import ProjectDashboard from '@/views/dashboard/project-dashboard.vue'

let currentWrapper: any = null

async function mountPage() {
  const wrapper = mount(ProjectDashboard, {
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

describe('project-dashboard.vue 项目看板（@matrix C-30）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chartInstances.length = 0
    mockGetProjectBudget.mockResolvedValue({ code: 200, data: { totalBudget: 100000, usedAmount: 30000 } })
    mockGetProjectProgress.mockResolvedValue({ code: 200, data: { completionRate: 0.85 } })
    mockGetProjectContract.mockResolvedValue({ code: 200, data: { contractTotal: 5000000, receivedAmount: 1234567 } })
    mockGetProjectOutput.mockResolvedValue({ code: 200, data: { trend: [{ month: '2026-01', amount: 100000 }] } })
  })

  afterEach(() => {
    vi.useRealTimers()
    if (currentWrapper) {
      try { currentWrapper.unmount() } catch { /* 忽略卸载异常 */ }
      currentWrapper = null
    }
  })

  // @matrix C-30-1
  it('选择项目后四维并行加载', async () => {
    const wrapper = await mountPage()
    setupState(wrapper).handleProjectChange(1001)
    await flushPromises()
    expect(mockGetProjectBudget).toHaveBeenCalledWith(1001)
    expect(mockGetProjectProgress).toHaveBeenCalledWith(1001)
    expect(mockGetProjectContract).toHaveBeenCalledWith(1001)
    expect(mockGetProjectOutput).toHaveBeenCalledWith(1001)
  })

  // @matrix C-30-10
  it('未选项目（undefined）不发请求', async () => {
    const wrapper = await mountPage()
    setupState(wrapper).handleProjectChange(undefined)
    await flushPromises()
    expect(mockGetProjectBudget).not.toHaveBeenCalled()
    expect(mockGetProjectProgress).not.toHaveBeenCalled()
    expect(mockGetProjectContract).not.toHaveBeenCalled()
    expect(mockGetProjectOutput).not.toHaveBeenCalled()
  })

  // @matrix C-30-2
  it('单维失败隔离：budget 报错不影响其他维度数据', async () => {
    mockGetProjectBudget.mockRejectedValue(new Error('budget down'))
    const wrapper = await mountPage()
    setupState(wrapper).handleProjectChange(1001)
    await flushPromises()
    const st = setupState(wrapper)
    expect(st.budget.error, '失败维度应有错误信息').toContain('budget down')
    expect(st.budget.data).toBeNull()
    // 其他维度不受影响
    expect(st.progress.error).toBe('')
    expect(st.progress.data).toEqual({ completionRate: 0.85 })
    expect(st.contract.data).toBeTruthy()
    expect(st.output.data).toBeTruthy()
  })

  // @matrix C-30-3
  it('isEmpty 空态判定（null/空数组/空对象）', async () => {
    const wrapper = await mountPage()
    const st = setupState(wrapper)
    expect(st.isEmpty(null)).toBe(true)
    expect(st.isEmpty(undefined)).toBe(true)
    expect(st.isEmpty([])).toBe(true)
    expect(st.isEmpty({})).toBe(true)
    expect(st.isEmpty({ a: 1 })).toBe(false)
    expect(st.isEmpty([1])).toBe(false)
  })

  // @matrix C-30-4
  it('超预算时剩余预算按 0 渲染（setOption 负载断言）', async () => {
    mockGetProjectBudget.mockResolvedValue({ code: 200, data: { totalBudget: 100, usedAmount: 150 } })
    const wrapper = await mountPage()
    setupState(wrapper).handleProjectChange(1001)
    await flushPromises()
    await nextTick()
    const budgetChart = chartInstances.find((c) => lastOption(c)?.series?.[0]?.type === 'pie')
    expect(budgetChart, '预算饼图应已渲染').toBeDefined()
    const data = lastOption(budgetChart).series[0].data
    const remaining = data.find((d: any) => d.name === '剩余预算')
    expect(remaining.value, '超预算剩余应为 0 不为负').toBe(0)
    const used = data.find((d: any) => d.name === '已执行金额')
    expect(used.value).toBe(150)
  })

  // @matrix C-30-5
  it('完成率越界裁剪：1.2→100，-0.3→0（仪表盘负载断言）', async () => {
    mockGetProjectProgress.mockResolvedValueOnce({ code: 200, data: { completionRate: 1.2 } })
    const wrapper = await mountPage()
    setupState(wrapper).handleProjectChange(1001)
    await flushPromises()
    await nextTick()
    const gauge = chartInstances.find((c) => lastOption(c)?.series?.[0]?.type === 'gauge')
    expect(gauge, '进度仪表盘应已渲染').toBeDefined()
    // percent 裁剪后位于 series[0].data[0].value（renderProgressChart L283 实证）
    expect(lastOption(gauge).series[0].data[0].value).toBe(100)

    // 切换到另一项目验证负值裁剪
    mockGetProjectProgress.mockResolvedValueOnce({ code: 200, data: { completionRate: -0.3 } })
    setupState(wrapper).handleProjectChange(1002)
    await flushPromises()
    await nextTick()
    const gauge2 = [...chartInstances].reverse().find((c) => lastOption(c)?.series?.[0]?.type === 'gauge')
    expect(lastOption(gauge2).series[0].data[0].value).toBe(0)
  })

  // @matrix C-30-9
  it('resize 防抖 300ms：连续触发只重绘一次', async () => {
    vi.useFakeTimers()
    const wrapper = await mountPage()
    setupState(wrapper).handleProjectChange(1001)
    await vi.runAllTimersAsync()
    await nextTick()
    const chart = chartInstances[0]
    expect(chart, '至少一个图表实例已创建').toBeDefined()

    window.dispatchEvent(new Event('resize'))
    window.dispatchEvent(new Event('resize'))
    window.dispatchEvent(new Event('resize'))
    // 未到 300ms 不应触发 resize
    expect(chart.resize).not.toHaveBeenCalled()
    vi.advanceTimersByTime(300)
    expect(chart.resize).toHaveBeenCalledTimes(1)
  })

  it('unmount → 已渲染图表 dispose', async () => {
    const wrapper = await mountPage()
    setupState(wrapper).handleProjectChange(1001)
    await flushPromises()
    await nextTick()
    expect(chartInstances.length, '应有图表实例').toBeGreaterThan(0)
    wrapper.unmount()
    currentWrapper = null
    // 至少预算饼图被 dispose（其余维度视渲染而定）
    expect(chartInstances.some((c) => c.dispose.mock.calls.length > 0)).toBe(true)
  })
})
