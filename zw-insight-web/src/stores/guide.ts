import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 核心链路速览抽屉的全局开关。
 *
 * 顶栏「?」按钮与首页引导条是两个互不相干的组件树，经此 store 唤起
 * 挂载在 DefaultLayout 上的 GuideQuickDrawer（抽屉本体全局仅一份）。
 */
export const useGuideStore = defineStore('guide', () => {
  /** 速览抽屉是否可见 */
  const quickVisible = ref(false)

  function openQuick() {
    quickVisible.value = true
  }

  function closeQuick() {
    quickVisible.value = false
  }

  return { quickVisible, openQuick, closeQuick }
})
