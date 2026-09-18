/**
 * 业务域色双源一致性守护（Batch 0.5，2026-09-17）
 *
 * 背景：业务域色有两处事实源——
 *   1. JS：src/constants/business-visual.ts 的 BUSINESS_DOMAINS[*].color（供 ECharts/标签渲染）
 *   2. CSS：src/styles/tokens/base.css 的 --zw-domain-*（供样式引用）
 * base.css 注释要求「两侧色值改动必须双向同步」，此前靠人工，无守护。
 * 本测试解析 base.css 与 JS 常量逐域比对，任一侧漂移即 FAIL（不静默）。
 */
import { describe, it, expect } from 'vitest'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'
import { BUSINESS_DOMAINS } from '@/constants/business-visual'

const here = dirname(fileURLToPath(import.meta.url))
const baseCss = readFileSync(resolve(here, '../styles/tokens/base.css'), 'utf-8')

/** 从 base.css 提取 --zw-domain-{key} 的 6 位十六进制值（小写归一） */
function cssDomainColor(key: string): string | undefined {
  const m = baseCss.match(new RegExp(`--zw-domain-${key}:\\s*(#[0-9a-fA-F]{6})`))
  return m ? m[1].toLowerCase() : undefined
}

describe('业务域色双源一致性（business-visual.ts ↔ tokens/base.css）', () => {
  const jsKeys = Object.keys(BUSINESS_DOMAINS)

  it('JS 侧每个域的 color 在 base.css 有同名 --zw-domain-* 且同值', () => {
    for (const key of jsKeys) {
      const jsColor = BUSINESS_DOMAINS[key].color.toLowerCase()
      const cssColor = cssDomainColor(key)
      expect(cssColor, `base.css 缺 --zw-domain-${key}（JS 侧=${jsColor}）`).toBeDefined()
      expect(jsColor, `域 ${key} 色值漂移：JS=${jsColor} CSS=${cssColor}`).toBe(cssColor)
    }
  })

  it('base.css 的 --zw-domain-* 全部在 JS 侧有对应（无孤儿 CSS 变量）', () => {
    const cssKeys = [...baseCss.matchAll(/--zw-domain-([a-z]+):/g)].map((m) => m[1])
    expect(cssKeys.length).toBeGreaterThan(0)
    for (const k of cssKeys) {
      expect(jsKeys, `CSS --zw-domain-${k} 在 business-visual.ts 无对应条目`).toContain(k)
    }
  })

  it('JS 侧 borderColor 与 color 同值（域色边框纪律，防单侧改动）', () => {
    for (const key of jsKeys) {
      const { color, borderColor } = BUSINESS_DOMAINS[key]
      expect(borderColor.toLowerCase(), `域 ${key} borderColor 应等于 color`).toBe(color.toLowerCase())
    }
  })
})
