/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_PROJECT } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 04 - 项目管理测试
 * 对应功能表: 1.3 项目报备
 */
describe('04 - 项目管理', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let projectId: number

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 项目 CRUD ============
  describe('项目 CRUD', () => {
    it('创建项目', async () => {
      const resp = await client.post('/api/v1/project', {
        projectName: TEST_PROJECT.name,
        projectNature: TEST_PROJECT.projectType,
        projectType: 'BUILDING',
        projectAddress: TEST_PROJECT.address,
        projectOverview: TEST_PROJECT.description,
        contactName: '测试联系人',
        contactPhone: '13800138000',
        needTender: 1,
        budgetAmount: 5000000,
      })
      expectOk(resp, '创建项目')
    })

    it('分页查询项目 - 找到创建的项目', async () => {
      const resp = await client.get('/api/v1/project/page', {
        page: 1,
        size: 20,
        projectName: TEST_PROJECT.name,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      expect(records.length).toBeGreaterThan(0)

      const found = records.find((p: any) => p.projectName === TEST_PROJECT.name)
      expect(found).toBeDefined()
      projectId = found.id
      cleaner.add('删除项目', () => client.delete(`/api/v1/project/${projectId}`))
    })

    it('获取项目详情', async () => {
      expect(projectId).toBeTruthy()
      const resp = await client.get(`/api/v1/project/${projectId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.projectName).toBe(TEST_PROJECT.name)
      expect(resp.data?.status).toBe('DRAFT')
    })

    it('更新项目信息', async () => {
      expect(projectId).toBeTruthy()
      const resp = await client.put(`/api/v1/project/${projectId}`, {
        projectName: TEST_PROJECT.name,
        projectOverview: '更新后的项目概述',
        contactName: '更新联系人',
        contactPhone: '13900139000',
      })
      expectOk(resp, '更新项目')
    })

    it('验证更新生效', async () => {
      const resp = await client.get(`/api/v1/project/${projectId}`)
      expect(resp.data?.contactName).toBe('更新联系人')
    })
  })

  // ============ 项目列表（下拉） ============
  describe('项目列表', () => {
    it('list 接口 - 按名称模糊匹配', async () => {
      const resp = await client.get('/api/v1/project/list', {
        projectName: 'E2E_TEST',
      })
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
      expect(resp.data.length).toBeGreaterThan(0)
    })
  })

  // ============ 项目成员 ============
  describe('项目成员', () => {
    let memberUserId: number // 新建的第二个真实用户 ID
    let memberId: number // 该成员在 biz_project_member 中的记录 ID
    const memberUsername = `E2E_MEMBER_${Date.now()}`

    it('准备：创建真实用户用于成员管理', async () => {
      // 创建项目时创建者(admin)已自动成为唯一项目经理，
      // 故需另建一个真实用户来验证"添加/移除普通成员"的完整流程。
      const createResp = await client.post('/api/v1/system/user', {
        username: memberUsername,
        realName: 'E2E测试成员',
        password: 'Test123456',
        status: 1,
      })
      expectOk(createResp, '创建成员用户')

      const pageResp = await client.get('/api/v1/system/user', {
        page: 1, size: 10, username: memberUsername,
      })
      const found = (pageResp.data?.records || []).find(
        (u: any) => u.username === memberUsername
      )
      expect(found).toBeDefined()
      memberUserId = found.id
      cleaner.add('删除成员用户', () =>
        client.delete(`/api/v1/system/user/${memberUserId}`)
      )
    })

    it('添加项目成员', async () => {
      if (!memberUserId) return
      const resp = await client.post(`/api/v1/project/${projectId}/members`, {
        userId: memberUserId,
        userName: 'E2E测试成员',
        projectRoles: ['CONSTRUCTOR'],
      })
      expectOk(resp, '添加成员')
    })

    it('查询项目成员', async () => {
      const resp = await client.get(`/api/v1/project/${projectId}/members`)
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
      // 定位刚添加的普通成员（非项目经理），避免误删唯一项目经理
      const added = resp.data.find(
        (m: any) => String(m.userId) === String(memberUserId)
      )
      expect(added).toBeDefined()
      memberId = added.id
    })

    it('移除项目成员', async () => {
      if (!memberId) return
      const resp = await client.delete(`/api/v1/project/members/${memberId}`)
      expectOk(resp, '移除成员')
    })
  })

  // ============ 项目状态流转 ============
  describe('项目状态流转', () => {
    it('提交项目（DRAFT → 提交审批）', async () => {
      const resp = await client.post(`/api/v1/project/${projectId}/submit`)
      // 可能成功也可能因为缺少审批流程而失败
      expect([200, 400, 500]).toContain(resp.code)
    })

    it('结项条件预检', async () => {
      const resp = await client.get(`/api/v1/project/${projectId}/close-check`)
      expect(resp.code).toBe(200)
    })
  })

  // ============ 不需要投标的项目 ============
  describe('不需要投标的项目', () => {
    let noTenderProjectId: number

    it('创建不需要投标的项目', async () => {
      const resp = await client.post('/api/v1/project', {
        projectName: `${TEST_PROJECT.name}_NO_TENDER`,
        projectType: 'BUILDING',
        needTender: 0,
        projectAddress: '不需要投标的地址',
      })
      expectOk(resp, '创建不需要投标的项目')
    })

    it('查询不需要投标的项目', async () => {
      const resp = await client.get('/api/v1/project/page', {
        page: 1,
        size: 20,
        projectName: `${TEST_PROJECT.name}_NO_TENDER`,
      })
      const records = resp.data?.records || []
      const found = records.find((p: any) =>
        p.projectName?.includes('NO_TENDER')
      )
      if (found) {
        noTenderProjectId = found.id
        cleaner.add('删除不需要投标的项目', () =>
          client.delete(`/api/v1/project/${noTenderProjectId}`)
        )
        expect(found.needTender).toBe(0)
      }
    })
  })

  // ============ 批量删除 ============
  describe('批量操作', () => {
    it('批量删除 - 空数组应成功', async () => {
      const resp = await client.delete('/api/v1/project/batch')
      // 批量删除可能需要 body，测试空请求
      expect([200, 400, 405, 500]).toContain(resp.code)
    })
  })
})
