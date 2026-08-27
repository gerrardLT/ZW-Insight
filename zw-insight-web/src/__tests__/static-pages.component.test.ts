/**
 * error/403.vue、error/404.vue 与 system/monitor 占位页组件测试
 *（2026-08-15 P3 收尾批：静态页渲染与导航行为钉住）
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { HomeFilled, RefreshRight } from '@/components/icons/registry'
import { createPinia, setActivePinia } from 'pinia'
import { routerKey } from 'vue-router'

import Page403 from '@/views/error/403.vue'
import Page404 from '@/views/error/404.vue'
import Monitor from '@/views/system/monitor/index.vue'
import { useUserStore } from '@/stores/user'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

const mockPush = vi.fn()
const mockBack = vi.fn()
// 403.vue 引入 user store（重新登录自愈入口）后需要 pinia 环境
const pinia = createPinia()
setActivePinia(pinia)
const mountOpts = {
  global: {
    plugins: [ElementPlus, pinia],
    mocks: { $router: { push: mockPush, back: mockBack } },
    // 403.vue script setup 内 useRouter() 走 inject(routerKey)，需单独 provide
    provide: { [routerKey]: { push: mockPush, back: mockBack } },
    components: { HomeFilled, RefreshRight },
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

  it('重新登录按钮清除登录态并跳 /login（陈旧态自愈入口）', async () => {
    mockPush.mockClear()
    useUserStore().setToken('tk-stale')
    wrapper = mount(Page403, mountOpts)
    await wrapper.findAll('button').find((b: any) => b.text().includes('重新登录'))!.trigger('click')
    expect(localStorage.getItem('token')).toBeNull()
    expect(mockPush).toHaveBeenCalledWith('/login')
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
