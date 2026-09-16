/**
 * ZwEmptyState 统一空态组件测试
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

  it('error type renders warning icon', () => {
    const wrapper = mount(ZwEmptyState, {
      props: { type: 'error', description: '失败' }
    })
    expect(wrapper.classes()).toContain('type-error')
    // error 类型也使用 IconAlertCircle 图标
    expect(wrapper.find('.empty-icon').exists()).toBe(true)
  })

  it('offline type renders warning state', () => {
    const wrapper = mount(ZwEmptyState, {
      props: { type: 'offline', description: '网络异常' }
    })
    expect(wrapper.classes()).toContain('type-offline')
  })

  it('permission type renders restricted message', () => {
    const wrapper = mount(ZwEmptyState, {
      props: { type: 'permission', description: '无权限' }
    })
    expect(wrapper.classes()).toContain('type-permission')
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
})
