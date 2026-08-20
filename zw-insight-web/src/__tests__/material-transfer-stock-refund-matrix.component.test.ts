/**
 * M3 账本补齐：B-1 材料域调拨/库存/退货页矩阵用例
 * views/material/transfer.vue（B3）+ stock.vue（B4）+ refund.vue（B5）
 *
 * @matrix B-3-1/B-3-2/B-3-4/B-3-5/B-3-6/B-3-7/B-3-8/B-3-9/B-3-10
 * @matrix B-4-1/B-4-2/B-4-3/B-4-5/B-4-6/B-4-7/B-4-8
 * @matrix B-5-1/B-5-4/B-5-5/B-5-6
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const {
  mockTransferPage, mockTransferDetail, mockCreateTransfer, mockSubmitTransfer,
  mockDeleteTransfer, mockStockPage, mockRefundPage, mockRefundDetail,
  mockProjectPage, mockWarning, mockSuccess, mockConfirm,
} = vi.hoisted(() => ({
  mockTransferPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockTransferDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockCreateTransfer: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSubmitTransfer: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDeleteTransfer: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockStockPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockRefundPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockRefundDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockProjectPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockWarning: vi.fn(),
  mockSuccess: vi.fn(),
  mockConfirm: vi.fn(async () => 'confirm'),
}))

vi.mock('@/api/material', () => ({
  getMaterialTransferPage: mockTransferPage,
  getMaterialTransferDetail: mockTransferDetail,
  createMaterialTransfer: mockCreateTransfer,
  updateMaterialTransfer: vi.fn(async (): Promise<any> => ({ code: 200 })),
  submitMaterialTransfer: mockSubmitTransfer,
  deleteMaterialTransfer: mockDeleteTransfer,
  getMaterialStockPage: mockStockPage,
  getMaterialRefundPage: mockRefundPage,
  getMaterialRefundDetail: mockRefundDetail,
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

import TransferView from '@/views/material/transfer.vue'
import StockView from '@/views/material/stock.vue'
import RefundView from '@/views/material/refund.vue'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __testDir = dirname(fileURLToPath(import.meta.url))
const transferSrc = readFileSync(resolve(__testDir, '../views/material/transfer.vue'), 'utf-8')
const stockSrc = readFileSync(resolve(__testDir, '../views/material/stock.vue'), 'utf-8')
const refundSrc = readFileSync(resolve(__testDir, '../views/material/refund.vue'), 'utf-8')

let wrapper: any = null
beforeEach(() => { vi.clearAllMocks() })
afterEach(() => {
  if (wrapper) { try { wrapper.unmount() } catch { /* 忽略 */ } wrapper = null }
})

