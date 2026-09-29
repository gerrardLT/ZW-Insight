import request from '@/utils/request'

export function getImageCaptcha() {
  return request.get('/v1/captcha/image')
}

export function sendSmsCaptcha(phone: string) {
  return request.post('/v1/captcha/sms', { phone })
}

export const TAC_ASSET_PATH = '/tac'
export const TAC_GENERATE_URL = '/api/v1/captcha/slider'
export const TAC_VERIFY_URL = '/api/v1/captcha/slider/verify'

type TacRequest = { method: string; data: unknown }
type TacResponse = { code?: number; data?: any; captcha?: any; id?: string }
type TacConfig = { addRequestChain: (chain: object) => void }

/**
 * TAC Web SDK 请求链适配：校验请求原样透传（{id, data: 行为轨迹}，由后端天爱校验）；
 * 仅把生成接口的 R 包装 {code, data: ImageCaptchaVO} 映射为 SDK 期望的 {id, captcha}。
 */
export function installTacApiAdapter(config: TacConfig) {
  config.addRequestChain({
    preRequest(type: string, param: TacRequest) {
      if (type === 'requestCaptchaData') {
        param.method = 'GET'
        param.data = undefined
      }
      return true
    },
    postRequest(type: string, _param: TacRequest, response: TacResponse) {
      if (type === 'requestCaptchaData' && response.data) {
        response.id = response.data.id
        response.captcha = response.data
        delete response.data
      }
      return true
    },
  })
}
