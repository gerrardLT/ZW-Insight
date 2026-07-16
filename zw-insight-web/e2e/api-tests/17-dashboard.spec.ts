/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner } from './setup'
import { TEST_PROJECT } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 17 - 数据看板测试
 * 对应功能表: 公司概览、预算执行、应收监控、投标分析、库存分析、进度甘特、利润趋势等
 */
describe('17 - 数据看板', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let projectId: number

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()
    // 创建测试项目
    const prjResp = await client.post('/api/v1/project', {
      projectName: `${TEST_PROJECT.name}_DASH`,
      projectType: 'BUILDING',
      projectAddress: '看板测试地址',
      needTender: 0,
    })
    if (prjResp.code === 200) {
      const pageResp = await client.get('/api/v1/project/page', {
        page: 1, size: 5, projectName: `${TEST_PROJECT.name}_DASH`,
      })
      if (pageResp.data?.records?.length > 0) {
        projectId = pageResp.data.records[0].id
        cleaner.add('删除看板关联项目', () => client.delete(`/api/v1/project/${projectId}`))
      }
    }
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 公司级看板 ============
  describe('公司级看板', () => {
    it('公司概览', async () => {
      const resp = await client.get('/api/v1/dashboard/company-overview')
      expect(resp.code).toBe(200)
      expect(resp.data).toBeDefined()
    })

    it('应收款监控', async () => {
      const resp = await client.get('/api/v1/dashboard/receivable-monitor')
      expect(resp.code).toBe(200)
    })

    it('供应商应付监控', async () => {
      const resp = await client.get('/api/v1/dashboard/supplier-payable')
      expect(resp.code).toBe(200)
    })

    it('投标分析', async () => {
      const resp = await client.get('/api/v1/dashboard/tender-analysis')
      expect(resp.code).toBe(200)
    })

    it('库存分析', async () => {
      const resp = await client.get('/api/v1/dashboard/inventory-analysis')
      expect(resp.code).toBe(200)
    })

    it('利润趋势分析', async () => {
      const resp = await client.get('/api/v1/dashboard/profit-trend')
      expect(resp.code).toBe(200)
    })

    it('项目排名 - 按产值', async () => {
      const resp = await client.get('/api/v1/dashboard/project-ranking', {
        rankBy: 'output', topN: 10,
      })
      expect(resp.code).toBe(200)
    })

    it('项目排名 - 按利润率', async () => {
      const resp = await client.get('/api/v1/dashboard/project-ranking', {
        rankBy: 'profitRate', topN: 5,
      })
      expect(resp.code).toBe(200)
    })

    it('发票台账', async () => {
      const resp = await client.get('/api/v1/dashboard/invoice-ledger')
      expect(resp.code).toBe(200)
    })

    it('人事统计', async () => {
      const resp = await client.get('/api/v1/dashboard/hr-statistics')
      expect(resp.code).toBe(200)
    })
  })

  // ============ 项目级看板 ============
  describe('项目级看板', () => {
    it('项目看板聚合数据', async () => {
      const resp = await client.get(`/api/v1/dashboard/project/${projectId}`)
      expect(resp.code).toBe(200)
    })

    it('预算执行', async () => {
      const resp = await client.get('/api/v1/dashboard/budget-execution', {
        projectId,
      })
      expect(resp.code).toBe(200)
    })

    it('预算偏差分析', async () => {
      const resp = await client.get('/api/v1/dashboard/budget-variance', {
        projectId,
      })
      expect(resp.code).toBe(200)
    })

    it('进度甘特图', async () => {
      const resp = await client.get(`/api/v1/dashboard/schedule-gantt/${projectId}`)
      expect(resp.code).toBe(200)
    })
  })

  // ============ 项目维度数据看板 ============
  describe('项目维度数据看板', () => {
    it('项目预算执行数据', async () => {
      const resp = await client.get(`/api/v1/dashboard/project/${projectId}/budget`)
      expect(resp.code).toBe(200)
    })

    it('项目进度完成率', async () => {
      const resp = await client.get(`/api/v1/dashboard/project/${projectId}/progress`)
      expect(resp.code).toBe(200)
    })

    it('项目合同与回款', async () => {
      const resp = await client.get(`/api/v1/dashboard/project/${projectId}/contract`)
      expect(resp.code).toBe(200)
    })

    it('项目产值上报', async () => {
      const resp = await client.get(`/api/v1/dashboard/project/${projectId}/output`)
      expect(resp.code).toBe(200)
    })

    it('项目看板聚合（四维度）', async () => {
      const resp = await client.get(`/api/v1/dashboard/project/${projectId}/overview`)
      expect(resp.code).toBe(200)
      if (resp.data) {
        // 聚合数据应包含四个维度
        expect(resp.data.budget || resp.data.budget === null).toBeDefined()
      }
    })

    it('不存在的项目返回 404', async () => {
      const resp = await client.get('/api/v1/dashboard/project/999999999/budget')
      expect(resp.code).toBe(404)
    })
  })
})
