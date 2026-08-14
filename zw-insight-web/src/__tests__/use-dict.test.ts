/**
 * useDict 组合式函数单元测试（2026-08-14 前端深度补测）
 *
 * 覆盖：字段映射（itemName/itemValue 及 label/value 回退）、
 * 接口异常置空不抛出、getLabel/getColor 字符串宽松匹配、
 * status=0 置 disabled、immediate=true 挂载时自动加载。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

vi.mock('@/api/system', () => ({
  getDictItemsByCode: vi.fn()
}))

import { getDictItemsByCode } from '@/api/system'
import { useDict } from '@/composables/useDict'

const mockDictApi = vi.mocked(getDictItemsByCode)

describe('composables/useDict', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('load：itemName/itemValue 映射为标准 label/value', async () => {
    mockDictApi.mockResolvedValue({
      data: [
        { itemName: '进行中', itemValue: 'IN_PROGRESS', color: 'blue', status: 1 },
        { itemName: '已停用', itemValue: 'DISABLED', cssClass: 'gray', status: 0 }
      ]
    } as any)

    const { items, loading, load } = useDict('project_status', false)
    await load()

    expect(mockDictApi).toHaveBeenCalledWith('project_status')
    expect(loading.value).toBe(false)
    expect(items.value).toEqual([
      { label: '进行中', value: 'IN_PROGRESS', color: 'blue', disabled: false },
      { label: '已停用', value: 'DISABLED', color: 'gray', disabled: true }
    ])
  })

  it('load：label/value 字段回退兼容', async () => {
    mockDictApi.mockResolvedValue({ data: [{ label: '甲', value: 1 }] } as any)
    const { items, load } = useDict('x', false)
    await load()
    expect(items.value[0]).toMatchObject({ label: '甲', value: 1 })
  })

  it('load：接口异常时置空列表不抛出，loading 复位', async () => {
    mockDictApi.mockRejectedValue(new Error('network'))
    const { items, loading, load } = useDict('x', false)
    await expect(load()).resolves.toBeUndefined()
    expect(items.value).toEqual([])
    expect(loading.value).toBe(false)
  })

  it('load：响应 data 非数组时置空', async () => {
    mockDictApi.mockResolvedValue({ data: 'unexpected' } as any)
    const { items, load } = useDict('x', false)
    await load()
    expect(items.value).toEqual([])
  })

  it('getLabel：命中返回 label / 未命中原样字符串化 / null 返回空串', async () => {
    mockDictApi.mockResolvedValue({ data: [{ itemName: '施工中', itemValue: 1 }] } as any)
    const { getLabel, load } = useDict('x', false)
    await load()

    expect(getLabel(1)).toBe('施工中')
    expect(getLabel('1')).toBe('施工中')   // 字符串宽松匹配
    expect(getLabel(999)).toBe('999')       // 未命中原样返回
    expect(getLabel(null)).toBe('')
    expect(getLabel(undefined)).toBe('')
  })

  it('getColor：命中返回颜色 / 未命中或空值返回空串', async () => {
    mockDictApi.mockResolvedValue({ data: [{ itemName: 'A', itemValue: 'a', color: 'red' }] } as any)
    const { getColor, load } = useDict('x', false)
    await load()

    expect(getColor('a')).toBe('red')
    expect(getColor('b')).toBe('')
    expect(getColor(null)).toBe('')
  })

  it('immediate=true：组件挂载时自动加载', async () => {
    mockDictApi.mockResolvedValue({ data: [] } as any)
    const host = defineComponent({
      setup() {
        useDict('auto_code')  // immediate 默认 true
        return () => h('div')
      }
    })
    mount(host)
    // onMounted 同步触发 load，load 内 await 一帧后断言调用
    await vi.waitFor(() => {
      expect(mockDictApi).toHaveBeenCalledWith('auto_code')
    })
  })
})
