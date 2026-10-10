import { computed } from 'vue'
import { getActivePinia } from 'pinia'
import { useAppStore } from '@/stores/app'

/**
 * 全局「当前项目」上下文访问器。
 *
 * 背景：`appStore` 早已定义 `currentProjectId` / `currentProjectName` 并做了持久化，
 * 但此前没有任何页面读写它，导致每个列表页的项目筛选进入时都是空的、每次都要重选。
 * 本模块把这段上下文接出来：顶栏切换器负责写入，各页筛选器负责读取初始值。
 *
 * 无 Pinia 实例时（部分组件测试直接挂载页面而不装 pinia）安全回落为「无上下文」：
 * 不抛错，也不改变原有「全部项目」的行为。
 */
function resolveStore() {
  const pinia = getActivePinia()
  return pinia ? useAppStore(pinia) : null
}

/**
 * 页面筛选器初始值：已设全局上下文则默认选中该项目，否则 undefined（全部项目）。
 *
 * 供各页 `queryParams` 初始化直接调用（不依赖组件 setup 的响应式上下文）。
 */
export function defaultProjectId(): number | string | undefined {
  return resolveStore()?.currentProjectId ?? undefined
}

/** 组合式访问器：需要响应式跟随全局上下文的组件使用 */
export function useProjectContext() {
  const appStore = resolveStore()

  /** 当前项目ID（未设置时为 undefined，语义等同于「全部项目」） */
  const currentProjectId = computed<number | string | undefined>(() => appStore?.currentProjectId ?? undefined)

  /** 是否已选择全局当前项目 */
  const hasProjectContext = computed(() => appStore?.currentProjectId != null)

  /** 设置全局当前项目（顶栏切换器使用） */
  function setCurrentProject(projectId: number | string | null, projectName = '') {
    appStore?.setCurrentProject(projectId, projectName)
  }

  /** 清除全局当前项目（顶栏选择「全部项目」时使用） */
  function clearProjectContext() {
    appStore?.clearProjectContext()
  }

  return {
    currentProjectId,
    hasProjectContext,
    setCurrentProject,
    clearProjectContext
  }
}
