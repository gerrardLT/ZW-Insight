/**
 * 材料入库/薪资统计缺陷钉住（2026-08-14 P0 @matrix 盲点 9/10）
 *
 * 盲点 10：入库编辑态绕过明细必填守卫（原 !isEdit 前缀致编辑可删光明细保存）。
 * 盲点 9：薪资统计工人姓名筛选失效（既不传后端也不本地过滤）。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

// ===================== 盲点 10：入库编辑守卫 =====================

const {
  mockInboundPage,
  mockCreateInbound,
  mockUpdateInbound,
  mockInboundDetail,
  mockWarn,
} = vi.hoisted(() => ({
  mockInboundPage: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreateInbound: vi.fn(async (_d?: any): Promise<any> => ({ code: 200 })),
  mockUpdateInbound: vi.fn(async (_d?: any): Promise<any> => ({ code: 200 })),
  mockInboundDetail: vi.fn(async (_id?: any): Promise<any> => ({ code: 200, data: { details: [] } })),
  mockWarn: vi.fn(),
}))

vi.mock('@/api/material', () => ({
  getMaterialInboundPage: mockInboundPage,
  getMaterialInboundDetail: mockInboundDetail,
  createMaterialInbound: mockCreateInbound,
  updateMaterialInbound: mockUpdateInbound,
  deleteMaterialInbound: vi.fn(async () => ({ code: 200 })),
  submitMaterialInbound: vi.fn(async () => ({ code: 200 })),
}))
vi.mock('@/components/ProjectSelector.vue', () => ({ default: { name: 'ProjectSelector', render: () => null } }))
vi.mock('@/components/PurchaseContractSelector.vue', () => ({ default: { name: 'PurchaseContractSelector', render: () => null } }))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { ...actual.ElMessage, warning: mockWarn, success: vi.fn(), error: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => true) },
  }
})

import MaterialInbound from '@/views/material/inbound.vue'

describe('入库明细守卫（@matrix 盲点 10）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('编辑态空明细提交被拦截，不调用 update（修复钉住点）', async () => {
    const wrapper = mount(MaterialInbound, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st: any = wrapper.vm.$.setupState

    // 模拟编辑态：打开编辑弹窗后删光明细（setupState 经 proxy 解包，直接赋值）
    st.isEdit = true
    st.formData.details = []
    // 绕过表单必填（仅测明细守卫）：打桩 validate
    st.formRef = { validate: async () => true }

    await st.handleFormSubmit()
    await flushPromises()

    expect(mockWarn).toHaveBeenCalledWith('请至少填写一条入库明细')
    expect(mockUpdateInbound).not.toHaveBeenCalled()
    expect(mockCreateInbound).not.toHaveBeenCalled()
  })

  it('新增态空明细同样被拦截（原有行为回归）', async () => {
    const wrapper = mount(MaterialInbound, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st: any = wrapper.vm.$.setupState

    st.isEdit = false
    st.formData.details = []
    st.formRef = { validate: async () => true }

    await st.handleFormSubmit()
    await flushPromises()

    expect(mockWarn).toHaveBeenCalledWith('请至少填写一条入库明细')
    expect(mockCreateInbound).not.toHaveBeenCalled()
  })

  it('编辑态带有效明细正常提交（放行回归）', async () => {
    const wrapper = mount(MaterialInbound, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st: any = wrapper.vm.$.setupState

    st.isEdit = true
    st.formData.projectId = 1
    st.formData.details = [{ materialName: '钢筋', specification: '', unit: '吨', quantity: 1, unitPrice: 1 }]
    st.formRef = { validate: async () => true }

    await st.handleFormSubmit()
    await flushPromises()

    expect(mockUpdateInbound).toHaveBeenCalled()
  })
})

// ===================== 盲点 9：薪资统计工人姓名本地过滤 =====================

const {
  mockSalaryStats,
  mockSalaryDetail,
  mockSalaryCompare,
} = vi.hoisted(() => ({
  mockSalaryStats: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: { teamList: [] } })),
  mockSalaryDetail: vi.fn(async (_p?: any): Promise<any> => ({
    code: 200,
    data: {
      records: [
        { workerName: '张三', amount: 100 },
        { workerName: '李四', amount: 200 },
        { workerName: '张小明', amount: 300 },
      ],
      total: 3,
    },
  })),
  mockSalaryCompare: vi.fn(async (_p?: any): Promise<any> => ({ code: 200, data: null })),
}))

vi.mock('@/api/labor', () => ({
  getSalaryStats: mockSalaryStats,
  getSalaryDetail: mockSalaryDetail,
  getSalaryCompare: mockSalaryCompare,
  exportSalaryExcel: vi.fn(async () => new Blob()),
}))

import SalaryStats from '@/views/labor/salary/stats.vue'

describe('薪资统计工人姓名过滤（@matrix 盲点 9）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSalaryDetail.mockResolvedValue({
      code: 200,
      data: {
        records: [
          { workerName: '张三', amount: 100 },
          { workerName: '李四', amount: 200 },
          { workerName: '张小明', amount: 300 },
        ],
        total: 3,
      },
    })
  })

  it('workerName 非空时对明细本地过滤（修复钉住点）', async () => {
    const wrapper = mount(SalaryStats, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st: any = wrapper.vm.$.setupState

    st.queryParams.projectId = 1
    st.queryParams.month = '2026-07'
    st.queryParams.workerName = '张'

    const row: any = { teamId: 10 }
    await st.loadTeamDetail(row, 1)

    // 仅保留包含「张」的工人（张三/张小明），李四被过滤
    expect(row._detailData.map((r: any) => r.workerName)).toEqual(['张三', '张小明'])
  })

  it('workerName 为空时不过滤（原有行为回归）', async () => {
    const wrapper = mount(SalaryStats, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const st: any = wrapper.vm.$.setupState

    st.queryParams.projectId = 1
    st.queryParams.month = '2026-07'
    st.queryParams.workerName = ''

    const row: any = { teamId: 10 }
    await st.loadTeamDetail(row, 1)

    expect(row._detailData.length).toBe(3)
  })
})
