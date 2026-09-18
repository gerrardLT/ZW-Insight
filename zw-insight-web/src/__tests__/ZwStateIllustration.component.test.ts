/**
 * ZwStateIllustration 品牌化状态插图测试（Batch 1.2，2026-09-17）
 * 钉住：4 态专属图形（形状区分）+ 共用蓝图底板 + size/aria 生效 + 无硬编码 hex（颜色经 CSS 类）。
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ZwStateIllustration from '@/components/visual/ZwStateIllustration.vue'

describe('ZwStateIllustration 品牌化状态插图', () => {
  it('4 态各渲染专属修饰类 + 共用蓝图底板虚线框', () => {
    for (const type of ['data', 'error', 'offline', 'permission'] as const) {
      const w = mount(ZwStateIllustration, { props: { type } })
      expect(w.find('svg.zw-illust').exists()).toBe(true)
      expect(w.find(`.zw-illust--${type}`).exists()).toBe(true)
      expect(w.find('.ill-frame').exists()).toBe(true)
    }
  })

  it('size 与 ariaLabel 生效（尺寸 / 无障碍）', () => {
    const w = mount(ZwStateIllustration, {
      props: { type: 'error', size: '96px', ariaLabel: '加载失败插图' },
    })
    const svg = w.find('svg.zw-illust')
    expect(svg.attributes('aria-label')).toBe('加载失败插图')
    expect((svg.element as unknown as SVGElement).style.width).toBe('96px')
  })

  it('error/offline 有橙点缀（ill-accent-s），data/permission 有中性线条', () => {
    expect(mount(ZwStateIllustration, { props: { type: 'error' } }).find('.ill-accent-s').exists()).toBe(true)
    expect(mount(ZwStateIllustration, { props: { type: 'offline' } }).find('.ill-accent-s').exists()).toBe(true)
    expect(mount(ZwStateIllustration, { props: { type: 'data' } }).findAll('.ill-line').length).toBeGreaterThan(0)
    expect(mount(ZwStateIllustration, { props: { type: 'permission' } }).find('.ill-accent-f').exists()).toBe(true)
  })

  it('SVG 无硬编码 hex 颜色（颜色一律经 CSS 类注入，随主题变量适配）', () => {
    const html = mount(ZwStateIllustration, { props: { type: 'error' } }).html()
    expect(html).not.toMatch(/#[0-9a-fA-F]{6}/)
    // 颜色不写在 presentation attribute（stroke=/fill= 不带字面色值）
    expect(html).not.toMatch(/(stroke|fill)="#/)
  })
})
