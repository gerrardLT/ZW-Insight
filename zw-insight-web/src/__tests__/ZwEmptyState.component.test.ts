/**
 * ZwEmptyState 统一空态组件测试（Batch 1.1：品牌化蓝图 SVG 4 态）
 * 断言与实现同步：插图由 EP 图标改为内联蓝图 SVG，图标恒中性、形状区分状态。
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ZwEmptyState from '@/components/ZwEmptyState.vue'

describe('ZwEmptyState', () => {
  it('data type renders correctly', () => {
    const wrapper = mount(ZwEmptyState, {
      props: { type: 'data', description: '无数据' }
    })
    expect(wrapper.classes()).toContain('type-data')
    expect(wrapper.text()).toContain('无数据')
  })

  it('每态渲染内联蓝图 SVG（.zw-illust）而非位图/EP 图标', () => {
    for (const type of ['data', 'error', 'offline', 'permission'] as const) {
      const wrapper = mount(ZwEmptyState, { props: { type } })
      expect(wrapper.find('.empty-icon .zw-illust').exists(), `${type} 应有内联 SVG`).toBe(true)
      // 共用蓝图底板虚线框
      expect(wrapper.find('.zw-illust .ill-frame').exists()).toBe(true)
    }
  })

  it('error type renders warning glyph', () => {
    const wrapper = mount(ZwEmptyState, {
      props: { type: 'error', description: '失败' }
    })
    expect(wrapper.classes()).toContain('type-error')
    expect(wrapper.find('.empty-icon').exists()).toBe(true)
    // error 专属：警示三角（path）+ 橙感叹号（ill-accent-s）
    expect(wrapper.find('.zw-illust path.ill-line').exists()).toBe(true)
    expect(wrapper.find('.zw-illust .ill-accent-s').exists()).toBe(true)
  })

  it('permission type renders lock glyph', () => {
    const wrapper = mount(ZwEmptyState, { props: { type: 'permission' } })
    expect(wrapper.classes()).toContain('type-permission')
    // 挂锁：锁体 rect + 锁梁 path
    expect(wrapper.findAll('.zw-illust rect.ill-line').length).toBeGreaterThan(0)
  })

  it('offline type renders cloud-off glyph', () => {
    const wrapper = mount(ZwEmptyState, { props: { type: 'offline' } })
    expect(wrapper.classes()).toContain('type-offline')
    expect(wrapper.find('.zw-illust .ill-accent-s').exists()).toBe(true)
  })

  it('未传 description 时按类型给方向性默认文案', () => {
    expect(mount(ZwEmptyState, { props: { type: 'error' } }).text()).toContain('加载失败')
    expect(mount(ZwEmptyState, { props: { type: 'permission' } }).text()).toContain('请联系管理员')
    expect(mount(ZwEmptyState, { props: { type: 'offline' } }).text()).toContain('网络')
  })

  it('action slot is rendered when provided', () => {
    const wrapper = mount(ZwEmptyState, {
      props: { type: 'data', description: '无数据' },
      slots: {
        action: '<button class="retry-btn">重试</button>'
      }
    })
    expect(wrapper.find('.empty-action .retry-btn').exists()).toBe(true)
  })

  it('custom description takes precedence over default', () => {
    const wrapper = mount(ZwEmptyState, {
      props: { type: 'data', description: '自定义文案' }
    })
    expect(wrapper.text()).toContain('自定义文案')
  })

  it('title 存在时渲染 .empty-title，且 SVG 带 aria-label（无障碍）', () => {
    const wrapper = mount(ZwEmptyState, {
      props: { type: 'error', title: '数据加载异常', description: '请重试' }
    })
    expect(wrapper.find('.empty-title').text()).toBe('数据加载异常')
    expect(wrapper.find('.zw-illust').attributes('aria-label')).toContain('数据加载异常')
  })
})
