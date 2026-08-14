/**
 * forgot-password.vue 组件测试（2026-08-14 P0 补测 @matrix D-2）
 *
 * 钉住忘记密码三步表单的纯前端逻辑：
 *   - 手机号格式规则（/^1[3-9]\d{9}$/，与后端 PHONE_PATTERN 一致）
 *   - 验证码格式规则（/^\d{6}$/）
 *   - 密码复杂度 validator（8-20 位字母+数字，与后端 PASSWORD_PATTERN 一致）
 *   - 确认密码一致性 validator
 *   - 手机号脱敏展示（138****5678）
 *   - 发送验证码→倒计时 60s→步骤推进（active 0→1）
 *
 * 说明：happy-dom 下 el-form.validate 不注册字段（返回 true 空校验），
 * 故规则断言改为直接驱动 setupState 暴露的 rules 对象（pattern/validator），结果确定。
 * 端到端真实流程在 e2e/api-tests/30-security.spec.ts（D-2 组）与
 * e2e/tests/real/forgot-password.spec.ts（UI 走查）覆盖。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockSendResetCode,
  mockVerifyResetCode,
  mockResetPassword,
  mockPush,
} = vi.hoisted(() => ({
  mockSendResetCode: vi.fn(async (_p?: any): Promise<any> => ({ code: 200 })),
  mockVerifyResetCode: vi.fn(async (_p?: any): Promise<any> => ({ code: 200 })),
  mockResetPassword: vi.fn(async (_p?: any): Promise<any> => ({ code: 200 })),
  mockPush: vi.fn(async () => {}),
}))

vi.mock('@/api/password-reset', () => ({
  sendResetCode: mockSendResetCode,
  verifyResetCode: mockVerifyResetCode,
  resetPassword: mockResetPassword,
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

import ForgotPassword from '@/views/login/forgot-password.vue'

let wrapper: ReturnType<typeof mount> | null = null

function mountPage() {
  wrapper = mount(ForgotPassword, {
    global: { plugins: [ElementPlus] },
    attachTo: document.body,
  })
  return wrapper
}

function st(w: any): any {
  return w.vm.$.setupState
}

/** 取规则数组中指定 prop 的 pattern（存在则返回 RegExp） */
function rulePattern(rules: any, prop: string): RegExp | undefined {
  const list = rules[prop] || []
  const found = list.find((r: any) => r.pattern)
  return found?.pattern
}

