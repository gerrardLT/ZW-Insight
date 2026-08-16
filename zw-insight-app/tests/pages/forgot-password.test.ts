// @vitest-environment happy-dom
/**
 * login/forgot-password.vue 页面级组件测试（2026-08-16 P3 方向2 批 3）
 *
 * 三步流程：验证手机号（sendResetCode）→ 校验验证码（verifyResetCode）→
 * 设置新密码（resetPassword + 延迟 reLaunch 登录页）。
 * phone/code 为 input[type=number]，经组件实例赋字符串值（uni-app 运行时
 * input 值恒为 string，Vue 原生 number 转换属环境差异，同 login 页适配）。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
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

import ForgotPassword from '@/pages/login/forgot-password.vue'
import { sendResetCode, verifyResetCode, resetPassword } from '@/api/auth'
import { resetUniStorage, getUni } from '../setup'

const mockSendCode = vi.mocked(sendResetCode)
const mockVerifyCode = vi.mocked(verifyResetCode)
const mockReset = vi.mocked(resetPassword)

function mountPage() {
  return mount(ForgotPassword)
}

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  ;(getUni() as any).reLaunch = vi.fn()
})

describe('login/forgot-password.vue 找回密码三步流程', () => {
  it('第一步：手机号格式非法拦截，不调 sendResetCode', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mountPage()

    wrapper.vm.form.phone = '12345'
    await wrapper.find('.primary-btn').trigger('click')
    await flushPromises()

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '手机号格式不正确' }))
    expect(mockSendCode).not.toHaveBeenCalled()
    expect(wrapper.vm.active).toBe(0)
    wrapper.unmount()
  })

  it('发送验证码成功进入第二步，展示脱敏手机号', async () => {
    mockSendCode.mockResolvedValue({ code: 200 })
    const wrapper = mountPage()

    wrapper.vm.form.phone = '13800138000'
    await wrapper.find('.primary-btn').trigger('click')
    await flushPromises()

    expect(mockSendCode).toHaveBeenCalledWith('13800138000')
    expect(wrapper.vm.active).toBe(1)
    expect(wrapper.text()).toContain('138****8000')
    wrapper.unmount()
  })

  it('第二步：验证码非 6 位数字拦截；上一步返回第一步', async () => {
    mockSendCode.mockResolvedValue({ code: 200 })
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mountPage()

    wrapper.vm.form.phone = '13800138000'
    await wrapper.find('.primary-btn').trigger('click')
    await flushPromises()

    wrapper.vm.form.code = '12a'
    const buttons = wrapper.findAll('button')
    const nextBtn = buttons.find((b) => b.text().includes('下一步'))!
    await nextBtn.trigger('click')
    await flushPromises()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '验证码为 6 位数字' }))
    expect(mockVerifyCode).not.toHaveBeenCalled()

    const prevBtn = buttons.find((b) => b.text().includes('上一步'))!
    await prevBtn.trigger('click')
    expect(wrapper.vm.active).toBe(0)
    wrapper.unmount()
  })

  it('校验验证码成功进入第三步', async () => {
    mockSendCode.mockResolvedValue({ code: 200 })
    mockVerifyCode.mockResolvedValue({ code: 200 })
    const wrapper = mountPage()

    wrapper.vm.form.phone = '13800138000'
    await wrapper.find('.primary-btn').trigger('click')
    await flushPromises()

    wrapper.vm.form.code = '654321'
    const nextBtn = wrapper.findAll('button').find((b) => b.text().includes('下一步'))!
    await nextBtn.trigger('click')
    await flushPromises()

    expect(mockVerifyCode).toHaveBeenCalledWith('13800138000', '654321')
    expect(wrapper.vm.active).toBe(2)
    wrapper.unmount()
  })

  it('第三步：密码复杂度不足/两次不一致拦截', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    mockSendCode.mockResolvedValue({ code: 200 })
    mockVerifyCode.mockResolvedValue({ code: 200 })
    const wrapper = mountPage()

    wrapper.vm.form.phone = '13800138000'
    await wrapper.find('.primary-btn').trigger('click')
    await flushPromises()
    wrapper.vm.form.code = '654321'
    const nextBtn = wrapper.findAll('button').find((b) => b.text().includes('下一步'))!
    await nextBtn.trigger('click')
    await flushPromises()

    const resetBtn = wrapper.findAll('button').find((b) => b.text().includes('重置密码'))!

    wrapper.vm.form.newPassword = 'short1'
    await resetBtn.trigger('click')
    await flushPromises()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '密码需 8-20 位且含字母和数字' }))

    wrapper.vm.form.newPassword = 'abcd1234'
    wrapper.vm.form.confirmPassword = 'abcd9999'
    await resetBtn.trigger('click')
    await flushPromises()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '两次输入的密码不一致' }))
    expect(mockReset).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('重置成功：resetPassword 三参 + 1.5s 后 reLaunch 登录页', async () => {
    vi.useFakeTimers()
    mockSendCode.mockResolvedValue({ code: 200 })
    mockVerifyCode.mockResolvedValue({ code: 200 })
    mockReset.mockResolvedValue({ code: 200 })
    const wrapper = mountPage()

    wrapper.vm.form.phone = '13800138000'
    await wrapper.find('.primary-btn').trigger('click')
    await flushPromises()
    wrapper.vm.form.code = '654321'
    await wrapper.findAll('button').find((b) => b.text().includes('下一步'))!.trigger('click')
    await flushPromises()

    wrapper.vm.form.newPassword = 'abcd1234'
    wrapper.vm.form.confirmPassword = 'abcd1234'
    await wrapper.findAll('button').find((b) => b.text().includes('重置密码'))!.trigger('click')
    await flushPromises()

    expect(mockReset).toHaveBeenCalledWith('13800138000', '654321', 'abcd1234')
    expect((getUni() as any).reLaunch).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1500)
    expect((getUni() as any).reLaunch).toHaveBeenCalledWith({ url: '/pages/login/index' })
    wrapper.unmount()
    vi.useRealTimers()
  })
})
