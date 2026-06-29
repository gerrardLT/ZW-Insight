// Vue Router RouteMeta 类型增强（模块增强，必须是 module 文件，故带 import/export）
// 注意：此处使用 `import 'vue-router'` + `export {}` 让 TS 将下面的 declare module
// 视为对真实 vue-router 模块的「增强合并」，而非「替换」。若放在全局 script 文件中
// 会覆盖整个模块类型，导致 useRouter/useRoute/createRouter 等被判定为不存在。
import 'vue-router'

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

export {}
