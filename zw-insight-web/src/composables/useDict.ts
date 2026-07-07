import { ref, onMounted } from 'vue'
import { getDictItemsByCode } from '@/api/system'

/** 字典项 */
export interface DictItem {
  label: string
  value: string | number
  color?: string
  disabled?: boolean
}

/**
 * 字典数据组合式函数
 * 根据字典编码加载字典项列表，可用于 Select、Tag 等组件
 *
 * @example
 * const { items, getLabel, loading } = useDict('project_status')
 *
 * @param dictCode 字典编码
 * @param immediate 是否立即加载（默认 true）
 */
export function useDict(dictCode: string, immediate = true) {
  const items = ref<DictItem[]>([])
  const loading = ref(false)

  async function load() {
    loading.value = true
    try {
      const res = await getDictItemsByCode(dictCode) as any
      const data = res?.data || res || []
      items.value = Array.isArray(data)
        ? data.map((item: any) => ({
            label: item.itemName || item.label || item.dictLabel || '',
            value: item.itemValue || item.value || item.dictValue || '',
            color: item.color || item.cssClass || '',
            disabled: item.status === 0
          }))
        : []
    } catch {
      items.value = []
    } finally {
      loading.value = false
    }
  }

  /**
   * 根据 value 获取对应的 label 文本
   */
  function getLabel(value: string | number | undefined | null): string {
    if (value === undefined || value === null) return ''
    const item = items.value.find(i => String(i.value) === String(value))
    return item?.label || String(value)
  }

  /**
   * 根据 value 获取对应的颜色
   */
  function getColor(value: string | number | undefined | null): string {
    if (value === undefined || value === null) return ''
    const item = items.value.find(i => String(i.value) === String(value))
    return item?.color || ''
  }

  if (immediate) {
    onMounted(load)
  }

  return { items, loading, load, getLabel, getColor }
}
