// @vitest-environment happy-dom
/**
 * ZwSkeleton 骨架屏组件测试 (S3.1)
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ZwSkeleton from '@/components/ZwSkeleton.vue'

describe('ZwSkeleton 骨架屏', () => {
  it('list 类型默认渲染 3 张卡片骨架', () => {
    const wrapper = mount(ZwSkeleton)
    expect(wrapper.findAll('.sk-card')).toHaveLength(3)
  })

  it('list 类型 rows 属性控制卡片数', () => {
    const wrapper = mount(ZwSkeleton, { props: { type: 'list', rows: 5 } })
    expect(wrapper.findAll('.sk-card')).toHaveLength(5)
  })

  it('form 类型渲染字段骨架 + 提交按钮骨架', () => {
    const wrapper = mount(ZwSkeleton, { props: { type: 'form', rows: 4 } })
    expect(wrapper.findAll('.sk-field')).toHaveLength(4)
    expect(wrapper.find('.sk-submit').exists()).toBe(true)
  })

  it('card 类型渲染 2×2 概览卡骨架', () => {
    const wrapper = mount(ZwSkeleton, { props: { type: 'card' } })
    expect(wrapper.findAll('.sk-stat')).toHaveLength(4)
  })

  it('首卡含品牌橙高亮条（关键数据位语义）', () => {
    const wrapper = mount(ZwSkeleton, { props: { type: 'list', rows: 2 } })
    const firstCard = wrapper.findAll('.sk-card')[0]
    expect(firstCard.find('.sk-chip.accent').exists()).toBe(true)
  })
})
