/**
 * utils/offlineData.ts 在线优先/离线回退读取助手单元测试（2026-08-15 P3 方向3 补测）
 *
 * 被测代码为 src/utils/offlineData.ts 真实实现；仅 @/api/common 的接口层
 * 经 vi.mock 隔离（协作方），缓存层走 offlineCache 真实读写（setup.ts uni 存储桩）。
 * 覆盖：离线读缓存/在线拉取并刷缓存/接口失败回退缓存/无缓存空态提示/
 * records 多形态提取（records/list/data 嵌套/裸数组）。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { resetUniStorage } from './setup'

const { mockGetProjectList, mockGetMaterialDict } = vi.hoisted(() => ({
  mockGetProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [] } })),
  mockGetMaterialDict: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [] } })),
}))

vi.mock('@/api/common', () => ({
  getProjectList: mockGetProjectList,
  getMaterialDict: mockGetMaterialDict,
}))

import { loadProjectList, loadMaterialDict, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import { offlineCache, STORAGE_KEYS } from '@/utils/offlineCache'
import { useNetworkStore } from '@/stores/network'
import { useUserStore } from '@/stores/user'

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

function setOffline(val: boolean) {
  useNetworkStore().setOffline(val)
}

describe('loadProjectList 项目列表读取', () => {
  it('在线：拉接口 + 刷新缓存 + fromCache=false', async () => {
    mockGetProjectList.mockResolvedValue({ code: 200, data: { records: [{ id: 1, projectName: 'P1' }] } })

    const result = await loadProjectList({ page: 1, size: 10 })

    expect(mockGetProjectList).toHaveBeenCalledWith({ page: 1, size: 10 })
    expect(result.records).toEqual([{ id: 1, projectName: 'P1' }])
    expect(result.fromCache).toBe(false)
    expect(result.empty).toBe(false)
    expect(result.meta.source).toBe('NETWORK')
    expect(result.meta.freshness).toBe('FRESH')
    expect(result.meta.onlineAttempted).toBe(true)
    // 缓存已刷新（后续离线可读）
    expect(offlineCache.get(STORAGE_KEYS.PROJECT_LIST)?.data).toBeTruthy()
  })

  it('离线：不发请求直接读缓存', async () => {
    offlineCache.set(STORAGE_KEYS.PROJECT_LIST, { records: [{ id: 2 }] }, 1)
    setOffline(true)

    const result = await loadProjectList()

    expect(mockGetProjectList).not.toHaveBeenCalled()
    expect(result.records).toEqual([{ id: 2 }])
    expect(result.fromCache).toBe(true)
    expect(result.meta.source).toBe('CACHE')
    expect(result.meta.fallbackReason).toBe('CACHE_ONLY')
    expect(result.meta.onlineAttempted).toBe(false)
  })

  it('离线且无缓存：空态 + 统一提示文案', async () => {
    setOffline(true)

    const result = await loadProjectList()

    expect(result.records).toEqual([])
    expect(result.fromCache).toBe(true)
    expect(result.empty).toBe(true)
    expect(result.message).toBe(NO_OFFLINE_DATA_TIP)
    expect(result.meta.source).toBe('CACHE')
    expect(result.meta.fallbackReason).toBe('CACHE_ONLY')
  })

  it('在线但接口失败：回退缓存（现场可用）', async () => {
    offlineCache.set(STORAGE_KEYS.PROJECT_LIST, { records: [{ id: 3 }] }, 1)
    mockGetProjectList.mockRejectedValue(new Error('网络错误'))

    const result = await loadProjectList()

    expect(result.fromCache).toBe(true)
    expect(result.records).toEqual([{ id: 3 }])
    expect(result.meta.source).toBe('CACHE')
    expect(result.meta.onlineAttempted).toBe(true)
    expect(result.meta.fallbackReason).toBe('NETWORK_ERROR')
    expect(result.message).toBe('在线请求失败，当前展示本地缓存')
  })

  it('在线接口返回裸数组亦能提取 records', async () => {
    mockGetProjectList.mockResolvedValue({ code: 200, data: [{ id: 4 }] })

    const result = await loadProjectList()

    expect(result.records).toEqual([{ id: 4 }])
  })
})

describe('loadMaterialDict 材料字典读取', () => {
  it('在线：拉接口并刷缓存', async () => {
    mockGetMaterialDict.mockResolvedValue({ code: 200, data: { list: [{ id: 1, materialName: '钢筋' }] } })

    const result = await loadMaterialDict({ keyword: '钢' })

    expect(mockGetMaterialDict).toHaveBeenCalledWith({ keyword: '钢' })
    expect(result.records).toEqual([{ id: 1, materialName: '钢筋' }]) // list 形态提取
    expect(result.fromCache).toBe(false)
  })

  it('离线读缓存；无缓存空态提示', async () => {
    setOffline(true)
    const emptyRes = await loadMaterialDict()
    expect(emptyRes.records).toEqual([])
    expect(emptyRes.fromCache).toBe(true)
    expect(emptyRes.empty).toBe(true)
    expect(emptyRes.message).toBe(NO_OFFLINE_DATA_TIP)
    expect(emptyRes.meta.source).toBe('CACHE')

    offlineCache.set(STORAGE_KEYS.MATERIAL_DICT, { data: { records: [{ id: 9 }] } }, 1) // data 嵌套形态
    const cached = await loadMaterialDict()
    expect(cached.records).toEqual([{ id: 9 }])
    expect(cached.meta.source).toBe('CACHE')
  })

  it('接口失败回退缓存', async () => {
    offlineCache.set(STORAGE_KEYS.MATERIAL_DICT, { records: [{ id: 5 }] }, 1)
    mockGetMaterialDict.mockRejectedValue(new Error('500'))

    const result = await loadMaterialDict()

    expect(result.fromCache).toBe(true)
    expect(result.records).toEqual([{ id: 5 }])
    expect(result.meta.source).toBe('CACHE')
    expect(result.meta.fallbackReason).toBe('NETWORK_ERROR')
  })

  it('缓存结构无法提取 records 时返回空态（兜底分支）', async () => {
    setOffline(true)
    offlineCache.set(STORAGE_KEYS.MATERIAL_DICT, { foo: 'bar' }, 1)

    const result = await loadMaterialDict()

    expect(result.records).toEqual([])
    expect(result.fromCache).toBe(true)
    expect(result.empty).toBe(true)
    expect(result.message).toBe(NO_OFFLINE_DATA_TIP)
    expect(result.meta.source).toBe('CACHE')
  })
})

describe('offlineCache.sync 离线缓存初始同步（需求 4.1）', () => {
  it('拉取材料字典/项目列表并写入缓存，已登录时同步用户信息', async () => {
    mockGetMaterialDict.mockResolvedValue({ code: 200, data: { records: [{ id: 1, materialName: '水泥' }] } })
    mockGetProjectList.mockResolvedValue({ code: 200, data: { records: [{ id: 2, projectName: 'P同步' }] } })
    useUserStore().setUserInfo({ username: 'u1', realName: '张三' })

    await offlineCache.sync()

    expect(mockGetMaterialDict).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }))
    expect(mockGetProjectList).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }))
    expect(offlineCache.get(STORAGE_KEYS.MATERIAL_DICT)?.data).toBeTruthy()
    expect(offlineCache.get(STORAGE_KEYS.PROJECT_LIST)?.data).toBeTruthy()
    expect(offlineCache.get(STORAGE_KEYS.USER_INFO)?.data).toEqual({ username: 'u1', realName: '张三' })
  })

  it('未登录时不写用户信息缓存', async () => {
    await offlineCache.sync()
    expect(offlineCache.get(STORAGE_KEYS.USER_INFO)).toBeNull()
  })
})
