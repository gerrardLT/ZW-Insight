/**
 * 登录域矩阵补盲测试（2026-08-20 账本全量补齐 M8 @matrix D-1/D-2/D-3）
 *
 * 覆盖账本缺口：
 *   - D-1-10 记住用户名：源码无此控件（盲点现状钉住，源码守卫）
 *   - D-2-4  错误验证码：verifyResetCode 失败不进入第 3 步
 *   - D-3-2  当前设备禁注销（tooltip 文案 + disabled 运行时）
 *   - D-3-4  注销取消：confirm 拒绝后不发 revoke 请求
 *   - D-3-5  离线设备（status!==1）注销按钮 disabled
 *   - D-3-7  空列表 el-empty 占位
 *
 * 与既有测试边界：login-page/forgot-password/refund-devices 已覆盖正向主链
 * （登录成功/失败刷新验证码、三套正则、脱敏、倒计时、formatLocation、注销确认），
 * 本文件仅补负向/边界缺口，不重复断言。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import ElementPlus from 'element-plus'

const {
  mockSendResetCode, mockVerifyResetCode, mockResetPassword,
  mockDevices, mockRevoke, mockConfirm,
} = vi.hoisted(() => ({
  mockSendResetCode: vi.fn(async (_p?: any): Promise<any> => ({ code: 200 })),
  mockVerifyResetCode: vi.fn(async (_p?: any): Promise<any> => ({ code: 200 })),
  mockResetPassword: vi.fn(async (_p?: any): Promise<any> => ({ code: 200 })),
  mockDevices: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockRevoke: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockConfirm: vi.fn(async (): Promise<string> => 'confirm'),
}))

vi.mock('@/api/password-reset', () => ({
  sendResetCode: mockSendResetCode,
  verifyResetCode: mockVerifyResetCode,
  resetPassword: mockResetPassword,
}))
vi.mock('@/api/device', () => ({
  getLoginDevices: mockDevices, revokeLoginDevice: mockRevoke,
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mockConfirm },
  }
})

import ForgotPassword from '@/views/login/forgot-password.vue'
import Devices from '@/views/user/devices.vue'

/** 源码守卫：CRLF 归一化后做包含断言 */
function norm(p: string): string {
  return readFileSync(resolve(__dirname, '..', p), 'utf-8').replace(/\r\n/g, '\n')
}

let wrapper: any = null
beforeEach(() => {
  vi.clearAllMocks()
  mockVerifyResetCode.mockResolvedValue({ code: 200 })
  mockConfirm.mockResolvedValue('confirm')
})
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  document.body.innerHTML = ''
})

describe('forgot-password.vue 负向缺口（@matrix D-2-4）', () => {
  it('错误验证码：verifyResetCode 失败时停留第 2 步不推进', async () => {
    mockVerifyResetCode.mockRejectedValueOnce(new Error('验证码错误'))
    wrapper = mount(ForgotPassword, { global: { plugins: [ElementPlus] }, attachTo: document.body })
    const st = wrapper.vm.$.setupState
    st.form.phone = '13812345678'
    st.form.code = '000000'
    st.active = 1
    await st.handleVerifyCode()
    await flushPromises()
    expect(mockVerifyResetCode).toHaveBeenCalledWith('13812345678', '000000')
    expect(st.active, '校验失败不得进入重置密码步骤').toBe(1)
  })
})

describe('user/devices.vue 负向/边界缺口（@matrix D-3-2/4/5/7）', () => {
  async function mountDevices(devices: any[]) {
    mockDevices.mockResolvedValue({ code: 200, data: devices })
    wrapper = mount(Devices, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('D-3-2 当前设备：禁注销按钮 + tooltip「当前设备不可注销」源码钉住', async () => {
    await mountDevices([{ id: 1, deviceName: 'Chrome/Win', isCurrent: true, status: 1 }])
    const src = norm('views/user/devices.vue')
    expect(src).toContain('当前设备不可注销')
    expect(src).toContain('v-if="row.isCurrent"')
    // 当前设备行渲染 disabled 的「当前设备」按钮
    const disabledBtn = wrapper.findAll('button.is-disabled')
    expect(disabledBtn.some((b: any) => b.text().includes('当前设备'))).toBe(true)
  })

  it('D-3-4 注销取消：confirm 拒绝后不调 revoke、列表不变', async () => {
    await mountDevices([{ id: 3, deviceName: 'App/iOS', status: 1 }])
    mockConfirm.mockRejectedValueOnce(new Error('cancel'))
    mockDevices.mockClear()
    // 组件 handleRevoke 无 try/catch：取消时 reject 向上抛（现状钉住），捕获后断言副作用为零
    await wrapper.vm.$.setupState.handleRevoke({ id: 3, deviceName: 'App/iOS' }).catch(() => {})
    await flushPromises()
    expect(mockRevoke).not.toHaveBeenCalled()
    expect(mockDevices, '取消后不重新拉列表').not.toHaveBeenCalled()
  })

  it('D-3-5 离线设备：status!==1 注销按钮 disabled（源码钉住 + 运行时）', async () => {
    await mountDevices([{ id: 4, deviceName: 'Safari/Mac', isCurrent: false, status: 0 }])
    const src = norm('views/user/devices.vue')
    expect(src).toContain(':disabled="row.status !== 1"')
    const revokeBtns = wrapper.findAll('button').filter((b: any) => b.text().includes('远程注销'))
    expect(revokeBtns, '离线设备行应有注销按钮').toHaveLength(1)
    expect(revokeBtns[0].classes()).toContain('is-disabled')
  })

  it('D-3-7 空列表：el-empty 占位「暂无登录设备记录」', async () => {
    await mountDevices([])
    const src = norm('views/user/devices.vue')
    expect(src).toContain('暂无登录设备记录')
    expect(wrapper.find('.el-empty').exists(), '空数据应渲染 el-empty').toBe(true)
    expect(wrapper.text()).toContain('暂无登录设备记录')
  })
})

describe('login/index.vue 盲点钉住（@matrix D-1-10）', () => {
  it('D-1-10 记住用户名：源码无对应控件（现状钉住，盲点待产品决策）', () => {
    const src = norm('views/login/index.vue')
    expect(src, '登录页无「记住用户名」控件').not.toContain('记住用户名')
    expect(src, '登录页无 remember 相关绑定').not.toMatch(/remember/i)
    expect(src, '登录页无 localStorage 持久化用户名').not.toContain('localStorage')
  })
})
