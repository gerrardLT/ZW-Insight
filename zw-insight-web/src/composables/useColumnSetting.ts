/**
 * useColumnSetting — 表格列显隐配置 composable
 * 按 tableKey 持久化 localStorage（zw-col-setting:{tableKey}），支持 reset
 * 注：写入采用同步方式（不依赖 watch 微任务），保证 toggle 后立即可读
 */
import { ref, computed } from 'vue'

export interface ColumnDef {
  key: string
  label: string
  /** 是否默认显示（默认 true） */
  defaultVisible?: boolean
}

export function useColumnSetting(tableKey: string, columns: ColumnDef[]) {
  const STORAGE_KEY = `zw-col-setting:${tableKey}`

  // 读取持久化状态，无记录则用默认值
  function load(): boolean[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) {
        const arr = JSON.parse(raw)
        if (Array.isArray(arr) && arr.length === columns.length) {
          return arr
        }
      }
    } catch { /* 损坏数据静默回退默认值 */ }
    return columns.map(col => col.defaultVisible !== false)
  }

  const visible = ref<boolean[]>(load())

  // 同步持久化（不依赖 watch，保证任何修改立即落盘）
  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(visible.value))
  }

  /** 可见列定义（过滤后的 ColumnDef 数组） */
  const visibleColumns = computed(() =>
    columns.filter((_, i) => visible.value[i])
  )

  /** 切换第 i 列显示/隐藏 */
  function toggle(i: number) {
    if (i >= 0 && i < visible.value.length) {
      visible.value[i] = !visible.value[i]
      persist()
    }
  }

  /** 批量设置（供 ColumnSettingPopover v-model 使用） */
  function setVisible(val: boolean[]) {
    visible.value = val
    persist()
  }

  /** 重置为默认 */
  function reset() {
    visible.value = columns.map(col => col.defaultVisible !== false)
    persist()
  }

  return { visible, visibleColumns, toggle, setVisible, reset }
}
