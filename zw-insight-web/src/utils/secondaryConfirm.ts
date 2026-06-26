/**
 * 二次确认（@SecondaryConfirm）全局协调器。
 *
 * 高风险操作的后端接口会在缺少有效 `X-Confirm-Password` 请求头时返回 HTTP 449，
 * 前端 axios 响应拦截器捕获 449 后调用 {@link requestSecondaryConfirm} 弹出密码输入框，
 * 用户确认后携带密码重发原请求（见 utils/request.ts）。
 *
 * 采用「单例 opener 注册」模式：全局挂载的 ConfirmPasswordDialog 组件在 onMounted 时
 * 通过 {@link registerConfirmOpener} 注册自身的打开方法，拦截器与组件之间由此解耦，
 * 无需在每个调用点手动放置对话框。
 */

/** 对话框打开方法签名：返回用户输入的密码；取消时 resolve(null)。 */
export type ConfirmOpener = (message: string) => Promise<string | null>

let opener: ConfirmOpener | null = null

/** 由全局 ConfirmPasswordDialog 组件在挂载时注册其打开方法。 */
export function registerConfirmOpener(fn: ConfirmOpener): void {
  opener = fn
}

/** 组件卸载时注销，避免悬挂引用。 */
export function unregisterConfirmOpener(fn: ConfirmOpener): void {
  if (opener === fn) {
    opener = null
  }
}

/**
 * 请求用户进行二次确认（输入登录密码）。
 *
 * @param message 提示文案（来自后端 449 响应的 message 字段）
 * @returns 用户输入的密码；若用户取消或对话框不可用则返回 null
 */
export function requestSecondaryConfirm(message: string): Promise<string | null> {
  if (!opener) {
    // 对话框尚未挂载（理论上不会发生，App.vue 已全局挂载）。
    // 返回 null 以终止重试，避免静默吞掉错误。
    return Promise.resolve(null)
  }
  return opener(message)
}
