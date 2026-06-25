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
