// @vitest-environment happy-dom
/**
 * mine 域页面级组件测试（2026-08-16 P3 方向2 批 2）
 *
 * 覆盖 mine/index.vue（用户信息/导航/退出确认流）、mine/password.vue
 *（四段校验 + 修改成功延迟登出）、mine/sign.vue（定位守卫/签到/日历回填）。
 * uni.showModal/getLocation/request 经 setup.ts 桩按需覆盖（捕获回调手工驱动）。
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/auth', () => ({
  login: vi.fn(),
  sendSmsCaptcha: vi.fn(),
  logout: vi.fn(),
  changePassword: vi.fn(),
  sendResetCode: vi.fn(),
  verifyResetCode: vi.fn(),
  resetPassword: vi.fn(),
}))

import MineIndex from '@/pages/mine/index.vue'
import PasswordPage from '@/pages/mine/password.vue'
import SignPage from '@/pages/mine/sign.vue'
import { logout as logoutApi, changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { resetUniStorage, getUni } from '../setup'

const mockLogoutApi = vi.mocked(logoutApi)
const mockChangePassword = vi.mocked(changePassword)

/** 覆盖 uni.showModal：记录最近一次 options，可手工触发 success */
function installModalSpy() {
  const calls: any[] = []
  ;(getUni() as any).showModal = (options: any) => calls.push(options)
  return calls
}

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  ;(getUni() as any).navigateTo = vi.fn()
  ;(getUni() as any).switchTab = vi.fn()
  ;(getUni() as any).reLaunch = vi.fn()
})

describe('mine/index.vue 我的页', () => {
  it('展示用户姓名/角色与头像后两字，未登录显示默认文案', async () => {
    const wrapper = mount(MineIndex)
    expect(wrapper.text()).toContain('未登录')

    useUserStore().setUserInfo({ realName: '张三丰', roleName: '项目经理' } as any)
    await nextTick()
    expect(wrapper.text()).toContain('张三丰')
    expect(wrapper.text()).toContain('项目经理')
    expect(wrapper.find('.avatar-text').text()).toBe('三丰')
    wrapper.unmount()
  })

  it('修改密码菜单跳转密码页，关于我们弹 showModal', async () => {
    const modalCalls = installModalSpy()
    const wrapper = mount(MineIndex)

    const items = wrapper.findAll('.menu-item')
    await items[0].trigger('click')
    expect((getUni() as any).navigateTo).toHaveBeenCalledWith({ url: '/pages/mine/password' })

    await items[1].trigger('click')
    expect(modalCalls[0]).toMatchObject({ title: '关于中维智营', showCancel: false })
    wrapper.unmount()
  })

  it('退出登录：确认后调 logoutApi + store.logout；取消则不调', async () => {
    mockLogoutApi.mockResolvedValue({ code: 200 })
    const modalCalls = installModalSpy()
    const store = useUserStore()
    store.setToken('tk-x')
    const wrapper = mount(MineIndex)

    await wrapper.find('.logout-btn').trigger('click')
    expect(modalCalls).toHaveLength(1)

    // 取消分支
    modalCalls[0].success({ confirm: false })
    await flushPromises()
    expect(mockLogoutApi).not.toHaveBeenCalled()
    expect(store.token).toBe('tk-x')

    // 确认分支
    modalCalls[0].success({ confirm: true })
    await flushPromises()
    expect(mockLogoutApi).toHaveBeenCalled()
    expect(store.token).toBe('')
    wrapper.unmount()
  })
})

