/**
 * ZwStateIllustration 品牌化状态插图测试（v2 插画级，2026-10-05 重绘）
 * 钉住：9 态专属修饰类 + 共用制图角标（.ill-mark，v2 替代 v1 整幅虚线底板）+
 *       size/aria 生效 + 无硬编码 hex（颜色经 CSS 类，随主题/品牌主色联动）+
 *       四基础态的橙点缀契约（与 ZwEmptyState 文案语义对齐）。
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import ZwStateIllustration from '@/components/visual/ZwStateIllustration.vue'

const __dirname = dirname(fileURLToPath(import.meta.url))

const ALL_TYPES = [
  'data', 'search', 'error', 'offline', 'permission',
  'settlement', 'fund', 'approval', 'blueprint',
] as const

describe('ZwStateIllustration 品牌化状态插图 v2', () => {
  it('9 态各渲染专属修饰类 + 共用制图角标（.ill-mark）', () => {
    for (const type of ALL_TYPES) {
      const w = mount(ZwStateIllustration, { props: { type } })
      expect(w.find('svg.zw-illust').exists(), `svg ${type}`).toBe(true)
      expect(w.find(`.zw-illust--${type}`).exists(), `修饰类 ${type}`).toBe(true)
      expect(w.find('.ill-mark').exists(), `角标 ${type}`).toBe(true)
      // 插画级体量：除角标外至少 3 个造型元素（防退化回图标级）
      expect(w.findAll('g').length).toBeGreaterThanOrEqual(2)
    }
  })

  it('viewBox 为 160×120 横版且高度自适应', () => {
    const w = mount(ZwStateIllustration, { props: { type: 'data' } })
    expect(w.find('svg').attributes('viewBox')).toBe('0 0 160 120')
  })

  it('size 与 ariaLabel 生效（宽度 / 无障碍）', () => {
    const w = mount(ZwStateIllustration, {
      props: { type: 'error', size: '220px', ariaLabel: '加载失败插图' },
    })
    const svg = w.find('svg.zw-illust')
    expect(svg.attributes('aria-label')).toBe('加载失败插图')
    expect((svg.element as unknown as SVGElement).style.width).toBe('220px')
  })

  it('四基础态橙点缀契约（error/offline 线点缀，data/permission 点点缀）', () => {
    expect(mount(ZwStateIllustration, { props: { type: 'error' } }).find('.ill-accent-s').exists()).toBe(true)
    expect(mount(ZwStateIllustration, { props: { type: 'offline' } }).find('.ill-accent-s').exists()).toBe(true)
    expect(mount(ZwStateIllustration, { props: { type: 'data' } }).find('.ill-accent-f').exists()).toBe(true)
    expect(mount(ZwStateIllustration, { props: { type: 'permission' } }).find('.ill-accent-f').exists()).toBe(true)
  })

  it('业务场景态均含中性线条与橙点缀（单一橙点纪律）', () => {
    for (const type of ['search', 'settlement', 'fund', 'approval', 'blueprint'] as const) {
      const w = mount(ZwStateIllustration, { props: { type } })
      expect(w.findAll('.ill-line, .ill-line-strong').length, `${type} 中性线条`).toBeGreaterThan(0)
      const accents = w.findAll('.ill-accent-s, .ill-accent-f').length
      expect(accents, `${type} 橙点缀`).toBeGreaterThan(0)
      expect(accents, `${type} 橙点缀不超 2 处（稀缺纪律）`).toBeLessThanOrEqual(2)
    }
  })

  it('SVG 无硬编码 hex 颜色（颜色一律经 CSS 类注入，随主题变量适配）', () => {
    const html = mount(ZwStateIllustration, { props: { type: 'blueprint' } }).html()
    expect(html).not.toMatch(/#[0-9a-fA-F]{6}/)
    expect(html).not.toMatch(/(stroke|fill)="#/)
  })

  it('组件自带水平自居中（回归：2026-10-06 项目墙 .wall-empty 场景块级 SVG 靠左）', () => {
    // happy-dom 不算 scoped CSS，改以 SFC 源码钉住规则存在
    const src = readFileSync(resolve(__dirname, '../components/visual/ZwStateIllustration.vue'), 'utf-8')
    expect(src).toMatch(/\.zw-illust\s*\{[^}]*margin-inline:\s*auto/s)
  })
})
