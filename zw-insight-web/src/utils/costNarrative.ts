/**
 * 成本五态数据叙事适配器（M3 专项数据口径）
 *
 * 冻结口径：
 * 1. baselineTotal: 基准预算
 * 2. currentTotal: 当前预算
 * 3. commitmentTotal: 已承诺成本（已签合同/已提单）
 * 4. actualTotal: 实际发生成本（已支付/已入库结算）
 * 5. forecastTotal: 完工预测成本 (EAC)
 *
 * 派生指标与真实性约束：
 * - varianceAmount = currentTotal - forecastTotal (优先采用后端返回值)
 * - remainingBudget = currentTotal - actualTotal (严禁冒充未承诺余额)
 * - 若月度流水 trends 为空，严格标记 hasTrendCapability = false，拒绝前端插值伪造图表。
 */

export interface CostFiveStateRaw {
  baselineTotal?: number | null
  currentTotal?: number | null
  commitmentTotal?: number | null
  actualTotal?: number | null
  forecastTotal?: number | null
  varianceAmount?: number | null
  varianceRate?: number | null
  remainingBudget?: number | null
  trends?: any[] | null
}

export interface CostStateNode {
  key: 'baseline' | 'current' | 'commitment' | 'actual' | 'forecast'
  label: string
  code: string
  amount: number
  formattedAmount: string
  ratioToCurrent: number // 相对于当前预算的比例
  status: 'normal' | 'warning' | 'danger'
}

export interface CostNarrativeResult {
  nodes: CostStateNode[]
  currentBudget: number
  actualTotal: number
  remainingBudget: number
  varianceAmount: number
  varianceRate: number
  hasCostData: boolean
  hasTrendCapability: boolean
}

export function formatWan(val: number | null | undefined): string {
  if (val === null || val === undefined || isNaN(val)) return '0.00'
  return (val / 10000).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  })
}

export function buildCostNarrative(raw?: CostFiveStateRaw | null): CostNarrativeResult {
  if (!raw) {
    return {
      nodes: [],
      currentBudget: 0,
      actualTotal: 0,
      remainingBudget: 0,
      varianceAmount: 0,
      varianceRate: 0,
      hasCostData: false,
      hasTrendCapability: false
    }
  }

  const baseline = Number(raw.baselineTotal) || 0
  const current = Number(raw.currentTotal) || 0
  const commitment = Number(raw.commitmentTotal) || 0
  const actual = Number(raw.actualTotal) || 0
  const forecast = Number(raw.forecastTotal) || current

  // 计算或优先使用后端 variance
  const varianceAmount = raw.varianceAmount !== undefined && raw.varianceAmount !== null
    ? Number(raw.varianceAmount)
    : (current - forecast)
  const varianceRate = raw.varianceRate !== undefined && raw.varianceRate !== null
    ? Number(raw.varianceRate)
    : (current > 0 ? (varianceAmount / current) * 100 : 0)

  // 严格执行口径：剩余预算为当前预算减实际发生
  const remainingBudget = raw.remainingBudget !== undefined && raw.remainingBudget !== null
    ? Number(raw.remainingBudget)
    : (current - actual)

  const hasCostData = (baseline > 0 || current > 0 || actual > 0)
  const hasTrendCapability = Array.isArray(raw.trends) && raw.trends.length > 0

  const calcRatio = (amount: number) => {
    if (current <= 0) return 0
    return Math.min(Math.max((amount / current) * 100, 0), 200)
  }

  const nodes: CostStateNode[] = [
    {
      key: 'baseline',
      label: '基准预算',
      code: 'BASE',
      amount: baseline,
      formattedAmount: formatWan(baseline),
      ratioToCurrent: calcRatio(baseline),
      status: 'normal'
    },
    {
      key: 'current',
      label: '当前预算',
      code: 'CURR',
      amount: current,
      formattedAmount: formatWan(current),
      ratioToCurrent: 100,
      status: 'normal'
    },
    {
      key: 'commitment',
      label: '已承诺成本',
      code: 'COMM',
      amount: commitment,
      formattedAmount: formatWan(commitment),
      ratioToCurrent: calcRatio(commitment),
      status: commitment > current ? 'warning' : 'normal'
    },
    {
      key: 'actual',
      label: '实际发生',
      code: 'ACTL',
      amount: actual,
      formattedAmount: formatWan(actual),
      ratioToCurrent: calcRatio(actual),
      status: actual > current ? 'danger' : (actual / (current || 1) > 0.85 ? 'warning' : 'normal')
    },
    {
      key: 'forecast',
      label: '完工预测 (EAC)',
      code: 'EAC',
      amount: forecast,
      formattedAmount: formatWan(forecast),
      ratioToCurrent: calcRatio(forecast),
      status: forecast > current ? 'danger' : 'normal'
    }
  ]

  return {
    nodes,
    currentBudget: current,
    actualTotal: actual,
    remainingBudget,
    varianceAmount,
    varianceRate,
    hasCostData,
    hasTrendCapability
  }
}
