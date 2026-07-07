import { usePermissionStore } from '@/stores/permission'

/**
 * 权限判断组合式函数
 * 用于在组件中便捷地进行按钮级权限控制
 *
 * @example
 * const { hasPermission, hasAnyPermission } = usePermission()
 * if (hasPermission('project:create')) { ... }
 */
export function usePermission() {
  const permissionStore = usePermissionStore()

  /**
   * 判断是否拥有指定权限
   * @param permission 权限标识，如 'project:create'
   */
  function hasPermission(permission: string): boolean {
    return permissionStore.hasPermission(permission)
  }

  /**
   * 判断是否拥有任一权限
   * @param permissions 权限标识数组
   */
  function hasAnyPermission(permissions: string[]): boolean {
    return permissionStore.hasAnyPermission(permissions)
  }

  /**
   * 判断是否为超级管理员
   */
  const isSuperAdmin = permissionStore.isSuperAdmin

  return { hasPermission, hasAnyPermission, isSuperAdmin }
}
