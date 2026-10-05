/**
 * API 错误 → 用户话术映射资产。
 *
 * 背景：后端约定（AGENTS.md 接口错误语义）——400 结构错、409 完整性冲突、
 * 449 二次确认、业务码 500 通常是 Service 抛出的具体业务提示（这类提示对用户
 * 有信息量，必须原样透出）。真正需要前端兜底话术的是两类：
 *   1) message 缺失（网络层/网关层错误，无业务体）
 *   2) message 是泛化文案（如「系统内部错误，请稍后重试」「请求失败」「操作失败」）
 *
 * 本表只做「兜底替换」，绝不覆盖具体业务提示（如「仅草稿状态可删除」）。
 */

/** 泛化文案集合：命中即视为无信息量，走码表兜底 */
const GENERIC_MESSAGES = new Set([
  '系统内部错误，请稍后重试',
  '请求失败',
  '操作失败',
  '操作成功失败',
  'Internal Server Error',
  'Bad Request',
  'error',
  '系统异常',
])

/** HTTP/业务码 → 用户话术（含动作指引） */
const CODE_MESSAGES: Record<number, string> = {
  400: '请求内容有误，请检查填写项后重试',
  401: '登录已过期，请重新登录',
  403: '您没有该功能的操作权限，请联系管理员开通',
  404: '请求的内容不存在或已被删除',
  405: '该操作方式不受支持，请刷新页面后重试',
  409: '数据已发生变化（可能与他人操作冲突），请刷新后重试',
  429: '操作过于频繁，请稍后再试',
  500: '系统开小差了，请稍后重试；若持续出现请联系管理员',
  502: '服务暂时不可用，请稍后重试',
  503: '系统维护中，请稍后再试',
  504: '请求超时，请检查网络后重试',
}

/** 网络层 error.message 关键词 → 话术（无 HTTP 状态码可用时） */
const NETWORK_MESSAGES: Array<[RegExp, string]> = [
  [/timeout|timed?\s*out/i, '请求超时，请检查网络后重试'],
  [/network|failed to fetch|ERR_CONNECTION/i, '网络连接异常，请检查网络后重试'],
  [/abort/i, '请求已取消'],
]

/**
 * 把后端/网络错误翻译成用户话术。
 * 优先级：具体业务提示（中文语义，网络英文报错不算） > 码表 > 网络关键词 > 默认。
 */
export function humanizeApiError(code: number | undefined, message?: string, fallback = '操作未完成，请稍后重试'): string {
  const msg = (message || '').trim()
  // 网络层英文报错（timeout of… / Failed to fetch）对用户无信息量，先翻译
  for (const [pattern, text] of NETWORK_MESSAGES) {
    if (msg && pattern.test(msg)) return text
  }
  const specific = msg && !GENERIC_MESSAGES.has(msg) ? msg : ''
  if (specific) return specific
  if (code != null && CODE_MESSAGES[code]) return CODE_MESSAGES[code]
  return fallback
}
