/* eslint-disable @typescript-eslint/no-explicit-any */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { createAuthedClient, createCleaner, expectOk } from './setup'
import { TEST_BASEDATA } from './test-data'
import type { ApiClient } from './api-client'
import type { TestDataCleaner } from './test-data'

/**
 * 20 - 基础信息测试
 * 对应功能表: 公司管理、供应商管理、甲方单位、材料字典、材料分类、供应商评价、黑名单
 */
describe('20 - 基础信息', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner

  beforeAll(async () => {
    client = createAuthedClient()
    cleaner = createCleaner()
  })

  afterAll(async () => {
    await cleaner.cleanup(client)
  })

  // ============ 公司管理 ============
  describe('自持公司', () => {
    let companyId: number

    it('创建公司', async () => {
      const resp = await client.post('/api/v1/basedata/company', {
        companyName: TEST_BASEDATA.companyName,
        creditCode: `E2E_${Date.now()}`,
        legalPerson: 'E2E法人',
        address: 'E2E公司地址',
      })
      expectOk(resp, '创建公司')
    })

    it('分页查询公司', async () => {
      const resp = await client.get('/api/v1/basedata/company/page', {
        page: 1, size: 20, companyName: TEST_BASEDATA.companyName,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.companyName === TEST_BASEDATA.companyName)
      expect(found).toBeDefined()
      companyId = found.id
      cleaner.add('删除公司', () => client.delete(`/api/v1/basedata/company/${companyId}`))
    })

    it('获取公司详情', async () => {
      expect(companyId).toBeTruthy()
      const resp = await client.get(`/api/v1/basedata/company/${companyId}`)
      expect(resp.code).toBe(200)
      expect(resp.data?.companyName).toBe(TEST_BASEDATA.companyName)
    })

    it('公司下拉列表', async () => {
      const resp = await client.get('/api/v1/basedata/company/list', {
        companyName: 'E2E_TEST',
      })
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
      expect(resp.data.length).toBeGreaterThan(0)
    })

    it('更新公司', async () => {
      const resp = await client.put(`/api/v1/basedata/company/${companyId}`, {
        companyName: TEST_BASEDATA.companyName,
        address: '更新后的公司地址',
      })
      expectOk(resp, '更新公司')
    })
  })

  // ============ 供应商管理 ============
  describe('供应商', () => {
    let supplierId: number

    it('创建供应商', async () => {
      const resp = await client.post('/api/v1/basedata/supplier', {
        supplierName: TEST_BASEDATA.supplierName,
        supplierType: 'MATERIAL',
        contactPerson: 'E2E联系人',
        contactPhone: '13900139000',
      })
      expectOk(resp, '创建供应商')
    })

    it('分页查询供应商', async () => {
      const resp = await client.get('/api/v1/basedata/supplier/page', {
        page: 1, size: 20, supplierName: TEST_BASEDATA.supplierName,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.supplierName === TEST_BASEDATA.supplierName)
      expect(found).toBeDefined()
      supplierId = found.id
      cleaner.add('删除供应商', () => client.delete(`/api/v1/basedata/supplier/${supplierId}`))
    })

    it('获取供应商详情', async () => {
      expect(supplierId).toBeTruthy()
      const resp = await client.get(`/api/v1/basedata/supplier/${supplierId}`)
      expect(resp.code).toBe(200)
    })

    it('更新供应商', async () => {
      const resp = await client.put(`/api/v1/basedata/supplier/${supplierId}`, {
        supplierName: TEST_BASEDATA.supplierName,
        contactPhone: '13800000001',
      })
      expectOk(resp, '更新供应商')
    })
  })

  // ============ 甲方单位 ============
  describe('甲方单位', () => {
    let ownerId: number

    it('创建甲方单位', async () => {
      const resp = await client.post('/api/v1/basedata/owner', {
        ownerName: TEST_BASEDATA.ownerName,
        contactPerson: 'E2E甲方联系人',
        contactPhone: '13700137000',
      })
      expectOk(resp, '创建甲方')
    })

    it('分页查询甲方', async () => {
      const resp = await client.get('/api/v1/basedata/owner/page', {
        page: 1, size: 20, ownerName: TEST_BASEDATA.ownerName,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.ownerName === TEST_BASEDATA.ownerName)
      expect(found).toBeDefined()
      ownerId = found.id
      cleaner.add('删除甲方', () => client.delete(`/api/v1/basedata/owner/${ownerId}`))
    })

    it('甲方下拉列表', async () => {
      const resp = await client.get('/api/v1/basedata/owner/list', {
        ownerName: 'E2E_TEST',
      })
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })

    it('更新甲方单位', async () => {
      const resp = await client.put(`/api/v1/basedata/owner/${ownerId}`, {
        ownerName: TEST_BASEDATA.ownerName,
        contactPerson: '更新后的联系人',
      })
      expectOk(resp, '更新甲方')
    })
  })

  // ============ 材料分类 ============
  describe('材料分类', () => {
    let categoryId: number

    it('创建材料分类', async () => {
      const resp = await client.post('/api/v1/basedata/material-category', {
        categoryName: `E2E分类_${Date.now()}`,
        categoryCode: `E2ECAT_${Date.now()}`,
      })
      expectOk(resp, '创建材料分类')
    })

    it('查询材料分类树', async () => {
      const resp = await client.get('/api/v1/basedata/material-category/tree')
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
      // 找到刚创建的
      const flat = JSON.stringify(resp.data)
      if (flat.includes('E2E分类')) {
        // 从树中找到叶子节点
        const findId = (nodes: any[]): number | null => {
          for (const node of nodes) {
            if (node.categoryName?.includes('E2E分类')) return node.id
            if (node.children) {
              const found = findId(node.children)
              if (found) return found
            }
          }
          return null
        }
        const id = findId(resp.data)
        if (id) {
          categoryId = id
          cleaner.add('删除材料分类', () =>
            client.delete(`/api/v1/basedata/material-category/${categoryId}`)
          )
        }
      }
    })
  })

  // ============ 材料字典 ============
  describe('材料字典', () => {
    let materialId: number

    it('创建材料', async () => {
      const resp = await client.post('/api/v1/basedata/material', {
        materialName: TEST_BASEDATA.materialName,
        materialCode: `E2E_MAT_${Date.now()}`,
        unit: '吨',
        specification: 'HRB400 Φ25',
      })
      expectOk(resp, '创建材料')
    })

    it('分页查询材料', async () => {
      const resp = await client.get('/api/v1/basedata/material/page', {
        page: 1, size: 20, materialName: TEST_BASEDATA.materialName,
      })
      expect(resp.code).toBe(200)
      const records = resp.data?.records || []
      const found = records.find((r: any) => r.materialName === TEST_BASEDATA.materialName)
      expect(found).toBeDefined()
      materialId = found.id
      cleaner.add('删除材料', () => client.delete(`/api/v1/basedata/material/${materialId}`))
    })

    it('获取材料详情', async () => {
      expect(materialId).toBeTruthy()
      const resp = await client.get(`/api/v1/basedata/material/${materialId}`)
      expect(resp.code).toBe(200)
    })

    it('更新材料', async () => {
      const resp = await client.put(`/api/v1/basedata/material/${materialId}`, {
        materialName: TEST_BASEDATA.materialName,
        unit: '千克',
      })
      expectOk(resp, '更新材料')
    })

    it('查询材料分类列表', async () => {
      const resp = await client.get('/api/v1/basedata/material/categories')
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })
  })

  // ============ 供应商评价 ============
  describe('供应商评价', () => {
    it('分页查询供应商评价', async () => {
      // 后端分页接口为根路径 GET（无 /page 别名），不能加 /page
      const resp = await client.get('/api/v1/basedata/supplier-evaluation', {
        page: 1, size: 10,
      })
      expect([200, 404]).toContain(resp.code)
    })
  })

  // ============ 供应商黑名单 ============
  describe('供应商黑名单', () => {
    it('分页查询黑名单', async () => {
      // 后端分页接口为根路径 GET（无 /page 别名），不能加 /page
      const resp = await client.get('/api/v1/basedata/supplier-blacklist', {
        page: 1, size: 10,
      })
      expect([200, 404]).toContain(resp.code)
    })
  })
})
