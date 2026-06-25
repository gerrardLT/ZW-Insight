/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

// 扩展 Vue Router RouteMeta 类型
declare module 'vue-router' {
  interface RouteMeta {
    /** 页面标题 */
    title?: string
    /** 菜单图标 */
    icon?: string
    /** 是否在导航菜单中隐藏 */
    hidden?: boolean
    /** 访问该路由所需的权限标识（单个或多个，满足其一即可） */
    permission?: string | string[]
  }
}
