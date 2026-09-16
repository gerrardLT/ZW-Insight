import { describe, it, expect } from 'vitest'
import { buildCostNarrative, formatWan } from '../utils/costNarrative'

describe('M3 数据叙事 - 成本五态数据口径测试 (PC & App 统一)', () => {
  it('基准、当前、承诺、实际与预测五态数值映射正确', () => {
    const raw = {
      baselineTotal: 1000000,
      currentTotal: 1200000,
      commitmentTotal: 800000,
      actualTotal: 500000,
      forecastTotal: 1100000,
      varianceAmount: 100000,
      varianceRate: 8.33,
      trends: []
    }

    const narrative = buildCostNarrative(raw)

    expect(narrative.currentBudget).toBe(1200000)
    expect(narrative.actualTotal).toBe(500000)
    // remainingBudget 必须是当前预算减实际发生成本
    expect(narrative.remainingBudget).toBe(700000)
    expect(narrative.varianceAmount).toBe(100000)
    expect(narrative.hasCostData).toBe(true)
    // 趋势为空数组时拒绝伪造能力
    expect(narrative.hasTrendCapability).toBe(false)

    expect(narrative.nodes).toHaveLength(5)
    expect(narrative.nodes[0].code).toBe('BASE')
    expect(narrative.nodes[1].code).toBe('CURR')
    expect(narrative.nodes[2].code).toBe('COMM')
    expect(narrative.nodes[3].code).toBe('ACTL')
    expect(narrative.nodes[4].code).toBe('EAC')
  })

  it('超预算与超 EAC 时的风险状态评级', () => {
    const raw = {
      baselineTotal: 1000000,
      currentTotal: 1000000,
      commitmentTotal: 1100000, // 承诺超标
      actualTotal: 1050000,     // 实际超标
      forecastTotal: 1200000    // EAC超标
    }

    const narrative = buildCostNarrative(raw)

    expect(narrative.nodes.find(n => n.key === 'commitment')?.status).toBe('warning')
    expect(narrative.nodes.find(n => n.key === 'actual')?.status).toBe('danger')
    expect(narrative.nodes.find(n => n.key === 'forecast')?.status).toBe('danger')
    expect(narrative.remainingBudget).toBe(-50000)
  })

  it('万分位格式化工具函数准确无误', () => {
    expect(formatWan(100000)).toBe('10.00')
    expect(formatWan(0)).toBe('0.00')
    expect(formatWan(null)).toBe('0.00')
  })
})
