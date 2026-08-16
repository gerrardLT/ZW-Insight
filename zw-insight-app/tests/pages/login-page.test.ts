// @vitest-environment happy-dom
/**
 * app 页面级组件测试基建验证：login/index.vue（2026-08-16 P3 方向2）
 *
 * uni-app 页面在 happy-dom 下用标准 vue-test-utils 挂载（uni 专有标签经
 * vitest.config.ts isCustomElement 声明为原生未知元素直接渲染）。
 * api/auth 走 vi.mock（记录调用参数），pinia 真实实例，uni.* 走 setup.ts 桩。
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

import LoginPage from '@/pages/login/index.vue'
import { login, sendSmsCaptcha } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { resetUniStorage, getUni } from '../setup'

const mockLogin = vi.mocked(login)
const mockSendSms = vi.mocked(sendSmsCaptcha)

function mountPage() {
  return mount(LoginPage)
}

// 环境差异适配：Vue 原生 input[type=number] 的 v-model 会把值转 number，
// 而 uni-app 运行时 input 事件值恒为 string（生产上 phone.trim() 安全）。
// 故手机号/验证码经组件实例赋字符串值，避免 happy-dom 下的数字转换差异。
function setSmsField(wrapper: any, field: 'phone' | 'smsCode', value: string) {
  wrapper.vm.smsForm[field] = value
}

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  ;(getUni() as any).switchTab = vi.fn()
  ;(getUni() as any).navigateTo = vi.fn()
})

describe('login/index.vue 登录页', () => {
  it('默认密码登录 tab，切换短信 tab 显示手机号/验证码表单', async () => {
    const wrapper = mountPage()
    expect(wrapper.text()).toContain('密码登录')
    expect(wrapper.find('input[placeholder="请输入用户名"]').exists()).toBe(true)
    expect(wrapper.find('input[placeholder="请输入手机号"]').exists()).toBe(false)

    const tabs = wrapper.findAll('.tab-item')
    await tabs[1].trigger('click')
    expect(wrapper.find('input[placeholder="请输入手机号"]').exists()).toBe(true)
    expect(wrapper.find('input[placeholder="请输入验证码"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('密码登录：用户名或密码为空时拦截并提示，不调 login', async () => {
    const wrapper = mountPage()
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast

    await wrapper.find('.login-btn').trigger('click')
    await flushPromises()

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入用户名和密码' }))
    expect(mockLogin).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('密码登录成功：loginType=PASSWORD + 写 token + switchTab 首页', async () => {
    mockLogin.mockResolvedValue({ code: 200, data: { token: 'tk-1', username: 'admin' } })
    const wrapper = mountPage()

    await wrapper.find('input[placeholder="请输入用户名"]').setValue('admin')
    await wrapper.find('input[placeholder="请输入密码"]').setValue('123456')
    await wrapper.find('.login-btn').trigger('click')
    await flushPromises()

    expect(mockLogin).toHaveBeenCalledWith(
      expect.objectContaining({ username: 'admin', password: '123456', loginType: 'PASSWORD' }),
    )
    expect(useUserStore().token).toBe('tk-1')
    expect((getUni() as any).switchTab).toHaveBeenCalledWith({ url: '/pages/home/index' })
    wrapper.unmount()
  })

  it('发送验证码：手机号格式非法时拦截，不调 sendSmsCaptcha', async () => {
    const wrapper = mountPage()
    await wrapper.findAll('.tab-item')[1].trigger('click')
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast

    await wrapper.find('input[placeholder="请输入手机号"]').setValue('12345')
    ;(wrapper.vm as any).smsForm.phone = '12345'
    await wrapper.find('.sms-btn').trigger('click')
    await flushPromises()

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '手机号格式不正确' }))
    expect(mockSendSms).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('发送验证码成功进入 60s 倒计时，按钮禁用并显示剩余秒数', async () => {
    vi.useFakeTimers()
    mockSendSms.mockResolvedValue({ code: 200 })
    const wrapper = mountPage()
    await wrapper.findAll('.tab-item')[1].trigger('click')

    setSmsField(wrapper, 'phone', '13800138000')
    await wrapper.find('.sms-btn').trigger('click')
    // 只 flush 微任务（sendSmsCaptcha promise），不推进定时器，
    // 否则倒计时 60 次 interval 全跑完 smsCooldown 归零 disabled 解除
    await flushPromises()

    expect(mockSendSms).toHaveBeenCalledWith('13800138000')
    expect(wrapper.find('.sms-btn').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.sms-btn').text()).toContain('60s后重发')
    wrapper.unmount()
    vi.useRealTimers()
  })

  it('短信登录：验证码非 6 位时拦截，不调 login', async () => {
    const wrapper = mountPage()
    await wrapper.findAll('.tab-item')[1].trigger('click')
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast

    setSmsField(wrapper, 'phone', '13800138000')
    setSmsField(wrapper, 'smsCode', '123')
    await wrapper.find('.login-btn').trigger('click')
    await flushPromises()

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '验证码为6位数字' }))
    expect(mockLogin).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('短信登录成功：loginType=SMS + 写 token + switchTab 首页', async () => {
    mockLogin.mockResolvedValue({ code: 200, data: { token: 'tk-2', username: 'u2' } })
    const wrapper = mountPage()
    await wrapper.findAll('.tab-item')[1].trigger('click')

    setSmsField(wrapper, 'phone', '13800138000')
    setSmsField(wrapper, 'smsCode', '654321')
    await wrapper.find('.login-btn').trigger('click')
    await flushPromises()

    expect(mockLogin).toHaveBeenCalledWith(
      expect.objectContaining({ phone: '13800138000', smsCode: '654321', loginType: 'SMS' }),
    )
    expect(useUserStore().token).toBe('tk-2')
    expect((getUni() as any).switchTab).toHaveBeenCalledWith({ url: '/pages/home/index' })
    wrapper.unmount()
  })
})
