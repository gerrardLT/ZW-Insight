/**
 * utils/offline.ts 离线队列单元测试（2026-08-15 P3 方向3 补测）
 *
 * 被测代码为 src/utils/offline.ts 真实实现；uni.getNetworkType/
 * onNetworkStatusChange/request 由测试按 setup.ts 约定覆盖为可控桩。
 * 覆盖：入队持久化/队列读取/同步（带 token 头、成功出队、失败保留）/清空/
 * 网络恢复自动触发同步。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { resetUniStorage, getUni } from './setup'
import {
  enqueueOfflineRequest,
  getOfflineQueue,
  getOfflineQueueCount,
  clearOfflineQueue,
  syncOfflineData,
  initOfflineDetection,
  getNetworkStatus,
} from '@/utils/offline'

const QUEUE_KEY = 'offline_request_queue'

let networkChangeHandler: ((res: any) => void) | null = null
let requestCalls: any[] = []
let requestImpl: (options: any) => Promise<any>

beforeEach(() => {
  resetUniStorage()
  networkChangeHandler = null
  requestCalls = []
  requestImpl = async () => ({ statusCode: 200 })
  const uni = getUni() as any
  uni.getNetworkType = (opts: any) => opts.success({ networkType: 'wifi' })
  uni.onNetworkStatusChange = (handler: any) => { networkChangeHandler = handler }
  uni.request = (options: any) => {
    requestCalls.push(options)
    return requestImpl(options)
  }
})

describe('offline 离线队列', () => {
  it('入队：持久化到存储且含 url/method/data', () => {
    enqueueOfflineRequest('/api/v1/material/inbound', 'POST', { qty: 1 })
    enqueueOfflineRequest('/api/v1/material/outbound', 'POST', { qty: 2 })

    expect(getOfflineQueueCount()).toBe(2)
    const queue = getOfflineQueue()
    expect(queue[0].url).toBe('/api/v1/material/inbound')
    expect(queue[0].method).toBe('POST')
    expect(queue[0].data).toEqual({ qty: 1 })
    expect(queue[0].id).toBeTruthy()
  })

  it('无队列时读取返回空数组', () => {
    expect(getOfflineQueue()).toEqual([])
    expect(getOfflineQueueCount()).toBe(0)
  })

  it('同步：成功请求出队并带 Bearer token 头，toast 同步数', async () => {
    getUni().setStorageSync('token', 'tk-9')
    enqueueOfflineRequest('/api/v1/a', 'POST', { n: 1 })
    enqueueOfflineRequest('/api/v1/b', 'POST', { n: 2 })
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast

    await syncOfflineData()

    expect(requestCalls).toHaveLength(2)
    expect(requestCalls[0].header['Authorization']).toBe('Bearer tk-9')
    expect(getOfflineQueueCount()).toBe(0)
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '已同步 2 条离线数据' }))
  })

  it('同步：失败请求保留在队列', async () => {
    enqueueOfflineRequest('/api/v1/ok', 'POST', {})
    enqueueOfflineRequest('/api/v1/bad', 'POST', {})
    requestImpl = async (options: any) => {
      if (options.url === '/api/v1/bad') throw new Error('网络不可达')
      return { statusCode: 200 }
    }

    await syncOfflineData()

    const queue = getOfflineQueue()
    expect(queue).toHaveLength(1)
    expect(queue[0].url).toBe('/api/v1/bad')
  })

  it('空队列同步不产生请求', async () => {
    await syncOfflineData()
    expect(requestCalls).toHaveLength(0)
  })

  it('清空队列移除存储键', () => {
    enqueueOfflineRequest('/api/v1/x', 'POST', {})
    clearOfflineQueue()
    expect(getOfflineQueueCount()).toBe(0)
    expect(getUni().getStorageSync(QUEUE_KEY)).toBe('')
  })

  it('初始化：wifi 在线；网络恢复自动触发同步', async () => {
    enqueueOfflineRequest('/api/v1/recover', 'POST', {})
    initOfflineDetection()
    expect(getNetworkStatus()).toBe(true)
    expect(networkChangeHandler).toBeTruthy()

    // 断网 → 在线：恢复回调触发 syncOfflineData
    networkChangeHandler!({ isConnected: false })
    expect(getNetworkStatus()).toBe(false)
    networkChangeHandler!({ isConnected: true })
    // syncOfflineData 异步执行，等待微任务冲刷
    await new Promise((r) => setTimeout(r, 0))
    expect(requestCalls).toHaveLength(1)
    expect(getOfflineQueueCount()).toBe(0)
  })
})
