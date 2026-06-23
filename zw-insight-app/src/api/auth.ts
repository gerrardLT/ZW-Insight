import request from '@/utils/request'

export function login(data: { username: string; password: string; captcha?: string; captchaKey?: string; tenantCode?: string }) {
  return request({ url: '/v1/auth/login', method: 'POST', data })
}

export function logout() {
  return request({ url: '/v1/auth/logout', method: 'POST' })
}

export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return request({ url: '/v1/auth/password', method: 'PUT', data })
}
