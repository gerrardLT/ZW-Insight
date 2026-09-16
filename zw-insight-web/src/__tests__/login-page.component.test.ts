/**
 * login/index.vue 登录页组件测试（2026-08-15 P3 收尾批 11）
 * forgot-password 已有既有测试；本文件补登录主流程。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import { createPinia, setActivePinia } from 'pinia'

const { mockPost, mockImageCaptcha, mockSendSms, mockPush } = vi.hoisted(() => ({
  mockPost: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockImageCaptcha: vi.fn(async (): Promise<any> => ({ code: 200, data: { uuid: 'cap-1', image: 'data:image/png;base64,x' } })),
  mockSendSms: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockPush: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  default: { post: mockPost, get: vi.fn() },
}))
vi.mock('@/api/captcha', () => ({
  getImageCaptcha: mockImageCaptcha,
  sendSmsCaptcha: mockSendSms,
}))
vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ push: mockPush }),
    useRoute: () => ({ query: {}, params: {} }),
  }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
  }
})

import Login from '@/views/login/index.vue'
import { useUserStore } from '@/stores/user'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

async function mountPage() {
  setActivePinia(createPinia())
  wrapper = mount(Login, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('login/index.vue 登录页', () => {
  it('挂载加载图形验证码', async () => {
    await mountPage()
    expect(mockImageCaptcha).toHaveBeenCalled()
  })

  it('登录成功：token/userInfo 写入 store 并跳转首页', async () => {
    mockPost.mockResolvedValue({
      code: 200,
      data: {
        token: 'tk-xyz', userId: 1, username: 'admin', realName: '管理员',
        tenantId: 1, tenantName: '默认租户', roles: ['ADMIN'], permissions: ['sys:user:list'],
      },
    })
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.formRef = { validate: vi.fn(async () => true) }
    st.loginForm.username = 'admin'
    st.loginForm.password = '123456'
    st.loginForm.captchaCode = 'abcd'
    await st.handleLogin()
    await flushPromises()
    expect(mockPost).toHaveBeenCalledWith('/v1/auth/login', expect.objectContaining({
      username: 'admin', password: '123456', captchaCode: 'abcd',
    }))
    const store = useUserStore()
    expect(store.token).toBe('tk-xyz')
    expect(mockPush).toHaveBeenCalledWith('/')
  })

  it('登录失败：自动刷新验证码且不跳转', async () => {
    mockPost.mockRejectedValue(new Error('用户名或密码错误'))
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.formRef = { validate: vi.fn(async () => true) }
    st.loginForm.username = 'admin'
    st.loginForm.password = 'wrong'
    mockImageCaptcha.mockClear()
    await st.handleLogin()
    await flushPromises()
    expect(mockImageCaptcha).toHaveBeenCalled() // 失败刷新验证码
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('忘记密码入口跳转 /forgot-password', async () => {
    const w = await mountPage()
    mockPush.mockClear()
    w.vm.$.setupState.goForgotPassword()
    expect(mockPush).toHaveBeenCalledWith('/forgot-password')
  })

  it('切换到短信 Tab：loginMode 变为 sms', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.switchMode('sms')
    expect(st.loginMode).toBe('sms')
  })

  it('短信登录成功：POST /v1/auth/login 载荷含 loginType:SMS 并跳转首页', async () => {
    mockPost.mockResolvedValue({
      code: 200,
      data: {
        token: 'tk-sms', userId: 2, username: 'zhang', realName: '张三',
        tenantId: 1, tenantName: '默认租户', roles: ['USER'], permissions: ['proj:view'],
      },
    })
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.loginMode = 'sms'
    st.smsFormRef = { validate: vi.fn(async () => true) }
    st.smsForm.phone = '13800138000'
    st.smsForm.smsCode = '123456'
    await st.handleSmsLogin()
    await flushPromises()
    expect(mockPost).toHaveBeenCalledWith('/v1/auth/login', expect.objectContaining({
      loginType: 'SMS', phone: '13800138000', smsCode: '123456',
    }))
    const store = useUserStore()
    expect(store.token).toBe('tk-sms')
    expect(mockPush).toHaveBeenCalledWith('/')
  })

  it('短信登录失败：不跳转首页', async () => {
    mockPost.mockRejectedValue(new Error('短信验证码错误或已过期'))
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.loginMode = 'sms'
    st.smsFormRef = { validate: vi.fn(async () => true) }
    st.smsForm.phone = '13800138000'
    st.smsForm.smsCode = '000000'
    await st.handleSmsLogin()
    await flushPromises()
    expect(mockPush).not.toHaveBeenCalled()
  })

  it('获取验证码：手机号合法时调用 sendSmsCaptcha 并启动 60s 倒计时', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.loginMode = 'sms'
    st.smsFormRef = { validateField: vi.fn(async () => true) }
    st.smsForm.phone = '13800138000'
    await st.handleSendSms()
    await flushPromises()
    expect(mockSendSms).toHaveBeenCalledWith('13800138000')
    expect(st.smsCooldown).toBe(60)
  })

  it('获取验证码：手机号校验不通过则不发送', async () => {
    const w = await mountPage()
    const st = w.vm.$.setupState
    st.loginMode = 'sms'
    st.smsFormRef = { validateField: vi.fn(async () => { throw new Error('invalid') }) }
    st.smsForm.phone = '123'
    await st.handleSendSms()
    await flushPromises()
    expect(mockSendSms).not.toHaveBeenCalled()
    expect(st.smsCooldown).toBe(0)
  })
})
