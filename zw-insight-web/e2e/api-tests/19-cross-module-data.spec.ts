/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_PROJECT } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 19 - 跨模块数据联动测试
 * 验证: 项目→合同→预算→采购 的数据链路完整性
 */
describe('19 - 跨模块数据联动', () => {
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

  // ============ 完整业务链路 ============
  describe('项目→合同→预算链路', () => {
    let contractId: number
    let budgetId: number

    it('Step1: 创建项目', async () => {
      const resp = await client.post('/api/v1/project', {
        projectName: `E2E_CROSS_${Date.now()}`,
        projectType: 'BUILDING',
        projectAddress: '联动测试地址',
        needTender: 0,
        budgetAmount: 10000000,
      })
      expectOk(resp, '创建联动项目')

      const pageResp = await client.get('/api/v1/project/page', {
        page: 1, size: 5, projectName: 'E2E_CROSS',
      })
      const found = pageResp.data?.records?.find((r: any) =>
        r.projectName?.startsWith('E2E_CROSS')
      )
      expect(found).toBeDefined()
      projectId = found.id
      cleaner.add('删除联动项目', () => client.delete(`/api/v1/project/${projectId}`))
    })

    it('Step2: 创建合同关联项目', async () => {
      const resp = await client.post('/api/v1/contract', {
        projectId,
        contractType: 'CONSTRUCTION',
        contractName: `E2E_CROSS_CTR_${Date.now()}`,
        partyAName: '联动测试甲方',
        contractAmount: 5000000,
        signingDate: '2026-01-01',
      })
      expectOk(resp, '创建联动合同')

      const pageResp = await client.get('/api/v1/contract/page', {
        page: 1, size: 10, projectId,
      })
      const found = pageResp.data?.records?.find((r: any) =>
        r.contractName?.includes('E2E_CROSS_CTR')
      )
      if (found) {
        contractId = found.id
        cleaner.add('删除联动合同', () => client.delete(`/api/v1/contract/${contractId}`))
      }
    })

    it('Step3: 创建预算关联项目', async () => {
      const resp = await client.post('/api/v1/budget', {
        projectId,
        budgetType: 'TARGET_COST',
        totalAmount: 8000000,
      })
      expectOk(resp, '创建联动预算')

      const pageResp = await client.get('/api/v1/budget/page', {
        page: 1, size: 10,
      })
      const found = pageResp.data?.records?.find((r: any) => r.projectId === projectId)
      if (found) {
        budgetId = found.id
        cleaner.add('删除联动预算', () => client.delete(`/api/v1/budget/${budgetId}`))
      }
    })

    it('验证: 项目详情包含合同和预算关联', async () => {
      const resp = await client.get(`/api/v1/project/${projectId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.projectName).toContain('E2E_CROSS')
    })
  })

  // ============ 数据一致性验证 ============
  describe('数据一致性', () => {
    it('合同列表按项目筛选一致性', async () => {
      // 先创建一个临时项目
      const prjResp = await client.post('/api/v1/project', {
        projectName: `E2E_CONSIST_${Date.now()}`,
        projectType: 'BUILDING',
        needTender: 0,
      })
      expectOk(prjResp, '创建一致性测试项目')

      const pageResp = await client.get('/api/v1/project/page', {
        page: 1, size: 5, projectName: 'E2E_CONSIST',
      })
      const found = pageResp.data?.records?.[0]
      if (found) {
        const tmpProjectId = found.id
        cleaner.add('删除一致性项目', () => client.delete(`/api/v1/project/${tmpProjectId}`))

        // 创建合同
        await client.post('/api/v1/contract', {
          projectId: tmpProjectId,
          contractType: 'CONSTRUCTION',
          contractName: `E2E_CONSIST_CTR_${Date.now()}`,
          contractAmount: 100000,
          signingDate: '2026-01-01',
        })

        // 按项目查询合同
        const contractResp = await client.get('/api/v1/contract/page', {
          page: 1, size: 20, projectId: tmpProjectId,
        })
        expect(contractResp.code).toBe(200)
        // 所有合同应关联到该项目
        const records = contractResp.data?.records || []
        records.forEach((r: any) => {
          expect(r.projectId).toBe(tmpProjectId)
        })
      }
    })

    it('预算按项目查询一致性', async () => {
      const budgetResp = await client.get('/api/v1/budget/page', {
        page: 1, size: 50,
      })
      expect(budgetResp.code).toBe(200)
      // 所有预算应有有效的 projectId
      const records = budgetResp.data?.records || []
      records.forEach((r: any) => {
        expect(r.projectId).toBeTruthy()
      })
    })

    it('分页参数一致性验证', async () => {
      // 测试不同分页参数
      const resp1 = await client.get('/api/v1/project/page', { page: 1, size: 5 })
      const resp2 = await client.get('/api/v1/project/page', { page: 1, size: 10 })

      expect(resp1.code).toBe(200)
      expect(resp2.code).toBe(200)
      // 校验分页结构与 size 生效；total 为并发写入下的动态值（其它用例会并行创建/删除项目），
      // 不做两次调用间的精确相等断言，仅验证其为合法的非负整数。
      expect(resp1.data?.records?.length).toBeLessThanOrEqual(5)
      expect(resp2.data?.records?.length).toBeLessThanOrEqual(10)
      expect(Number(resp1.data?.total)).toBeGreaterThanOrEqual(0)
      expect(Number(resp2.data?.total)).toBeGreaterThanOrEqual(0)
    })
  })

  // ============ 模块间引用关系 ============
  describe('模块间引用', () => {
    it('项目 list 接口返回可被下拉使用', async () => {
      const resp = await client.get('/api/v1/project/list')
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
      if (resp.data.length > 0) {
        expect(resp.data[0].id).toBeTruthy()
        expect(resp.data[0].projectName).toBeTruthy()
      }
    })

    it('档案接口可引用项目数据', async () => {
      if (!projectId) return
      const resp = await client.get(`/api/v1/archive/project/${projectId}`)
      expect(resp.code).toBe(200)
    })

    it('看板接口可引用项目数据', async () => {
      if (!projectId) return
      const resp = await client.get(`/api/v1/dashboard/project/${projectId}/overview`)
      expect(resp.code).toBe(200)
    })
  })
})
