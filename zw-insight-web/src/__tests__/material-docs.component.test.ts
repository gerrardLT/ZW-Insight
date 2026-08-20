/**
 * material 域入/出库与调拨单页组件测试（2026-08-15 P3 收尾批）
 *
 * inbound / outbound / transfer 为「主单+明细行」结构（formData.details），
 * 不适配标准 CRUD 工厂，定制用例：
 * - 渲染 + 搜索/重置（page/size 口径）
 * - 空明细守卫：未填明细保存被 warning 拦截（盲点 10 修复行为，新增/编辑态均生效）
 * - 行提交审批
 * 三页共用 @/api/material 模块 mock。
 */
import { describe, it, expect, vi, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockInboundPage, mockInboundCreate, mockInboundDelete, mockInboundSubmit, mockInboundDetail,
  mockOutboundPage, mockOutboundCreate, mockOutboundDelete, mockOutboundSubmit, mockOutboundDetail,
  mockTransferPage, mockTransferCreate, mockTransferDelete, mockTransferSubmit, mockTransferDetail,
  mockWarning,
} = vi.hoisted(() => {
  const page = () => vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } }))
  const ok = () => vi.fn(async (): Promise<any> => ({ code: 200 }))
  const detail = () => vi.fn(async (): Promise<any> => ({ code: 200, data: { details: [] } }))
  return {
    mockInboundPage: page(), mockInboundCreate: ok(), mockInboundDelete: ok(), mockInboundSubmit: ok(), mockInboundDetail: detail(),
    mockOutboundPage: page(), mockOutboundCreate: ok(), mockOutboundDelete: ok(), mockOutboundSubmit: ok(), mockOutboundDetail: detail(),
    mockTransferPage: page(), mockTransferCreate: ok(), mockTransferDelete: ok(), mockTransferSubmit: ok(), mockTransferDetail: detail(),
    mockWarning: vi.fn(),
  }
})

vi.mock('@/api/material', () => ({
  getMaterialInboundPage: mockInboundPage, getMaterialInboundDetail: mockInboundDetail, createMaterialInbound: mockInboundCreate,
  updateMaterialInbound: vi.fn(), deleteMaterialInbound: mockInboundDelete, submitMaterialInbound: mockInboundSubmit,
  getMaterialOutboundPage: mockOutboundPage, getMaterialOutboundDetail: mockOutboundDetail, createMaterialOutbound: mockOutboundCreate,
  updateMaterialOutbound: vi.fn(), deleteMaterialOutbound: mockOutboundDelete, submitMaterialOutbound: mockOutboundSubmit,
  getMaterialTransferPage: mockTransferPage, getMaterialTransferDetail: mockTransferDetail, createMaterialTransfer: mockTransferCreate,
  updateMaterialTransfer: vi.fn(), deleteMaterialTransfer: mockTransferDelete, submitMaterialTransfer: mockTransferSubmit,
}))
// inbound 内嵌 ProjectSelector 子组件会调 getProjectList，
// outbound/transfer 页面自身调 getProjectPage 加载项目下拉，一并 mock 防真实请求
vi.mock('@/api/project', () => ({
  getProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  getProjectPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
}))
// inbound 内嵌 PurchaseContractSelector 子组件会调 getPurchaseContractPage
vi.mock('@/api/purchase', () => ({
  getPurchaseContractPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: mockWarning },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import Inbound from '@/views/material/inbound.vue'
import Outbound from '@/views/material/outbound.vue'
import Transfer from '@/views/material/transfer.vue'

let wrapper: any = null
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
  vi.clearAllMocks()
})

const pages: Array<{ name: string; comp: any; pageMock: any; createMock: any; submitMock: any; label: string; pageKey: 'pageNum' | 'page'; pageArgKey: 'pageNum' | 'page'; clearKey: string }> = [
  { name: 'inbound', comp: Inbound, pageMock: mockInboundPage, createMock: mockInboundCreate, submitMock: mockInboundSubmit, label: '入库单', pageKey: 'page', pageArgKey: 'page', clearKey: 'projectId' },
  // 2026-08-21 缺陷#5 口径对齐：出库本地状态 pageNum/pageSize，请求实参映射为后端 page/size
  { name: 'outbound', comp: Outbound, pageMock: mockOutboundPage, createMock: mockOutboundCreate, submitMock: mockOutboundSubmit, label: '出库单', pageKey: 'pageNum', pageArgKey: 'page', clearKey: 'projectId' },
  { name: 'transfer', comp: Transfer, pageMock: mockTransferPage, createMock: mockTransferCreate, submitMock: mockTransferSubmit, label: '调拨单', pageKey: 'pageNum', pageArgKey: 'pageNum', clearKey: 'fromProjectId' },
]

async function mountPage(p: (typeof pages)[number], records: any[] = []) {
  p.pageMock.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(p.comp, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

for (const p of pages) {
  describe(`material/${p.name}.vue ${p.label}`, () => {
    it('挂载加载列表并渲染行', async () => {
      const w = await mountPage(p, [
        { id: 1, projectName: '滨江花园一期', materialName: '螺纹钢', quantity: 100, status: 'DRAFT' },
        { id: 2, projectName: '城南市政', materialName: '水泥', quantity: 50, status: 'APPROVED' },
      ])
      expect(p.pageMock).toHaveBeenCalled()
      expect(w.findAll('.el-table__row')).toHaveLength(2)
    })

    it('搜索重置页码、重置清空条件', async () => {
      await mountPage(p)
      const st = wrapper.vm.$.setupState
      st.queryParams[p.pageKey] = 3
      p.pageMock.mockClear()
      st.handleSearch()
      await flushPromises()
      expect(st.queryParams[p.pageKey]).toBe(1)
      expect((p.pageMock.mock.calls as any)[0][0][p.pageArgKey]).toBe(1)
      st.handleReset()
      await flushPromises()
      expect(st.queryParams[p.pageKey]).toBe(1)
      expect(st.queryParams[p.clearKey]).toBeUndefined()
    })

    it('空明细保存被守卫拦截（盲点 10：新增/编辑态均生效）', async () => {
      await mountPage(p)
      const st = wrapper.vm.$.setupState
      st.handleAdd()
      await flushPromises()
      // handleAdd 默认带一条空明细行 → materialName 为空过滤后为 0 条
      st.formData.projectId = 1
      await st.handleFormSubmit()
      await flushPromises()
      expect(mockWarning).toHaveBeenCalled() // 「请至少填写一条…明细」
      expect(p.createMock).not.toHaveBeenCalled()
    })

    it('行提交 → 调对应 submit API', async () => {
      await mountPage(p, [{ id: 61, status: 'DRAFT' }])
      await wrapper.vm.$.setupState.handleSubmit({ id: 61 })
      await flushPromises()
      expect(p.submitMock).toHaveBeenCalledWith(61)
    })
  })
}
