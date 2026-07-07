import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/** 菜单项类型 */
export interface MenuItem {
  id: number
  parentId: number
  menuName: string
  menuType: string
  path?: string
  component?: string
  icon?: string
  permission?: string
  sortOrder: number
  visible: number
  status: number
  children?: MenuItem[]
}

/**
 * 权限 Store — 管理菜单、按钮权限和动态路由
 */
export const usePermissionStore = defineStore('permission', () => {
  /** 用户菜单树 */
  const menus = ref<MenuItem[]>([])

  /** 用户按钮权限集合 */
  const permissions = ref<string[]>([])

  /** 是否已加载过权限数据 */
  const loaded = ref(false)

  /** 是否为超级管理员 */
  const isSuperAdmin = computed(() => permissions.value.includes('*:*:*'))

  /**
   * 设置菜单列表
   */
  function setMenus(data: MenuItem[]) {
    menus.value = data
  }

  /**
   * 设置按钮权限列表
   */
  function setPermissions(perms: string[]) {
    permissions.value = perms
    loaded.value = true
  }

  /**
   * 判断是否拥有指定权限
   * @param permission 权限标识，如 'project:create'
   */
  function hasPermission(permission: string): boolean {
    if (isSuperAdmin.value) return true
    return permissions.value.includes(permission)
  }

  /**
   * 判断是否拥有任一权限
   * @param permissionList 权限标识数组
   */
  function hasAnyPermission(permissionList: string[]): boolean {
    if (isSuperAdmin.value) return true
    return permissionList.some(p => permissions.value.includes(p))
  }

  /**
   * 从菜单树中递归提取所有按钮权限
   */
  function extractPermissionsFromMenus(menuList: MenuItem[]): string[] {
    const perms: string[] = []
    function walk(items: MenuItem[]) {
      for (const item of items) {
        if (item.menuType === 'BUTTON' && item.permission) {
          perms.push(item.permission)
        }
        if (item.children?.length) {
          walk(item.children)
        }
      }
    }
    walk(menuList)
    return perms
  }

  /**
   * 重置权限状态
   */
  function reset() {
    menus.value = []
    permissions.value = []
    loaded.value = false
  }

  return {
    menus,
    permissions,
    loaded,
    isSuperAdmin,
    setMenus,
    setPermissions,
    hasPermission,
    hasAnyPermission,
    extractPermissionsFromMenus,
    reset
  }
}, {
  persist: true
})
