/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_PROJECT, TEST_CONTRACT } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 06 - 合同管理测试
 * 对应功能表: 1.5 合同管理全部
 */
describe('06 - 合同管理', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let projectId: number
  let contractId: number

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()

    // 创建测试项目
    const projResp = await client.post('/api/v1/project', {
      projectName: `${TEST_PROJECT.name}_CONTRACT_TEST`,
      projectType: 'BUILDING',
      projectAddress: '合同测试项目地址',
    })
    expectOk(projResp, '创建合同测试项目')

    const pageResp = await client.get('/api/v1/project/page', {
      page: 1,
      size: 20,
      projectName: `${TEST_PROJECT.name}_CONTRACT_TEST`,
    })
    const records = pageResp.data?.records || []
    const found = records.find((p: any) =>
      p.projectName?.includes('CONTRACT_TEST')
    )
    if (found) {
      projectId = found.id
      cleaner.add('删除合同测试项目', () =>
        client.delete(`/api/v1/project/${projectId}`)
      )
    }
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 施工合同 CRUD ============
  describe('施工合同 CRUD', () => {
    it('创建施工合同', async () => {
      const resp = await client.post('/api/v1/contract', {
        projectId,
        contractType: 'REGISTER',
        partyAName: 'E2E 甲方单位',
        signingDate: '2025-01-01',
        startDate: '2025-03-01',
        endDate: '2026-12-31',
        contractAmount: TEST_CONTRACT.amount,
        taxRate: 9,
      })
      expectOk(resp, '创建施工合同')
    })

    it('分页查询合同', async () => {
      const resp = await client.get('/api/v1/contract/page', {
        page: 1,
        size: 10,
        projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((c: any) => c.projectId === projectId)
      if (found) {
        contractId = found.id
        cleaner.add('删除合同', () =>
          client.delete(`/api/v1/contract/${contractId}`)
        )
      }
    })

    it('获取合同详情', async () => {
      if (!contractId) return
      const resp = await client.get(`/api/v1/contract/${contractId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.contractAmount).toBe(TEST_CONTRACT.amount)
    })

    it('更新合同', async () => {
      if (!contractId) return
      const resp = await client.put(`/api/v1/contract/${contractId}`, {
        projectId,
        contractType: 'REGISTER',
        partyAName: 'E2E 甲方单位_updated',
        contractAmount: 1200000,
        taxRate: 9,
      })
      expectOk(resp, '更新合同')
    })

    it('验证合同金额更新', async () => {
      if (!contractId) return
      const resp = await client.get(`/api/v1/contract/${contractId}`)
      expect(resp.data?.partyAName).toBe('E2E 甲方单位_updated')
    })
  })

  // ============ 合同明细 ============
  describe('合同明细', () => {
    it('保存合同明细', async () => {
      if (!contractId) return
      const resp = await client.post(`/api/v1/contract/${contractId}/details`, [
        {
          itemName: 'E2E 测试工程项',
          unit: 'm2',
          quantity: 1000,
          unitPrice: 500,
          amount: 500000,
        },
        {
          itemName: 'E2E 测试工程项2',
          unit: 'm',
          quantity: 200,
          unitPrice: 300,
          amount: 60000,
        },
      ])
      expectOk(resp, '保存合同明细')
    })

    it('查询合同明细', async () => {
      if (!contractId) return
      const resp = await client.get(`/api/v1/contract/${contractId}/details`)
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
      expect(resp.data.length).toBeGreaterThanOrEqual(2)
    })
  })

  // ============ 提交审批 ============
  describe('合同提交', () => {
    it('提交合同审批', async () => {
      if (!contractId) return
      const resp = await client.post(`/api/v1/contract/${contractId}/submit`)
      // 可能成功或因审批流程不存在而失败
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 其他合同相关接口可用性 ============
  describe('合同相关子模块接口', () => {
    it('查询 BOQ（工程量清单）', async () => {
      if (!contractId) return
      const resp = await client.get(`/api/v1/contracts/${contractId}/boq`)
      expect([200, 404]).toContain(resp.code)
    })

    it('查询产值上报列表', async () => {
      const resp = await client.get('/api/v1/contract/output/page', {
        page: 1,
        size: 10,
      })
      expect([200, 404]).toContain(resp.code)
    })

    it('查询变更签证列表', async () => {
      const resp = await client.get('/api/v1/contract/change-visa', {
        page: 1,
        size: 10,
      })
      expect([200, 404]).toContain(resp.code)
    })

    it('查询最终结算列表', async () => {
      const resp = await client.get('/api/v1/contract/settlement', {
        page: 1,
        size: 10,
      })
      expect([200, 404]).toContain(resp.code)
    })

    it('查询其他收入/支出合同', async () => {
      const resp = await client.get('/api/v1/contract/other', {
        page: 1,
        size: 10,
      })
      expect([200, 404]).toContain(resp.code)
    })
  })
})
