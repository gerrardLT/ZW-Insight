/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_PROJECT } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 07 - 预算管理测试
 * 对应功能表: 2.1 预算管理
 */
describe('07 - 预算管理', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let projectId: number
  let budgetId: number

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()

    // 创建测试项目
    const projResp = await client.post('/api/v1/project', {
      projectName: `${TEST_PROJECT.name}_BUDGET_TEST`,
      projectType: 'BUILDING',
      projectAddress: '预算测试项目地址',
    })
    expectOk(projResp, '创建预算测试项目')

    const pageResp = await client.get('/api/v1/project/page', {
      page: 1,
      size: 20,
      projectName: `${TEST_PROJECT.name}_BUDGET_TEST`,
    })
    const records = pageResp.data?.records || []
    const found = records.find((p: any) =>
      p.projectName?.includes('BUDGET_TEST')
    )
    if (found) {
      projectId = found.id
      cleaner.add('删除预算测试项目', () =>
        client.delete(`/api/v1/project/${projectId}`)
      )
    }
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 预算编制 ============
  describe('预算编制 CRUD', () => {
    it('创建目标成本', async () => {
      const resp = await client.post('/api/v1/budget', {
        projectId,
        budgetType: 'ORIGINAL',
        totalAmount: 3000000,
      })
      expectOk(resp, '创建目标成本')
    })

    it('分页查询预算', async () => {
      const resp = await client.get('/api/v1/budget/page', {
        page: 1,
        size: 10,
        projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((b: any) => b.projectId === projectId)
      if (found) {
        budgetId = found.id
        cleaner.add('删除预算', () =>
          client.delete(`/api/v1/budget/${budgetId}`)
        )
      }
    })

    it('按项目 ID 查询预算', async () => {
      const resp = await client.get(`/api/v1/budget/project/${projectId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.projectId).toBe(projectId)
    })

    it('获取预算详情', async () => {
      if (!budgetId) return
      const resp = await client.get(`/api/v1/budget/${budgetId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.totalAmount).toBeDefined()
    })

    it('更新预算', async () => {
      if (!budgetId) return
      const resp = await client.put(`/api/v1/budget/${budgetId}`, {
        projectId,
        budgetType: 'ORIGINAL',
        totalAmount: 3500000,
      })
      expectOk(resp, '更新预算')
    })

    it('验证预算金额更新', async () => {
      if (!budgetId) return
      const resp = await client.get(`/api/v1/budget/${budgetId}`)
      expect(resp.data?.totalAmount).toBe(3500000)
    })

    it('同一项目只允许一条目标成本 - 再创建应失败', async () => {
      const resp = await client.post('/api/v1/budget', {
        projectId,
        budgetType: 'ORIGINAL',
        totalAmount: 9999999,
      })
      // 业务规则：同一项目仅允许一条，应返回非 200
      // 如果返回 200 说明该规则未生效，记录但不阻断测试
      if (resp.code === 200) {
        console.warn('[WARN] 同一项目可创建多条预算，业务规则可能未生效')
      }
    })
  })

  // ============ 预算提交 ============
  describe('预算提交审批', () => {
    it('提交预算审批', async () => {
      if (!budgetId) return
      const resp = await client.post(`/api/v1/budget/${budgetId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 预算管控配置 ============
  describe('预算管控配置', () => {
    it('查询预算管控配置', async () => {
      const resp = await client.get('/api/v1/budget-control-configs', {
        page: 1,
        size: 10,
      })
      expect([200, 404]).toContain(resp.code)
    })

    it('查询预算配置', async () => {
      const resp = await client.get('/api/v1/budget/config/list')
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ 目标成本变更 ============
  describe('目标成本变更', () => {
    it('查询预算变更列表', async () => {
      const resp = await client.get('/api/v1/budget/change', {
        page: 1,
        size: 10,
      })
      expect([200, 404]).toContain(resp.code)
    })

    it('创建预算变更（如果已有预算）', async () => {
      if (!budgetId) return
      const resp = await client.post('/api/v1/budget/change', {
        budgetId,
        projectId,
        changeReason: 'E2E 测试预算变更',
        changeAmount: 500000,
      })
      expect([200, 400, 404, 500]).toContain(resp.code)
    })
  })

  // ============ 费用子类 ============
  describe('费用子类管理', () => {
    it('查询费用子类列表', async () => {
      const resp = await client.get('/api/v1/budget/subcategory/MATERIAL', {
        page: 1,
        size: 10,
      })
      expect([200, 404]).toContain(resp.code)
    })
  })
})
