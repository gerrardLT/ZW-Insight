/**
 * Property 5 & 6: 编号规则表单校验
 *
 * **Validates: Requirements 4.11**
 *
 * 使用 Vitest + fast-check 验证：
 * - Property 5: businessType 合法性校验
 *   - 包含 [a-zA-Z0-9_] 以外字符或长度超 50 → 校验拒绝
 *   - 匹配 /^[a-zA-Z0-9_]{1,50}$/ → 校验通过
 * - Property 6: seqLength 范围校验
 *   - [1,10] 范围外或非整数 → 校验拒绝
 *   - 1-10 的整数 → 校验通过
 *
 * 依赖安装：npm install -D vitest fast-check
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import { validateBusinessType, validateSeqLength } from '@/utils/serial-number-validation'

// 合法字符集
const validChars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_'.split('')

// 非法字符集 — 常见的非法字符
const illegalChars = '!@#$%^&*()-+=[]{}|;:\'",.<>?/`~ \t\n'.split('')

describe('Property 5: businessType 表单校验', () => {
  it('合法字符串（仅字母/数字/下划线，长度 1-50）应通过校验', () => {
    fc.assert(
      fc.property(
        fc.string({ unit: fc.constantFrom(...validChars), minLength: 1, maxLength: 50 }),
        (value) => {
          expect(validateBusinessType(value)).toBe(true)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('包含非法字符的字符串应被拒绝', () => {
    fc.assert(
      fc.property(
        fc.tuple(
          // 合法前缀（可能为空）
          fc.string({ unit: fc.constantFrom(...validChars), minLength: 0, maxLength: 10 }),
          // 至少一个非法字符
          fc.constantFrom(...illegalChars),
          // 合法后缀（可能为空）
          fc.string({ unit: fc.constantFrom(...validChars), minLength: 0, maxLength: 10 })
        ).map(([prefix, illegal, suffix]) => prefix + illegal + suffix),
        (value) => {
          expect(validateBusinessType(value)).toBe(false)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('长度超过 50 的字符串应被拒绝', () => {
    fc.assert(
      fc.property(
        fc.string({ unit: fc.constantFrom(...validChars), minLength: 51, maxLength: 100 }),
        (value) => {
          expect(validateBusinessType(value)).toBe(false)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('空字符串应被拒绝', () => {
    expect(validateBusinessType('')).toBe(false)
  })
})

describe('Property 6: seqLength 范围校验', () => {
  it('1-10 范围内的整数应通过校验', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 10 }),
        (value) => {
          expect(validateSeqLength(value)).toBe(true)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('小于 1 的整数应被拒绝', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: -1000, max: 0 }),
        (value) => {
          expect(validateSeqLength(value)).toBe(false)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('大于 10 的整数应被拒绝', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 11, max: 10000 }),
        (value) => {
          expect(validateSeqLength(value)).toBe(false)
        }
      ),
      { numRuns: 100 }
    )
  })

  it('非整数浮点数应被拒绝', () => {
    fc.assert(
      fc.property(
        fc.double({ min: -100, max: 100, noNaN: true, noDefaultInfinity: true })
          .filter(n => !Number.isInteger(n)),
        (value) => {
          expect(validateSeqLength(value)).toBe(false)
        }
      ),
      { numRuns: 100 }
    )
  })
})
