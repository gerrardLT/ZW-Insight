import request from '@/utils/request'

export function getImageCaptcha() {
  return request.get('/v1/captcha/image')
}

export function sendSmsCaptcha(phone: string) {
  return request.post('/v1/captcha/sms', { phone })
}
