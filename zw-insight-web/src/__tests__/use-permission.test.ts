/**
 * usePermission 组合式函数单元测试（2026-08-14 补测；V2026_124 权限源修正）
 *
 * 覆盖：hasPermission/hasAnyPermission 读取 userStore.permissions（与登录、v-permission 同源）、
 * isSuperAdmin 为响应式 computed（权限变更后自动更新）。
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePermission } from '@/composables/usePermission'
import { useUserStore } from '@/stores/user'

describe('composables/usePermission', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('hasPermission 读取登录写入的 userStore.permissions', () => {
    useUserStore().setPermissions(['project:create'])
    const { hasPermission } = usePermission()
    expect(hasPermission('project:create')).toBe(true)
    expect(hasPermission('project:delete')).toBe(false)
  })

  it('hasAnyPermission 任一命中即放行', () => {
    useUserStore().setPermissions(['a:b'])
    const { hasAnyPermission } = usePermission()
    expect(hasAnyPermission(['x:y', 'a:b'])).toBe(true)
    expect(hasAnyPermission(['x:y'])).toBe(false)
  })

  it('超管通配权限 *:*:* 放行任意码', () => {
    useUserStore().setPermissions(['*:*:*'])
    const { hasPermission, hasAnyPermission, isSuperAdmin } = usePermission()
    expect(hasPermission('anything:at:all')).toBe(true)
    expect(hasAnyPermission(['x:y'])).toBe(true)
    expect(isSuperAdmin.value).toBe(true)
  })

  it('isSuperAdmin 响应式：权限变更后自动更新（回归防护）', () => {
    const store = useUserStore()
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
