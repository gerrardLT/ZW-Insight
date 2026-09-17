// @vitest-environment happy-dom
/**
 * ZwUI 业务壳组件库测试（Stage 0.3，2026-09-17）
 * 钉住两类契约：①契约 class 渲染（页面既有测试依赖）②行为语义（插槽/事件/条件渲染）
 */
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ZwiPage from '@/components/zwi/ZwiPage.vue'
import ZwiSectionCard from '@/components/zwi/ZwiSectionCard.vue'
import ZwiCell from '@/components/zwi/ZwiCell.vue'
import ZwiStatCard from '@/components/zwi/ZwiStatCard.vue'
import ZwiEmptyState from '@/components/zwi/ZwiEmptyState.vue'
import ZwiFormPage from '@/components/zwi/ZwiFormPage.vue'
import ZwiField from '@/components/zwi/ZwiField.vue'
import ZwiPickerField from '@/components/zwi/ZwiPickerField.vue'

describe('ZwiPage 页面骨架', () => {
  it('渲染 .zw-page 契约 class 并透传默认插槽', () => {
    const wrapper = mount(ZwiPage, { slots: { default: '<view class="inner-x">内容</view>' } })
    expect(wrapper.find('.zw-page').exists()).toBe(true)
    expect(wrapper.find('.inner-x').exists()).toBe(true)
  })
})

describe('ZwiSectionCard 分区卡', () => {
  it('渲染契约 class：.section + .zw-card + .section-title，标题与眉题', () => {
    const wrapper = mount(ZwiSectionCard, { props: { title: '常用功能', eyebrow: 'Shortcuts' } })
    expect(wrapper.find('.section').exists()).toBe(true)
    expect(wrapper.find('.zw-card').exists()).toBe(true)
    expect(wrapper.find('.section-title').text()).toBe('常用功能')
    expect(wrapper.find('.eyebrow-cap').text()).toBe('Shortcuts')
  })

  it('action 插槽渲染（右侧操作位）', () => {
    const wrapper = mount(ZwiSectionCard, {
      props: { title: '消息' },
      slots: { action: '<text class="act-x">信息中心</text>' },
    })
    expect(wrapper.find('.act-x').exists()).toBe(true)
  })

  it('无标题无 action 时不渲染头部（纯内容卡）', () => {
    const wrapper = mount(ZwiSectionCard, { slots: { default: '<view>body</view>' } })
    expect(wrapper.find('.section-header').exists()).toBe(false)
  })
})

describe('ZwiCell 列表行', () => {
  it('渲染 .cell-row：title/desc/value/箭头', () => {
    const wrapper = mount(ZwiCell, {
      props: { title: '付款审批', desc: '申请人：张三', value: '2026-09-17', arrow: true },
    })
    expect(wrapper.find('.cell-row').exists()).toBe(true)
    expect(wrapper.find('.cell-title').text()).toBe('付款审批')
    expect(wrapper.find('.cell-desc').text()).toContain('张三')
    expect(wrapper.find('.cell-value').text()).toBe('2026-09-17')
    expect(wrapper.find('.cell-arrow').exists()).toBe(true)
  })

  it('clickable 时点击触发 click 事件；last 去分隔线', async () => {
    const wrapper = mount(ZwiCell, { props: { title: '行', clickable: true, last: true } })
    expect(wrapper.find('.cell-clickable').exists()).toBe(true)
    expect(wrapper.find('.cell-last').exists()).toBe(true)
    await wrapper.find('.cell-row').trigger('click')
    expect(wrapper.emitted('click')).toHaveLength(1)
  })

  it('S3.1 kv 变体：详情页键值行挂 .cell-kv（语义轻重反转），默认 row 不挂', () => {
    const kv = mount(ZwiCell, { props: { title: '项目名称', value: '滨江花园一期', variant: 'kv' } })
    expect(kv.find('.cell-kv').exists()).toBe(true)
    expect(kv.find('.cell-title').text()).toBe('项目名称')
    expect(kv.find('.cell-value').text()).toBe('滨江花园一期')

    const row = mount(ZwiCell, { props: { title: '付款审批', value: '09-17' } })
    expect(row.find('.cell-kv').exists()).toBe(false)
  })

  it('kv 变体不自动变成可点击（详情页只读行不应有按压反馈）', async () => {
    const wrapper = mount(ZwiCell, { props: { title: '合同金额', value: '200.00万', variant: 'kv' } })
    expect(wrapper.find('.cell-clickable').exists()).toBe(false)
    await wrapper.find('.cell-row').trigger('click')
    expect(wrapper.emitted('click')).toBeUndefined()
  })

  it('#value 插槽可承载复杂值区（未读点/加重金额），与 value prop 共存时两者均渲染', () => {
    const wrapper = mount(ZwiCell, {
      props: { title: '实际发生', variant: 'kv', value: 'prop 值' },
      slots: { value: '<text class="slot-x">插槽值</text>' },
    })
    expect(wrapper.find('.slot-x').text()).toBe('插槽值')
    expect(wrapper.find('.cell-value').text()).toBe('prop 值')
  })
})

