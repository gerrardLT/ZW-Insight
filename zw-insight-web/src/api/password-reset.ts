import request from '@/utils/request'

/**
 * 忘记密码 / 密码重置 API
 * 后端：PasswordResetController（已登录前可访问，路径白名单）
 */

/** 发送密码重置短信验证码 */
export function sendResetCode(phone: string) {
  return request.post('/v1/auth/password-reset/send-code', { phone })
}

/** 校验密码重置短信验证码 */
export function verifyResetCode(phone: string, code: string) {
  return request.post('/v1/auth/password-reset/verify-code', { phone, code })
}

/** 提交新密码完成重置 */
export function resetPassword(phone: string, code: string, newPassword: string) {
  return request.post('/v1/auth/password-reset/reset', { phone, code, newPassword })
}
