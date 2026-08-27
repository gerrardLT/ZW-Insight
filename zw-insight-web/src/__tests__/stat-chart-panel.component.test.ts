/**
 * StatChartPanel 通用统计图表卡片组件测试（P0 差距收口 T7，tasks 3.7）
 *
 * 钉住三态契约：
 * - fetchData 抛错 → 失败态展示错误消息 + 重试按钮（后端 BusinessException 空数据提示走此通道，不静默）
 * - buildOption 返回 null → 空态 el-empty（emptyText）
 * - 成功 → echarts.init + setOption(option, true)（notMerge 防残留序列）
 *
 * 模式与 dashboard-index.component.test.ts 一致：真实 Element Plus 挂载，
 * mock echarts 模块（happy-dom 无 canvas），afterEach unmount。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { chartInstances, chartInit } = vi.hoisted(() => {
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
  return { chartInstances: instances, chartInit: init }
})

vi.mock('echarts', () => ({ init: chartInit }))
// 组件引入 useAppStore（暗色联动）后需 mock，避免无 pinia 环境报 getActivePinia；
// 用 reactive 状态使 isDark 可测试性切换，钉住主题重绘行为
vi.mock('@/stores/app', async () => {
  const { reactive } = await import('vue')
  const state = reactive({ isDark: false })
  return { useAppStore: () => state }
})

import StatChartPanel from '@/components/StatChartPanel.vue'
import { useAppStore } from '@/stores/app'
import { applyChartTheme, chartThemeLight, chartThemeDark } from '@/constants/chart-theme'

let currentWrapper: any = null

function mountPanel(props: Record<string, any>) {
  const wrapper = mount(StatChartPanel, {
    props: props as any,
    global: { plugins: [ElementPlus] },
  })
  currentWrapper = wrapper
  return wrapper
}

describe('StatChartPanel 三态契约（T7）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    chartInstances.length = 0
    ;(useAppStore() as any).isDark = false
  })

  afterEach(() => {
    if (currentWrapper) {
      try { currentWrapper.unmount() } catch { /* 忽略卸载异常 */ }
      currentWrapper = null
    }
  })

  it('fetchData 抛错 → 失败态显示错误消息与重试按钮，不静默', async () => {
    const fetchData = vi.fn(async () => { throw new Error('该项目暂无已审批的产值上报') })
    const wrapper = mountPanel({ title: '产值趋势', fetchData, buildOption: vi.fn() })
    await flushPromises()

    const errorBox = wrapper.find('[data-testid="stat-panel-error"]')
    expect(errorBox.exists()).toBe(true)
    expect(errorBox.text()).toContain('该项目暂无已审批的产值上报')
    expect(wrapper.find('[data-testid="stat-panel-empty"]').exists()).toBe(false)
    expect(chartInit).not.toHaveBeenCalled()
  })

  it('失败态点击重试 → 重新调用 fetchData', async () => {
    const fetchData = vi.fn(async () => { throw new Error('加载失败') })
    const wrapper = mountPanel({ title: '回款率', fetchData, buildOption: vi.fn() })
    await flushPromises()
    expect(fetchData).toHaveBeenCalledTimes(1)

    await wrapper.find('[data-testid="stat-panel-error"] button').trigger('click')
    await flushPromises()
    expect(fetchData).toHaveBeenCalledTimes(2)
  })

  it('buildOption 返回 null → 空态展示 emptyText', async () => {
    const fetchData = vi.fn(async () => ({ list: [] }))
    const buildOption = vi.fn(() => null)
    const wrapper = mountPanel({ title: '资金计划', fetchData, buildOption, emptyText: '暂无已审批的付款申请' })
    await flushPromises()

    const emptyBox = wrapper.find('[data-testid="stat-panel-empty"]')
    expect(emptyBox.exists()).toBe(true)
    expect(emptyBox.text()).toContain('暂无已审批的付款申请')
    // 空态插图为自绘蓝图角标（SVG + zw-empty-icon），替代 el-empty 默认插画
    expect(emptyBox.find('svg.zw-empty-icon').exists()).toBe(true)
    expect(wrapper.find('[data-testid="stat-panel-error"]').exists()).toBe(false)
    expect(chartInit).not.toHaveBeenCalled()
  })

  it('成功且有数据 → echarts.init + setOption(主题化 option, true)（notMerge）', async () => {
    const option = { xAxis: { type: 'category', data: ['2026-01'] }, series: [{ type: 'bar', data: [1] }] }
    const fetchData = vi.fn(async () => ({ ok: true }))
    const buildOption = vi.fn(() => option)
    const wrapper = mountPanel({ title: '工资发放趋势', fetchData, buildOption })
    await flushPromises()

    expect(wrapper.find('[data-testid="stat-panel-error"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="stat-panel-empty"]').exists()).toBe(false)
    expect(chartInit).toHaveBeenCalledTimes(1)
    // option 经 applyChartTheme 填充亮色主题默认值（色板/轴/tooltip），业务字段不变
    const themed = applyChartTheme(option, chartThemeLight)
    expect(chartInstances[0].setOption).toHaveBeenCalledWith(themed, true)
    expect(themed.series).toEqual(option.series)
  })

  it('暗色切换 → 不重复请求，以缓存数据重建暗色 option 重绘', async () => {
    const option = { xAxis: { type: 'category', data: ['2026-01'] }, series: [{ type: 'bar', data: [1] }] }
    const fetchData = vi.fn(async () => ({ ok: true }))
    const buildOption = vi.fn(() => option)
    const wrapper = mountPanel({ title: '工资发放趋势', fetchData, buildOption })
    await flushPromises()
    expect(fetchData).toHaveBeenCalledTimes(1)
    expect(chartInstances[0].setOption).toHaveBeenCalledTimes(1)

    ;(useAppStore() as any).isDark = true
    await flushPromises()

    expect(fetchData).toHaveBeenCalledTimes(1) // 不重复请求接口
    expect(buildOption).toHaveBeenCalledTimes(2)
    expect(chartInstances[0].setOption).toHaveBeenCalledTimes(2)
    expect(chartInstances[0].setOption).toHaveBeenLastCalledWith(applyChartTheme(option, chartThemeDark), true)
  })

  it('暴露 reload：重复加载复用同一图表实例，不重复 init', async () => {
    const fetchData = vi.fn(async () => ({ ok: true }))
    const buildOption = vi.fn(() => ({ series: [] }))
    const wrapper = mountPanel({ title: '组合看板', fetchData, buildOption })
    await flushPromises()
    expect(chartInit).toHaveBeenCalledTimes(1)

    ;(wrapper.vm as any).reload()
    await flushPromises()
    expect(fetchData).toHaveBeenCalledTimes(2)
    expect(chartInit).toHaveBeenCalledTimes(1) // 复用实例
    expect(chartInstances[0].setOption).toHaveBeenCalledTimes(2)
  })
})
