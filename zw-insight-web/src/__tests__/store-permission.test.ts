/**
 * permission store 单元测试（2026-08-14 前端深度补测）
 *
 * 覆盖：权限判断（精确匹配/超管绕过/任一匹配）、
 * 菜单树递归提取按钮权限、loaded 状态机、reset。
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePermissionStore, type MenuItem } from '@/stores/permission'

function menu(partial: Partial<MenuItem>): MenuItem {
  return {
    id: 0,
    parentId: 0,
    menuName: '',
    menuType: 'MENU',
    sortOrder: 0,
    visible: 1,
    status: 1,
    ...partial
  }
}

describe('stores/permission', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('setPermissions 置位 loaded', () => {
    const store = usePermissionStore()
    expect(store.loaded).toBe(false)
    store.setPermissions(['a:b'])
    expect(store.loaded).toBe(true)
  })

  it('hasPermission：精确命中 true / 未命中 false', () => {
    const store = usePermissionStore()
    store.setPermissions(['project:create', 'project:edit'])
    expect(store.hasPermission('project:create')).toBe(true)
    expect(store.hasPermission('project:delete')).toBe(false)
  })

  it('hasPermission：超级管理员（*:*:*）绕过一切校验', () => {
    const store = usePermissionStore()
    store.setPermissions(['*:*:*'])
    expect(store.isSuperAdmin).toBe(true)
    expect(store.hasPermission('任意:权限:标识')).toBe(true)
  })

  it('hasAnyPermission：任一命中即 true / 全部缺失 false / 空数组 false', () => {
    const store = usePermissionStore()
    store.setPermissions(['a:b'])
    expect(store.hasAnyPermission(['x:y', 'a:b'])).toBe(true)
    expect(store.hasAnyPermission(['x:y', 'z:w'])).toBe(false)
    expect(store.hasAnyPermission([])).toBe(false)
  })

  it('hasAnyPermission：超级管理员绕过', () => {
    const store = usePermissionStore()
    store.setPermissions(['*:*:*'])
    expect(store.hasAnyPermission(['不存在:的:权限'])).toBe(true)
  })

  it('extractPermissionsFromMenus：仅提取 BUTTON 类型且带 permission 的节点', () => {
    const store = usePermissionStore()
    const menus: MenuItem[] = [
      menu({ id: 1, menuType: 'MENU' }),                       // 菜单节点不提
      menu({ id: 2, menuType: 'BUTTON', permission: '' }),     // 空 permission 不提
      menu({ id: 3, menuType: 'BUTTON', permission: 'p:c' }),  // 有效按钮
      menu({ id: 4, menuType: 'MENU', permission: 'm:x' })     // MENU 类型即使带 permission 也不提
    ]
    expect(store.extractPermissionsFromMenus(menus)).toEqual(['p:c'])
  })

  it('extractPermissionsFromMenus：递归穿透多层 children', () => {
    const store = usePermissionStore()
    const menus: MenuItem[] = [
      menu({
        id: 1,
        children: [
          menu({ id: 11, menuType: 'BUTTON', permission: 'l2:a' }),
          menu({
            id: 12,
            children: [
              menu({ id: 121, menuType: 'BUTTON', permission: 'l3:b' })
            ]
          })
        ]
      }),
      menu({ id: 2, menuType: 'BUTTON', permission: 'l1:c' })
    ]
    expect(store.extractPermissionsFromMenus(menus).sort()).toEqual(['l1:c', 'l2:a', 'l3:b'])
  })

  it('extractPermissionsFromMenus：空数组返回空', () => {
    const store = usePermissionStore()
    expect(store.extractPermissionsFromMenus([])).toEqual([])
  })

  it('reset 清空全部状态', () => {
    const store = usePermissionStore()
    store.setMenus([menu({ id: 1 })])
    store.setPermissions(['a:b'])
    store.reset()
    expect(store.menus).toEqual([])
    expect(store.permissions).toEqual([])
    expect(store.loaded).toBe(false)
  })
})
