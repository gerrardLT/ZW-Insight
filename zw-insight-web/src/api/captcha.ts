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
      } else if (type === 'validCaptcha') {
        // TAC 1.4 发送 ISO-8601 时间；tianai-captcha 1.5.5 core 的轨迹字段要求 epoch ms。
        // 仅规范化协议类型，位置与完整行为轨迹原样透传。
        const track = (param.data as { data?: { startTime?: string | number; stopTime?: string | number } })?.data
        if (track) {
          if (typeof track.startTime === 'string') track.startTime = Date.parse(track.startTime)
          if (typeof track.stopTime === 'string') track.stopTime = Date.parse(track.stopTime)
        }
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
