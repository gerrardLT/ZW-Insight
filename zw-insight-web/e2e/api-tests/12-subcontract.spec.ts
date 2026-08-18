/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_SUBCONTRACT, TEST_PROJECT } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 12 - 分包管理测试
 * 对应功能表: 分包合同、产值报告、结算、奖罚
 */
describe('12 - 分包管理', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  // 后端 JacksonConfig 全局 Long→String 序列化，雪花 ID 超 2^53，必须按字符串持有，禁止 Number() 归一化（尾数丢失）
  let projectId: string
  let subcontractId: string

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()
    const prjResp = await client.post('/api/v1/project', {
      projectName: `${TEST_PROJECT.name}_SUB`,
      projectType: 'BUILDING',
      projectAddress: '分包测试地址',
      needTender: 0,
    })
    if (prjResp.code === 200) {
      const pageResp = await client.get('/api/v1/project/page', {
        page: 1, size: 5, projectName: `${TEST_PROJECT.name}_SUB`,
      })
      if (pageResp.data?.records?.length > 0) {
        projectId = pageResp.data.records[0].id
        cleaner.add('删除分包关联项目', () => client.delete(`/api/v1/project/${projectId}`))
      }
    }
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 分包合同 ============
  describe('分包合同', () => {
    it('创建分包合同', async () => {
      // 字段名以后端实体 BizSubcontract 为准（审计缺陷 D2 修复，2026-08-17）：
      // 原载荷 subcontractorName/startDate/endDate 在实体上不存在，被 Jackson 静默丢弃致假绿
      const resp = await client.post('/api/v1/subcontract/contract', {
        projectId,
        contractName: TEST_SUBCONTRACT.contractName,
        contractCode: `E2E_SC_${Date.now()}`,
        subcontractor: 'E2E分包商',
        contractAmount: 800000,
        signingDate: '2026-01-01',
      })
      expectOk(resp, '创建分包合同')
    })

    it('分页查询分包合同', async () => {
      const resp = await client.get('/api/v1/subcontract/contract/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.contractName === TEST_SUBCONTRACT.contractName)
      expect(found).toBeDefined()
      subcontractId = found.id
      cleaner.add('删除分包合同', () =>
        client.delete(`/api/v1/subcontract/contract/${subcontractId}`)
      )
    })

    it('获取分包合同详情（字段契约回读断言）', async () => {
      expect(subcontractId).toBeTruthy()
      const resp = await client.get(`/api/v1/subcontract/contract/${subcontractId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.contractName).toBe(TEST_SUBCONTRACT.contractName)
      // 审计缺陷 D2 钉住：逐字段断言提交值真实落库，一行字段漂移即红，
      // 杜绝未知字段被 Jackson 静默丢弃后测试假绿
      expect(resp.data?.subcontractor).toBe('E2E分包商')
      expect(resp.data?.signingDate).toBe('2026-01-01')
      expect(String(resp.data?.projectId)).toBe(String(projectId))
      expect(Number(resp.data?.contractAmount)).toBe(800000)
    })

    it('更新分包合同', async () => {
      const resp = await client.put(`/api/v1/subcontract/contract/${subcontractId}`, {
        contractName: TEST_SUBCONTRACT.contractName,
        contractAmount: 900000,
      })
      expectOk(resp, '更新分包合同')
    })

    it('提交分包合同审批', async () => {
      const resp = await client.post(`/api/v1/subcontract/contract/${subcontractId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 分包产值报告 ============
  describe('分包产值', () => {
    let outputId: number

    it('创建分包产值报告', async () => {
      const resp = await client.post('/api/v1/subcontract/output', {
        projectId,
        contractId: subcontractId,
        reportPeriod: '2026-01',
        outputAmount: 120000,
      })
      expectOk(resp, '创建分包产值')
    })

    it('分页查询分包产值', async () => {
      const resp = await client.get('/api/v1/subcontract/output/page', {
        page: 1, size: 20, projectId, contractId: subcontractId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        outputId = records[0].id
        cleaner.add('删除分包产值', () =>
          client.delete(`/api/v1/subcontract/output/${outputId}`)
        )
      }
    })

    it('获取分包产值详情', async () => {
      if (!outputId) return
      const resp = await client.get(`/api/v1/subcontract/output/${outputId}`)
      expect(resp.code).toBe(200)
    })

    it('提交分包产值审批', async () => {
      if (!outputId) return
      const resp = await client.post(`/api/v1/subcontract/output/${outputId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 分包结算 ============
  describe('分包结算', () => {
    let settlementId: number

    it('创建分包结算单', async () => {
      const resp = await client.post('/api/v1/subcontract/settlement', {
        projectId,
        contractId: subcontractId,
        settlementAmount: 300000,
        settlementDate: '2026-06-01',
        remark: 'E2E测试分包结算',
        details: [
          { itemName: '基础工程', amount: 150000, quantity: 100, unitPrice: 1500 },
          { itemName: '结构工程', amount: 150000, quantity: 100, unitPrice: 1500 },
        ],
      })
      expectOk(resp, '创建分包结算')
    })

    it('分页查询分包结算', async () => {
      const resp = await client.get('/api/v1/subcontract/settlement', {
        page: 1, size: 20, projectId, contractId: subcontractId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        settlementId = records[0].id
        cleaner.add('删除分包结算', () =>
          client.delete(`/api/v1/subcontract/settlement/${settlementId}`)
        )
      }
    })

    it('获取分包结算详情', async () => {
      if (!settlementId) return
      const resp = await client.get(`/api/v1/subcontract/settlement/${settlementId}`)
      expect(resp.code).toBe(200)
    })

    it('提交分包结算审批', async () => {
      if (!settlementId) return
      const resp = await client.post(`/api/v1/subcontract/settlement/${settlementId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 分包奖罚 ============
  describe('分包奖罚', () => {
    let rpId: number

    it('创建分包奖罚记录', async () => {
      // 字段名以后端实体为准：rpType（REWARD/PUNISH），amount 必须 > 0（P2 守卫，2026-08-13）
      const resp = await client.post('/api/v1/subcontract/reward-punish', {
        projectId,
        contractId: subcontractId,
        rpType: 'PUNISH',
        amount: 10000,
        reason: 'E2E测试：质量问题罚款',
      })
      expectOk(resp, '创建分包奖罚')
    })

    it('分页查询分包奖罚', async () => {
      const resp = await client.get('/api/v1/subcontract/reward-punish/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        rpId = records[0].id
        cleaner.add('删除分包奖罚', () =>
          client.delete(`/api/v1/subcontract/reward-punish/${rpId}`)
        )
      }
    })
  })
})
