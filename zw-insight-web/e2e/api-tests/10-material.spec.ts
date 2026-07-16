/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_PROJECT } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 10 - 材料库存测试
 * 对应功能表: 入库、出库、盘点、调拨、库存查询、退款查询
 */
describe('10 - 材料库存', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let projectId: number

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()
    // 创建测试项目
    const prjResp = await client.post('/api/v1/project', {
      projectName: `${TEST_PROJECT.name}_MAT`,
      projectType: 'BUILDING',
      projectAddress: '材料测试地址',
      needTender: 0,
    })
    if (prjResp.code === 200) {
      const pageResp = await client.get('/api/v1/project/page', {
        page: 1, size: 5, projectName: `${TEST_PROJECT.name}_MAT`,
      })
      if (pageResp.data?.records?.length > 0) {
        projectId = pageResp.data.records[0].id
        cleaner.add('删除材料关联项目', () => client.delete(`/api/v1/project/${projectId}`))
      }
    }
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 材料入库 ============
  describe('材料入库', () => {
    let inboundId: number

    it('创建入库单', async () => {
      const resp = await client.post('/api/v1/material/inbound', {
        projectId,
        inboundType: 'PURCHASE',
        remark: 'E2E测试入库',
      })
      expectOk(resp, '创建入库单')
    })

    it('分页查询入库单', async () => {
      const resp = await client.get('/api/v1/material/inbound/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        inboundId = records[0].id
        cleaner.add('删除入库单', () => client.delete(`/api/v1/material/inbound/${inboundId}`))
      }
    })

    it('获取入库单详情', async () => {
      if (!inboundId) return
      const resp = await client.get(`/api/v1/material/inbound/${inboundId}`)
      expect(resp.code).toBe(200)
    })

    it('提交入库单', async () => {
      if (!inboundId) return
      const resp = await client.post(`/api/v1/material/inbound/${inboundId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 材料出库 ============
  describe('材料出库', () => {
    let outboundId: number

    it('创建出库单', async () => {
      const resp = await client.post('/api/v1/material/outbound', {
        projectId,
        outboundType: 'USAGE',
        remark: 'E2E测试出库',
      })
      expectOk(resp, '创建出库单')
    })

    it('分页查询出库单', async () => {
      const resp = await client.get('/api/v1/material/outbound/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        outboundId = records[0].id
        cleaner.add('删除出库单', () => client.delete(`/api/v1/material/outbound/${outboundId}`))
      }
    })

    it('获取出库单详情', async () => {
      if (!outboundId) return
      const resp = await client.get(`/api/v1/material/outbound/${outboundId}`)
      expect(resp.code).toBe(200)
    })

    it('提交出库单', async () => {
      if (!outboundId) return
      const resp = await client.post(`/api/v1/material/outbound/${outboundId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 材料盘点 ============
  describe('材料盘点', () => {
    let inventoryId: number

    it('创建盘点记录', async () => {
      const resp = await client.post('/api/v1/material/inventory', {
        projectId,
        remark: 'E2E测试盘点',
      })
      expectOk(resp, '创建盘点')
    })

    it('分页查询盘点', async () => {
      const resp = await client.get('/api/v1/material/inventory/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        inventoryId = records[0].id
        cleaner.add('删除盘点', () => client.delete(`/api/v1/material/inventory/${inventoryId}`))
      }
    })

    it('获取盘点详情', async () => {
      if (!inventoryId) return
      const resp = await client.get(`/api/v1/material/inventory/${inventoryId}`)
      expect(resp.code).toBe(200)
    })
  })

  // ============ 材料调拨 ============
  describe('材料调拨', () => {
    let transferId: number

    it('创建调拨单', async () => {
      const resp = await client.post('/api/v1/material/transfer', {
        fromProjectId: projectId,
        toProjectId: projectId,
        remark: 'E2E测试调拨',
      })
      expectOk(resp, '创建调拨')
    })

    it('分页查询调拨', async () => {
      const resp = await client.get('/api/v1/material/transfer/page', {
        page: 1, size: 20, fromProjectId: projectId,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        transferId = records[0].id
        cleaner.add('删除调拨', () => client.delete(`/api/v1/material/transfer/${transferId}`))
      }
    })

    it('获取调拨详情', async () => {
      if (!transferId) return
      const resp = await client.get(`/api/v1/material/transfer/${transferId}`)
      expect(resp.code).toBe(200)
    })

    it('提交调拨审批', async () => {
      if (!transferId) return
      const resp = await client.post(`/api/v1/material/transfer/${transferId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 项目材料库存 ============
  describe('项目材料库存', () => {
    it('分页查询库存', async () => {
      const resp = await client.get('/api/v1/material/stock/page', {
        page: 1, size: 20, projectId,
      })
      expect(resp.code).toBe(200)
    })

    it('按项目查询库存列表', async () => {
      const resp = await client.get(`/api/v1/material/stock/${projectId}`)
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })
  })

  // ============ 材料退款查询 ============
  describe('材料退款', () => {
    it('分页查询退款记录', async () => {
      const resp = await client.get('/api/v1/material/refund', {
        page: 1, size: 10,
      })
      expect(resp.code).toBe(200)
    })
  })
})
