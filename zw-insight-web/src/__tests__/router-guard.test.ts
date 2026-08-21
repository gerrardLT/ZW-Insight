/**
 * 路由守卫单元测试（2026-08-14 前端深度补测；2026-08-22 权限守卫 S5 扩展）
 *
 * 覆盖 router.beforeEach 全部分支：
 * - 未登录：非白名单 → /login；白名单（login/forgot-password/403/404）放行
 * - 已登录访问 /login → 重定向首页
 * - meta.permission 单值/数组校验：命中放行、未命中 → /403、超管 *:*:* 绕过
 * - constantRoutes 真实路由视图码：无视图码 403 / 持视图码放行 / 写路由细化码覆盖父级
 *
 * 视图组件全部 mock 为哑组件：本测试只验证守卫决策逻辑，不加载真实页面。
 * 注意：每次导航附加唯一 _t query——vue-router 对「push 到当前所在路径」
 * 抛 redundant navigation 短路导航（守卫不执行），query 不影响守卫的 to.path 判断。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { defineComponent } from 'vue'

vi.mock('nprogress', () => ({ default: { start: vi.fn(), done: vi.fn() } }))

const stub = defineComponent({ render: () => null })
vi.mock('@/views/login/index.vue', () => ({ default: stub }))
vi.mock('@/views/login/forgot-password.vue', () => ({ default: stub }))
vi.mock('@/layouts/DefaultLayout.vue', () => ({ default: stub }))
vi.mock('@/views/dashboard/index.vue', () => ({ default: stub }))
vi.mock('@/views/dashboard/project-dashboard.vue', () => ({ default: stub }))
vi.mock('@/views/project/index.vue', () => ({ default: stub }))
vi.mock('@/views/project/form.vue', () => ({ default: stub }))
vi.mock('@/views/project/detail.vue', () => ({ default: stub }))
vi.mock('@/views/finance/invoice-apply.vue', () => ({ default: stub }))
vi.mock('@/views/user/devices.vue', () => ({ default: stub }))
vi.mock('@/views/system/user/index.vue', () => ({ default: stub }))
vi.mock('@/views/error/403.vue', () => ({ default: stub }))
vi.mock('@/views/error/404.vue', () => ({ default: stub }))

import router from '@/router'
import { useUserStore } from '@/stores/user'

/** 动态注册带 meta.permission 的测试路由（单值/数组校验基础用例） */
let permRoutesAdded = false
function ensurePermRoutes() {
  if (permRoutesAdded) return
  permRoutesAdded = true
  router.addRoute({ path: '/perm-single', component: stub, meta: { permission: 'test:single' } })
  router.addRoute({ path: '/perm-multi', component: stub, meta: { permission: ['perm:a', 'perm:b'] } })
}

/** 带唯一 query 的导航（防 redundant navigation 短路），断言用 path 不含 query */
let navSeq = 0
async function nav(target: string) {
  navSeq += 1
  await router.push({ path: target, query: { _t: String(navSeq) } })
  return router.currentRoute.value.path
}

describe('router 全局守卫', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    ensurePermRoutes()
  })

  describe('未登录（无 token）', () => {
    it('访问业务页被重定向到 /login', async () => {
      expect(await nav('/project/list')).toBe('/login')
    })

    it('白名单路径放行：/login', async () => {
      expect(await nav('/login')).toBe('/login')
    })

    it('白名单路径放行：/forgot-password', async () => {
      expect(await nav('/forgot-password')).toBe('/forgot-password')
    })
  })

  describe('已登录', () => {
    beforeEach(() => {
      const store = useUserStore()
      store.setToken('tk-1')
      // /login 重定向落点 /dashboard 需 dashboard:view；/project/list 需 project:view
      store.setPermissions(['dashboard:view', 'project:view'])
    })

    it('访问 /login 重定向首页（最终落在 /dashboard）', async () => {
      expect(await nav('/login')).toBe('/dashboard')
    })

    it('持视图码的业务页正常放行', async () => {
      expect(await nav('/project/list')).toBe('/project/list')
    })
  })

  describe('meta.permission 权限校验', () => {
    it('无权限 → /403', async () => {
      const store = useUserStore()
      store.setToken('tk-1')
      store.setPermissions(['别的:权限'])
      expect(await nav('/perm-single')).toBe('/403')
    })

    it('持有单值权限 → 放行', async () => {
      const store = useUserStore()
      store.setToken('tk-1')
      store.setPermissions(['test:single'])
      expect(await nav('/perm-single')).toBe('/perm-single')
    })

    it('数组权限：任一命中即放行', async () => {
      const store = useUserStore()
      store.setToken('tk-1')
      store.setPermissions(['perm:b'])
      expect(await nav('/perm-multi')).toBe('/perm-multi')
    })

    it('数组权限：全部缺失 → /403', async () => {
      const store = useUserStore()
      store.setToken('tk-1')
      store.setPermissions(['perm:x'])
      expect(await nav('/perm-multi')).toBe('/403')
    })

    it('超级管理员（*:*:*）绕过 meta.permission', async () => {
      const store = useUserStore()
      store.setToken('tk-1')
      store.setPermissions(['*:*:*'])
      expect(await nav('/perm-single')).toBe('/perm-single')
    })
  })

  describe('constantRoutes 真实路由视图码（权限守卫 S4）', () => {
    it('无视图码访问模块路由 → /403', async () => {
      const store = useUserStore()
      store.setToken('tk-1')
      store.setPermissions(['message:view'])
      expect(await nav('/project/list')).toBe('/403')
    })

    it('持有模块视图码 → 放行', async () => {
      const store = useUserStore()
      store.setToken('tk-1')
      store.setPermissions(['finance:view'])
      expect(await nav('/finance/invoice-apply')).toBe('/finance/invoice-apply')
    })

    it('写路由细化：仅持 project:view 访问新增项目 → /403', async () => {
      const store = useUserStore()
      store.setToken('tk-1')
      store.setPermissions(['project:view'])
      expect(await nav('/project/create')).toBe('/403')
    })

    it('写路由细化：持 project:create 放行新增项目', async () => {
      const store = useUserStore()
      store.setToken('tk-1')
      store.setPermissions(['project:view', 'project:create'])
      expect(await nav('/project/create')).toBe('/project/create')
    })

    it('无权限路由不受影响：个人中心（无 meta.permission）放行', async () => {
      const store = useUserStore()
      store.setToken('tk-1')
      store.setPermissions([])
      expect(await nav('/user/devices')).toBe('/user/devices')
    })

    it('超级管理员（*:*:*）访问任意模块路由放行', async () => {
      const store = useUserStore()
      store.setToken('tk-1')
      store.setPermissions(['*:*:*'])
      expect(await nav('/system/user')).toBe('/system/user')
    })
  })
})
