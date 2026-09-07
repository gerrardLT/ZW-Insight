/**
 * 多端接口基址配置（P1 发布就绪：BASE_URL 单一配置源）
 *
 * - H5：相对路径 `/api`
 *     - dev：由 vite 代理转发到联调服务器（见 vite.config 的 proxy）
 *     - 生产：由同源反向代理转发（部署侧配置，不在前端硬编码环境地址）
 * - 微信小程序 / App（UNI_PLATFORM=mp-weixin|app|app-plus）：
 *     必须为完整 HTTPS 地址（小程序要求 wss/request 合法域名白名单，
 *     见《评估报告》发布前置项）。正式地址申请下来之前保持为空，
 *     由请求直接失败暴露配置缺失——不伪造地址、不静默降级。
 *
 * 取值走 UNI_PLATFORM（uni-app 构建期注入；测试环境无此变量，回退 h5）。
 */

/** 小程序 / App 端正式服务地址（发布前填写，如 'https://api.example.com/api'） */
export const MP_APP_BASE_URL = ''

const platform =
  (typeof process !== 'undefined' && process.env && process.env.UNI_PLATFORM) || 'h5'

const isNativeOrMp =
  platform === 'mp-weixin' || platform === 'app' || platform === 'app-plus'

/** 接口基址：request.ts / syncEngine.ts 统一从此处引用 */
export const BASE_URL = isNativeOrMp ? MP_APP_BASE_URL : '/api'
