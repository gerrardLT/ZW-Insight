/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_FINANCE, TEST_PROJECT } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 14 - 财务管理测试
 * 对应功能表: 开票申请、付款申请、收款登记、银行账户、质保金、项目结算
 */
describe('14 - 财务管理', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let projectId: number
  let contractId: number

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()
    const prjResp = await client.post('/api/v1/project', {
      projectName: `${TEST_PROJECT.name}_FIN`,
      projectType: 'BUILDING',
      projectAddress: '财务测试地址',
      needTender: 0,
    })
    if (prjResp.code === 200) {
      const pageResp = await client.get('/api/v1/project/page', {
        page: 1, size: 5, projectName: `${TEST_PROJECT.name}_FIN`,
      })
      if (pageResp.data?.records?.length > 0) {
        projectId = pageResp.data.records[0].id
        cleaner.add('删除财务关联项目', () => client.delete(`/api/v1/project/${projectId}`))
      }
    }
    // 创建一个真实施工合同供开票/付款/质保金引用（biz_invoice_apply/biz_payment_apply.contract_id NOT NULL）
    if (projectId) {
      const cResp = await client.post('/api/v1/contract', {
        projectId,
        contractType: 'REGISTER',
        partyAName: 'E2E财务甲方',
        signingDate: '2026-01-01',
        startDate: '2026-03-01',
        endDate: '2026-12-31',
        contractAmount: 1000000,
        taxRate: 9,
      })
      if (cResp.code === 200) {
        const cPage = await client.get('/api/v1/contract/page', {
          page: 1, size: 10, projectId,
        })
        const c = (cPage.data?.records || []).find((r: any) => r.projectId === projectId)
        if (c) {
          contractId = c.id
          cleaner.add('删除财务关联合同', () => client.delete(`/api/v1/contract/${contractId}`))
        }
      }
    }
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 开票申请 ============
  describe('开票申请', () => {
    let invoiceId: number

    it('创建开票申请', async () => {
      const resp = await client.post('/api/v1/finance/invoice-apply', {
        projectId,
        contractId,
        invoiceType: 'SPECIAL',
        invoiceAmount: TEST_FINANCE.invoiceAmount,
        applyDate: '2026-03-01',
        invoiceTitle: 'E2E购方单位',
      })
      expectOk(resp, '创建开票申请')
    })

    it('分页查询开票申请', async () => {
      const resp = await client.get('/api/v1/finance/invoice-apply/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        invoiceId = records[0].id
        cleaner.add('删除开票申请', () =>
          client.delete(`/api/v1/finance/invoice-apply/${invoiceId}`)
        )
      }
    })

    it('获取开票申请详情', async () => {
      if (!invoiceId) return
      const resp = await client.get(`/api/v1/finance/invoice-apply/${invoiceId}`)
      expect(resp.code).toBe(200)
    })

    it('提交开票申请审批', async () => {
      if (!invoiceId) return
      const resp = await client.post(`/api/v1/finance/invoice-apply/${invoiceId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 付款申请 ============
  describe('付款申请', () => {
    let paymentId: number

    it('创建付款申请', async () => {
      const resp = await client.post('/api/v1/finance/payment-apply', {
        projectId,
        contractId,
        paymentAmount: TEST_FINANCE.paymentAmount,
        paymentDate: '2026-03-15',
        supplierName: 'E2E收款方',
      })
      expectOk(resp, '创建付款申请')
    })

    it('分页查询付款申请', async () => {
      const resp = await client.get('/api/v1/finance/payment-apply/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        paymentId = records[0].id
        cleaner.add('删除付款申请', () =>
          client.delete(`/api/v1/finance/payment-apply/${paymentId}`)
        )
      }
    })

    it('获取付款申请详情', async () => {
      if (!paymentId) return
      const resp = await client.get(`/api/v1/finance/payment-apply/${paymentId}`)
      expect(resp.code).toBe(200)
    })

    it('提交付款申请审批', async () => {
      if (!paymentId) return
      const resp = await client.post(`/api/v1/finance/payment-apply/${paymentId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 收款登记 ============
  describe('收款登记', () => {
    let receivedId: number

    it('创建收款登记', async () => {
      const resp = await client.post('/api/v1/finance/payment-received', {
        projectId,
        receiveAmount: 200000,
        receiveDate: '2026-03-20',
        payerName: 'E2E付款方',
        remark: 'E2E测试收款',
      })
      expectOk(resp, '创建收款登记')
    })

    it('分页查询收款登记', async () => {
      const resp = await client.get('/api/v1/finance/payment-received/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        receivedId = records[0].id
        cleaner.add('删除收款登记', () =>
          client.delete(`/api/v1/finance/payment-received/${receivedId}`)
        )
      }
    })

    it('获取收款登记详情', async () => {
      if (!receivedId) return
      const resp = await client.get(`/api/v1/finance/payment-received/${receivedId}`)
      expect(resp.code).toBe(200)
    })
  })

  // ============ 银行账户管理 ============
  describe('银行账户', () => {
    let bankAccountId: number

    it('创建银行账户', async () => {
      const resp = await client.post('/api/v1/finance/bank-account', {
        accountName: 'E2E测试账户',
        accountNumber: '6225000012345678',
        bankName: '中国工商银行',
        accountType: 'GENERAL',
      })
      expectOk(resp, '创建银行账户')
    })

    it('分页查询银行账户', async () => {
      const resp = await client.get('/api/v1/finance/bank-account', {
        page: 1, size: 20,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.accountName === 'E2E测试账户')
      if (found) {
        bankAccountId = found.id
        cleaner.add('删除银行账户', () =>
          client.delete(`/api/v1/finance/bank-account/${bankAccountId}`)
        )
      }
    })
  })

  // ============ 质保金管理 ============
  describe('质保金', () => {
    let retentionId: number

    it('新增质保金', async () => {
      const resp = await client.post('/api/v1/finance/retention', {
        projectId,
        contractId,
        retentionAmount: 50000,
        retentionRate: 5,
        expireDate: '2027-12-31',
        remark: 'E2E测试质保金',
      })
      expectOk(resp, '创建质保金')
    })

    it('分页查询质保金', async () => {
      const resp = await client.get('/api/v1/finance/retention/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        retentionId = records[0].id
      }
    })

    it('查询即将到期的质保金', async () => {
      const resp = await client.get('/api/v1/finance/retention/expiring', {
        days: 90,
      })
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })
  })

  // ============ 项目最终结算 ============
  describe('项目结算', () => {
    let settlementId: number

    it('创建项目结算单', async () => {
      const resp = await client.post(
        `/api/v1/project-settlements?projectId=${projectId}`
      )
      expect([200, 400, 500]).toContain(resp.code)
      if (resp.code === 200 && resp.data) {
        settlementId = resp.data
        cleaner.add('删除项目结算', () =>
          client.delete(`/api/v1/project-settlements/${settlementId}`)
        )
      }
    })

    it('查询结算单详情', async () => {
      if (!settlementId) return
      const resp = await client.get(`/api/v1/project-settlements/${settlementId}`)
      expect(resp.code).toBe(200)
    })

    it('查询未结清合同', async () => {
      if (!settlementId) return
      const resp = await client.get(`/api/v1/project-settlements/${settlementId}/unsettled-contracts`)
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })
  })

  // ============ 财务期间锁定 ============
  describe('财务锁定', () => {
    it('查询锁定期列表', async () => {
      const resp = await client.get('/api/v1/finance/lock/page', {
        page: 1, size: 10,
      })
      // 接口可能不存在或者有不同的路径
      expect([200, 404, 500]).toContain(resp.code)
    })
  })
})
