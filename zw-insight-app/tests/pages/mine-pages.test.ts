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
    // 新增第0项为定位签到
    await items[0].trigger('click')
    expect((getUni() as any).navigateTo).toHaveBeenCalledWith({ url: '/pages/mine/sign' })

    await items[1].trigger('click')
    expect((getUni() as any).navigateTo).toHaveBeenCalledWith({ url: '/pages/mine/password' })

    await items[2].trigger('click')
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

  it('S3.1 ZwiField 化：三个密码框仍为 password 类型（防明文回归）+ label 常驻 + sticky 底条', async () => {
    const wrapper = mount(PasswordPage)

    // 上轮脚本化转换曾静默丢掉 type="password"，此处钉住不得回归
    const inputs = wrapper.findAll('.form-input')
    expect(inputs).toHaveLength(3)
    inputs.forEach((i) => expect(i.attributes('type')).toBe('password'))

    // critique P2：字段语义由常驻 label 承载，不再只靠 placeholder
    const labels = wrapper.findAll('.form-label').map((l) => l.text())
    expect(labels[0]).toContain('原密码')
    expect(labels[1]).toContain('新密码')
    expect(labels[2]).toContain('确认密码')
    // 必填标记
    expect(wrapper.findAll('.form-required')).toHaveLength(3)
    // 原页底游离 .tips 改为新密码行常驻 hint
    expect(wrapper.find('.form-hint').text()).toContain('至少 6 位')

    // critique P0：主操作进 sticky 底部条（ZwiFormPage .form-footer + .submit-btn 契约）
    expect(wrapper.find('.form-footer').exists()).toBe(true)
    expect(wrapper.find('.submit-btn').text()).toContain('确认修改')
    wrapper.unmount()
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
    const request = vi.fn(async () => ({ statusCode: 200, data: { code: 200, data: {} } }))
    ;(getUni() as any).request = request
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(SignPage)
    await flushPromises()

    await wrapper.find('.sign-btn').trigger('click')
    await flushPromises()

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请等待定位完成' }))
    // 挂载时 loadCalendar 会调 monthly 接口，仅断言签到接口未调
    expect(request.mock.calls.every((c: any[]) => c[0].method !== 'POST')).toBe(true)
    wrapper.unmount()
  })

  it('签到成功：todaySigned + isInRange 按后端 isInRange===1 判定', async () => {
    installLocation(30.123, 120.456)
    const request = vi.fn(async (options: any) => {
      if (options.url.includes('/site/sign/monthly')) {
        return { statusCode: 200, data: { code: 200, data: { signDays: 1, dailyRecords: [] } } }
      }
      if (options.url.includes('/site/sign')) {
        return { statusCode: 200, data: { code: 200, data: { isInRange: 1 } } }
      }
      return { statusCode: 200, data: { code: 200, data: {} } }
    })
    ;(getUni() as any).request = request
    getUni().setStorageSync('token', 'tk-sign')

    const wrapper = mount(SignPage)
    await flushPromises()

    await wrapper.find('.sign-btn').trigger('click')
    await flushPromises()

    const signCall = request.mock.calls.find((c: any[]) => c[0].method === 'POST')
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
      if (options.url.includes('/site/sign/monthly')) {
        return { statusCode: 200, data: { code: 200, data: { signDays: 2, dailyRecords: [{ date: todayStr, signed: true }] } } }
      }
      return { statusCode: 200, data: { code: 200, data: {} } }
    })

    const wrapper = mount(SignPage)
    await flushPromises()

    expect(wrapper.vm.calendarDays.length).toBe(new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate())
    expect(wrapper.vm.signDays).toBe(2)
    expect(wrapper.vm.todaySigned).toBe(true)
    expect(wrapper.find('.sign-btn').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('S3.1 项目选择器走 ZwiPickerField：label 常驻 + 点击触发器开弹层', async () => {
    installLocation(30.123, 120.456)
    ;(getUni() as any).request = vi.fn(async () => ({ statusCode: 200, data: { code: 200, data: {} } }))

    const wrapper = mount(SignPage)
    await flushPromises()

    expect(wrapper.find('.picker-trigger').exists()).toBe(true)
    expect(wrapper.find('.form-label').text()).toContain('打卡项目')
    expect(wrapper.find('.form-required').exists()).toBe(true)
    // 未拉到项目列表时为占位态（不靠 placeholder 承载语义）
    expect(wrapper.find('.picker-placeholder').exists()).toBe(true)

    expect(wrapper.vm.showProjectPicker).toBe(false)
    await wrapper.find('.picker-trigger').trigger('click')
    expect(wrapper.vm.showProjectPicker).toBe(true)
    wrapper.unmount()
  })
})
