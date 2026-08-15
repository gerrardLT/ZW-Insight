/**
 * error/403.vue、error/404.vue 与 system/monitor 占位页组件测试
 *（2026-08-15 P3 收尾批：静态页渲染与导航行为钉住）
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { HomeFilled } from '@element-plus/icons-vue'

import Page403 from '@/views/error/403.vue'
import Page404 from '@/views/error/404.vue'
import Monitor from '@/views/system/monitor/index.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

const mockPush = vi.fn()
const mockBack = vi.fn()
const mountOpts = {
  global: {
    plugins: [ElementPlus],
    mocks: { $router: { push: mockPush, back: mockBack } },
    components: { HomeFilled },
  },
}

describe('error/403.vue', () => {
  it('渲染 403 文案与操作按钮', () => {
    wrapper = mount(Page403, mountOpts)
    expect(wrapper.text()).toContain('403')
    expect(wrapper.text()).toContain('无访问权限')
    expect(wrapper.text()).toContain('返回首页')
  })

  it('返回首页按钮调 router.push(/)', async () => {
    mockPush.mockClear()
    wrapper = mount(Page403, mountOpts)
    await wrapper.findAll('button').find((b: any) => b.text().includes('返回首页'))!.trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/')
  })

  it('返回上一页按钮调 router.back', async () => {
    mockBack.mockClear()
    wrapper = mount(Page403, mountOpts)
    await wrapper.findAll('button').find((b: any) => b.text().includes('返回上一页'))!.trigger('click')
    expect(mockBack).toHaveBeenCalled()
  })
})

describe('error/404.vue', () => {
  it('渲染 404 文案与操作按钮', () => {
    wrapper = mount(Page404, mountOpts)
    expect(wrapper.text()).toContain('404')
    expect(wrapper.text()).toContain('返回首页')
  })

  it('返回首页按钮调 router.push(/)', async () => {
    mockPush.mockClear()
    wrapper = mount(Page404, mountOpts)
    await wrapper.findAll('button').find((b: any) => b.text().includes('返回首页'))!.trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/')
  })
})

describe('system/monitor/index.vue 占位页', () => {
  it('渲染占位提示（任务 10.3 待实现现状钉住）', () => {
    wrapper = mount(Monitor, { global: { plugins: [ElementPlus] } })
    expect(wrapper.text()).toContain('系统监控')
    expect(wrapper.text()).toContain('待实现')
  })
})
