/**
 * 平台/推送配置/询价/检查页组件测试（2026-08-15 P3 收尾批 13）
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockStoragePage, mockStorageCreate, mockStorageUpdate, mockStorageDelete,
  mockTenantPage, mockTenantCreate, mockTenantUpdate, mockTenantDisable, mockTenantEnable, mockTenantRenew, mockTenantModules, mockTenantTypePage,
  mockPushConfigPage, mockPushConfigCreate, mockPushConfigUpdate, mockPushConfigDelete, mockTemplatePage,
  mockInquiryPage, mockInquiryCreate, mockInquiryUpdate, mockInquiryDelete, mockInquiryPublish,
  mockQuotations, mockRanking, mockConfirmWinner, mockBidResult,
  mockQualityPage, mockSafetyPage, mockQualityDelete, mockSafetyDelete,
  mockInspectionDetail,
} = vi.hoisted(() => {
  const page = () => vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } }))
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  return {
    mockStoragePage: page(), mockStorageCreate: ok(), mockStorageUpdate: ok(), mockStorageDelete: ok(),
    mockTenantPage: page(), mockTenantCreate: ok(), mockTenantUpdate: ok(),
    mockTenantDisable: ok(), mockTenantEnable: ok(), mockTenantRenew: ok(), mockTenantModules: ok(),
    mockTenantTypePage: page(),
    mockPushConfigPage: page(), mockPushConfigCreate: ok(), mockPushConfigUpdate: ok(), mockPushConfigDelete: ok(),
    mockTemplatePage: page(),
    mockInquiryPage: page(), mockInquiryCreate: ok(), mockInquiryUpdate: ok(), mockInquiryDelete: ok(), mockInquiryPublish: ok(),
    mockQuotations: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockRanking: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
    mockConfirmWinner: ok(),
    mockBidResult: vi.fn(async (): Promise<any> => ({ code: 200, data: null })),
    mockQualityPage: page(), mockSafetyPage: page(), mockQualityDelete: ok(), mockSafetyDelete: ok(),
    mockInspectionDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  }
})

vi.mock('@/api/file', () => ({
  getStoragePage: mockStoragePage, createStorage: mockStorageCreate, updateStorage: mockStorageUpdate, deleteStorage: mockStorageDelete,
}))
vi.mock('@/api/platform', () => ({
  getTenantPage: mockTenantPage, createTenant: mockTenantCreate, updateTenant: mockTenantUpdate,
  disableTenant: mockTenantDisable, enableTenant: mockTenantEnable, renewTenant: mockTenantRenew,
  updateTenantModules: mockTenantModules, getTenantTypePage: mockTenantTypePage,
}))
vi.mock('@/api/message', () => ({
  getPushConfigPage: mockPushConfigPage, createPushConfig: mockPushConfigCreate,
  updatePushConfig: mockPushConfigUpdate, deletePushConfig: mockPushConfigDelete,
  getTemplatePage: mockTemplatePage,
}))
vi.mock('@/api/purchase', () => ({
  getInquiryPage: mockInquiryPage, createInquiry: mockInquiryCreate, updateInquiry: mockInquiryUpdate,
  deleteInquiry: mockInquiryDelete, publishInquiry: mockInquiryPublish,
  getQuotationList: mockQuotations, calculateBidRanking: mockRanking, confirmBidWinner: mockConfirmWinner,
  getBidResultByInquiry: mockBidResult,
}))
vi.mock('@/api/site', () => ({
  getQualityInspectionPage: mockQualityPage, getSafetyInspectionPage: mockSafetyPage,
  deleteQualityInspection: mockQualityDelete, deleteSafetyInspection: mockSafetyDelete,
}))
vi.mock('@/api/inspection-scheme', () => ({
  getInspectionDetail: mockInspectionDetail,
}))
// inspection/index 内嵌 ProjectSelector 子组件会调 getProjectList，mock 防真实请求
vi.mock('@/api/project', () => ({
  getProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
}))
vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    useRouter: () => ({ push: vi.fn(), back: vi.fn() }),
    useRoute: () => ({ query: {}, params: { id: '1' } }),
  }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Storage from '@/views/platform/storage/index.vue'
import Tenant from '@/views/platform/tenant/index.vue'
import PushConfig from '@/views/message/push-config/index.vue'
import Inquiry from '@/views/purchase/inquiry.vue'
import InspectionIndex from '@/views/site/inspection/index.vue'
import InspectionDetail from '@/views/site/inspection/detail.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

describe('platform/storage/index.vue 存储配置', () => {
  async function mountPage(records: any[] = []) {
    mockStoragePage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(Storage, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载存储配置列表', async () => {
    const w = await mountPage([{ id: 1, storageType: 'MINIO', endpoint: 'minio:9000' }])
    expect(mockStoragePage).toHaveBeenCalled()
    expect(w.findAll('.el-table__row')).toHaveLength(1)
  })

  it('新增走 create、编辑走 update', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockStorageCreate).toHaveBeenCalledTimes(1)
    st.handleEdit({ id: 3, storageType: 'MINIO' })
    await flushPromises()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockStorageUpdate).toHaveBeenCalledTimes(1)
  })

  it('删除调 deleteStorage', async () => {
    await mountPage([{ id: 5 }])
    await wrapper.vm.$.setupState.handleDelete({ id: 5 })
    await flushPromises()
    expect(mockStorageDelete).toHaveBeenCalledWith(5)
  })
})

describe('platform/tenant/index.vue 租户管理', () => {
  async function mountPage(records: any[] = []) {
    mockTenantPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    mockTenantTypePage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    wrapper = mount(Tenant, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载租户分页', async () => {
    const w = await mountPage([{ id: 1, tenantName: '租户A', status: 'ACTIVE' }])
    expect(mockTenantPage).toHaveBeenCalled()
    expect(w.text()).toContain('租户A')
  })

  it('停用/启用租户调对应 API', async () => {
    await mountPage([{ id: 2, tenantName: '租户B', status: 'ACTIVE' }])
    const st = wrapper.vm.$.setupState
    await st.handleDisable({ id: 2, tenantName: '租户B' })
    await flushPromises()
    expect(mockTenantDisable).toHaveBeenCalledWith(2)
    await st.handleEnable({ id: 2, tenantName: '租户B' })
    await flushPromises()
    expect(mockTenantEnable).toHaveBeenCalledWith(2)
  })

  it('续期弹窗回显租户与默认 30 天', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleRenew({ id: 7, tenantName: '租户C' })
    await flushPromises()
    expect(st.renewDialogVisible).toBe(true)
    expect(st.renewForm.tenantId).toBe(7)
    expect(st.renewForm.durationDays).toBe(30) // 默认续期天数钉住
  })
})

describe('message/push-config/index.vue 推送配置', () => {
  async function mountPage() {
    mockPushConfigPage.mockResolvedValue({ code: 200, data: { records: [{ id: 1, businessType: 'APPROVAL' }], total: 1 } })
    mockTemplatePage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    wrapper = mount(PushConfig, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载配置列表与模板下拉数据源', async () => {
    await mountPage()
    expect(mockPushConfigPage).toHaveBeenCalled()
    expect(mockTemplatePage).toHaveBeenCalled()
  })

  it('新增走 create、删除调 deletePushConfig', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    st.handleAdd()
    await flushPromises()
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockPushConfigCreate).toHaveBeenCalledTimes(1)
    await st.handleDelete({ id: 1 })
    await flushPromises()
    expect(mockPushConfigDelete).toHaveBeenCalledWith(1)
  })
})

describe('purchase/inquiry.vue 询价管理（含比价定标）', () => {
  async function mountPage(records: any[] = []) {
    mockInquiryPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    wrapper = mount(Inquiry, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载加载询价列表', async () => {
    const w = await mountPage([{ id: 1, title: '钢筋询价', status: 'PUBLISHED' }])
    expect(mockInquiryPage).toHaveBeenCalled()
    expect(w.text()).toContain('钢筋询价')
  })

  it('发布询价调 publishInquiry', async () => {
    await mountPage([{ id: 3, title: 'X', status: 'DRAFT' }])
    await wrapper.vm.$.setupState.handlePublish({ id: 3 })
    await flushPromises()
    expect(mockInquiryPublish).toHaveBeenCalledWith(3)
  })

  it('打开比价定标弹窗拉取报价列表', async () => {
    mockQuotations.mockResolvedValue({ code: 200, data: [{ id: 1, supplierName: 'S1', totalAmount: 100 }] })
    await mountPage([{ id: 4, title: 'Y', status: 'PUBLISHED' }])
    const st = wrapper.vm.$.setupState
    await st.handleBid({ id: 4, title: 'Y', status: 'PUBLISHED' })
    await flushPromises()
    expect(mockQuotations).toHaveBeenCalledWith(4)
    expect(st.bidDialogVisible).toBe(true)
  })

  it('计算排名调 calculateBidRanking', async () => {
    await mountPage([{ id: 5, title: 'Z', status: 'PUBLISHED' }])
    const st = wrapper.vm.$.setupState
    await st.handleBid({ id: 5, title: 'Z', status: 'PUBLISHED' })
    await flushPromises()
    mockRanking.mockClear()
    await st.handleCalculateRanking()
    await flushPromises()
    expect(mockRanking).toHaveBeenCalledWith(5)
  })
})

describe('site/inspection/index.vue 检查列表（质量/安全双 tab）', () => {
  async function mountPage() {
    mockQualityPage.mockResolvedValue({ code: 200, data: { records: [{ id: 1 }], total: 1 } })
    mockSafetyPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    wrapper = mount(InspectionIndex, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    return wrapper
  }

  it('挂载默认质量检查 tab', async () => {
    await mountPage()
    expect(mockQualityPage).toHaveBeenCalled()
  })

  it('tab 切换到安全检查', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    mockSafetyPage.mockClear()
    st.activeTab = 'safety'
    st.handleTabChange()
    await flushPromises()
    expect(mockSafetyPage).toHaveBeenCalled()
  })

  it('删除按 tab 路由到对应 API', async () => {
    await mountPage()
    const st = wrapper.vm.$.setupState
    await st.handleDelete({ id: 8 })
    await flushPromises()
    expect(mockQualityDelete).toHaveBeenCalledWith(8)
  })
})

describe('site/inspection/detail.vue 检查详情', () => {
  it('挂载按路由 id 拉详情', async () => {
    wrapper = mount(InspectionDetail, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(mockInspectionDetail).toHaveBeenCalled()
  })
})
