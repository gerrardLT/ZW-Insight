/**
 * 税率选择器纯逻辑工具
 *
 * 将 TaxRateSelector.vue 的核心决策逻辑抽离为纯函数，便于单元测试与属性测试。
 * 对应需求：6.3（选择预设填值）、6.4（手动输入精度）、6.6（手动修改清除选中）。
 */

export interface TaxRateOption {
  id: number
  name: string
  rateValue: number
}

/**
 * 将税率数值规范化为 2 位小数精度。
 * null/undefined/非有限数 → undefined。
 */
export function normalizeRate(val: number | null | undefined): number | undefined {
  if (val === null || val === undefined) return undefined
  const n = Number(val)
  if (!Number.isFinite(n)) return undefined
  return Number(n.toFixed(2))
}

/**
 * 生成下拉选项展示文案：「税率名称（数值%）」，数值精度 2 位小数。
 * 对应需求 6.1 / 6.2。
 */
export function formatOptionLabel(name: string, rateValue: number): string {
  return `${name}（${Number(rateValue).toFixed(2)}%）`
}

/**
 * 选择某预定义税率后应填入的税率数值（精度 2 位小数）。
 * 找不到对应选项时返回 undefined。
 * 对应需求 6.3。
 */
export function resolveSelectedValue(
  options: TaxRateOption[],
  id: number | null | undefined
): number | undefined {
  if (id === null || id === undefined) return undefined
  const opt = options.find((o) => o.id === id)
  if (!opt) return undefined
  return normalizeRate(opt.rateValue)
}

/**
 * 根据税率数值反查匹配的预定义选项 ID（用于编辑回显下拉选中状态）。
 * 无匹配返回 undefined。
 */
export function findMatchingOptionId(
  options: TaxRateOption[],
  value: number | null | undefined
): number | undefined {
  const target = normalizeRate(value)
  if (target === undefined) return undefined
  const matched = options.find((o) => normalizeRate(o.rateValue) === target)
  return matched ? matched.id : undefined
}

/**
 * 用户手动修改税率数值后，是否应清除下拉选中状态。
 * 规则（需求 6.6）：当前已选预设、且手动值与该预设数值不一致时，应清除选中。
 * 无选中、或手动值与选中预设一致时，保留选中。
 */
export function shouldClearSelection(
  options: TaxRateOption[],
  selectedId: number | null | undefined,
  manualValue: number | null | undefined
): boolean {
  if (selectedId === null || selectedId === undefined) return false
  const presetVal = resolveSelectedValue(options, selectedId)
  return presetVal !== normalizeRate(manualValue)
}
