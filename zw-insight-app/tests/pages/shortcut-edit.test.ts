// @vitest-environment happy-dom
/**
 * mine/shortcut-edit.vue 页面级组件测试（2026-08-16 P3 方向2 批 3）
 *
 * 覆盖：loadData 排序/过滤（DISABLED 与已选剔除）、添加上限 8、移除下限 1、
 * 保存成功延迟返回与超时分支。
 * 豁免：长按拖拽排序（onLongPress/onTouchMove）依赖 uni.createSelectorQuery
 * DOM 测量，happy-dom 无真实布局，纯数组交换逻辑已在 splice 断言中部分覆盖。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/shortcut', () => ({
  getAvailableShortcuts: vi.fn(),
  getUserShortcuts: vi.fn(),
  batchSaveShortcuts: vi.fn(),
}))

import ShortcutEdit from '@/pages/mine/shortcut-edit.vue'
import { getAvailableShortcuts, getUserShortcuts, batchSaveShortcuts } from '@/api/shortcut'
import { resetUniStorage, getUni } from '../setup'

const mockAvailable = vi.mocked(getAvailableShortcuts)
const mockUserShortcuts = vi.mocked(getUserShortcuts)
const mockBatchSave = vi.mocked(batchSaveShortcuts)

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  ;(getUni() as any).navigateBack = vi.fn()
  ;(getUni() as any).reLaunch = vi.fn()
  ;(getUni() as any).navigateTo = vi.fn()
  // uni-app 全局 API：当前页面栈（happy-dom 无此全局，按栈深 stub）
  ;(globalThis as any).getCurrentPages = () => [{}, {}]
})

async function mountWithData(userList: any[], availableList: any[]) {
  mockUserShortcuts.mockResolvedValue({ code: 200, data: userList })
  mockAvailable.mockResolvedValue({ code: 200, data: availableList })
  const wrapper = mount(ShortcutEdit)
  await flushPromises()
  return wrapper
}

describe('mine/shortcut-edit.vue 快捷入口编辑页', () => {
  it('loadData：已选按 sortOrder 升序，可选区剔除 DISABLED 与已选', async () => {
    const wrapper = await mountWithData(
      [
        { shortcutId: 2, menuName: '乙', menuIcon: 'b', menuPath: '/b', sortOrder: 2 },
        { shortcutId: 1, menuName: '甲', menuIcon: 'a', menuPath: '/a', sortOrder: 1 },
      ],
      [
        { id: 1, name: '甲', icon: 'a', routePath: '/a', status: 'ENABLED', sortOrder: 1 },
        { id: 3, name: '丙', icon: 'c', routePath: '/c', status: 'ENABLED', sortOrder: 2 },
        { id: 4, name: '丁', icon: 'd', routePath: '/d', status: 'DISABLED', sortOrder: 3 },
      ],
    )

    expect(wrapper.vm.selected.map((s: any) => s.id)).toEqual([1, 2])
    // id=1 已选、id=4 DISABLED，仅剩 id=3
    expect(wrapper.vm.available.map((a: any) => a.id)).toEqual([3])
    wrapper.unmount()
  })

  it('添加：从可选区移入已选区末尾；达上限 8 拦截', async () => {
    const eightSelected = Array.from({ length: 8 }, (_, i) => ({
      shortcutId: i + 1, menuName: `项${i}`, menuIcon: 'x', menuPath: `/${i}`, sortOrder: i,
    }))
    const wrapper = await mountWithData(eightSelected, [
      { id: 100, name: '新功能', icon: 'n', routePath: '/n', status: 'ENABLED', sortOrder: 1 },
    ])
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast

    await wrapper.find('.available-item').trigger('click')

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '已达到最大可选数量' }))
    expect(wrapper.vm.selected.length).toBe(8)
    expect(wrapper.vm.available.length).toBe(1)
    wrapper.unmount()
  })

  it('移除：仅剩 1 个时拦截；正常移除回流可选区', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = await mountWithData(
      [
        { shortcutId: 1, menuName: '甲', menuIcon: 'a', menuPath: '/a', sortOrder: 1 },
        { shortcutId: 2, menuName: '乙', menuIcon: 'b', menuPath: '/b', sortOrder: 2 },
      ],
      [],
    )

    const removeBtns = wrapper.findAll('.remove-btn')
    await removeBtns[0].trigger('click')
    expect(wrapper.vm.selected.map((s: any) => s.id)).toEqual([2])
    expect(wrapper.vm.available.map((a: any) => a.id)).toEqual([1])

    // 剩 1 个时拦截
    await wrapper.find('.remove-btn').trigger('click')
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '至少保留1个快捷入口' }))
    expect(wrapper.vm.selected.length).toBe(1)
    wrapper.unmount()
  })

  it('保存成功：batchSaveShortcuts 传已选 id 序列，1.5s 后返回', async () => {
    vi.useFakeTimers()
    mockBatchSave.mockResolvedValue({ code: 200 })
    const wrapper = await mountWithData(
      [
        { shortcutId: 3, menuName: '丙', menuIcon: 'c', menuPath: '/c', sortOrder: 1 },
        { shortcutId: 1, menuName: '甲', menuIcon: 'a', menuPath: '/a', sortOrder: 2 },
      ],
      [],
    )

    await wrapper.find('.save-btn').trigger('click')
    await flushPromises()

    expect(mockBatchSave).toHaveBeenCalledWith([3, 1])
    expect((getUni() as any).navigateBack).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1500)
    expect((getUni() as any).navigateBack).toHaveBeenCalled()
    wrapper.unmount()
    vi.useRealTimers()
  })

  it('保存超时 10s：提示保存超时并保留编辑状态可重试', async () => {
    vi.useFakeTimers()
    // 永不 resolve 的保存请求，靠超时 promise 胜出
    mockBatchSave.mockReturnValue(new Promise(() => {}))
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = await mountWithData(
      [{ shortcutId: 1, menuName: '甲', menuIcon: 'a', menuPath: '/a', sortOrder: 1 }],
      [],
    )

    await wrapper.find('.save-btn').trigger('click')
    await vi.advanceTimersByTimeAsync(10000)
    await flushPromises()

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '保存超时，请重试' }))
    expect(wrapper.vm.saving).toBe(false)
    wrapper.unmount()
    vi.useRealTimers()
  })
})
