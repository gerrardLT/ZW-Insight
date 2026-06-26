/**
 * 税率选择器逻辑测试
 *
 * **Validates: Requirements 6.1, 6.3, 6.4, 6.6**
 *
 * 使用 Vitest + fast-check 验证税率选择器纯逻辑：
 * - 选择预设税率自动填值（精度2位小数）
 * - 选项展示文案格式「名称（数值%）」
 * - 手动输入值规范化为2位小数
 * - 手动修改与选中预设不一致时清除选中状态
 *
 * 依赖：vitest、fast-check
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import {
  normalizeRate,
  formatOptionLabel,
  resolveSelectedValue,
  findMatchingOptionId,
  shouldClearSelection,
  type TaxRateOption
} from '@/utils/tax-rate-selector'

const sampleOptions: TaxRateOption[] = [
  { id: 1, name: '增值税13%', rateValue: 13 },
  { id: 2, name: '增值税9%', rateValue: 9 },
  { id: 3, name: '增值税6%', rateValue: 6 },
  { id: 4, name: '小规模3%', rateValue: 3.5 }
]

describe('normalizeRate', () => {
  it('保留两位小数', () => {
    expect(normalizeRate(13)).toBe(13)
    expect(normalizeRate(3.456)).toBe(3.46)
    expect(normalizeRate(0.005)).toBe(0.01)
  })

  it('空值返回 undefined', () => {
    expect(normalizeRate(undefined)).toBeUndefined()
    expect(normalizeRate(null)).toBeUndefined()
    expect(normalizeRate(NaN)).toBeUndefined()
  })
})

describe('formatOptionLabel', () => {
  it('生成「名称（数值%）」格式，数值2位小数', () => {
    expect(formatOptionLabel('增值税13%', 13)).toBe('增值税13%（13.00%）')
    expect(formatOptionLabel('小规模', 3.5)).toBe('小规模（3.50%）')
  })
})

describe('resolveSelectedValue', () => {
  it('返回选中选项税率值（2位小数）', () => {
    expect(resolveSelectedValue(sampleOptions, 1)).toBe(13)
    expect(resolveSelectedValue(sampleOptions, 4)).toBe(3.5)
  })

  it('无匹配或空ID返回 undefined', () => {
    expect(resolveSelectedValue(sampleOptions, 999)).toBeUndefined()
    expect(resolveSelectedValue(sampleOptions, undefined)).toBeUndefined()
  })
})

describe('findMatchingOptionId', () => {
  it('按数值反查选项ID', () => {
    expect(findMatchingOptionId(sampleOptions, 13)).toBe(1)
    expect(findMatchingOptionId(sampleOptions, 3.5)).toBe(4)
  })

  it('无匹配数值返回 undefined', () => {
    expect(findMatchingOptionId(sampleOptions, 7)).toBeUndefined()
    expect(findMatchingOptionId(sampleOptions, undefined)).toBeUndefined()
  })
})

describe('shouldClearSelection (Req 6.6)', () => {
  it('未选中时不清除', () => {
    expect(shouldClearSelection(sampleOptions, undefined, 5)).toBe(false)
  })

  it('手动值与选中预设一致时不清除', () => {
    expect(shouldClearSelection(sampleOptions, 1, 13)).toBe(false)
  })

  it('手动值与选中预设不一致时清除', () => {
    expect(shouldClearSelection(sampleOptions, 1, 12)).toBe(true)
    expect(shouldClearSelection(sampleOptions, 1, undefined)).toBe(true)
  })
})

describe('属性测试', () => {
  // 选择任一预设税率，填入的数值恒等于该预设税率值（2位小数）—— Req 6.3
  it('选择预设填入对应税率值', () => {
    fc.assert(
      fc.property(fc.constantFrom(...sampleOptions.map((o) => o.id)), (id) => {
        const opt = sampleOptions.find((o) => o.id === id)!
        expect(resolveSelectedValue(sampleOptions, id)).toBe(Number(opt.rateValue.toFixed(2)))
      })
    )
  })

  // 手动输入任意 0.00-100.00 的值后，规范化结果恒为非负、≤100、且不超过2位小数 —— Req 6.4
  it('手动输入值规范化为合法精度', () => {
    fc.assert(
      fc.property(fc.double({ min: 0, max: 100, noNaN: true }), (raw) => {
        const n = normalizeRate(raw)
        expect(n).not.toBeUndefined()
        expect(n!).toBeGreaterThanOrEqual(0)
        expect(n!).toBeLessThanOrEqual(100)
        // 小数位不超过 2 位
        const decimals = (n!.toString().split('.')[1] || '').length
        expect(decimals).toBeLessThanOrEqual(2)
      })
    )
  })

  // 选中预设后，若手动值不等于该预设值则必清除选中；等于则必保留 —— Req 6.6
  it('清除选中决策与值一致性等价', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...sampleOptions.map((o) => o.id)),
        fc.double({ min: 0, max: 100, noNaN: true }),
        (id, manual) => {
          const presetVal = resolveSelectedValue(sampleOptions, id)
          const manualNorm = normalizeRate(manual)
          const cleared = shouldClearSelection(sampleOptions, id, manual)
          expect(cleared).toBe(presetVal !== manualNorm)
        }
      )
    )
  })
})
