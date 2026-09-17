// @vitest-environment happy-dom
/**
 * 移动端成本控制看板组件测试 (project-cost-control)
 * 覆盖 pages/project/cost-control/index.vue
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/common', () => ({
  getProjectList: vi.fn(),
  getProjectCostControl: vi.fn(),
}))

import CostControlDashboard from '@/pages/project/cost-control/index.vue'
import { getProjectList, getProjectCostControl } from '@/api/common'
import { resetUniStorage } from '../setup'

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  vi.mocked(getProjectList).mockResolvedValue({
    code: 200,
    data: { records: [{ id: 1, projectName: '测试示范工程' }] },
  } as any)
  vi.mocked(getProjectCostControl).mockResolvedValue({
    code: 200,
    data: {
      summary: {
        baselineBudget: 10000000,
        currentBudget: 10500000,
        commitmentCost: 9000000,
        actualCost: 6500000,
        forecastCost: 10200000,
        variance: 300000, // 正偏差：节约
      },
      categorySummaries: [
        { categoryName: '人工费', currentBudget: 2000000, actualCost: 1500000, commitmentCost: 1800000, variance: 200000 },
        { categoryName: '材料费', currentBudget: 5000000, actualCost: 3200000, commitmentCost: 4500000, variance: 500000 },
      ],
    },
  } as any)
})

describe('project/cost-control/index.vue 移动端成本控制看板', () => {
  it('初始化挂载后加载 6 维 CBS 成本控制核心指标 (万元)', async () => {
    const wrapper = mount(CostControlDashboard)
    await flushPromises()

    expect(wrapper.text()).toContain('测试示范工程')
    expect(wrapper.text()).toContain('项目成本控制总览')
    expect(wrapper.text()).toContain('1000.00万') // 基准
    expect(wrapper.text()).toContain('1050.00万') // 当前
    expect(wrapper.text()).toContain('900.00万')  // 签约
    expect(wrapper.text()).toContain('650.00万')  // 实际
    expect(wrapper.text()).toContain('30.00万')   // 偏差
    expect(wrapper.text()).toContain('预算受控')
    wrapper.unmount()
  })

  it('超支预警：当 CV < 0 时展示超支警示与红标', async () => {
    vi.mocked(getProjectCostControl).mockResolvedValue({
      code: 200,
      data: {
        summary: {
          baselineBudget: 5000000,
          currentBudget: 5000000,
          commitmentCost: 5500000,
          actualCost: 5200000,
          forecastCost: 5500000,
          variance: -200000, // 负偏差：超支 20 万
        },
        categorySummaries: [],
      },
    } as any)

    const wrapper = mount(CostControlDashboard)
    await flushPromises()

    expect(wrapper.text()).toContain('存在超支风险')
    expect(wrapper.vm.isOverBudget).toBe(true)
    expect(wrapper.vm.cvClass).toBe('danger')
    wrapper.unmount()
  })

  it('正确渲染 CBS 费用科目明细卡片', async () => {
    const wrapper = mount(CostControlDashboard)
    await flushPromises()

    expect(wrapper.text()).toContain('CBS 费用科目明细')
    expect(wrapper.text()).toContain('人工费')
    expect(wrapper.text()).toContain('材料费')
    expect(wrapper.text()).toContain('节约 20.00万')
    wrapper.unmount()
  })

  it('S3.1 项目选择器走 ZwiPickerField（label 上置常驻）', async () => {
    const wrapper = mount(CostControlDashboard)
    await flushPromises()

    expect(wrapper.find('.picker-trigger').exists()).toBe(true)
    expect(wrapper.find('.form-label').text()).toContain('监控项目')
    // 已选值落在触发器内（不靠 placeholder 承载语义）
    expect(wrapper.find('.picker-value').text()).toBe('测试示范工程')
    expect(wrapper.find('.picker-placeholder').exists()).toBe(false)
    wrapper.unmount()
  })

  it('S3.1 CBS 科目卡三列横排改竖向 ZwiCell kv 行（2 科目 × 3 行）', async () => {
    const wrapper = mount(CostControlDashboard)
    await flushPromises()

    expect(wrapper.findAll('.cell-kv')).toHaveLength(6)
    expect(wrapper.text()).toContain('当前预算')
    expect(wrapper.text()).toContain('实际发生')
    expect(wrapper.text()).toContain('签约承诺')
    // 实际发生保留加重强调（走 #value 插槽，非 .cell-value）
    expect(wrapper.findAll('.cat-actual')).toHaveLength(2)
    expect(wrapper.find('.cat-actual').text()).toBe('150.00万元')
    wrapper.unmount()
  })

  it('S3.1 无 CBS 数据走 ZwiEmptyState（契约 .empty 保留）', async () => {
    vi.mocked(getProjectCostControl).mockResolvedValue({
      code: 200,
      data: { summary: { currentBudget: 0, actualCost: 0, variance: 0 }, categorySummaries: [] },
    } as any)

    const wrapper = mount(CostControlDashboard)
    await flushPromises()

    expect(wrapper.find('.empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('当前项目暂无 CBS 科目数据')
    wrapper.unmount()
  })

  it('S3.1 加载失败走 ZwiEmptyState error 变体 + 重试 CTA', async () => {
    vi.mocked(getProjectCostControl).mockRejectedValue(new Error('服务异常'))

    const wrapper = mount(CostControlDashboard)
    await flushPromises()

    expect(wrapper.vm.loadFailed).toBe(true)
    expect(wrapper.find('.empty-error').exists()).toBe(true)
    expect(wrapper.text()).toContain('成本数据加载失败')
    expect(wrapper.find('.retry-btn').exists()).toBe(true)
    wrapper.unmount()
  })
})
