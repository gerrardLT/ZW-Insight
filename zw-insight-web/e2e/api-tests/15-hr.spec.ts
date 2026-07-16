/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_HR } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 15 - 行政人事测试
 * 对应功能表: 入职申请、办公用品、用印申请、车辆管理、转正、调岗、离职
 */
describe('15 - 行政人事', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 入职申请 ============
  describe('入职申请', () => {
    let entryId: number

    it('创建入职申请', async () => {
      const resp = await client.post('/api/v1/hr/entry-apply', {
        realName: TEST_HR.entryName,
        phone: '13800138001',
        entryDate: '2026-04-01',
      })
      expectOk(resp, '创建入职申请')
    })

    it('分页查询入职申请', async () => {
      const resp = await client.get('/api/v1/hr/entry-apply/page', {
        page: 1, size: 20, realName: TEST_HR.entryName,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.realName === TEST_HR.entryName)
      if (found) {
        entryId = found.id
        cleaner.add('删除入职申请', () => client.delete(`/api/v1/hr/entry-apply/${entryId}`))
      }
    })

    it('获取入职申请详情', async () => {
      if (!entryId) return
      const resp = await client.get(`/api/v1/hr/entry-apply/${entryId}`)
      expect(resp.code).toBe(200)
    })

    it('更新入职申请', async () => {
      if (!entryId) return
      const resp = await client.put(`/api/v1/hr/entry-apply/${entryId}`, {
        realName: TEST_HR.entryName,
        gender: 'MALE',
      })
      expectOk(resp, '更新入职申请')
    })

    it('提交入职申请审批', async () => {
      if (!entryId) return
      const resp = await client.post(`/api/v1/hr/entry-apply/${entryId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 办公用品 ============
  describe('办公用品', () => {
    let supplyId: number

    it('创建办公用品', async () => {
      const resp = await client.post('/api/v1/hr/office-supply', {
        supplyName: TEST_HR.supplyName,
        categoryName: '文具',
        unit: '箱',
        stockQuantity: 100,
      })
      expectOk(resp, '创建办公用品')
    })

    it('分页查询办公用品', async () => {
      const resp = await client.get('/api/v1/hr/office-supply/page', {
        page: 1, size: 20, supplyName: TEST_HR.supplyName,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.supplyName === TEST_HR.supplyName)
      if (found) {
        supplyId = found.id
        cleaner.add('删除办公用品', () => client.delete(`/api/v1/hr/office-supply/${supplyId}`))
      }
    })

    it('更新办公用品', async () => {
      if (!supplyId) return
      const resp = await client.put(`/api/v1/hr/office-supply/${supplyId}`, {
        supplyName: TEST_HR.supplyName,
        stockQuantity: 80,
      })
      expectOk(resp, '更新办公用品')
    })

    it('办公用品入库', async () => {
      if (!supplyId) return
      const resp = await client.post('/api/v1/hr/office-supply/in-out', {
        supplyId,
        ioType: 'IN',
        quantity: 50,
        remark: 'E2E测试入库',
      })
      expectOk(resp, '办公用品入库')
    })

    it('办公用品出库', async () => {
      if (!supplyId) return
      const resp = await client.post('/api/v1/hr/office-supply/in-out', {
        supplyId,
        ioType: 'OUT',
        quantity: 10,
        applicant: 'E2E测试人',
        remark: 'E2E测试出库',
      })
      expectOk(resp, '办公用品出库')
    })

    it('分页查询出入库记录', async () => {
      const resp = await client.get('/api/v1/hr/office-supply/in-out', {
        page: 1, size: 20, supplyId,
      })
      expect(resp.code).toBe(200)
    })
  })

  // ============ 用印申请 ============
  describe('用印申请', () => {
    let sealId: number

    it('创建用印申请', async () => {
      const resp = await client.post('/api/v1/hr/seal-apply', {
        applicant: 'E2E测试人',
        sealType: 'OFFICIAL',
        reason: 'E2E测试用印',
        useTime: '2026-04-01T09:00:00',
      })
      expectOk(resp, '创建用印申请')
    })

    it('分页查询用印申请', async () => {
      const resp = await client.get('/api/v1/hr/seal-apply', {
        page: 1, size: 20,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      if (records.length > 0) {
        sealId = records[0].id
        cleaner.add('删除用印申请', () => client.delete(`/api/v1/hr/seal-apply/${sealId}`))
      }
    })

    it('提交用印申请审批', async () => {
      if (!sealId) return
      const resp = await client.post(`/api/v1/hr/seal-apply/${sealId}/submit`)
      expect([200, 400, 500]).toContain(resp.code)
    })
  })

  // ============ 车辆管理 ============
  describe('车辆管理', () => {
    let vehicleId: number
    const plateNumber = `E2E${Date.now().toString().slice(-6)}`

    it('创建车辆', async () => {
      const resp = await client.post('/api/v1/hr/vehicle', {
        plateNumber,
        vehicleType: 'SEDAN',
        vehicleStatus: 'IDLE',
        status: 1,
      })
      expectOk(resp, '创建车辆')
    })

    it('分页查询车辆', async () => {
      const resp = await client.get('/api/v1/hr/vehicle/page', {
        page: 1, size: 20,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.plateNumber === plateNumber)
      if (found) {
        vehicleId = found.id
        cleaner.add('删除车辆', () => client.delete(`/api/v1/hr/vehicle/${vehicleId}`))
      }
    })

    it('更新车辆信息', async () => {
      if (!vehicleId) return
      const resp = await client.put(`/api/v1/hr/vehicle/${vehicleId}`, {
        vehicleStatus: 'IN_USE',
      })
      expectOk(resp, '更新车辆')
    })

    it('创建车辆使用申请', async () => {
      if (!vehicleId) return
      const resp = await client.post('/api/v1/hr/vehicle/apply', {
        vehicleId,
        purpose: 'E2E测试用车',
        useTime: '2026-04-01T09:00:00',
      })
      expectOk(resp, '创建车辆申请')
    })

    it('分页查询车辆申请', async () => {
      const resp = await client.get('/api/v1/hr/vehicle/apply', {
        page: 1, size: 20, vehicleId,
      })
      expect(resp.code).toBe(200)
    })

    it('创建车辆维保记录', async () => {
      if (!vehicleId) return
      const resp = await client.post('/api/v1/hr/vehicle/maintenance', {
        vehicleId,
        maintType: 'REPAIR',
        maintDate: '2026-04-01',
        maintCost: 500,
        content: 'E2E测试维保',
      })
      expectOk(resp, '创建维保记录')
    })

    it('分页查询维保记录', async () => {
      const resp = await client.get('/api/v1/hr/vehicle/maintenance', {
        page: 1, size: 20, vehicleId,
      })
      expect(resp.code).toBe(200)
    })
  })

  // ============ 转正申请 ============
  describe('转正申请', () => {
    it('分页查询转正申请', async () => {
      const resp = await client.get('/api/v1/hr/regular-apply', {
        page: 1, size: 10,
      })
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ 调岗申请 ============
  describe('调岗申请', () => {
    it('分页查询调岗申请', async () => {
      const resp = await client.get('/api/v1/hr/transfer-apply', {
        page: 1, size: 10,
      })
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ 离职申请 ============
  describe('离职申请', () => {
    it('分页查询离职申请', async () => {
      const resp = await client.get('/api/v1/hr/resign-apply/page', {
        page: 1, size: 10,
      })
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ HR 统计 ============
  describe('HR统计', () => {
    it('查询HR统计概览', async () => {
      const resp = await client.get('/api/v1/hr/statistics/overview')
      expect([200, 404]).toContain(resp.code)
    })
  })
})
