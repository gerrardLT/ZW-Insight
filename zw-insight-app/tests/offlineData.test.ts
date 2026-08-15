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
    expect(result).toEqual({ records: [{ id: 1, projectName: 'P1' }], fromCache: false, empty: false })
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
  })

  it('离线且无缓存：空态 + 统一提示文案', async () => {
    setOffline(true)

    const result = await loadProjectList()

    expect(result).toEqual({ records: [], fromCache: true, empty: true, message: NO_OFFLINE_DATA_TIP })
  })

  it('在线但接口失败：回退缓存（现场可用）', async () => {
    offlineCache.set(STORAGE_KEYS.PROJECT_LIST, { records: [{ id: 3 }] }, 1)
    mockGetProjectList.mockRejectedValue(new Error('网络错误'))

    const result = await loadProjectList()

    expect(result.fromCache).toBe(true)
    expect(result.records).toEqual([{ id: 3 }])
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
    expect(await loadMaterialDict()).toEqual({
      records: [], fromCache: true, empty: true, message: NO_OFFLINE_DATA_TIP,
    })

    offlineCache.set(STORAGE_KEYS.MATERIAL_DICT, { data: { records: [{ id: 9 }] } }, 1) // data 嵌套形态
    const cached = await loadMaterialDict()
    expect(cached.records).toEqual([{ id: 9 }])
  })

  it('接口失败回退缓存', async () => {
    offlineCache.set(STORAGE_KEYS.MATERIAL_DICT, { records: [{ id: 5 }] }, 1)
    mockGetMaterialDict.mockRejectedValue(new Error('500'))

    const result = await loadMaterialDict()

    expect(result.fromCache).toBe(true)
    expect(result.records).toEqual([{ id: 5 }])
  })
})
