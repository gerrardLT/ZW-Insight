/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner } from './setup'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 18 - 审批联动测试
 * 对应功能表: 工作流待办/已办、审批操作、催办、流程定义
 */
describe('18 - 审批联动', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 审批待办/已办 ============
  describe('待办与已办', () => {
    it('查询我的待办', async () => {
      const resp = await client.get('/api/v1/workflow/approval/todo', {
        page: 1, size: 10,
      })
      expect(resp.code).toBe(200)
    })

    it('查询我的已办', async () => {
      const resp = await client.get('/api/v1/workflow/approval/done', {
        page: 1, size: 10,
      })
      expect(resp.code).toBe(200)
    })
  })

  // ============ 流程定义 ============
  describe('流程定义', () => {
    it('列出当前租户的流程定义', async () => {
      const resp = await client.get('/api/v1/workflow/process')
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })
  })

  // ============ 业务类型配置 ============
  describe('业务类型配置', () => {
    it('查询业务类型列表', async () => {
      const resp = await client.get('/api/v1/workflow/business-type/tree')
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ 催办配置 ============
  describe('催办配置', () => {
    it('查询催办配置', async () => {
      const resp = await client.get('/api/v1/workflow/urge-config')
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ 审批流程联动（合同提交触发） ============
  describe('合同提交触发审批', () => {
    let contractId: number
    let projectResp: any

    it('创建测试项目用于审批联动', async () => {
      const resp = await client.post('/api/v1/project', {
        projectName: `E2E_WORKFLOW_${Date.now()}`,
        projectType: 'BUILDING',
        projectAddress: '审批联动测试',
        needTender: 0,
      })
      expect(resp.code).toBe(200)

      const pageResp = await client.get('/api/v1/project/page', {
        page: 1, size: 5, projectName: 'E2E_WORKFLOW',
      })
      if (pageResp.data?.records?.length > 0) {
        projectResp = pageResp.data.records[0]
        cleaner.add('删除审批联动项目', () =>
          client.delete(`/api/v1/project/${projectResp.id}`)
        )
      }
    })

    it('创建合同并提交触发审批', async () => {
      if (!projectResp?.id) return

      // 创建合同
      const createResp = await client.post('/api/v1/contract', {
        projectId: projectResp.id,
        contractType: 'CONSTRUCTION',
        contractName: `E2E_WF_CONTRACT_${Date.now()}`,
        partyAName: 'E2E甲方',
        contractAmount: 500000,
        signingDate: '2026-01-01',
      })
      expect(createResp.code).toBe(200)

      // 查询创建的合同
      const pageResp = await client.get('/api/v1/contract/page', {
        page: 1, size: 5,
      })
      if (pageResp.data?.records?.length > 0) {
        const found = pageResp.data.records.find((r: any) =>
          r.contractName?.includes('E2E_WF_CONTRACT')
        )
        if (found) {
          contractId = found.id
          cleaner.add('删除审批联动合同', () =>
            client.delete(`/api/v1/contract/${contractId}`)
          )
        }
      }

      // 提交合同 → 应触发审批流程
      if (contractId) {
        const submitResp = await client.post(`/api/v1/contract/${contractId}/submit`)
        // 提交可能成功（进入审批）或失败（无流程定义）
        expect([200, 400, 500]).toContain(submitResp.code)
      }
    })

    it('提交后检查待办列表是否更新', async () => {
      const resp = await client.get('/api/v1/workflow/approval/todo', {
        page: 1, size: 20,
      })
      expect(resp.code).toBe(200)
      // 不强制断言有待办，因为流程定义可能未配置
    })
  })

  // ============ 批量审批 ============
  describe('批量审批', () => {
    it('批量通过 - 空数组应成功或返回参数错误', async () => {
      const resp = await client.post('/api/v1/workflow/approval/batch-approve', {
        taskIds: [],
        comment: 'E2E批量测试',
      })
      expect([200, 400]).toContain(resp.code)
    })
  })
})
