import request from '@/utils/request'

export function getImageCaptcha() {
  return request.get('/v1/captcha/image')
}

export function sendSmsCaptcha(phone: string) {
  return request.post('/v1/captcha/sms', { phone })
}

/** 获取滑块验证挑战：{ challengeId, gapPct } */
export function getSliderCaptcha() {
  return request.get('/v1/captcha/slider')
}

/** 校验滑块位置，成功返回一次性 sliderToken */
export function verifySliderCaptcha(challengeId: string, pct: number) {
  return request.post('/v1/captcha/slider/verify', { challengeId, pct })
}
