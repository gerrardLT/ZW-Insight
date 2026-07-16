/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_PROJECT } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 16 - 档案管理测试
 * 对应功能表: 项目档案、投标档案、合同档案、供应商档案、人事档案等
 */
describe('16 - 档案管理', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let projectId: number

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()
    // 创建测试项目用于关联
    const prjResp = await client.post('/api/v1/project', {
      projectName: `${TEST_PROJECT.name}_ARCHIVE`,
      projectType: 'BUILDING',
      projectAddress: '档案测试地址',
      needTender: 0,
    })
    if (prjResp.code === 200) {
      const pageResp = await client.get('/api/v1/project/page', {
        page: 1, size: 5, projectName: `${TEST_PROJECT.name}_ARCHIVE`,
      })
      if (pageResp.data?.records?.length > 0) {
        projectId = pageResp.data.records[0].id
        cleaner.add('删除档案关联项目', () => client.delete(`/api/v1/project/${projectId}`))
      }
    }
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 项目档案 ============
  describe('项目档案', () => {
    it('查询项目档案', async () => {
      const resp = await client.get(`/api/v1/archive/project/${projectId}`)
      expect(resp.code).toBe(200)
      expect(resp.data).toBeDefined()
    })
  })

  // ============ 预算档案 ============
  describe('预算档案', () => {
    it('查询预算档案', async () => {
      const resp = await client.get(`/api/v1/archive/budget/${projectId}`)
      expect(resp.code).toBe(200)
    })
  })

  // ============ 合同档案 ============
  describe('合同档案', () => {
    it('查询合同档案（使用 ID=1）', async () => {
      const resp = await client.get('/api/v1/archive/contract/1')
      // 合同可能不存在，返回 data 为 null 也算正常
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ 投标档案 ============
  describe('投标档案', () => {
    it('查询投标档案（使用 ID=1）', async () => {
      const resp = await client.get('/api/v1/archive/tender/1')
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ 供应商档案 ============
  describe('供应商档案', () => {
    it('查询供应商档案（使用 ID=1）', async () => {
      const resp = await client.get('/api/v1/archive/supplier/1')
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ 材料合同档案 ============
  describe('材料合同档案', () => {
    it('查询材料合同档案', async () => {
      const resp = await client.get('/api/v1/archive/material-contract/1')
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ 分包档案 ============
  describe('分包档案', () => {
    it('查询分包档案', async () => {
      const resp = await client.get('/api/v1/archive/subcontract/1')
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ 机械合同档案 ============
  describe('机械合同档案', () => {
    it('查询机械合同档案', async () => {
      const resp = await client.get('/api/v1/archive/machine-contract/1')
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ 人事档案 ============
  describe('人事档案', () => {
    it('查询人事档案（userId=1）', async () => {
      const resp = await client.get('/api/v1/archive/personnel/1')
      expect(resp.code).toBe(200)
    })
  })

  // ============ 车辆档案 ============
  describe('车辆档案', () => {
    it('查询车辆档案（vehicleId=1）', async () => {
      const resp = await client.get('/api/v1/archive/vehicle/1')
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ 其它合同档案列表 ============
  describe('其它合同档案', () => {
    it('查询其它收入合同档案', async () => {
      const resp = await client.get('/api/v1/archive/other-income-contract', {
        page: 1, size: 10,
      })
      expect(resp.code).toBe(200)
    })

    it('查询其它支出合同档案', async () => {
      const resp = await client.get('/api/v1/archive/other-expense-contract', {
        page: 1, size: 10,
      })
      expect(resp.code).toBe(200)
    })
  })

  // ============ 办公用品档案 ============
  describe('办公用品档案', () => {
    it('查询办公用品档案列表', async () => {
      const resp = await client.get('/api/v1/archive/office-supply', {
        page: 1, size: 10,
      })
      expect(resp.code).toBe(200)
    })
  })
})