describe('ZwiStatCard 数值卡', () => {
  it('渲染 .stat-card/.stat-value/.stat-label（契约 class）+ 单位', () => {
    const wrapper = mount(ZwiStatCard, { props: { value: '5,150', unit: '万', label: '合同总额' } })
    expect(wrapper.find('.stat-card').exists()).toBe(true)
    expect(wrapper.find('.stat-value').text()).toContain('5,150')
    expect(wrapper.find('.stat-unit').text()).toBe('万')
    expect(wrapper.find('.stat-label').text()).toBe('合同总额')
  })

  it('calm 模式 tone 着色数字；solid 模式浅彩底', () => {
    const calm = mount(ZwiStatCard, { props: { value: '38', label: '进行中', tone: 'brand' } })
    expect(calm.find('.stat-value-brand').exists()).toBe(true)
    const solid = mount(ZwiStatCard, {
      props: { value: '3', label: '待结算', mode: 'solid', tone: 'warning' },
    })
    expect(solid.find('.stat-solid-warning').exists()).toBe(true)
  })
})

describe('ZwiEmptyState 四变体空态', () => {
  it('渲染 .empty 契约 class + 双主题插画 + 默认文案', () => {
    const wrapper = mount(ZwiEmptyState)
    expect(wrapper.find('.empty').exists()).toBe(true)
    expect(wrapper.find('.empty-light').exists()).toBe(true)
    expect(wrapper.find('.empty-dark').exists()).toBe(true)
    expect(wrapper.find('.empty-text').text()).toBe('暂无数据')
  })

  it('四变体默认文案 + 自定义描述覆盖', () => {
    expect(mount(ZwiEmptyState, { props: { type: 'error' } }).find('.empty-text').text()).toContain('加载失败')
    expect(mount(ZwiEmptyState, { props: { type: 'offline' } }).find('.empty-text').text()).toContain('联网')
    expect(mount(ZwiEmptyState, { props: { type: 'permission' } }).find('.empty-text').text()).toContain('权限')
    expect(
      mount(ZwiEmptyState, { props: { description: '暂无待办任务' } }).find('.empty-text').text()
    ).toBe('暂无待办任务')
  })

  it('action 插槽渲染（空态只教一个动作）', () => {
    const wrapper = mount(ZwiEmptyState, {
      slots: { action: '<text class="cta-x">重试</text>' },
    })
    expect(wrapper.find('.empty-action').exists()).toBe(true)
    expect(wrapper.find('.cta-x').text()).toBe('重试')
  })
})

describe('ZwiFormPage 表单页骨架', () => {
  it('sticky 底部条 + .submit-btn 契约 class + submit 事件', async () => {
    const wrapper = mount(ZwiFormPage, {
      props: { submitText: '提交入库', loading: false, disabled: false },
      global: { stubs: { 'wd-button': { template: '<button class="submit-btn"><slot /></button>' } } },
    })
    expect(wrapper.find('.form-footer').exists()).toBe(true)
    const btn = wrapper.find('.submit-btn')
    expect(btn.exists()).toBe(true)
    expect(btn.text()).toContain('提交入库')
    await btn.trigger('click')
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('submitText 为空时不渲染底部条（纯内容页）', () => {
    const wrapper = mount(ZwiFormPage, { slots: { default: '<view>内容</view>' } })
    expect(wrapper.find('.form-footer').exists()).toBe(false)
  })
})

describe('ZwiField 表单行', () => {
  it('渲染契约 class：.form-item/.form-label/.form-input；required 标记', () => {
    const wrapper = mount(ZwiField, { props: { label: '材料名称', required: true, modelValue: '钢筋' } })
    expect(wrapper.find('.form-item').exists()).toBe(true)
    expect(wrapper.find('.form-label').text()).toContain('材料名称')
    expect(wrapper.find('.form-required').exists()).toBe(true)
    const input = wrapper.find('.form-input')
    expect(input.exists()).toBe(true)
    expect((input.element as HTMLInputElement).value).toBe('钢筋')
  })

  it('error 显示校验错误并置灰 hint；无 error 显示 hint', () => {
    const err = mount(ZwiField, { props: { label: '数量', hint: '整数或小数', error: '必填' } })
    expect(err.find('.form-error').text()).toBe('必填')
    expect(err.find('.form-hint').exists()).toBe(false)
    expect(err.find('.form-item-error').exists()).toBe(true)
    const ok = mount(ZwiField, { props: { label: '数量', hint: '整数或小数' } })
    expect(ok.find('.form-hint').text()).toBe('整数或小数')
  })

  it('默认插槽可覆盖内置 input（自定义控件行）', () => {
    const wrapper = mount(ZwiField, {
      props: { label: '日期' },
      slots: { default: '<view class="custom-x">日期控件</view>' },
    })
    expect(wrapper.find('.custom-x').exists()).toBe(true)
    expect(wrapper.find('.form-input').exists()).toBe(false)
  })
})

describe('ZwiPickerField 选择行', () => {
  it('渲染 .form-item/.form-label/.picker-trigger；已选值与占位态', () => {
    const picked = mount(ZwiPickerField, {
      props: { label: '所属项目', displayValue: '滨江花园一期' },
    })
    expect(picked.find('.form-label').text()).toContain('所属项目')
    expect(picked.find('.picker-value').text()).toBe('滨江花园一期')
    expect(picked.find('.picker-placeholder').exists()).toBe(false)

    const empty = mount(ZwiPickerField, { props: { label: '所属项目', placeholder: '请选择项目' } })
    expect(empty.find('.picker-placeholder').exists()).toBe(true)
    expect(empty.find('.picker-value').text()).toBe('请选择项目')
  })

  it('点击行触发 open 事件', async () => {
    const wrapper = mount(ZwiPickerField, { props: { label: '项目' } })
    await wrapper.find('.form-item').trigger('click')
    expect(wrapper.emitted('open')).toHaveLength(1)
  })
})
