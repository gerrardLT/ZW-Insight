import request from '@/utils/request'

export function getImageCaptcha() {
  return request.get('/v1/captcha/image')
}

export function sendSmsCaptcha(phone: string) {
  return request.post('/v1/captcha/sms', { phone })
}

export interface SliderCaptchaChallenge {
  challengeId: string
  backgroundImage: string
  pieceImage: string
  pieceY: number
  imageWidth: number
  imageHeight: number
  pieceWidth: number
}

/** 获取图片拼图挑战；目标 X 仅保存在服务端，响应不得泄漏。 */
export function getSliderCaptcha() {
  return request.get<any, { data: SliderCaptchaChallenge }>('/v1/captcha/slider')
}

/** 校验拼图片左上角 X 坐标，成功返回一次性 sliderToken。 */
export function verifySliderCaptcha(challengeId: string, x: number) {
  return request.post<any, { data: { sliderToken: string } }>('/v1/captcha/slider/verify', { challengeId, x })
}
