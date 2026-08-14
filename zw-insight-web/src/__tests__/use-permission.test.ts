/**
 * usePermission 组合式函数单元测试（2026-08-14 前端深度补测）
 *
 * 覆盖：hasPermission/hasAnyPermission 委托到 permission store、
 * isSuperAdmin 为响应式 computed（权限变更后自动更新）。
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePermission } from '@/composables/usePermission'
import { usePermissionStore } from '@/stores/permission'

describe('composables/usePermission', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('hasPermission 委托 store 判断', () => {
    usePermissionStore().setPermissions(['project:create'])
    const { hasPermission } = usePermission()
    expect(hasPermission('project:create')).toBe(true)
    expect(hasPermission('project:delete')).toBe(false)
  })

  it('hasAnyPermission 委托 store 判断', () => {
    usePermissionStore().setPermissions(['a:b'])
    const { hasAnyPermission } = usePermission()
    expect(hasAnyPermission(['x:y', 'a:b'])).toBe(true)
    expect(hasAnyPermission(['x:y'])).toBe(false)
  })

  it('isSuperAdmin 响应式：权限变更后自动更新（回归防护）', () => {
    const store = usePermissionStore()
    store.setPermissions(['普通:权限'])
    const { isSuperAdmin } = usePermission()

    expect(isSuperAdmin.value).toBe(false)

    // 权限变更（如重新登录/切换角色）后必须同步更新
    store.setPermissions(['*:*:*'])
    expect(isSuperAdmin.value).toBe(true)

    store.setPermissions([])
    expect(isSuperAdmin.value).toBe(false)
  })
})
