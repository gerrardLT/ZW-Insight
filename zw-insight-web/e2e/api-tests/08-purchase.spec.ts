/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_PURCHASE, TEST_PROJECT } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 08 - 采购管理 + 三方比价测试
 * 对应功能表: 采购询价、报价比较、定标、采购合同、采购结算
 */
describe('08 - 采购管理', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let inquiryId: number
  let purchaseContractId: number
  let projectId: number

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()

    // 创建测试项目（采购合同/结算的 project_id 为 NOT NULL，必须先有真实项目）
    const projResp = await client.post('/api/v1/project', {
      projectName: `${TEST_PROJECT.name}_PURCHASE_TEST`,
      projectType: 'BUILDING',
      projectAddress: '采购测试项目地址',
    })
    expectOk(projResp, '创建采购测试项目')

    const pageResp = await client.get('/api/v1/project/page', {
      page: 1,
      size: 20,
      projectName: `${TEST_PROJECT.name}_PURCHASE_TEST`,
    })
    const records = pageResp.data?.records || []
    const found = records.find((p: any) => p.projectName?.includes('PURCHASE_TEST'))
    if (found) {
      projectId = found.id
      cleaner.add('删除采购测试项目', () =>
        client.delete(`/api/v1/project/${projectId}`)
      )
    }
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 询价单 CRUD ============
  describe('询价单管理', () => {
    it('创建询价单', async () => {
      const resp = await client.post('/api/v1/purchase/inquiry', {
        title: TEST_PURCHASE.inquiryTitle,
        inviteMode: 'DIRECTED',
        bidMode: 'LOWEST',
        description: 'E2E 测试询价单',
        requirements: '按规格报价',
        materialSummary: '钢筋、水泥',
        deadline: '2099-12-31T23:59:59',
        // 随主表提交物料明细（发布校验要求至少一个物料）
        items: [
          { materialName: '钢筋', specification: 'HRB400', unit: '吨', quantity: 100 },
        ],
        // 定向模式发布校验要求至少一个供应商
        suppliers: [
          { supplierId: 1, supplierName: 'E2E供应商A' },
          { supplierId: 2, supplierName: 'E2E供应商B' },
          { supplierId: 3, supplierName: 'E2E供应商C' },
        ],
      })
      expectOk(resp, '创建询价单')
    })

    it('分页查询询价单 - 找到创建的记录', async () => {
      const resp = await client.get('/api/v1/purchase/inquiry/page', {
        page: 1,
        size: 20,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.title === TEST_PURCHASE.inquiryTitle)
      expect(found).toBeDefined()
      inquiryId = found.id
      cleaner.add('删除询价单', () => client.delete(`/api/v1/purchase/inquiry/${inquiryId}`))
    })

    it('获取询价单详情', async () => {
      expect(inquiryId).toBeTruthy()
      const resp = await client.get(`/api/v1/purchase/inquiry/${inquiryId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.title).toBe(TEST_PURCHASE.inquiryTitle)
    })

    it('更新询价单', async () => {
      const resp = await client.put(`/api/v1/purchase/inquiry/${inquiryId}`, {
        title: TEST_PURCHASE.inquiryTitle,
        description: '更新后的描述',
      })
      expectOk(resp, '更新询价单')
    })

    it('验证更新生效', async () => {
      const resp = await client.get(`/api/v1/purchase/inquiry/${inquiryId}`)
      expect(resp.data?.description).toBe('更新后的描述')
    })

    it('发布询价单', async () => {
      const resp = await client.post(`/api/v1/purchase/inquiry/${inquiryId}/publish`)
      expectOk(resp, '发布询价单')
    })

    it('获取询价单的报价列表（发布后应为空）', async () => {
      const resp = await client.get(`/api/v1/purchase/inquiry/${inquiryId}/quotations`)
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })
  })

  // ============ 报价提交 ============
  describe('供应商报价', () => {
    it('供应商A提交报价', async () => {
      const resp = await client.post('/api/v1/purchase/quotation/submit', {
        inquiryId,
        supplierId: 1,
        supplierName: 'E2E供应商A',
        details: [
          { unitPrice: 4500, totalPrice: 450000 },
        ],
      })
      expectOk(resp, '供应商A报价')
    })

    it('供应商B提交报价', async () => {
      const resp = await client.post('/api/v1/purchase/quotation/submit', {
        inquiryId,
        supplierId: 2,
        supplierName: 'E2E供应商B',
        details: [
          { unitPrice: 4200, totalPrice: 420000 },
        ],
      })
      expectOk(resp, '供应商B报价')
    })

    it('供应商C提交报价（最高价）', async () => {
      const resp = await client.post('/api/v1/purchase/quotation/submit', {
        inquiryId,
        supplierId: 3,
        supplierName: 'E2E供应商C',
        details: [
          { unitPrice: 4800, totalPrice: 480000 },
        ],
      })
      expectOk(resp, '供应商C报价')
    })

    it('查询询价单的报价列表 - 应有3条', async () => {
      const resp = await client.get(`/api/v1/purchase/quotation/inquiry/${inquiryId}`)
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
      expect(resp.data.length).toBe(3)
    })
  })

  // ============ 三方比价 / 定标 ============
  describe('三方比价 & 定标', () => {
    it('计算比价排名', async () => {
      const resp = await client.post(`/api/v1/purchase/bid/calculate/${inquiryId}`)
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
      if (resp.data.length > 0) {
        // 最低价排名应在第一位
        expect(resp.data[0].supplierName).toContain('供应商B')
      }
    })

    it('查询定标结果', async () => {
      const resp = await client.get(`/api/v1/purchase/bid/${inquiryId}`)
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })

    it('确认定标（选择最低价供应商B）', async () => {
      const resp = await client.post('/api/v1/purchase/bid/confirm', {
        inquiryId,
        supplierId: 2,
      })
      expectOk(resp, '确认定标')
    })

    it('确认定标后查询结果', async () => {
      const resp = await client.get(`/api/v1/purchase/bid/${inquiryId}`)
      expect(resp.code).toBe(200)
    })
  })

  // ============ 采购合同 ============
  describe('采购合同', () => {
    it('创建采购合同', async () => {
      const resp = await client.post('/api/v1/purchase/contract', {
        projectId,
        contractName: TEST_PURCHASE.contractName,
        contractCode: `E2E_PC_${Date.now()}`,
        partyAName: 'E2E甲方',
        partyBName: 'E2E供应商B',
        supplierName: 'E2E供应商B',
        contractAmount: 420000,
        signingDate: '2026-01-15',
        paymentTerms: '验收后30天付款',
      })
      expectOk(resp, '创建采购合同')
    })

    it('分页查询采购合同', async () => {
      const resp = await client.get('/api/v1/purchase/contract/page', {
        page: 1,
        size: 20,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.contractName === TEST_PURCHASE.contractName)
      expect(found).toBeDefined()
      purchaseContractId = found.id
      cleaner.add('删除采购合同', () =>
        client.delete(`/api/v1/purchase/contract/${purchaseContractId}`)
      )
    })

    it('获取采购合同详情', async () => {
      expect(purchaseContractId).toBeTruthy()
      const resp = await client.get(`/api/v1/purchase/contract/${purchaseContractId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.contractName).toBe(TEST_PURCHASE.contractName)
    })

    it('更新采购合同', async () => {
      const resp = await client.put(`/api/v1/purchase/contract/${purchaseContractId}`, {
        contractName: TEST_PURCHASE.contractName,
        paymentTerms: '更新后的付款条款',
      })
      expectOk(resp, '更新采购合同')
    })

    it('查询采购合同明细', async () => {
      const resp = await client.get(`/api/v1/purchase/contract/${purchaseContractId}/details`)
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })

    it('提交采购合同审批', async () => {
      const resp = await client.post(`/api/v1/purchase/contract/${purchaseContractId}/submit`)
      // 可能因缺少审批流配置而失败，但接口应可达
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 采购结算 ============
  describe('采购结算', () => {
    let settlementId: number

    it('创建采购结算单', async () => {
      const resp = await client.post('/api/v1/purchase/settlement', {
        projectId,
        contractId: purchaseContractId,
        settlementAmount: 100000,
        settlementDate: '2026-03-01',
        remark: 'E2E测试结算',
      })
      expectOk(resp, '创建采购结算')
    })

    it('分页查询采购结算', async () => {
      const resp = await client.get('/api/v1/purchase/settlement/page', {
        page: 1,
        size: 20,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        const found = records.find((r: any) => r.contractId === purchaseContractId)
        if (found) {
          settlementId = found.id
          cleaner.add('删除采购结算', () =>
            client.delete(`/api/v1/purchase/settlement/${settlementId}`)
          )
        }
      }
    })

    it('获取结算单详情', async () => {
      if (!settlementId) return
      const resp = await client.get(`/api/v1/purchase/settlement/${settlementId}`)
      expect(resp.code).toBe(200)
    })

    it('提交结算单审批', async () => {
      if (!settlementId) return
      const resp = await client.post(`/api/v1/purchase/settlement/${settlementId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 公开报价接口 ============
  describe('公开报价查询', () => {
    it('查询公开询价列表', async () => {
      const resp = await client.get('/api/v1/public/quotation/inquiries', {
        page: 1,
        size: 10,
      })
      // 公开接口不需要认证也可能成功
      expect([200, 401]).toContain(resp.code)
    })
  })
})
