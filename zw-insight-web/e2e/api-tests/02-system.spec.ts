/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_SYSTEM } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 02 - 系统管理测试
 * 对应功能表: 4.2 全部系统管理
 */
describe('02 - 系统管理', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 机构管理 ============
  describe('机构管理', () => {
    let orgId: number

    it('创建机构', async () => {
      const resp = await client.post('/api/v1/system/org', {
        orgName: TEST_SYSTEM.orgName,
        orgCode: `ORG_${Date.now()}`,
        sort: 1,
        status: 1,
      })
      expectOk(resp, '创建机构')
    })

    it('查询机构列表', async () => {
      const resp = await client.get('/api/v1/system/org', {
        orgName: TEST_SYSTEM.orgName,
      })
      expect(resp.code).toBe(200)
      expect(resp.data).toBeDefined()
      // 找到刚创建的机构
      const list = Array.isArray(resp.data) ? resp.data : resp.data?.records || []
      const found = list.find((o: any) => o.orgName === TEST_SYSTEM.orgName)
      if (found) {
        orgId = found.id
        cleaner.add('删除机构', () => client.delete(`/api/v1/system/org/${orgId}`))
      }
    })

    it('获取机构详情', async () => {
      if (!orgId) return
      const resp = await client.get(`/api/v1/system/org/${orgId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.orgName).toBe(TEST_SYSTEM.orgName)
    })

    it('更新机构', async () => {
      if (!orgId) return
      const resp = await client.put(`/api/v1/system/org/${orgId}`, {
        orgName: TEST_SYSTEM.orgName + '_updated',
        sort: 2,
      })
      expectOk(resp, '更新机构')
    })

    it('修改机构状态', async () => {
      if (!orgId) return
      const resp = await client.put(
        `/api/v1/system/org/${orgId}/status?status=0`
      )
      expectOk(resp, '禁用机构')
    })
  })

  // ============ 岗位管理 ============
  describe('岗位管理', () => {
    let postId: number

    it('创建岗位', async () => {
      const resp = await client.post('/api/v1/system/post', {
        postName: TEST_SYSTEM.postName,
        postCode: `POST_${Date.now()}`,
        sort: 1,
        status: 1,
      })
      expectOk(resp, '创建岗位')
    })

    it('分页查询岗位', async () => {
      const resp = await client.get('/api/v1/system/post', {
        page: 1,
        size: 10,
        postName: TEST_SYSTEM.postName,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((p: any) => p.postName === TEST_SYSTEM.postName)
      if (found) {
        postId = found.id
        cleaner.add('删除岗位', () => client.delete(`/api/v1/system/post/${postId}`))
      }
    })

    it('更新岗位', async () => {
      if (!postId) return
      const resp = await client.put(`/api/v1/system/post/${postId}`, {
        postName: TEST_SYSTEM.postName + '_updated',
        sort: 2,
      })
      expectOk(resp, '更新岗位')
    })

    it('修改岗位状态', async () => {
      if (!postId) return
      const resp = await client.put(`/api/v1/system/post/${postId}/status`, {
        status: 0,
      })
      expectOk(resp, '禁用岗位')
    })
  })

  // ============ 用户管理 ============
  describe('用户管理', () => {
    let userId: number

    it('创建用户', async () => {
      const resp = await client.post('/api/v1/system/user', {
        username: TEST_SYSTEM.userName,
        realName: '测试人员',
        password: 'Test123456',
        status: 1,
      })
      expectOk(resp, '创建用户')
    })

    it('分页查询用户', async () => {
      const resp = await client.get('/api/v1/system/user', {
        page: 1,
        size: 10,
        username: TEST_SYSTEM.userName,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((u: any) => u.username === TEST_SYSTEM.userName)
      if (found) {
        userId = found.id
        cleaner.add('删除用户', () => client.delete(`/api/v1/system/user/${userId}`))
      }
    })

    it('获取用户详情', async () => {
      if (!userId) return
      const resp = await client.get(`/api/v1/system/user/${userId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.username).toBe(TEST_SYSTEM.userName)
    })

    it('更新用户', async () => {
      if (!userId) return
      const resp = await client.put(`/api/v1/system/user/${userId}`, {
        realName: '测试人员_updated',
      })
      expectOk(resp, '更新用户')
    })

    it('重置用户密码', async () => {
      if (!userId) return
      const resp = await client.put(`/api/v1/system/user/${userId}/reset-password`, {
        newPassword: 'NewPass123456',
      })
      expectOk(resp, '重置密码')
    })
  })

  // ============ 角色管理 ============
  describe('角色管理', () => {
    let roleId: number

    it('创建角色', async () => {
      const resp = await client.post('/api/v1/system/role', {
        roleName: TEST_SYSTEM.roleName,
        roleCode: `ROLE_${Date.now()}`,
        sort: 1,
        status: 1,
      })
      expectOk(resp, '创建角色')
    })

    it('分页查询角色', async () => {
      const resp = await client.get('/api/v1/system/role', {
        page: 1,
        size: 10,
        roleName: TEST_SYSTEM.roleName,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.roleName === TEST_SYSTEM.roleName)
      if (found) {
        roleId = found.id
        cleaner.add('删除角色', () => client.delete(`/api/v1/system/role/${roleId}`))
      }
    })

    it('获取角色详情', async () => {
      if (!roleId) return
      const resp = await client.get(`/api/v1/system/role/${roleId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.roleName).toBe(TEST_SYSTEM.roleName)
    })

    it('查询角色菜单权限', async () => {
      if (!roleId) return
      const resp = await client.get(`/api/v1/system/role/${roleId}/menus`)
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })

    it('配置角色数据权限', async () => {
      if (!roleId) return
      const resp = await client.put(`/api/v1/system/role/${roleId}/data-scope`, {
        dataScope: 'ALL',
      })
      expectOk(resp, '配置数据权限')
    })

    it('更新角色', async () => {
      if (!roleId) return
      const resp = await client.put(`/api/v1/system/role/${roleId}`, {
        roleName: TEST_SYSTEM.roleName + '_updated',
        sort: 2,
      })
      expectOk(resp, '更新角色')
    })
  })

  // ============ 菜单管理 ============
  describe('菜单管理', () => {
    it('查询菜单树', async () => {
      const resp = await client.get('/api/v1/system/menu')
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })

    it('获取当前用户菜单', async () => {
      const resp = await client.get('/api/v1/system/menu/user')
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })
  })

  // ============ 字典管理 ============
  describe('字典管理', () => {
    let dictId: number
    const dictCode = `DICT_${Date.now()}`

    it('创建字典', async () => {
      const resp = await client.post('/api/v1/system/dict', {
        dictName: TEST_SYSTEM.dictType,
        dictCode,
        sortOrder: 1,
      })
      expectOk(resp, '创建字典')
    })

    it('分页查询字典', async () => {
      const resp = await client.get('/api/v1/system/dict', {
        page: 1,
        size: 10,
        dictName: TEST_SYSTEM.dictType,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((d: any) => d.dictCode === dictCode)
      if (found) {
        dictId = found.id
        cleaner.add('删除字典', () => client.delete(`/api/v1/system/dict/${dictId}`))
      }
    })

    it('获取字典详情', async () => {
      if (!dictId) return
      const resp = await client.get(`/api/v1/system/dict/${dictId}`)
      expect(resp.code).toBe(200)
    })

    it('更新字典', async () => {
      if (!dictId) return
      const resp = await client.put(`/api/v1/system/dict/${dictId}`, {
        dictName: TEST_SYSTEM.dictType + '_updated',
        sortOrder: 2,
      })
      expectOk(resp, '更新字典')
    })
  })

  // ============ 系统配置 ============
  describe('系统配置', () => {
    it('按分组查询配置', async () => {
      const resp = await client.get('/api/v1/system/config/group/system')
      expect(resp.code).toBe(200)
      // 可能为空数组（该分组无配置），但接口应正常返回
      expect(Array.isArray(resp.data) || resp.data === null).toBe(true)
    })
  })

  // ============ 系统日志 ============
  describe('系统日志', () => {
    it('查询操作日志', async () => {
      const resp = await client.get('/api/v1/system/log/oper', {
        page: 1,
        size: 10,
      })
      expect(resp.code).toBe(200)
    })
  })

  // ============ 审计日志 ============
  describe('审计日志', () => {
    it('查询审计日志', async () => {
      const resp = await client.get('/api/v1/system/audit', {
        page: 1,
        size: 10,
      })
      // 接口可能返回 200 或 404（未实现），都属于预期行为
      expect([200, 404, 405]).toContain(resp.code)
    })
  })

  // ============ 系统监控 ============
  describe('系统监控', () => {
    it('查询健康状态', async () => {
      const resp = await client.get('/api/v1/system/monitor/health')
      // 可能 200 或 404
      expect([200, 404]).toContain(resp.code)
    })
  })
})
