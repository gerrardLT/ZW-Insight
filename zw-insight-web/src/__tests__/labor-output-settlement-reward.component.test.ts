import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const mockOutputPage = vi.fn()
const mockOutputCreate = vi.fn()
const mockOutputSubmit = vi.fn()
const mockOutputDelete = vi.fn()

const mockSettlementPage = vi.fn()
const mockSettlementCreate = vi.fn()
const mockSettlementSubmit = vi.fn()
const mockSettlementDelete = vi.fn()

const mockRewardPage = vi.fn()
const mockRewardCreate = vi.fn()
const mockRewardSubmit = vi.fn()
const mockRewardDelete = vi.fn()

const mockContractPage = vi.fn()

vi.mock('@/api/labor', () => ({
  getLaborOutputPage: (...args: any[]) => mockOutputPage(...args),
  createLaborOutput: (...args: any[]) => mockOutputCreate(...args),
  updateLaborOutput: vi.fn(),
  deleteLaborOutput: (...args: any[]) => mockOutputDelete(...args),
  submitLaborOutput: (...args: any[]) => mockOutputSubmit(...args),

  getLaborSettlementPage: (...args: any[]) => mockSettlementPage(...args),
  createLaborSettlement: (...args: any[]) => mockSettlementCreate(...args),
  updateLaborSettlement: vi.fn(),
  deleteLaborSettlement: (...args: any[]) => mockSettlementDelete(...args),
  submitLaborSettlement: (...args: any[]) => mockSettlementSubmit(...args),

  getLaborRewardPage: (...args: any[]) => mockRewardPage(...args),
  createLaborReward: (...args: any[]) => mockRewardCreate(...args),
  updateLaborReward: vi.fn(),
  deleteLaborReward: (...args: any[]) => mockRewardDelete(...args),
  submitLaborReward: (...args: any[]) => mockRewardSubmit(...args),

  getLaborContractPage: (...args: any[]) => mockContractPage(...args),
}))

vi.mock('@/api/project', () => ({
  getProjectList: vi.fn(async () => ({ code: 200, data: [{ id: 1, projectName: '测试项目' }] })),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import LaborOutput from '@/views/labor/output.vue'
import LaborSettlement from '@/views/labor/settlement.vue'
import LaborRewardPunish from '@/views/labor/reward-punish.vue'

describe('劳务管理断头页面补齐测试', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockContractPage.mockResolvedValue({ code: 200, data: { records: [{ id: 10, contractName: '劳务合同A' }], total: 1 } })
  })

  describe('views/labor/output.vue 产值上报', () => {
    it('正确加载并渲染产值列表与状态', async () => {
      mockOutputPage.mockResolvedValue({
        code: 200,
        data: {
          records: [
            { id: 101, contractName: '劳务合同A', currentOutput: 50000, cumulativeOutput: 100000, status: 'APPROVED' },
            { id: 102, contractName: '劳务合同A', currentOutput: 20000, cumulativeOutput: 120000, status: 'DRAFT' },
          ],
          total: 2,
        },
      })

      const wrapper = mount(LaborOutput, { global: { plugins: [ElementPlus] } })
      await flushPromises()

      expect(mockOutputPage).toHaveBeenCalled()
      expect(wrapper.text()).toContain('50,000.00')
      expect(wrapper.text()).toContain('已确认')
      expect(wrapper.text()).toContain('草稿')
      wrapper.unmount()
    })
  })

  describe('views/labor/settlement.vue 劳务结算', () => {
    it('正确加载并渲染结算列表与发起付款按钮', async () => {
      mockSettlementPage.mockResolvedValue({
        code: 200,
        data: {
          records: [
            { id: 201, projectId: 1, contractId: 10, contractName: '劳务合同A', settlementAmount: 60000, cumulativeSettlement: 60000, status: 'APPROVED' },
            { id: 202, projectId: 1, contractId: 10, contractName: '劳务合同A', settlementAmount: 40000, cumulativeSettlement: 100000, status: 'DRAFT' },
          ],
          total: 2,
        },
      })

      const wrapper = mount(LaborSettlement, { global: { plugins: [ElementPlus] } })
      await flushPromises()

      expect(mockSettlementPage).toHaveBeenCalled()
      expect(wrapper.text()).toContain('60,000.00')
      expect(wrapper.text()).toContain('已审批')
      expect(wrapper.text()).toContain('发起付款')
      wrapper.unmount()
    })
  })

  describe('views/labor/reward-punish.vue 劳务奖惩', () => {
    it('正确加载并渲染奖惩列表与类型标签', async () => {
      mockRewardPage.mockResolvedValue({
        code: 200,
        data: {
          records: [
            { id: 301, contractName: '劳务合同A', rpType: 'REWARD', amount: 5000, reason: '施工质量优异', status: 'APPROVED' },
            { id: 302, contractName: '劳务合同A', rpType: 'PUNISH', amount: 2000, reason: '未按规佩戴安全帽', status: 'DRAFT' },
          ],
          total: 2,
        },
      })

      const wrapper = mount(LaborRewardPunish, { global: { plugins: [ElementPlus] } })
      await flushPromises()

      expect(mockRewardPage).toHaveBeenCalled()
      expect(wrapper.text()).toContain('5,000.00')
      expect(wrapper.text()).toContain('奖励')
      expect(wrapper.text()).toContain('处罚')
      expect(wrapper.text()).toContain('施工质量优异')
      wrapper.unmount()
    })
  })
})