describe('mine/password.vue 修改密码页', () => {
  async function fillAndSubmit(wrapper: any, form: Partial<Record<'oldPassword' | 'newPassword' | 'confirmPassword', string>>) {
    Object.assign(wrapper.vm.form, { oldPassword: '', newPassword: '', confirmPassword: '' }, form)
    await wrapper.find('.submit-btn').trigger('click')
    await flushPromises()
  }

  it('原密码为空拦截', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(PasswordPage)
    await fillAndSubmit(wrapper, {})
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入原密码' }))
    expect(mockChangePassword).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('新密码少于 6 位拦截', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(PasswordPage)
    await fillAndSubmit(wrapper, { oldPassword: 'old', newPassword: '12345' })
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '新密码至少6位' }))
    expect(mockChangePassword).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('两次密码不一致拦截', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(PasswordPage)
    await fillAndSubmit(wrapper, { oldPassword: 'old', newPassword: '123456', confirmPassword: '654321' })
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '两次密码不一致' }))
    expect(mockChangePassword).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('校验通过调 changePassword，成功后延迟 1.5s 登出', async () => {
    vi.useFakeTimers()
    mockChangePassword.mockResolvedValue({ code: 200 })
    const wrapper = mount(PasswordPage)
    useUserStore().setToken('tk-keep')

    await fillAndSubmit(wrapper, { oldPassword: 'old', newPassword: 'new123456', confirmPassword: 'new123456' })

    expect(mockChangePassword).toHaveBeenCalledWith({ oldPassword: 'old', newPassword: 'new123456' })
    expect(useUserStore().token).toBe('tk-keep')

    vi.advanceTimersByTime(1500)
    expect(useUserStore().token).toBe('')
    wrapper.unmount()
    vi.useRealTimers()
  })
})

describe('mine/sign.vue 签到页', () => {
  beforeEach(() => {
    // sign.vue onMounted 有 setInterval 时钟更新，用 fake timers 防挂起
    vi.useFakeTimers()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  function installLocation(latitude: number, longitude: number) {
    ;(getUni() as any).getLocation = (options: any) => {
      options.success({ latitude, longitude })
    }
  }

  it('未定位完成点击签到被拦截', async () => {
    ;(getUni() as any).getLocation = () => {} // 定位不回调
    const request = vi.fn(async () => ({ data: { code: 200, data: {} } }))
    ;(getUni() as any).request = request
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(SignPage)
    await flushPromises()

    await wrapper.find('.sign-btn').trigger('click')
    await flushPromises()

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请等待定位完成' }))
    // 挂载时 loadCalendar 会调 monthly 接口，仅断言签到接口未调
    expect(request.mock.calls.every((c: any[]) => c[0].url !== '/api/v1/site/sign')).toBe(true)
    wrapper.unmount()
  })

  it('签到成功：todaySigned + isInRange 按后端 isInRange===1 判定', async () => {
    installLocation(30.123, 120.456)
    const request = vi.fn(async (options: any) => {
      if (options.url === '/api/v1/site/sign') {
        return { data: { code: 200, data: { isInRange: 1 } } }
      }
      return { data: { code: 200, data: { signDays: 1, dailyRecords: [] } } }
    })
    ;(getUni() as any).request = request
    getUni().setStorageSync('token', 'tk-sign')

    const wrapper = mount(SignPage)
    await flushPromises()

    await wrapper.find('.sign-btn').trigger('click')
    await flushPromises()

    const signCall = request.mock.calls.find((c: any[]) => c[0].url === '/api/v1/site/sign')
    expect(signCall).toBeTruthy()
    expect(signCall![0].header.Authorization).toBe('Bearer tk-sign')
    expect(signCall![0].data.latitude).toBe(30.123)
    expect(wrapper.vm.todaySigned).toBe(true)
    expect(wrapper.vm.isInRange).toBe(true)
    expect(wrapper.find('.sign-btn').text()).toContain('今日已签到')
    wrapper.unmount()
  })

  it('日历回填：monthly dailyRecords 标记已签日，今日已签则按钮禁用', async () => {
    installLocation(30.123, 120.456)
    const now = new Date()
    const todayStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
    ;(getUni() as any).request = vi.fn(async (options: any) => {
      if (options.url === '/api/v1/site/sign/monthly') {
        return { data: { code: 200, data: { signDays: 2, dailyRecords: [{ date: todayStr, signed: true }] } } }
      }
      return { data: { code: 200, data: {} } }
    })

    const wrapper = mount(SignPage)
    await flushPromises()

    expect(wrapper.vm.calendarDays.length).toBe(new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate())
    expect(wrapper.vm.signDays).toBe(2)
    expect(wrapper.vm.todaySigned).toBe(true)
    expect(wrapper.find('.sign-btn').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })
})
