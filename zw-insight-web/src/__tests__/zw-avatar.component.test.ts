/**
 * ZwAvatar 用户头像组件测试（P0 资产，2026-10-06）
 * 钉住：姓名→首字 + 稳定哈希取色（同名恒同色/异名大概率异色）、
 *       自定义 src 展示图片、@error 回落首字、无名回落中性剪影、尺寸档位。
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

import ZwAvatar from '@/components/ZwAvatar.vue'

describe('ZwAvatar 用户头像', () => {
  it('有姓名渲染首字，底色来自稳定调色板', () => {
    const w = mount(ZwAvatar, { props: { name: '王婷' } })
    expect(w.find('[data-testid="zw-avatar-text"]').text()).toBe('王')
    expect(w.find('[data-testid="zw-avatar-text"]').attributes('style')).toContain('#ffffff')
  })

  it('同名恒同色（哈希稳定），不同名大概率异色', () => {
    const a = mount(ZwAvatar, { props: { name: '王婷' } })
    const b = mount(ZwAvatar, { props: { name: '王婷' } })
    expect(a.find('.zw-avatar').attributes('style')).toBe(b.find('.zw-avatar').attributes('style'))

    const names = ['王婷', '李明', '张三', '赵六', '钱五', '孙七', '周八', '吴九', '郑十']
    const colors = new Set(names.map((n) => mount(ZwAvatar, { props: { name: n } }).find('.zw-avatar').attributes('style')))
    // 9 个姓名至少命中 5 种配色（调色板 8 色，哈希分布应足够散）
    expect(colors.size).toBeGreaterThanOrEqual(5)
  })

  it('有 src 展示图片；@error 自动回落首字（裂图纪律）', async () => {
    const w = mount(ZwAvatar, { props: { name: '王婷', src: 'https://example.com/a.png' } })
    expect(w.find('[data-testid="zw-avatar-img"]').exists()).toBe(true)
    expect(w.find('[data-testid="zw-avatar-text"]').exists()).toBe(false)

    await w.find('[data-testid="zw-avatar-img"]').trigger('error')
    expect(w.find('[data-testid="zw-avatar-img"]').exists()).toBe(false)
    expect(w.find('[data-testid="zw-avatar-text"]').text()).toBe('王')
  })

  it('src 变化时重置失败态（重试新图）', async () => {
    const w = mount(ZwAvatar, { props: { name: '王婷', src: 'https://example.com/a.png' } })
    await w.find('[data-testid="zw-avatar-img"]').trigger('error')
    expect(w.find('[data-testid="zw-avatar-text"]').exists()).toBe(true)
    await w.setProps({ src: 'https://example.com/b.png' })
    expect(w.find('[data-testid="zw-avatar-img"]').exists()).toBe(true)
  })

  it('无姓名回落中性剪影（无文字、中性底色）', () => {
    const w = mount(ZwAvatar, { props: {} })
    const text = w.find('[data-testid="zw-avatar-text"]')
    expect(text.text()).toBe('')
    expect(text.attributes('style')).toContain('--zw-text-quaternary')
  })

  it('尺寸档位生效', () => {
    const w = mount(ZwAvatar, { props: { name: '王婷', size: 'lg' } })
    expect(w.find('.zw-avatar--lg').exists()).toBe(true)
  })
})
