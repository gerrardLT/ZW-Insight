import { ref, onMounted, onBeforeUnmount, nextTick, type Ref } from 'vue'

/**
 * 表格行级键盘导航（S2.2 纪律的 composable 化，V2026_117 阶段三 3b）。
 *
 * 从 project/index、finance/payment-apply、material/inbound、workflow/approval 四处
 * 内联实现去重抽取：↑↓ 移动高亮行，Enter 触发 onEnter 回调（默认查看详情）。
 * 输入控件聚焦时不拦截按键。
 *
 * @param rowsRef        表格数据数组 ref（用于边界判断）
 * @param onEnter        Enter 键回调（参数=当前行数据；可为 null 表示不响应 Enter）
 * @returns install/uninstall 供 onMounted/onBeforeUnmount 调用；reset 供数据刷新后归位
 */
export function useRowNavigation(
  rowsRef: Ref<any[]>,
  onEnter: ((row: any) => void) | null,
  selector = '.el-table__body-wrapper tbody tr.el-table__row'
) {
  const currentRowIndex = ref(-1)

  function highlight(index: number) {
    nextTick(() => {
      const rows = document.querySelectorAll(selector)
      rows.forEach((row, i) => {
        if (i === index) {
          row.classList.add('keyboard-focused')
          row.scrollIntoView({ block: 'nearest' })
        } else {
          row.classList.remove('keyboard-focused')
        }
      })
    })
  }

  function navigate(direction: number) {
    const newIndex = currentRowIndex.value + direction
    if (newIndex >= 0 && newIndex < rowsRef.value.length) {
      currentRowIndex.value = newIndex
      highlight(newIndex)
    }
  }

  function handleKeydown(event: KeyboardEvent) {
    const target = event.target as HTMLElement
    if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.isContentEditable)) {
      return
    }
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      navigate(1)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      navigate(-1)
    } else if (event.key === 'Enter') {
      if (onEnter && currentRowIndex.value >= 0 && currentRowIndex.value < rowsRef.value.length) {
        event.preventDefault()
        onEnter(rowsRef.value[currentRowIndex.value])
      }
    }
  }

  /** 数据刷新后调用：重置高亮到未选中 */
  function reset() {
    currentRowIndex.value = -1
  }

  function install() {
    document.addEventListener('keydown', handleKeydown)
  }
  function uninstall() {
    document.removeEventListener('keydown', handleKeydown)
  }

  return { currentRowIndex, navigate, reset, install, uninstall }
}
