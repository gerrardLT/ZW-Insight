// @vitest-environment happy-dom
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ZwMoney from '../src/components/ZwMoney.vue'
import ZwStatusBadge from '../src/components/ZwStatusBadge.vue'

describe('Mobile Micro Components', () => {
  it('ZwMoney renders tabular formatted amount', () => {
    const wrapper = mount(ZwMoney, { props: { value: 12345.67 } })
    expect(wrapper.text()).toContain('12,345.67')
    expect(wrapper.classes()).toContain('zw-money')
    expect(wrapper.classes()).toContain('tabular-num')
  })

  it('ZwStatusBadge renders status text and badge class', () => {
    const wrapper = mount(ZwStatusBadge, { props: { status: 'success', text: '已通过' } })
    expect(wrapper.text()).toBe('已通过')
    expect(wrapper.classes()).toContain('status-badge-success')
  })

  it('ZwStatusBadge maps pending to warning', () => {
    const wrapper = mount(ZwStatusBadge, { props: { status: 'pending', text: '审批中' } })
    expect(wrapper.text()).toBe('审批中')
    expect(wrapper.classes()).toContain('status-badge-warning')
  })
})
