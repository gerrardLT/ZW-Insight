/**
 * ZwFormPane 组件测试（阶段三 R5 表单契约）
 * 覆盖：标题/描述渲染 / 默认与操作/审批插槽 / 锚点开关 / 分区标题样式类。
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

import ZwFormPane from '@/components/ZwFormPane.vue'

const SECTIONS = [
  { id: 'basic', title: '基本信息' },
  { id: 'amount', title: '金额与条款' },
]

describe('components/ZwFormPane.vue 表单契约容器', () => {
  it('渲染标题、描述与默认插槽', () => {
    const wrapper = mount(ZwFormPane, {
      props: { title: '付款申请', description: '提交后进入审批' },
      slots: { default: '<div class="field">字段区</div>' },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.text()).toContain('付款申请')
    expect(wrapper.text()).toContain('提交后进入审批')
    expect(wrapper.find('.field').exists()).toBe(true)
    wrapper.unmount()
  })

  it('操作区与审批区按插槽存在性渲染', () => {
    const wrapper = mount(ZwFormPane, {
      slots: {
        default: '<div>字段区</div>',
        actions: '<button class="btn">提交</button>',
        approval: '<div class="flow">审批记录</div>',
      },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.find('.zw-form-pane__actions .btn').exists()).toBe(true)
    expect(wrapper.find('.zw-form-pane__approval .flow').exists()).toBe(true)
    wrapper.unmount()
  })

  it('anchor 关闭时不渲染锚点导航，开启时渲染', () => {
    const closed = mount(ZwFormPane, {
      props: { sections: SECTIONS, anchor: false },
      slots: { default: '<div>字段区</div>' },
      global: { plugins: [ElementPlus] },
    })
    expect(closed.find('.zw-form-pane__anchor').exists()).toBe(false)
    closed.unmount()

    const opened = mount(ZwFormPane, {
      props: { sections: SECTIONS, anchor: true },
      slots: { default: '<div>字段区</div>' },
      global: { plugins: [ElementPlus] },
    })
    expect(opened.find('.zw-form-pane__anchor').exists()).toBe(true)
    opened.unmount()
  })

  it('分区标题与派生只读使用约定样式类', () => {
    const wrapper = mount(ZwFormPane, {
      slots: { default: '<div class="zw-form-pane__section-title">基本信息</div><span class="zw-form-pane__derived">派生值</span>' },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.find('.zw-form-pane__section-title').text()).toBe('基本信息')
    expect(wrapper.find('.zw-form-pane__derived').text()).toBe('派生值')
    wrapper.unmount()
  })
})