/** 以 promise 方式运行 validator（resolve=通过，reject=校验错误） */
function runValidator(rules: any, prop: string, value: any): Promise<void> {
  const list = rules[prop] || []
  const found = list.find((r: any) => typeof r.validator === 'function')
  if (!found) return Promise.reject(new Error(`no validator for ${prop}`))
  return new Promise((resolve, reject) => {
    found.validator({}, value, (err?: Error) => (err ? reject(err) : resolve()))
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  mockSendResetCode.mockResolvedValue({ code: 200 })
  mockVerifyResetCode.mockResolvedValue({ code: 200 })
  mockResetPassword.mockResolvedValue({ code: 200 })
})

afterEach(() => {
  vi.useRealTimers()
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

describe('forgot-password.vue 表单校验（@matrix D-2）', () => {
  it('手机号格式规则：非法格式不匹配，合法 1[3-9] 开头 11 位匹配', async () => {
    const w = mountPage()
    const state = st(w)
    const pattern = rulePattern(state.phoneRules, 'phone')
    expect(pattern, '手机号规则应含 pattern').toBeInstanceOf(RegExp)
    for (const bad of ['12345678901', '1381234567', '138123456789', '23812345678']) {
      expect(pattern!.test(bad), `${bad} 应不匹配`).toBe(false)
    }
    for (const good of ['13812345678', '15912345678', '19912345678']) {
      expect(pattern!.test(good), `${good} 应匹配`).toBe(true)
    }
  })

  it('验证码格式规则：仅 6 位数字匹配', async () => {
    const w = mountPage()
    const state = st(w)
    const pattern = rulePattern(state.codeRules, 'code')
    expect(pattern).toBeInstanceOf(RegExp)
    for (const bad of ['12345', '12345a', '1234567', '']) {
      expect(pattern!.test(bad)).toBe(false)
    }
    expect(pattern!.test('123456')).toBe(true)
    expect(pattern!.test('000000')).toBe(true)
  })

  it('密码复杂度 validator：与后端 PASSWORD_PATTERN 一致（8-20 位字母+数字）', async () => {
    const w = mountPage()
    const state = st(w)
    for (const bad of ['12345678', 'abcdefgh', 'Ab12345', 'Ab123456789012345678901']) {
      await expect(runValidator(state.pwdRules, 'newPassword', bad)).rejects.toThrow()
    }
    await expect(runValidator(state.pwdRules, 'newPassword', 'Abcd1234')).resolves.toBeUndefined()
    await expect(runValidator(state.pwdRules, 'newPassword', 'aB3')).rejects.toThrow()
  })

  it('确认密码 validator：与 newPassword 不一致被拒，一致通过', async () => {
    const w = mountPage()
    const state = st(w)
    state.form.newPassword = 'Abcd1234'
    state.form.confirmPassword = 'Abcd1235'
    await expect(runValidator(state.pwdRules, 'confirmPassword', state.form.confirmPassword)).rejects.toThrow()
    state.form.confirmPassword = 'Abcd1234'
    await expect(runValidator(state.pwdRules, 'confirmPassword', state.form.confirmPassword)).resolves.toBeUndefined()
  })

  it('手机号脱敏展示：11 位脱敏为 138****5678，非 11 位原样', async () => {
    const w = mountPage()
    const state = st(w)
    state.form.phone = '13812345678'
    expect(state.maskedPhone).toBe('138****5678')
    state.form.phone = '1381234'
    expect(state.maskedPhone).toBe('1381234')
  })

  it('发送验证码成功：调用 API（位置参数）→ 启动 60s 倒计时 → 步骤推进到 1', async () => {
    vi.useFakeTimers()
    const w = mountPage()
    const state = st(w)
    state.form.phone = '13812345678'

    await state.handleSendCode()
    await flushPromises()
    expect(mockSendResetCode).toHaveBeenCalledWith('13812345678')
    expect(state.countdown).toBe(60)
    expect(state.active).toBe(1)

    vi.advanceTimersByTime(30_000)
    expect(state.countdown).toBe(30)
    vi.advanceTimersByTime(30_000)
    expect(state.countdown).toBe(0)
  })

  it('发送验证码失败：不启动倒计时、不推进步骤', async () => {
    vi.useFakeTimers()
    mockSendResetCode.mockRejectedValueOnce(new Error('发送失败'))
    const w = mountPage()
    const state = st(w)
    state.form.phone = '13812345678'

    await state.handleSendCode()
    await flushPromises()
    expect(state.countdown).toBe(0)
    expect(state.active).toBe(0)
  })

  it('校验验证码成功：调用 API 并推进到步骤 2', async () => {
    const w = mountPage()
    const state = st(w)
    state.form.phone = '13812345678'
    state.form.code = '123456'

    await state.handleVerifyCode()
    await flushPromises()
    expect(mockVerifyResetCode).toHaveBeenCalledWith('13812345678', '123456')
    expect(state.active).toBe(2)
  })

  it('重置密码成功：调用 API 并跳转登录页', async () => {
    const w = mountPage()
    const state = st(w)
    state.form.phone = '13812345678'
    state.form.code = '123456'
    state.form.newPassword = 'Abcd1234'

    await state.handleReset()
    await flushPromises()
    expect(mockResetPassword).toHaveBeenCalledWith('13812345678', '123456', 'Abcd1234')
    expect(mockPush).toHaveBeenCalledWith('/login')
  })
})