async function mountTransfer(records: any[] = []) {
  mockTransferPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(TransferView, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}
async function mountStock(records: any[] = []) {
  mockStockPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(StockView, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}
async function mountRefund(records: any[] = []) {
  mockRefundPage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
  wrapper = mount(RefundView, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('material/transfer.vue B3 矩阵', () => {
  it('B-3-1 必填规则：fromProjectId/toProjectId 两条提示文案钉住', async () => {
    const w = await mountTransfer()
    const rules = w.vm.$.setupState.formRules
    expect(rules.fromProjectId[0].message).toBe('请选择调出项目')
    expect(rules.toProjectId[0].message).toBe('请选择调入项目')
  })

  it('B-3-2 同项目调拨前端拦截：warning「调出项目与调入项目不能相同」，不发请求', async () => {
    const w = await mountTransfer()
    const st = w.vm.$.setupState
    st.handleAdd()
    st.formData.fromProjectId = 1
    st.formData.toProjectId = 1
    st.formData.details = [{ materialName: '钢筋', specification: '', unit: '吨', quantity: 1, unitPrice: 1 }]
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockWarning).toHaveBeenCalledWith('调出项目与调入项目不能相同')
    expect(mockCreateTransfer).not.toHaveBeenCalled()
  })

  it('B-3-4 空明细守卫：warning「请至少添加一条调拨明细」', async () => {
    const w = await mountTransfer()
    const st = w.vm.$.setupState
    st.handleAdd()
    st.formData.fromProjectId = 1
    st.formData.toProjectId = 2
    await st.handleFormSubmit()
    await flushPromises()
    expect(mockWarning).toHaveBeenCalledWith('请至少添加一条调拨明细')
    expect(mockCreateTransfer).not.toHaveBeenCalled()
  })

  it('B-3-5 四态状态标签：草稿/审批中/已审批/已驳回 标签与颜色', async () => {
    const w = await mountTransfer()
    const st = w.vm.$.setupState
    expect(st.getStatusLabel('DRAFT')).toBe('草稿')
    expect(st.getStatusLabel('SUBMITTED')).toBe('审批中')
    expect(st.getStatusLabel('APPROVED')).toBe('已审批')
    expect(st.getStatusLabel('REJECTED')).toBe('已驳回')
    expect(st.getStatusType('SUBMITTED')).toBe('warning')
    expect(st.getStatusType('APPROVED')).toBe('success')
    expect(st.getStatusType('REJECTED')).toBe('danger')
  })

  it('B-3-6 操作按钮条件渲染：DRAFT 三按钮、REJECTED 仅提交、SUBMITTED/APPROVED 无按钮', async () => {
    const w = await mountTransfer([
      { id: 1, status: 'DRAFT', fromProjectName: 'A', toProjectName: 'B' },
      { id: 2, status: 'REJECTED', fromProjectName: 'A', toProjectName: 'B' },
      { id: 3, status: 'SUBMITTED', fromProjectName: 'A', toProjectName: 'B' },
      { id: 4, status: 'APPROVED', fromProjectName: 'A', toProjectName: 'B' },
    ])
    const rows = w.findAll('.el-table__row')
    const btnText = (r: any) => r.findAll('button').map((b: any) => b.text()).join(' ')
    expect(btnText(rows[0])).toContain('编辑')
    expect(btnText(rows[0])).toContain('提交')
    expect(btnText(rows[0])).toContain('删除')
    expect(btnText(rows[1])).toContain('提交')
    expect(btnText(rows[1])).not.toContain('编辑')
    expect(btnText(rows[1])).not.toContain('删除')
    expect(btnText(rows[2])).not.toContain('提交')
    expect(btnText(rows[3])).not.toContain('提交')
  })

  it('B-3-7 提交成功文案「已提交审批，审批通过后变更库存」', async () => {
    const w = await mountTransfer()
    await w.vm.$.setupState.handleSubmit({ id: 5 })
    await flushPromises()
    expect(mockConfirm).toHaveBeenCalled()
    expect(mockSubmitTransfer).toHaveBeenCalledWith(5)
    expect(mockSuccess).toHaveBeenCalledWith('已提交审批，审批通过后变更库存')
  })

  it('B-3-8 调出/调入项目筛选参数透传 loadData', async () => {
    const w = await mountTransfer()
    const st = w.vm.$.setupState
    st.queryParams.fromProjectId = 3
    st.queryParams.toProjectId = 4
    mockTransferPage.mockClear()
    st.handleSearch()
    await flushPromises()
    const params = (mockTransferPage.mock.calls as any)[0][0]
    expect(params.fromProjectId).toBe(3)
    expect(params.toProjectId).toBe(4)
    expect(st.queryParams.pageNum).toBe(1)
  })

  it('B-3-9 编辑回显明细：details 原样回显', async () => {
    const w = await mountTransfer()
    const st = w.vm.$.setupState
    const details = [{ materialName: '水泥', specification: 'P.O42.5', unit: '吨', quantity: 3, unitPrice: 100 }]
    mockTransferDetail.mockResolvedValue({ code: 200, data: { id: 8, fromProjectId: 1, toProjectId: 2, transferDate: '2026-08-01', details } })
    await st.handleEdit({ id: 8 })
    await flushPromises()
    expect(st.formData.details).toEqual(details)
    expect(st.formData.fromProjectId).toBe(1)
  })

  it('B-3-10 调拨数量 min=0 precision=2 边界（源码钉住：0 允许、负数拒绝）', async () => {
    await mountTransfer()
    expect(transferSrc).toContain('v-model="row.quantity" :min="0" :precision="2"')
    expect(transferSrc).toContain('v-model="row.unitPrice" :min="0" :precision="2"')
  })
})

describe('material/stock.vue B4 矩阵', () => {
  it('B-4-1/B-4-2 库存预警判定：stockQuantity<=minStock 显示「库存不足」；minStock=null 恒「正常」', async () => {
    const w = await mountStock([
      { materialName: '钢筋', stockQuantity: 5, minStock: 10 },
      { materialName: '水泥', stockQuantity: 0, minStock: null },
      { materialName: '砂石', stockQuantity: 10, minStock: 10 },
    ])
    const rows = w.findAll('.el-table__row')
    expect(rows[0].text()).toContain('库存不足')
    expect(rows[1].text()).toContain('正常')
    expect(rows[1].text()).not.toContain('库存不足')
    // 边界相等亦告警（<=）
    expect(rows[2].text()).toContain('库存不足')
    expect(stockSrc).toContain('row.minStock != null && row.stockQuantity <= row.minStock')
  })

  it('B-4-3 warning=NORMAL/LOW 筛选选项与参数透传', async () => {
    const w = await mountStock()
    const st = w.vm.$.setupState
    expect(stockSrc).toContain('<el-option label="正常" value="NORMAL" />')
    expect(stockSrc).toContain('<el-option label="不足" value="LOW" />')
    st.queryParams.warning = 'LOW'
    mockStockPage.mockClear()
    st.handleSearch()
    await flushPromises()
    expect((mockStockPage.mock.calls as any)[0][0].warning).toBe('LOW')
  })

  it('B-4-5 只读页面无增删改入口（源码无新增/编辑/删除按钮）', async () => {
    const w = await mountStock()
    expect(w.findAll('button').map((b: any) => b.text()).join()).not.toContain('新增')
    expect(stockSrc).not.toContain('新增')
    expect(stockSrc).not.toContain('handleEdit')
    expect(stockSrc).not.toContain('handleDelete')
  })

  it('B-4-6 重置清空三条件并重新加载', async () => {
    const w = await mountStock()
    const st = w.vm.$.setupState
    st.queryParams.materialName = '钢筋'
    st.queryParams.projectName = '滨江'
    st.queryParams.warning = 'LOW'
    st.queryParams.pageNum = 3
    mockStockPage.mockClear()
    st.handleReset()
    await flushPromises()
    expect(st.queryParams.materialName).toBe('')
    expect(st.queryParams.projectName).toBe('')
    expect(st.queryParams.warning).toBe('')
    expect(st.queryParams.pageNum).toBe(1)
    expect(mockStockPage).toHaveBeenCalled()
  })

  it('B-4-7 分页参数 pageNum/pageSize（后端 page/size，参数被忽略——失配缺陷现状钉住）', async () => {
    const w = await mountStock()
    const st = w.vm.$.setupState
    expect(st.queryParams.pageNum).toBe(1)
    expect(st.queryParams.pageSize).toBe(10)
    expect(stockSrc).toContain('v-model:current-page="queryParams.pageNum"')
  })

  it('B-4-8 空结果渲染：无匹配记录表格空态不报错', async () => {
    const w = await mountStock([])
    expect(w.findAll('.el-table__row')).toHaveLength(0)
    expect(w.html()).toContain('el-table__empty')
  })
})

describe('material/refund.vue B5 矩阵', () => {
  it('B-5-1 只读守卫：仅 alert 说明+明细按钮，无增删改入口', async () => {
    const w = await mountRefund([{ id: 1, refundCode: 'TK1', status: 'PENDING' }])
    expect(refundSrc).toContain('退款记录由退货出库审批通过后自动生成，此处仅供查询')
    expect(refundSrc).not.toContain('新增')
    expect(refundSrc).not.toContain('handleEdit')
    expect(refundSrc).not.toContain('handleDelete')
    const rowBtns = w.findAll('.el-table__row button').map((b: any) => b.text()).join(' ')
    expect(rowBtns).toBe('明细')
  })

  it('B-5-4 明细弹窗字段完整性：退款单号/金额/合同ID/关联出库单ID/原因+明细表', async () => {
    const w = await mountRefund([{ id: 6, refundCode: 'TK6', status: 'PENDING' }])
    mockRefundDetail.mockResolvedValue({ code: 200, data: { refundCode: 'TK6', refundAmount: 300, contractId: 11, outboundId: 22, refundReason: '质量问题', details: [{ materialName: '钢筋', quantity: 3, unitPrice: 100, amount: 300 }] } })
    await w.vm.$.setupState.handleViewDetail({ id: 6 })
    await flushPromises()
    expect(mockRefundDetail).toHaveBeenCalledWith(6)
    expect(w.vm.$.setupState.detailVisible).toBe(true)
    expect(refundSrc).toContain('label="退款单号"')
    expect(refundSrc).toContain('label="采购合同ID"')
    expect(refundSrc).toContain('label="关联出库单ID"')
    expect(refundSrc).toContain('label="退款原因"')
  })

  it('B-5-5 状态四态映射：草稿/待审批/已通过/已驳回', async () => {
    const w = await mountRefund()
    const st = w.vm.$.setupState
    expect(st.getStatusLabel('DRAFT')).toBe('草稿')
    expect(st.getStatusLabel('PENDING')).toBe('待审批')
    expect(st.getStatusLabel('APPROVED')).toBe('已通过')
    expect(st.getStatusLabel('REJECTED')).toBe('已驳回')
    expect(st.getStatusType('PENDING')).toBe('warning')
  })

  it('B-5-6 formatMoney 空值容错：null/undefined → 「-」，0 → 「0.00」，千分位', async () => {
    const w = await mountRefund()
    const st = w.vm.$.setupState
    expect(st.formatMoney(null)).toBe('-')
    expect(st.formatMoney(undefined)).toBe('-')
    expect(st.formatMoney(0)).toBe('0.00')
    expect(st.formatMoney(1234567.8)).toBe('1,234,567.80')
  })
})
