import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'
import ZwTableDensitySwitch from '@/components/ZwTableDensitySwitch.vue'
import ZwFormAffixNav from '@/components/ZwFormAffixNav.vue'
import { useAppStore } from '@/stores/app'

describe('ZwTableDensitySwitch 组件测试', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('默认激活 default 密度', () => {
    const wrapper = mount(ZwTableDensitySwitch, {
      global: { plugins: [ElementPlus] }
    })
    const buttons = wrapper.findAll('.density-btn')
    expect(buttons).toHaveLength(3)
    expect(buttons[1].classes()).toContain('active')
  })

  it('点击 compact 切换为紧凑模式并同步 appStore', async () => {
    const appStore = useAppStore()
    const wrapper = mount(ZwTableDensitySwitch, {
      global: { plugins: [ElementPlus] }
    })
    const buttons = wrapper.findAll('.density-btn')
    await buttons[0].trigger('click')

    expect(wrapper.emitted('change')?.[0]).toEqual(['compact'])
    expect(appStore.tableDensity).toBe('compact')
  })

  it('支持受控 modelValue 模式', async () => {
    const wrapper = mount(ZwTableDensitySwitch, {
      props: {
        modelValue: 'loose',
        global: false
      },
      global: { plugins: [ElementPlus] }
    })
    const buttons = wrapper.findAll('.density-btn')
    expect(buttons[2].classes()).toContain('active')
  })
})

describe('ZwFormAffixNav 组件测试', () => {
  it('正确渲染段落与编号并支持激活状态', () => {
    const sections = [
      { id: 'sec-base', title: '基础信息', required: true, completed: true },
      { id: 'sec-detail', title: '合同明细', required: true, completed: false },
      { id: 'sec-attach', title: '附件清单', completed: false }
    ]

    const wrapper = mount(ZwFormAffixNav, {
      props: { sections }
    })

    const items = wrapper.findAll('.nav-item')
    expect(items).toHaveLength(3)

    // 检查标尺编号
    expect(wrapper.find('.nav-index').text()).toBe('01')

    // 检查必填星号
    expect(items[0].find('.required-mark').exists()).toBe(true)
    expect(items[2].find('.required-mark').exists()).toBe(false)
  })
})
