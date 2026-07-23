import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { RouteLocationNormalized } from 'vue-router'

export interface TagView {
  path: string
  fullPath: string
  title: string
  name?: string
}

/**
 * 标签页导航 Store — 管理已访问的页面标签
 */
export const useTagsViewStore = defineStore('tagsView', () => {
  /** 已访问的标签列表 */
  const visitedViews = ref<TagView[]>([])

  /** 添加标签 */
  function addView(route: RouteLocationNormalized) {
    const title = route.meta?.title as string | undefined
    if (!title || route.meta?.hidden) return
    if (visitedViews.value.some((v) => v.path === route.path)) return

    visitedViews.value.push({
      path: route.path,
      fullPath: route.fullPath,
      title,
      name: route.name as string | undefined
    })
  }

  /** 关闭指定标签，返回需要跳转的目标标签 */
  function closeView(target: TagView): TagView | undefined {
    const idx = visitedViews.value.findIndex((v) => v.path === target.path)
    if (idx === -1) return undefined
    visitedViews.value.splice(idx, 1)
    // 返回相邻标签供跳转
    return visitedViews.value[idx] || visitedViews.value[idx - 1]
  }

  /** 关闭其他标签 */
  function closeOthers(target: TagView) {
    visitedViews.value = visitedViews.value.filter(
      (v) => v.path === target.path || v.path === '/dashboard'
    )
  }

  /** 关闭所有标签（保留首页） */
  function closeAll() {
    visitedViews.value = visitedViews.value.filter((v) => v.path === '/dashboard')
  }

  return {
    visitedViews,
    addView,
    closeView,
    closeOthers,
    closeAll
  }
}, {
  persist: {
    paths: ['visitedViews']
  }
})
