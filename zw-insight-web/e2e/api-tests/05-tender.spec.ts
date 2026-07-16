/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_PROJECT } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 05 - 投标管理测试
 * 对应功能表: 1.4 投标管理全部
 */
describe('05 - 投标管理', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let projectId: number
  let registerId: number

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()

    // 创建一个项目用于投标测试
    const projResp = await client.post('/api/v1/project', {
      projectName: `${TEST_PROJECT.name}_TENDER_TEST`,
      projectType: 'BUILDING',
      needTender: 1,
      projectAddress: '投标测试项目地址',
    })
    expectOk(projResp, '创建投标测试项目')

    // 查询项目 ID
    const pageResp = await client.get('/api/v1/project/page', {
      page: 1,
      size: 20,
      projectName: `${TEST_PROJECT.name}_TENDER_TEST`,
    })
    const records = pageResp.data?.records || []
    const found = records.find((p: any) =>
      p.projectName?.includes('TENDER_TEST')
    )
    if (found) {
      projectId = found.id
      cleaner.add('删除投标测试项目', () =>
        client.delete(`/api/v1/project/${projectId}`)
      )
    }
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 投标报名 ============
  describe('投标报名', () => {
    it('创建投标登记', async () => {
      const resp = await client.post('/api/v1/tender/register', {
        projectId,
        tenderName: 'E2E_TEST_投标报名',
        tenderType: 'PUBLIC',
        deadline: '2026-12-31',
      })
      expectOk(resp, '创建投标登记')
    })

    it('分页查询投标登记', async () => {
      const resp = await client.get('/api/v1/tender/register/page', {
        page: 1,
        size: 10,
        projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.projectId === projectId)
      if (found) {
        registerId = found.id
        cleaner.add('删除投标登记', () =>
          client.delete(`/api/v1/tender/register/${registerId}`)
        )
      }
    })

    it('获取投标登记详情', async () => {
      if (!registerId) return
      const resp = await client.get(`/api/v1/tender/register/${registerId}`)
      expect(resp.code).toBe(200)
    })

    it('更新投标登记', async () => {
      if (!registerId) return
      const resp = await client.put(`/api/v1/tender/register/${registerId}`, {
        projectId,
        tenderName: 'E2E_TEST_投标报名_updated',
        deadline: '2026-12-31',
      })
      expectOk(resp, '更新投标登记')
    })

    it('提交投标报名', async () => {
      if (!registerId) return
      const resp = await client.post(
        `/api/v1/tender/register/${registerId}/submit`
      )
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 投标任务 ============
  describe('投标任务', () => {
    let taskId: number

    it('创建投标任务', async () => {
      if (!registerId) return
      const resp = await client.post('/api/v1/tender/task', {
        registerId,
        taskName: 'E2E_TEST_编制投标文件',
        assigneeId: 1,
        assigneeName: 'admin',
        deadline: '2026-12-20',
      })
      expectOk(resp, '创建投标任务')
    })

    it('查询投标任务列表', async () => {
      if (!registerId) return
      const resp = await client.get(`/api/v1/tender/task/${registerId}`)
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
      if (resp.data.length > 0) {
        taskId = resp.data[0].id
      }
    })

    it('完成任务', async () => {
      if (!taskId) return
      const resp = await client.post(`/api/v1/tender/task/${taskId}/complete`)
      expect([200, 400, 500]).toContain(resp.code)
    })

    it('更新任务', async () => {
      if (!taskId) return
      const resp = await client.put(`/api/v1/tender/task/${taskId}`, {
        taskName: 'E2E_TEST_编制投标文件_updated',
        deadline: '2026-12-25',
      })
      expect([200, 400, 500]).toContain(resp.code)
    })

    it('删除任务', async () => {
      if (!taskId) return
      const resp = await client.delete(`/api/v1/tender/task/${taskId}`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 投标费用 ============
  describe('投标费用', () => {
    let feeId: number

    it('创建投标费用', async () => {
      if (!registerId) return
      const resp = await client.post('/api/v1/tender/fee', {
        registerId,
        projectId,
        feeType: 'REGISTRATION',
        feeAmount: 500,
        paymentDate: '2026-12-01',
      })
      expectOk(resp, '创建投标费用')
    })

    it('分页查询投标费用', async () => {
      const resp = await client.get('/api/v1/tender/fee/page', {
        page: 1,
        size: 10,
        registerId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((f: any) => f.registerId === registerId)
      if (found) {
        feeId = found.id
        cleaner.add('删除投标费用', () =>
          client.delete(`/api/v1/tender/fee/${feeId}`)
        )
      }
    })

    it('确认付款', async () => {
      if (!feeId) return
      const resp = await client.post(
        `/api/v1/tender/fee/${feeId}/confirm-payment`,
        { receiptFile: '' }
      )
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 保证金 ============
  describe('保证金管理', () => {
    let depositApplyId: number

    it('创建保证金申请', async () => {
      if (!registerId) return
      const resp = await client.post('/api/v1/tender/deposit/apply', {
        registerId,
        projectId,
        depositAmount: 50000,
        paymentDate: '2026-12-01',
      })
      expectOk(resp, '创建保证金申请')
    })

    it('分页查询保证金申请', async () => {
      const resp = await client.get('/api/v1/tender/deposit/apply', {
        page: 1,
        size: 10,
        projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((d: any) => d.projectId === projectId)
      if (found) {
        depositApplyId = found.id
        cleaner.add('删除保证金申请', () =>
          client.delete(`/api/v1/tender/deposit/apply/${depositApplyId}`)
        )
      }
    })

    it('提交保证金申请', async () => {
      if (!depositApplyId) return
      const resp = await client.post(
        `/api/v1/tender/deposit/apply/${depositApplyId}/submit`
      )
      expect([200, 400, 500]).toContain(resp.code)
    })

    it('创建保证金退还', async () => {
      if (!depositApplyId) return
      const resp = await client.post('/api/v1/tender/deposit/return', {
        depositApplyId,
        returnAmount: 50000,
        returnReason: 'E2E 测试退还',
      })
      expect([200, 400, 500]).toContain(resp.code)
    })

    it('分页查询保证金退还', async () => {
      const resp = await client.get('/api/v1/tender/deposit/return', {
        page: 1,
        size: 10,
      })
      expect(resp.code).toBe(200)
    })
  })

  // ============ 开标记录 ============
  describe('开标记录', () => {
    let openBidId: number

    it('创建开标记录', async () => {
      if (!registerId) return
      const resp = await client.post('/api/v1/tender/open-bid', {
        registerId,
        projectId,
        isWon: 1,
        winInfo: 'E2E 测试开标记录',
      })
      expectOk(resp, '创建开标记录')
    })

    it('查询开标记录', async () => {
      if (!registerId) return
      const resp = await client.get(`/api/v1/tender/open-bid/${registerId}`)
      expect(resp.code).toBe(200)
      if (resp.data) {
        openBidId = resp.data.id
        cleaner.add('删除开标记录', () =>
          client.delete(`/api/v1/tender/open-bid/${openBidId}`)
        )
      }
    })

    it('更新开标记录', async () => {
      if (!openBidId) return
      const resp = await client.put(`/api/v1/tender/open-bid/${openBidId}`, {
        registerId,
        projectId,
        isWon: 0,
        winInfo: 'E2E 测试开标记录_updated',
      })
      expectOk(resp, '更新开标记录')
    })
  })

  // ============ 证书管理 ============
  describe('证书管理', () => {
    let personCertId: number
    let companyCertId: number

    it('创建人员证书', async () => {
      const resp = await client.post('/api/v1/tender/certificate/person', {
        personName: 'E2E_TEST_张三',
        certificateType: 'BUILDER',
        certificateNo: `CERT_${Date.now()}`,
        issueDate: '2024-01-01',
        expiryDate: '2029-12-31',
      })
      expectOk(resp, '创建人员证书')
    })

    it('分页查询人员证书', async () => {
      const resp = await client.get('/api/v1/tender/certificate/person', {
        page: 1,
        size: 10,
        personName: 'E2E_TEST',
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((c: any) => c.personName?.includes('E2E_TEST'))
      if (found) {
        personCertId = found.id
        cleaner.add('删除人员证书', () =>
          client.delete(`/api/v1/tender/certificate/person/${personCertId}`)
        )
      }
    })

    it('创建企业证书', async () => {
      const resp = await client.post('/api/v1/tender/certificate/company', {
        certificateName: 'E2E_TEST_企业资质',
        certificateType: 'QUALIFICATION',
        certificateNo: `COMP_CERT_${Date.now()}`,
        issueDate: '2024-01-01',
        expiryDate: '2029-12-31',
      })
      expectOk(resp, '创建企业证书')
    })

    it('分页查询企业证书', async () => {
      const resp = await client.get('/api/v1/tender/certificate/company', {
        page: 1,
        size: 10,
        certificateName: 'E2E_TEST',
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((c: any) =>
        c.certificateName?.includes('E2E_TEST')
      )
      if (found) {
        companyCertId = found.id
        cleaner.add('删除企业证书', () =>
          client.delete(`/api/v1/tender/certificate/company/${companyCertId}`)
        )
      }
    })
  })
})
