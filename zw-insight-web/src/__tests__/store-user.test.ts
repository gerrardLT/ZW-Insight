/**
 * user store 单元测试（2026-08-14 前端深度补测）
 *
 * 覆盖：token 持久化读写、logout 全量清空、各 setter。
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '@/stores/user'

describe('stores/user', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('初始 token 为空（localStorage 无值）', () => {
    const store = useUserStore()
    expect(store.token).toBe('')
  })

  it('setToken 同步写入 localStorage', () => {
    const store = useUserStore()
    store.setToken('tk-123')
    expect(store.token).toBe('tk-123')
    expect(localStorage.getItem('token')).toBe('tk-123')
  })

  it('setUserInfo / setMenus / setPermissions 正常赋值', () => {
    const store = useUserStore()
    store.setUserInfo({ id: 1, username: 'admin' })
    store.setMenus([{ id: 1, menuName: '首页' }])
    store.setPermissions(['project:create'])
    expect(store.userInfo).toEqual({ id: 1, username: 'admin' })
    expect(store.menus).toHaveLength(1)
    expect(store.permissions).toEqual(['project:create'])
  })

  it('logout 清空全部状态并移除 localStorage token', () => {
    const store = useUserStore()
    store.setToken('tk-123')
    store.setUserInfo({ id: 1 })
    store.setMenus([{ id: 1 }])
    store.setPermissions(['x:y'])

    store.logout()

    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
    expect(store.menus).toEqual([])
    expect(store.permissions).toEqual([])
    expect(localStorage.getItem('token')).toBeNull()
  })

  it('store 创建时从 localStorage 恢复已有 token（刷新页面场景）', () => {
    localStorage.setItem('token', 'persisted-tk')
    // 新 pinia 实例 → store 重新初始化
    setActivePinia(createPinia())
    const store = useUserStore()
    expect(store.token).toBe('persisted-tk')
  })
})
