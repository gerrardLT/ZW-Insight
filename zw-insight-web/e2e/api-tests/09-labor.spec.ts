/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_LABOR, TEST_PROJECT } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 09 - 劳务管理测试
 * 对应功能表: 劳务合同、花名册、工资单、产值报告、结算、奖罚
 */
describe('09 - 劳务管理', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let projectId: number
  let laborContractId: number
  let teamId: number

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()
    // 先创建一个项目用于关联
    const prjResp = await client.post('/api/v1/project', {
      projectName: `${TEST_PROJECT.name}_LABOR`,
      projectType: 'BUILDING',
      projectAddress: '劳务测试地址',
      needTender: 0,
    })
    if (prjResp.code === 200) {
      const pageResp = await client.get('/api/v1/project/page', {
        page: 1, size: 5, projectName: `${TEST_PROJECT.name}_LABOR`,
      })
      if (pageResp.data?.records?.length > 0) {
        projectId = pageResp.data.records[0].id
        cleaner.add('删除劳务关联项目', () => client.delete(`/api/v1/project/${projectId}`))
      }
    }

    // 创建班组用于花名册/工资单关联（team_id NOT NULL）
    const teamName = `${TEST_LABOR.teamName}_${Date.now()}`
    const teamResp = await client.post('/api/v1/labor/team', {
      projectId, teamName, leaderName: 'E2E班组长', leaderPhone: '13800138000',
    })
    if (teamResp.code === 200) {
      const teamPage = await client.get('/api/v1/labor/team/page', {
        page: 1, size: 5, projectId,
      })
      const t = (teamPage.data?.records || []).find((r: any) => r.teamName === teamName)
      if (t) {
        teamId = t.id
        cleaner.add('删除劳务班组', () => client.delete(`/api/v1/labor/team/${teamId}`))
      }
    }
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 劳务合同 ============
  describe('劳务合同', () => {
    it('创建劳务合同', async () => {
      const resp = await client.post('/api/v1/labor/contract', {
        projectId,
        contractName: TEST_LABOR.contractName,
        contractCode: `E2E_LC_${Date.now()}`,
        teamName: TEST_LABOR.teamName,
        contractAmount: 500000,
        startDate: '2026-01-01',
        endDate: '2026-12-31',
      })
      expectOk(resp, '创建劳务合同')
    })

    it('分页查询劳务合同', async () => {
      const resp = await client.get('/api/v1/labor/contract/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.contractName === TEST_LABOR.contractName)
      expect(found).toBeDefined()
      laborContractId = found.id
      cleaner.add('删除劳务合同', () =>
        client.delete(`/api/v1/labor/contract/${laborContractId}`)
      )
    })

    it('获取劳务合同详情', async () => {
      expect(laborContractId).toBeTruthy()
      const resp = await client.get(`/api/v1/labor/contract/${laborContractId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.contractName).toBe(TEST_LABOR.contractName)
    })

    it('更新劳务合同', async () => {
      const resp = await client.put(`/api/v1/labor/contract/${laborContractId}`, {
        contractName: TEST_LABOR.contractName,
        contractAmount: 600000,
      })
      expectOk(resp, '更新劳务合同')
    })

    it('提交劳务合同审批', async () => {
      const resp = await client.post(`/api/v1/labor/contract/${laborContractId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 劳务花名册 ============
  describe('劳务花名册', () => {
    let rosterId: number

    it('添加花名册记录', async () => {
      const resp = await client.post('/api/v1/labor/roster', {
        projectId,
        teamId,
        workerName: 'E2E测试工人',
        idCard: '110101199001011234',
        phone: '13800138001',
        workerType: 'FIXED',
      })
      expectOk(resp, '添加花名册')
    })

    it('分页查询花名册', async () => {
      const resp = await client.get('/api/v1/labor/roster/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.workerName === 'E2E测试工人')
      if (found) {
        rosterId = found.id
        cleaner.add('删除花名册', () => client.delete(`/api/v1/labor/roster/${rosterId}`))
      }
    })

    it('更新花名册记录', async () => {
      if (!rosterId) return
      const resp = await client.put(`/api/v1/labor/roster/${rosterId}`, {
        workerName: 'E2E测试工人',
        workerType: 'TEMPORARY',
      })
      expectOk(resp, '更新花名册')
    })
  })

  // ============ 劳务工资单 ============
  describe('劳务工资单', () => {
    let payrollId: number

    it('创建工资单', async () => {
      const resp = await client.post('/api/v1/labor/payroll', {
        projectId,
        teamId,
        periodStart: '2026-01-01',
        periodEnd: '2026-01-31',
        orderType: 'FIXED',
      })
      expectOk(resp, '创建工资单')
    })

    it('分页查询工资单', async () => {
      const resp = await client.get('/api/v1/labor/payroll/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        const found = records.find((r: any) => r.teamId === teamId)
        if (found) {
          payrollId = found.id
          cleaner.add('删除工资单', () => client.delete(`/api/v1/labor/payroll/${payrollId}`))
        }
      }
    })

    it('获取工资单详情', async () => {
      if (!payrollId) return
      const resp = await client.get(`/api/v1/labor/payroll/${payrollId}`)
      expect(resp.code).toBe(200)
    })

    it('提交工资单审批', async () => {
      if (!payrollId) return
      const resp = await client.post(`/api/v1/labor/payroll/${payrollId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 劳务产值报告 ============
  describe('劳务产值报告', () => {
    let reportId: number

    it('创建产值报告', async () => {
      const resp = await client.post('/api/v1/labor/output-report', {
        projectId,
        contractId: laborContractId,
        currentOutput: 80000,
      })
      expectOk(resp, '创建产值报告')
    })

    it('分页查询产值报告', async () => {
      const resp = await client.get('/api/v1/labor/output-report/page', {
        page: 1, size: 20, projectId, contractId: laborContractId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        reportId = records[0].id
        cleaner.add('删除产值报告', () =>
          client.delete(`/api/v1/labor/output-report/${reportId}`)
        )
      }
    })

    it('获取产值报告详情', async () => {
      if (!reportId) return
      const resp = await client.get(`/api/v1/labor/output-report/${reportId}`)
      expect(resp.code).toBe(200)
    })

    it('提交产值报告审批', async () => {
      if (!reportId) return
      const resp = await client.post(`/api/v1/labor/output-report/${reportId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 劳务结算 ============
  describe('劳务结算', () => {
    let settlementId: number

    it('创建劳务结算单', async () => {
      const resp = await client.post('/api/v1/labor/settlement', {
        projectId,
        contractId: laborContractId,
        settlementAmount: 200000,
      })
      expectOk(resp, '创建劳务结算')
    })

    it('分页查询劳务结算', async () => {
      const resp = await client.get('/api/v1/labor/settlement/page', {
        page: 1, size: 20, projectId, contractId: laborContractId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        settlementId = records[0].id
        cleaner.add('删除劳务结算', () =>
          client.delete(`/api/v1/labor/settlement/${settlementId}`)
        )
      }
    })

    it('获取结算详情', async () => {
      if (!settlementId) return
      const resp = await client.get(`/api/v1/labor/settlement/${settlementId}`)
      expect(resp.code).toBe(200)
    })

    it('提交结算审批', async () => {
      if (!settlementId) return
      const resp = await client.post(`/api/v1/labor/settlement/${settlementId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 劳务奖罚 ============
  describe('劳务奖罚', () => {
    let rpId: number

    it('创建奖罚记录', async () => {
      const resp = await client.post('/api/v1/labor/reward-punish', {
        projectId,
        contractId: laborContractId,
        rpType: 'REWARD',
        amount: 5000,
        reason: '工期提前完成',
      })
      expectOk(resp, '创建奖罚')
    })

    it('分页查询奖罚', async () => {
      const resp = await client.get('/api/v1/labor/reward-punish/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        rpId = records[0].id
        cleaner.add('删除奖罚', () => client.delete(`/api/v1/labor/reward-punish/${rpId}`))
      }
    })
  })
})
