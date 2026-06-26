import request from '@/utils/request'

interface PasswordLoginData {
  username: string
  password: string
  captcha?: string
  captchaKey?: string
  tenantCode?: string
  loginType?: 'PASSWORD'
}

interface SmsLoginData {
  phone: string
  smsCode: string
  loginType: 'SMS'
  tenantCode?: string
}

export function login(data: PasswordLoginData | SmsLoginData) {
  return request({ url: '/v1/auth/login', method: 'POST', data })
}

export function sendSmsCaptcha(phone: string) {
  return request({ url: '/v1/captcha/sms', method: 'POST', data: { phone } })
}

export function logout() {
  return request({ url: '/v1/auth/logout', method: 'POST' })
}

export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request({ url: '/v1/auth/password', method: 'PUT', data })
}

/** 忘记密码：发送重置短信验证码 */
export function sendResetCode(phone: string) {
  return request({ url: '/v1/auth/password-reset/send-code', method: 'POST', data: { phone } })
}

/** 忘记密码：校验重置短信验证码 */
export function verifyResetCode(phone: string, code: string) {
  return request({ url: '/v1/auth/password-reset/verify-code', method: 'POST', data: { phone, code } })
}

/** 忘记密码：提交新密码完成重置 */
export function resetPassword(phone: string, code: string, newPassword: string) {
  return request({ url: '/v1/auth/password-reset/reset', method: 'POST', data: { phone, code, newPassword } })
}
