import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'

export type ThemeMode = 'light' | 'dark'

/** 获取系统偏好主题 */
function getSystemTheme(): ThemeMode {
  if (typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches) {
    return 'dark'
  }
  return 'light'
}

/**
 * 应用全局状态 Store — 管理侧边栏、主题、设备等全局 UI 状态
 */
export const useAppStore = defineStore('app', () => {
  /** 侧边栏是否折叠 */
  const sidebarCollapsed = ref(false)

  /** 当前设备类型 */
  const device = ref<'desktop' | 'mobile'>('desktop')

  /** 全局 loading 状态 */
  const loading = ref(false)

  /** 全局 loading 计数器（支持并发请求） */
  const loadingCount = ref(0)

  /** 当前选中的项目ID（全局项目上下文） */
  const currentProjectId = ref<number | null>(null)

  /** 当前选中的项目名称 */
  const currentProjectName = ref<string>('')

  /** 面包屑是否显示 */
  const showBreadcrumb = ref(true)

  /** 标签栏是否显示 */
  const showTagsView = ref(true)

  /** 当前主题模式 */
  const theme = ref<ThemeMode>(getSystemTheme())

  /** 是否有全局项目上下文 */
  const hasProjectContext = computed(() => currentProjectId.value !== null)

  /** 是否为深色主题 */
  const isDark = computed(() => theme.value === 'dark')

  /** 应用主题到 DOM */
  function applyTheme(mode: ThemeMode) {
    document.documentElement.dataset.theme = mode
  }

  /** 切换主题 */
  function toggleTheme() {
    theme.value = theme.value === 'light' ? 'dark' : 'light'
  }

  /** 设置主题 */
  function setTheme(mode: ThemeMode) {
    theme.value = mode
  }

  // 监听主题变化，自动应用到 DOM
  watch(theme, (val) => {
    applyTheme(val)
  }, { immediate: true })

  /** 切换侧边栏折叠 */
  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  /** 设置设备类型 */
  function setDevice(type: 'desktop' | 'mobile') {
    device.value = type
  }

  /** 开始加载 */
  function startLoading() {
    loadingCount.value++
    loading.value = true
  }

  /** 结束加载 */
  function endLoading() {
    loadingCount.value = Math.max(0, loadingCount.value - 1)
    if (loadingCount.value === 0) {
      loading.value = false
    }
  }

  /** 设置当前项目上下文 */
  function setCurrentProject(projectId: number | null, projectName = '') {
    currentProjectId.value = projectId
    currentProjectName.value = projectName
  }

  /** 清除项目上下文 */
  function clearProjectContext() {
    currentProjectId.value = null
    currentProjectName.value = ''
  }

  return {
    sidebarCollapsed,
    device,
    loading,
    loadingCount,
    currentProjectId,
    currentProjectName,
    showBreadcrumb,
    showTagsView,
    theme,
    hasProjectContext,
    isDark,
    applyTheme,
    toggleTheme,
    setTheme,
    toggleSidebar,
    setDevice,
    startLoading,
    endLoading,
    setCurrentProject,
    clearProjectContext
  }
}, {
  persist: {
    paths: ['sidebarCollapsed', 'showBreadcrumb', 'showTagsView', 'currentProjectId', 'currentProjectName', 'theme']
  }
})
