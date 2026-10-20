import { computed } from 'vue'
import { getActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'

/**
 * 权限判断组合式函数
 * 用于在组件中便捷地进行按钮级权限控制
 *
 * 权限来源：登录响应写入的 userStore.permissions（与 v-permission 指令同源）。
 * 不再读取 permission store —— 后者在登录流程中从未被写入，曾导致首页
 * finance:view 等判断恒为 false（V2026_124 修复）。
 *
 * @example
 * const { hasPermission, hasAnyPermission } = usePermission()
 * if (hasPermission('project:create')) { ... }
 */
export function usePermission() {
  // 壳测试（只挂 ElementPlus 未装 Pinia）下 useUserStore 会抛错；
  // 无 Pinia 时按「无权限」处理，保证页面仍可挂载、按钮按最小权限隐藏。
  const userStore = getActivePinia() ? useUserStore() : null
  const permissionList = computed<string[]>(() => userStore?.permissions || [])

  /**
   * 判断是否拥有指定权限
   * @param permission 权限标识，如 'project:create'
   */
  function hasPermission(permission: string): boolean {
    return permissionList.value.includes('*:*:*') || permissionList.value.includes(permission)
  }

  /**
   * 判断是否拥有任一权限
   * @param permissions 权限标识数组
   */
  function hasAnyPermission(permissions: string[]): boolean {
    return permissionList.value.includes('*:*:*') || permissions.some((p) => permissionList.value.includes(p))
  }

  /**
   * 判断是否为超级管理员
   * computed 包装保证响应性：登录/切换账号后权限列表变化可即时反映。
   */
  const isSuperAdmin = computed(() => permissionList.value.includes('*:*:*'))

  return { hasPermission, hasAnyPermission, isSuperAdmin }
}
