/**
 * stores（user/network）单元测试（2026-08-15 P3 方向3 补测）
 *
 * 被测代码为 src/stores/*.ts 真实实现；uni 存储走 setup.ts 内存桩。
 * 覆盖：token 持久化/登出清理+跳转、离线标记与 networkType='none' 联动。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { resetUniStorage, getUni } from './setup'
import { useUserStore } from '@/stores/user'
import { useNetworkStore } from '@/stores/network'

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  ;(getUni() as any).reLaunch = vi.fn()
})

describe('user store', () => {
  it('setToken：state 与 uni 存储双写', () => {
    const store = useUserStore()
    store.setToken('tk-abc')
    expect(store.token).toBe('tk-abc')
    expect(getUni().getStorageSync('token')).toBe('tk-abc')
  })

  it('初始化从存储恢复 token', () => {
    getUni().setStorageSync('token', 'tk-persisted')
    setActivePinia(createPinia()) // 新实例重新读存储
    expect(useUserStore().token).toBe('tk-persisted')
  })

  it('logout：清 token/userInfo + 移除存储 + reLaunch 登录页', () => {
    const store = useUserStore()
    store.setToken('tk-x')
    store.setUserInfo({ id: 1, username: 'u' })

    store.logout()

    expect(store.token).toBe('')
    expect(store.userInfo).toBeNull()
    expect(getUni().getStorageSync('token')).toBe('')
    expect((getUni() as any).reLaunch).toHaveBeenCalledWith({ url: '/pages/login/index' })
  })
})

describe('network store', () => {
  it('默认在线（isOffline=false）', () => {
    expect(useNetworkStore().isOffline).toBe(false)
  })

  it('setOffline 直接切换标记', () => {
    const store = useNetworkStore()
    store.setOffline(true)
    expect(store.isOffline).toBe(true)
  })

  it('setNetworkType：none/空值视为离线，其余在线', () => {
    const store = useNetworkStore()
    store.setNetworkType('none')
    expect(store.isOffline).toBe(true)
    expect(store.networkType).toBe('none')
    store.setNetworkType('')
    expect(store.isOffline).toBe(true)
    store.setNetworkType('wifi')
    expect(store.isOffline).toBe(false)
    expect(store.networkType).toBe('wifi')
  })
})
