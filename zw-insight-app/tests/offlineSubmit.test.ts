/**
 * offlineSubmit 单元测试（2026-08-29 P0-D1 离线写入接线配套）
 *
 * 被测代码为 src/utils/offlineSubmit.ts 真实实现；
 * uni 存储走 setup.ts 内存桩，网络状态走真实 network store（Pinia）。
 * 覆盖：在线直连 / 离线入队（endpoint+payload+type）/ 两段式等场景的离线拒绝。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { resetUniStorage, getUni } from './setup'
import { useNetworkStore } from '@/stores/network'
import { syncEngine } from '@/utils/syncEngine'
import { submitOrQueue, rejectIfOffline } from '@/utils/offlineSubmit'

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
})

describe('submitOrQueue', () => {
  it('在线：直接执行 saveFn，不入队', async () => {
    const saveFn = vi.fn().mockResolvedValue({ code: 200 })
    const { queued } = await submitOrQueue(saveFn, {
      endpoint: '/v1/material/inbound',
      payload: { materialId: 1 }
    })

    expect(queued).toBe(false)
    expect(saveFn).toHaveBeenCalledTimes(1)
    expect(syncEngine.getQueue()).toHaveLength(0)
  })

  it('离线：不调用 saveFn，写入操作队列并 toast 提示', async () => {
    const network = useNetworkStore()
    network.setOffline(true)
    const showToast = vi.fn()
    ;(getUni() as any).showToast = showToast
    const saveFn = vi.fn()

    const { queued } = await submitOrQueue(saveFn, {
      endpoint: '/v1/site/construction-log',
      payload: { projectId: 9, todayWork: '浇筑' }
    })

    expect(queued).toBe(true)
    expect(saveFn).not.toHaveBeenCalled()
    const queue = syncEngine.getQueue()
    expect(queue).toHaveLength(1)
    expect(queue[0]).toMatchObject({
      type: 'CREATE',
      endpoint: '/v1/site/construction-log',
      payload: { projectId: 9, todayWork: '浇筑' },
      status: 'PENDING'
    })
    expect(showToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: expect.stringContaining('离线队列') })
    )
  })

  it('离线：type 可显式指定（默认 CREATE）', async () => {
    const network = useNetworkStore()
    network.setOffline(true)

    await submitOrQueue(vi.fn(), {
      type: 'UPDATE',
      endpoint: '/v1/site/inspection/1/results',
      payload: { results: [] }
    })

    expect(syncEngine.getQueue()[0].type).toBe('UPDATE')
  })

  it('在线提交失败时原样抛出（由请求层统一处理，不静默入队）', async () => {
    const saveFn = vi.fn().mockRejectedValue(new Error('boom'))
    await expect(
      submitOrQueue(saveFn, { endpoint: '/v1/x', payload: {} })
    ).rejects.toThrow('boom')
    expect(syncEngine.getQueue()).toHaveLength(0)
  })
})

describe('rejectIfOffline', () => {
  it('在线：返回 false，不弹提示', () => {
    const showToast = vi.fn()
    ;(getUni() as any).showToast = showToast
    expect(rejectIfOffline('需联网')).toBe(false)
    expect(showToast).not.toHaveBeenCalled()
  })

  it('离线：返回 true 并明示原因（不静默）', () => {
    useNetworkStore().setOffline(true)
    const showToast = vi.fn()
    ;(getUni() as any).showToast = showToast

    expect(rejectIfOffline('审批操作需联网进行')).toBe(true)
    expect(showToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: '审批操作需联网进行' })
    )
  })
})
