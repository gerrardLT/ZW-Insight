import request from '@/utils/request'

interface PasswordLoginData {
  username: string
  password: string
  /** 图形验证码（后端 auth.captcha-enabled=true 时必填） */
  captchaCode?: string
  /** 图形验证码 uuid（GET /v1/captcha/image 返回） */
  captchaUuid?: string
  tenantCode?: string
  loginType?: 'PASSWORD'
}

interface SmsLoginData {
  phone: string
  smsCode: string
  loginType: 'SMS'
  tenantCode?: string
}

/** 获取图形验证码（CaptchaController GET /image，返回 { uuid, imageBase64 }，base64 带 data:image/png 前缀） */
export function getImageCaptcha() {
  return request({ url: '/v1/captcha/image' })
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
