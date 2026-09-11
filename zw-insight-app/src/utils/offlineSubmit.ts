/**
 * 离线提交助手（p2-advanced 需求 5.1 / 5.2）
 *
 * 统一封装业务写入页的提交入口：
 *  - 在线：直接调用真实接口（不改变原有请求链路与错误提示）
 *  - 离线（网络 store 判定）：将操作写入 syncEngine 本地操作队列，
 *    联网恢复后由 App.vue 触发 syncEngine.syncAll() 按时间顺序自动提交
 *
 * 约束（不做静默处理）：
 *  - 仅当网络状态明确为离线时才入队；弱网请求失败不入队（避免重复提交），
 *    由请求层 toast 错误、表单保留数据供用户重试
 *  - 含文件附件（临时路径不可序列化）、两段式审批（避免遗留永久 DRAFT）、
 *    审批动作等强一致操作，离线时使用 rejectIfOffline() 明确拒绝并提示
 */

import { useNetworkStore } from '@/stores/network'
import { syncEngine } from './syncEngine'

export interface OfflineSubmitOptions {
  /** 目标接口路径（不含 /api 前缀，如 '/v1/material/inbound'） */
  endpoint: string
  /** 请求负载（必须可 JSON 序列化，不得含文件临时路径） */
  payload: any
  /** 操作类型，默认 CREATE */
  type?: 'CREATE' | 'UPDATE'
}

export interface OfflineSubmitResult {
  /** true = 已入离线队列（未实际提交服务端）；false = 已在线提交成功 */
  queued: boolean
}

/**
 * 在线直连 / 离线入队提交
 * @param saveFn 在线时执行的真实接口调用（保持页面原有 api 封装）
 * @param opts   离线入队所需的 endpoint / payload / type
 * @throws 在线提交失败时原样抛出（错误提示由请求层统一处理）
 */
export async function submitOrQueue<T>(
  saveFn: () => Promise<T>,
  opts: OfflineSubmitOptions
): Promise<OfflineSubmitResult> {
  const network = useNetworkStore()

  if (!network.isOffline) {
    await saveFn()
    return { queued: false }
  }

  syncEngine.enqueue({
    type: opts.type ?? 'CREATE',
    endpoint: opts.endpoint,
    payload: opts.payload
  })
  network.setQueueCount(syncEngine.getQueue().length)
  uni.showToast({ title: '已存入离线队列，联网后自动同步', icon: 'none' })
  return { queued: true }
}

/**
 * 离线拦截：不适合入队的操作（含附件 / 两段式审批 / 审批动作）离线时明确拒绝
 * @param message 离线提示文案
 * @returns true = 当前离线且已提示（调用方应中止提交）；false = 在线（可继续）
 */
export function rejectIfOffline(message: string): boolean {
  const network = useNetworkStore()
  if (network.isOffline) {
    uni.showToast({ title: message, icon: 'none' })
    return true
  }
  return false
}
