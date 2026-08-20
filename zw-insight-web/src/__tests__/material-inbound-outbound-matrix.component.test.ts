/**
 * M3 账本补齐：B-1 材料域入库/出库页矩阵用例
 * views/material/inbound.vue（B1）+ views/material/outbound.vue（B2）
 *
 * @matrix B-1-2/B-1-3/B-1-4/B-1-5/B-1-7/B-1-8/B-1-9/B-1-12
 * @matrix B-2-1/B-2-2/B-2-3/B-2-6/B-2-7/B-2-10
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockInboundPage, mockInboundDetail, mockCreateInbound, mockUpdateInbound,
  mockSubmitInbound, mockDeleteInbound,
  mockOutboundPage, mockOutboundDetail, mockCreateOutbound, mockUpdateOutbound,
  mockSubmitOutbound, mockDeleteOutbound,
  mockProjectPage, mockWarning, mockSuccess, mockConfirm,
} = vi.hoisted(() => ({
  mockInboundPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockInboundDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: { details: [] } })),
  mockCreateInbound: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdateInbound: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmitInbound: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDeleteInbound: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockOutboundPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockOutboundDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockCreateOutbound: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockUpdateOutbound: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmitOutbound: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDeleteOutbound: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockProjectPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockWarning: vi.fn(),
  mockSuccess: vi.fn(),
  mockConfirm: vi.fn(async () => 'confirm'),
}))

vi.mock('@/api/material', () => ({
  getMaterialInboundPage: mockInboundPage,
  getMaterialInboundDetail: mockInboundDetail,
  createMaterialInbound: mockCreateInbound,
  updateMaterialInbound: mockUpdateInbound,
  submitMaterialInbound: mockSubmitInbound,
  deleteMaterialInbound: mockDeleteInbound,
  getMaterialOutboundPage: mockOutboundPage,
  getMaterialOutboundDetail: mockOutboundDetail,
  createMaterialOutbound: mockCreateOutbound,
  updateMaterialOutbound: mockUpdateOutbound,
  submitMaterialOutbound: mockSubmitOutbound,
  deleteMaterialOutbound: mockDeleteOutbound,
}))
vi.mock('@/api/project', () => ({
  getProjectPage: mockProjectPage,
}))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: mockSuccess, error: vi.fn(), warning: mockWarning, info: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: mockConfirm },
  }
})

import InboundView from '@/views/material/inbound.vue'
import OutboundView from '@/views/material/outbound.vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))
const inboundSrc = readFileSync(resolve(__testDir, '../views/material/inbound.vue'), 'utf-8')
const outboundSrc = readFileSync(resolve(__testDir, '../views/material/outbound.vue'), 'utf-8')

const stubs = {
  ProjectSelector: { template: '<div class="stub-project-selector" />', props: ['modelValue'] },
  PurchaseContractSelector: { template: '<div class="stub-contract-selector" />', props: ['modelValue', 'projectId'] },
}

let wrapper: any = null
beforeEach(() => { vi.clearAllMocks() })
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

async function mountInbound(records: any[] = []) {
  mockInboundPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(InboundView, { global: { plugins: [ElementPlus], stubs } })
  await flushPromises()
  return wrapper
}
async function mountOutbound(records: any[] = []) {
  mockOutboundPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(OutboundView, { global: { plugins: [ElementPlus], stubs } })
  await flushPromises()
  return wrapper
}

describe('material/inbound.vue B1 矩阵', () => {
  it('B-1-2 空 materialName 明细拦截：warning「请至少填写一条入库明细」，不发创建请求', async () => {
    const w = await mountInbound()
    const st = w.vm.$.setupState
    st.handleAdd()
    st.formData.projectId = 1
    st.formData.inboundDate = '2026-08-20'
    st.formData.details = [{ materialName: '', specification: '', unit: '', quantity: 1, unitPrice: 1 }]
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockWarning).toHaveBeenCalledWith('请至少填写一条入库明细')
    expect(mockCreateInbound).not.toHaveBeenCalled()
  })

  it('B-1-3 明细金额联动 = 数量×单价 toFixed(2)：2.5×100 → 250.00（模板表达式钉住）', async () => {
    await mountInbound()
    const row = { quantity: 2.5, unitPrice: 100 }
    expect(((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2)).toBe('250.00')
    expect(inboundSrc).toContain('((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2)')
  })

  it('B-1-4 明细数量/单价 min=0 precision=2 边界（源码钉住，负值拒绝、超 2 位截断）', async () => {
    await mountInbound()
    expect(inboundSrc).toContain('v-model="row.quantity" :min="0" :precision="2"')
    expect(inboundSrc).toContain('v-model="row.unitPrice" :min="0" :precision="2"')
  })

  it('B-1-5 切换项目后采购合同重置：handleProjectChange 清 contractId', async () => {
    const w = await mountInbound()
    const st = w.vm.$.setupState
    st.handleAdd()
    st.formData.contractId = 99
    st.handleProjectChange()
    expect(st.formData.contractId).toBeUndefined()
  })

  it('B-1-7 编辑回显明细：quantity/unitPrice Number 归一化回显', async () => {
    const w = await mountInbound()
    const st = w.vm.$.setupState
    mockInboundDetail.mockResolvedValue({ code: 200, data: { details: [{ materialName: '水泥', specification: 'P.O42.5', unit: '吨', quantity: '2.50', unitPrice: '100.00' }] } })
    await st.handleEdit({ id: 7, projectId: 1, contractId: 2, inboundDate: '2026-08-01', directOutbound: 0 })
    await flushPromises()
    expect(mockInboundDetail).toHaveBeenCalledWith(7)
    expect(st.formData.details[0].quantity).toBe(2.5)
    expect(st.formData.details[0].unitPrice).toBe(100)
    expect(st.isEdit).toBe(true)
  })

  it('B-1-8 编辑态明细守卫实证修正：2026-08-14 P0 盲点 10 修复后编辑模式同样拦截空明细（账本「绕过守卫」预期已过时）', async () => {
    const w = await mountInbound()
    const st = w.vm.$.setupState
    st.isEdit = true
    st.formData = { id: 7, projectId: 1, contractId: undefined, inboundDate: '2026-08-01', directOutbound: 0, details: [] }
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockWarning).toHaveBeenCalledWith('请至少填写一条入库明细')
    expect(mockUpdateInbound).not.toHaveBeenCalled()
    // 源码实证：守卫无 !isEdit 前缀（注释中的历史描述除外）
    expect(inboundSrc).toContain('if (formData.value.details.filter(d => d.materialName).length === 0)')
  })

  it('B-1-9 空 materialName 明细行被 payload 过滤：2 行 1 空名 → payload.details 仅 1 行', async () => {
    const w = await mountInbound()
    const st = w.vm.$.setupState
    st.handleAdd()
    st.formData.projectId = 1
    st.formData.inboundDate = '2026-08-20'
    st.formData.details = [
      { materialName: '钢筋', specification: '', unit: '吨', quantity: 1, unitPrice: 10 },
      { materialName: '', specification: '', unit: '', quantity: 2, unitPrice: 20 },
    ]
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockCreateInbound).toHaveBeenCalledTimes(1)
    const payload = (mockCreateInbound.mock.calls as any)[0][0]
    expect(payload.details).toHaveLength(1)
    expect(payload.details[0].materialName).toBe('钢筋')
  })

  it('B-1-12 状态列二分显示：非 APPROVED 一律「草稿」（SUBMITTED 亦显示草稿，与调拨四态不一致现状钉住）', async () => {
    const w = await mountInbound([
      { id: 1, inboundCode: 'A', status: 'SUBMITTED', totalAmount: 1 },
      { id: 2, inboundCode: 'B', status: 'APPROVED', totalAmount: 2 },
    ])
    const rows = w.findAll('.el-table__row')
    expect(rows[0].text()).toContain('草稿')
    expect(rows[1].text()).toContain('已审批')
    expect(inboundSrc).toContain("row.status === 'APPROVED' ? '已审批' : '草稿'")
  })
})

describe('material/outbound.vue B2 矩阵', () => {
  it('B-2-1 必填规则：projectId/outboundType 两条提示文案钉住', async () => {
    const w = await mountOutbound()
    const rules = w.vm.$.setupState.formRules
    expect(rules.projectId[0].message).toBe('请选择项目')
    expect(rules.outboundType[0].message).toBe('请选择出库类型')
  })

  it('B-2-2 空明细守卫：warning「请至少添加一条出库明细」，不发请求', async () => {
    const w = await mountOutbound()
    const st = w.vm.$.setupState
    st.handleAdd()
    st.formData.projectId = 1
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockWarning).toHaveBeenCalledWith('请至少添加一条出库明细')
    expect(mockCreateOutbound).not.toHaveBeenCalled()
  })

  it('B-2-3 出库类型 PICK/RETURN：新建默认 PICK，列表 RETURN 显示「退货」', async () => {
    const w = await mountOutbound([
      { id: 1, outboundType: 'RETURN', projectName: 'P1', status: 'DRAFT' },
      { id: 2, outboundType: 'PICK', projectName: 'P1', status: 'DRAFT' },
    ])
    const st = w.vm.$.setupState
    st.handleAdd()
    expect(st.formData.outboundType).toBe('PICK')
    const rows = w.findAll('.el-table__row')
    expect(rows[0].text()).toContain('退货')
    expect(rows[1].text()).toContain('领料')
  })

  it('B-2-6 编辑回显：outboundType 缺省回落 PICK', async () => {
    const w = await mountOutbound()
    const st = w.vm.$.setupState
    mockOutboundDetail.mockResolvedValue({ code: 200, data: { id: 9, projectId: 1, outboundType: null, details: [] } })
    await st.handleEdit({ id: 9 })
    await flushPromises()
    expect(st.formData.outboundType).toBe('PICK')
  })

  it('B-2-7 展开行明细子表：type="expand" 列含名称/规格/单位/数量/单价（源码钉住）', async () => {
    await mountOutbound()
    expect(outboundSrc).toContain('<el-table-column type="expand">')
    expect(outboundSrc).toContain('prop="materialName" label="材料名称"')
    expect(outboundSrc).toContain('prop="quantity" label="数量"')
    expect(outboundSrc).toContain('prop="unitPrice" label="单价(元)"')
  })

  it('B-2-10 分页请求口径对齐：本地状态 pageNum/pageSize，请求实参回落 page/size 与入库页一致（2026-08-21 缺陷#5 修复后翻正向）', async () => {
    const w = await mountOutbound()
    const st = w.vm.$.setupState
    expect(st.queryParams.pageNum).toBe(1)
    expect(st.queryParams.pageSize).toBe(10)
    expect(outboundSrc).toContain('v-model:current-page="queryParams.pageNum"')
    expect(inboundSrc).toContain('v-model:current-page="queryParams.page"')
    mockOutboundPage.mockClear()
    st.queryParams.pageNum = 2
    st.handleSearch()
    await flushPromises()
    expect(mockOutboundPage).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 10 }))
  })
})
