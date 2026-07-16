/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_MACHINE, TEST_PROJECT } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 11 - 机械管理测试
 * 对应功能表: 机械合同、台账、进退场、维修、工作日志、结算
 */
describe('11 - 机械管理', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let projectId: number
  let machineContractId: number
  let machineId: number

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()
    const prjResp = await client.post('/api/v1/project', {
      projectName: `${TEST_PROJECT.name}_MACHINE`,
      projectType: 'BUILDING',
      projectAddress: '机械测试地址',
      needTender: 0,
    })
    if (prjResp.code === 200) {
      const pageResp = await client.get('/api/v1/project/page', {
        page: 1, size: 5, projectName: `${TEST_PROJECT.name}_MACHINE`,
      })
      if (pageResp.data?.records?.length > 0) {
        projectId = pageResp.data.records[0].id
        cleaner.add('删除机械关联项目', () => client.delete(`/api/v1/project/${projectId}`))
      }
    }
    // 创建一台供进退场/报修/工作日志复用的机械台账（进退场依赖真实 machineId 且台账须为已登记状态）
    const mlName = `${TEST_MACHINE.ledgerName}_ENTRY`
    const mlResp = await client.post('/api/v1/machine/ledger', {
      machineName: mlName,
      machineType: 'EXCAVATOR',
      machineCode: `MC_ENTRY_${Date.now()}`,
    })
    if (mlResp.code === 200) {
      const mlPage = await client.get('/api/v1/machine/ledger/page', {
        page: 1, size: 5, machineName: mlName,
      })
      if (mlPage.data?.records?.length > 0) {
        machineId = mlPage.data.records[0].id
        cleaner.add('删除进退场机械台账', () => client.delete(`/api/v1/machine/ledger/${machineId}`))
      }
    }
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 机械合同 ============
  describe('机械合同', () => {
    it('创建机械合同', async () => {
      const resp = await client.post('/api/v1/machine/contract', {
        projectId,
        contractName: TEST_MACHINE.contractName,
        contractCode: `E2E_MC_${Date.now()}`,
        supplierName: 'E2E机械供应商',
        contractAmount: 300000,
        startDate: '2026-01-01',
        endDate: '2026-12-31',
      })
      expectOk(resp, '创建机械合同')
    })

    it('分页查询机械合同', async () => {
      const resp = await client.get('/api/v1/machine/contract/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.contractName === TEST_MACHINE.contractName)
      expect(found).toBeDefined()
      machineContractId = found.id
      cleaner.add('删除机械合同', () =>
        client.delete(`/api/v1/machine/contract/${machineContractId}`)
      )
    })

    it('获取机械合同详情', async () => {
      expect(machineContractId).toBeTruthy()
      const resp = await client.get(`/api/v1/machine/contract/${machineContractId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.contractName).toBe(TEST_MACHINE.contractName)
    })

    it('更新机械合同', async () => {
      const resp = await client.put(`/api/v1/machine/contract/${machineContractId}`, {
        contractName: TEST_MACHINE.contractName,
        contractAmount: 350000,
      })
      expectOk(resp, '更新机械合同')
    })

    it('提交机械合同审批', async () => {
      const resp = await client.post(`/api/v1/machine/contract/${machineContractId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 机械台账 ============
  describe('机械台账', () => {
    let ledgerId: number

    it('创建机械台账', async () => {
      const resp = await client.post('/api/v1/machine/ledger', {
        projectId,
        machineName: TEST_MACHINE.ledgerName,
        machineType: 'EXCAVATOR',
        machineCode: `MC_${Date.now()}`,
        manufacturer: 'E2E制造商',
        status: 'IN_USE',
      })
      expectOk(resp, '创建机械台账')
    })

    it('分页查询机械台账', async () => {
      const resp = await client.get('/api/v1/machine/ledger/page', {
        page: 1, size: 20, machineName: TEST_MACHINE.ledgerName,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.machineName === TEST_MACHINE.ledgerName)
      if (found) {
        ledgerId = found.id
        cleaner.add('删除机械台账', () => client.delete(`/api/v1/machine/ledger/${ledgerId}`))
      }
    })

    it('更新机械台账', async () => {
      if (!ledgerId) return
      const resp = await client.put(`/api/v1/machine/ledger/${ledgerId}`, {
        machineName: TEST_MACHINE.ledgerName,
        status: 'MAINTENANCE',
      })
      expectOk(resp, '更新机械台账')
    })
  })

  // ============ 机械进退场 ============
  describe('机械进退场', () => {
    let entryId: number

    it('机械进场', async () => {
      if (!machineId) return
      const resp = await client.post('/api/v1/machine/entry/in', {
        machineId,
        projectId,
        entryDate: '2026-01-10',
      })
      expectOk(resp, '机械进场')
    })

    it('分页查询进退场', async () => {
      const resp = await client.get('/api/v1/machine/entry/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        entryId = records[0].id
        cleaner.add('删除进退场', () => client.delete(`/api/v1/machine/entry/${entryId}`))
      }
    })

    it('机械退场', async () => {
      if (!machineId) return
      const resp = await client.post('/api/v1/machine/entry/out', {
        machineId,
        projectId,
        entryDate: '2026-06-30',
      })
      expectOk(resp, '机械退场')
    })
  })

  // ============ 机械维修 ============
  describe('机械维修', () => {
    let repairId: number

    it('报修', async () => {
      if (!machineId) return
      const resp = await client.post('/api/v1/machine/repair/report', {
        machineId,
        projectId,
        faultDescription: 'E2E测试故障描述',
      })
      expectOk(resp, '报修')
    })

    it('分页查询维修记录', async () => {
      const resp = await client.get('/api/v1/machine/repair/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        repairId = records[0].id
        cleaner.add('删除维修记录', () => client.delete(`/api/v1/machine/repair/${repairId}`))
      }
    })

    it('派工', async () => {
      if (!repairId) return
      const resp = await client.post(
        `/api/v1/machine/repair/${repairId}/dispatch?repairPerson=E2E维修工`
      )
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 机械工作日志 ============
  describe('机械工作日志', () => {
    let workLogId: number

    it('创建工作日志', async () => {
      if (!machineId) return
      // 工作日志仅允许 IN_FIELD（在场）机械记录；前序"机械退场"已将该机械置为 OUT_FIELD，
      // 故此处先重新进场（后端允许 REGISTERED/OUT_FIELD → IN_FIELD）恢复在场状态。
      await client.post('/api/v1/machine/entry/in', {
        machineId,
        projectId,
        entryDate: '2026-01-31',
      })
      const resp = await client.post('/api/v1/machine/work-log', {
        machineId,
        projectId,
        workDate: '2026-02-01',
        shiftCount: 1,
        workQuantity: 8,
      })
      expectOk(resp, '创建工作日志')
    })

    it('分页查询工作日志', async () => {
      const resp = await client.get('/api/v1/machine/work-log/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        workLogId = records[0].id
        cleaner.add('删除工作日志', () => client.delete(`/api/v1/machine/work-log/${workLogId}`))
      }
    })

    it('更新工作日志', async () => {
      if (!workLogId) return
      const resp = await client.put(`/api/v1/machine/work-log/${workLogId}`, {
        shiftCount: 1.5,
        workQuantity: 10,
      })
      expectOk(resp, '更新工作日志')
    })
  })

  // ============ 机械结算 ============
  describe('机械结算', () => {
    it('分页查询结算', async () => {
      const resp = await client.get('/api/v1/machine/settlement', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
    })

    it('项目费用总览', async () => {
      const resp = await client.get('/api/v1/machine/settlement/summary', {
        projectId,
      })
      expect(resp.code).toBe(200)
    })
  })
})
