/**
 * UserMultiSelector 组件测试（V2026_123 抄送人选人）
 * 覆盖：挂载加载候选人 / 选择后输出 number[] 并去重 / 清空输出空数组。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const mockCandidates = vi.hoisted(() => vi.fn(async (): Promise<any> => ({
  code: 200,
  data: [
    { id: '1900000000000000001', username: 'zhangsan', realName: '张三' },
    { id: '1900000000000000002', username: 'lisi', realName: '' },
  ],
})))

vi.mock('@/api/system', () => ({
  getUserCandidates: mockCandidates,
}))

import UserMultiSelector from '@/components/UserMultiSelector.vue'

describe('components/UserMultiSelector.vue 通用选人', () => {
  beforeEach(() => mockCandidates.mockClear())

  it('挂载时加载候选人（启用用户轻量视图）', async () => {
    const wrapper = mount(UserMultiSelector, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(mockCandidates).toHaveBeenCalledTimes(1)
    expect(mockCandidates).toHaveBeenCalledWith({ keyword: '', limit: 50 })
    wrapper.unmount()
  })

  it('选择后按字符串原样输出并去重（雪花 ID 禁止 Number 转换）', async () => {
    const wrapper = mount(UserMultiSelector, {
      props: { modelValue: [] },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('change', ['1900000000000000001', '1900000000000000001', '1900000000000000002'])
    await flushPromises()
    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toBeTruthy()
    expect(emitted![emitted!.length - 1][0]).toEqual(['1900000000000000001', '1900000000000000002'])
    wrapper.unmount()
  })

  it('清空时输出空数组', async () => {
    const wrapper = mount(UserMultiSelector, {
      props: { modelValue: ['1900000000000000001', '1900000000000000002'] },
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()
    wrapper.findComponent({ name: 'ElSelect' }).vm.$emit('change', [])
    await flushPromises()
    const emitted = wrapper.emitted('update:modelValue')!
    expect(emitted[emitted.length - 1][0]).toEqual([])
    wrapper.unmount()
  })
})
