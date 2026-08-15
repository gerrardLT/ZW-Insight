/**
 * .vue SFC 模块声明（2026-08-15 P3 方向3：视图组件测试需要）
 */
declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<Record<string, unknown>, Record<string, unknown>, any>
  export default component
}
