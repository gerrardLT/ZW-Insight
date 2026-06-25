/**
 * 离线缓存工具
 * 检测网络状态，无网时将请求暂存本地，恢复后自动同步
 */

const OFFLINE_QUEUE_KEY = 'offline_request_queue'

interface OfflineRequest {
  id: string
  url: string
  method: string
  data: any
  timestamp: number
}

/** 当前网络状态 */
let isOnline = true

/** 初始化网络监听 */
export function initOfflineDetection() {
  uni.getNetworkType({
    success: (res) => { isOnline = res.networkType !== 'none' }
  })
  uni.onNetworkStatusChange((res) => {
    isOnline = res.isConnected
    if (isOnline) { syncOfflineData() }
  })
}

/** 是否在线 */
export function getNetworkStatus(): boolean { return isOnline }

/** 将请求存入离线队列 */
export function enqueueOfflineRequest(url: string, method: string, data: any) {
  const queue = getOfflineQueue()
  const request: OfflineRequest = {
    id: Date.now() + '_' + Math.random().toString(36).slice(2),
    url, method, data, timestamp: Date.now()
  }
  queue.push(request)
  uni.setStorageSync(OFFLINE_QUEUE_KEY, JSON.stringify(queue))
  uni.showToast({ title: '已保存至离线队列', icon: 'none' })
}

/** 获取离线队列 */
export function getOfflineQueue(): OfflineRequest[] {
  const raw = uni.getStorageSync(OFFLINE_QUEUE_KEY)
  return raw ? JSON.parse(raw) : []
}

/** 同步离线数据到服务端 */
export async function syncOfflineData() {
  const queue = getOfflineQueue()
  if (queue.length === 0) return

  let successCount = 0
  const failedQueue: OfflineRequest[] = []
  const token = uni.getStorageSync('token')

  for (const req of queue) {
    try {
      await uni.request({
        url: req.url,
        method: req.method as any,
        data: req.data,
        header: { Authorization: `Bearer ${token}` }
      })
      successCount++
    } catch {
      failedQueue.push(req)
    }
  }

  // 更新队列（仅保留失败的）
  uni.setStorageSync(OFFLINE_QUEUE_KEY, JSON.stringify(failedQueue))

  if (successCount > 0) {
    uni.showToast({ title: `已同步 ${successCount} 条离线数据`, icon: 'success' })
  }
  if (failedQueue.length > 0) {
    console.warn(`离线同步：${failedQueue.length} 条数据同步失败`)
  }
}

/** 清除离线队列 */
export function clearOfflineQueue() {
  uni.removeStorageSync(OFFLINE_QUEUE_KEY)
}

/** 获取离线队列数量 */
export function getOfflineQueueCount(): number {
  return getOfflineQueue().length
}
