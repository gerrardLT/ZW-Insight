import { describe, it, expect, vi, beforeEach } from 'vitest'

// ---- mock request (使用 vi.hoisted 确保提升后仍可访问) ----
const { mockRequest } = vi.hoisted(() => ({
  mockRequest: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

vi.mock('@/utils/request', () => ({ default: mockRequest }))

// ---- import SUT ----
import {
  getContractPage,
  getContractDetail,
  createContract,
  updateContract,
  deleteContract,
  submitContract,
  getContractDetails,
  saveContractDetails,
  getChangeVisaPage,
  createChangeVisa,
  submitChangeVisa,
  getOtherContractPage,
  getOtherContractDetail,
  createOtherContract,
  updateOtherContract,
  deleteOtherContract,
  getQuantityListPage,
  createQuantityList,
  updateQuantityList,
  deleteQuantityList,
  importQuantityList,
  getFinalSettlementPage,
  createFinalSettlement,
  submitFinalSettlement,
  getOutputReportPage,
  createOutputReport,
  submitOutputReport,
  getBomItems,
  createBomItem,
  updateBomItem,
  deleteBomItem,
  importBomItems,
} from '@/api/contract'

describe('contract API 模块', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ======================== 施工合同 ========================

  describe('施工合同', () => {
    it('getContractPage 调用 GET /v1/contract/page', () => {
      const params = { pageNum: 1, pageSize: 10 }
      getContractPage(params)
      expect(mockRequest.get).toHaveBeenCalledWith('/v1/contract/page', { params })
    })

    it('getContractDetail 调用 GET /v1/contract/:id', () => {
      getContractDetail(5)
      expect(mockRequest.get).toHaveBeenCalledWith('/v1/contract/5')
    })

    it('createContract 调用 POST /v1/contract', () => {
      const data = { name: '合同A', amount: 500000 }
      createContract(data)
      expect(mockRequest.post).toHaveBeenCalledWith('/v1/contract', data)
    })

    it('updateContract 调用 PUT /v1/contract/:id', () => {
      const data = { id: 3, name: '更新合同' }
      updateContract(data)
      expect(mockRequest.put).toHaveBeenCalledWith('/v1/contract/3', data)
    })

    it('deleteContract 调用 DELETE /v1/contract/:id', () => {
      deleteContract(8)
      expect(mockRequest.delete).toHaveBeenCalledWith('/v1/contract/8')
    })

    it('submitContract 调用 PUT /v1/contract/:id/submit', () => {
      submitContract(2)
      expect(mockRequest.put).toHaveBeenCalledWith('/v1/contract/2/submit')
    })
  })

  // ======================== 合同明细 ========================

  describe('合同明细', () => {
    it('getContractDetails 调用 GET /v1/contract/:id/details', () => {
      getContractDetails(10)
      expect(mockRequest.get).toHaveBeenCalledWith('/v1/contract/10/details')
    })

    it('saveContractDetails 调用 POST /v1/contract/:id/details', () => {
      const items = [{ name: '明细1', amount: 100 }]
      saveContractDetails(10, items)
      expect(mockRequest.post).toHaveBeenCalledWith('/v1/contract/10/details', items)
    })
  })

  // ======================== 变更签证 ========================

  describe('变更签证', () => {
    it('getChangeVisaPage 调用 GET /v1/contract/change-visa', () => {
      const params = { pageNum: 1 }
      getChangeVisaPage(params)
      expect(mockRequest.get).toHaveBeenCalledWith('/v1/contract/change-visa', { params })
    })

    it('createChangeVisa 调用 POST /v1/contract/change-visa', () => {
      const data = { description: '变更内容' }
      createChangeVisa(data)
      expect(mockRequest.post).toHaveBeenCalledWith('/v1/contract/change-visa', data)
    })

    it('submitChangeVisa 调用 POST /v1/contract/change-visa/:id/submit', () => {
      submitChangeVisa(15)
      expect(mockRequest.post).toHaveBeenCalledWith('/v1/contract/change-visa/15/submit')
    })
  })

  // ======================== 其他合同 ========================

  describe('其他合同', () => {
    it('getOtherContractPage 调用 GET /v1/contract/other/page', () => {
      getOtherContractPage({ pageNum: 1 })
      expect(mockRequest.get).toHaveBeenCalledWith('/v1/contract/other/page', { params: { pageNum: 1 } })
    })

    it('getOtherContractDetail 调用 GET /v1/contract/other/:id', () => {
      getOtherContractDetail(20)
      expect(mockRequest.get).toHaveBeenCalledWith('/v1/contract/other/20')
    })

    it('createOtherContract 调用 POST /v1/contract/other', () => {
      const data = { name: '其他合同' }
      createOtherContract(data)
      expect(mockRequest.post).toHaveBeenCalledWith('/v1/contract/other', data)
    })

    it('updateOtherContract 调用 PUT /v1/contract/other/:id', () => {
      const data = { name: '更新' }
      updateOtherContract(20, data)
      expect(mockRequest.put).toHaveBeenCalledWith('/v1/contract/other/20', data)
    })

    it('deleteOtherContract 调用 DELETE /v1/contract/other/:id', () => {
      deleteOtherContract(20)
      expect(mockRequest.delete).toHaveBeenCalledWith('/v1/contract/other/20')
    })
  })

  // ======================== 工程量清单 ========================

  describe('工程量清单', () => {
    it('getQuantityListPage 调用 GET /v1/contract/quantity', () => {
      getQuantityListPage({ pageNum: 1 })
      expect(mockRequest.get).toHaveBeenCalledWith('/v1/contract/quantity', { params: { pageNum: 1 } })
    })

    it('createQuantityList 调用 POST /v1/contract/quantity', () => {
      const data = { name: '清单项' }
      createQuantityList(data)
      expect(mockRequest.post).toHaveBeenCalledWith('/v1/contract/quantity', data)
    })

    it('updateQuantityList 调用 PUT /v1/contract/quantity/:id', () => {
      updateQuantityList(5, { name: '更新' })
      expect(mockRequest.put).toHaveBeenCalledWith('/v1/contract/quantity/5', { name: '更新' })
    })

    it('deleteQuantityList 调用 DELETE /v1/contract/quantity/:id', () => {
      deleteQuantityList(5)
      expect(mockRequest.delete).toHaveBeenCalledWith('/v1/contract/quantity/5')
    })

    it('importQuantityList 调用 POST 并带 multipart header', () => {
      const formData = new FormData()
      formData.append('file', 'test')
      importQuantityList(formData)
      expect(mockRequest.post).toHaveBeenCalledWith('/v1/contract/quantity/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    })
  })

  // ======================== 竣工结算 ========================

  describe('竣工结算', () => {
    it('getFinalSettlementPage 调用 GET /v1/contract/settlement', () => {
      getFinalSettlementPage({})
      expect(mockRequest.get).toHaveBeenCalledWith('/v1/contract/settlement', { params: {} })
    })

    it('createFinalSettlement 调用 POST /v1/contract/settlement', () => {
      const data = { amount: 999 }
      createFinalSettlement(data)
      expect(mockRequest.post).toHaveBeenCalledWith('/v1/contract/settlement', data)
    })

    it('submitFinalSettlement 调用 POST /v1/contract/settlement/:id/submit', () => {
      submitFinalSettlement(12)
      expect(mockRequest.post).toHaveBeenCalledWith('/v1/contract/settlement/12/submit')
    })
  })

  // ======================== 产值报告 ========================

  describe('产值报告', () => {
    it('getOutputReportPage 调用 GET /v1/contract/output', () => {
      getOutputReportPage({ pageNum: 1 })
      expect(mockRequest.get).toHaveBeenCalledWith('/v1/contract/output', { params: { pageNum: 1 } })
    })

    it('createOutputReport 调用 POST /v1/contract/output', () => {
      const data = { period: '2024-01' }
      createOutputReport(data)
      expect(mockRequest.post).toHaveBeenCalledWith('/v1/contract/output', data)
    })

    it('submitOutputReport 调用 POST /v1/contract/output/:id/submit', () => {
      submitOutputReport(7)
      expect(mockRequest.post).toHaveBeenCalledWith('/v1/contract/output/7/submit')
    })
  })

  // ======================== 工程量清单(BOM) ========================

  describe('工程量清单(BOM)', () => {
    it('getBomItems 调用 GET /v1/contract/bom/:contractId', () => {
      getBomItems(3)
      expect(mockRequest.get).toHaveBeenCalledWith('/v1/contract/bom/3')
    })

    it('createBomItem 调用 POST /v1/contract/bom', () => {
      const data = { name: 'BOM项', quantity: 100 }
      createBomItem(data)
      expect(mockRequest.post).toHaveBeenCalledWith('/v1/contract/bom', data)
    })

    it('updateBomItem 调用 PUT /v1/contract/bom/:id', () => {
      updateBomItem(9, { quantity: 200 })
      expect(mockRequest.put).toHaveBeenCalledWith('/v1/contract/bom/9', { quantity: 200 })
    })

    it('deleteBomItem 调用 DELETE /v1/contract/bom/:id', () => {
      deleteBomItem(9)
      expect(mockRequest.delete).toHaveBeenCalledWith('/v1/contract/bom/9')
    })

    it('importBomItems 调用 POST 并带 multipart header', () => {
      const formData = new FormData()
      formData.append('file', 'bom-data')
      importBomItems(formData)
      expect(mockRequest.post).toHaveBeenCalledWith('/v1/contract/bom/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
    })
  })
})
