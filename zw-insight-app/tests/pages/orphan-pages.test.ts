// @vitest-environment happy-dom
/**
 * 孤儿页补全后的页面级组件测试（2026-08-16 决策 B；评审修复后按后端实体契约翻转断言）
 *
 * 断言基准为后端真实实体字段（Jackson 静默丢弃未知字段，钉错字段=假安心）：
 * - BizInvoiceReceived：projectId/supplierName/invoiceAmount/taxRate/invoiceDate
 * - BizOtherPayment：projectId/payerName/paymentAmount/paymentDate/remark
 * - BizPersonalReimbursement：totalAmount/reimbursementDate/remark（两段式 save→submit）
 * - BizReserveFundApply：projectId/applicant/applyDate/applyAmount（两段式 save→submit）
 * - BizReserveFundReturn：reserveApplyId/returnAmount/returnDate（待归还口径扣 offsetAmount）
 * - BizMaterialOutbound(RETURN)：details 数组含 unitPrice（退款金额=数量×单价）
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/common', () => ({
  getProjectList: vi.fn(),
  getMaterialDict: vi.fn(),
  saveInvoiceReceived: vi.fn(),
  saveOtherPayment: vi.fn(),
  savePersonalReimbursement: vi.fn(),
  submitPersonalReimbursement: vi.fn(),
  getReserveFundApplyPage: vi.fn(),
  saveReserveFundApply: vi.fn(),
  submitReserveFundApply: vi.fn(),
  saveReserveFundReturn: vi.fn(),
  saveMaterialOutbound: vi.fn(),
}))

import InvoiceReceived from '@/pages/finance/invoice-received.vue'
import OtherPayment from '@/pages/finance/other-payment.vue'
import PersonalReimbursement from '@/pages/finance/personal-reimbursement.vue'
import ReserveFundApply from '@/pages/finance/reserve-fund-apply.vue'
import ReserveFundReturn from '@/pages/finance/reserve-fund-return.vue'
import MaterialReturn from '@/pages/material/return.vue'
import {
  getProjectList, getReserveFundApplyPage, saveInvoiceReceived,
  saveOtherPayment, savePersonalReimbursement, submitPersonalReimbursement,
  saveReserveFundApply, submitReserveFundApply, saveReserveFundReturn,
  saveMaterialOutbound,
} from '@/api/common'
import { resetUniStorage, getUni } from '../setup'

beforeEach(() => {
  resetUniStorage()
  setActivePinia(createPinia())
  vi.clearAllMocks()
  vi.mocked(getProjectList).mockResolvedValue({ code: 200, data: { records: [{ id: 1, projectName: 'P1' }] } })
  ;(getUni() as any).navigateBack = vi.fn()
})

function today() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

describe('finance/invoice-received.vue 收票登记页（对齐 BizInvoiceReceived）', () => {
  it('默认收票日期今天；三段校验 + 载荷 invoiceAmount/supplierName/taxRate', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(saveInvoiceReceived).mockResolvedValue({ code: 200 })
    const wrapper = mount(InvoiceReceived)
    await flushPromises()

    expect(wrapper.vm.form.invoiceDate).toBe(today())

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入供应商名称' }))
    wrapper.vm.form.supplierName = '供应商B'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '收票金额必须大于0' }))

    wrapper.vm.form.invoiceAmount = '6666'
    wrapper.vm.form.taxRate = '13'
    await wrapper.vm.handleSubmit()
    await flushPromises()
    expect(vi.mocked(saveInvoiceReceived)).toHaveBeenCalledWith({
      projectId: 1, supplierName: '供应商B', invoiceAmount: 6666, taxRate: 13, invoiceDate: today(),
    })
    wrapper.unmount()
  })

  it('金额非数字（NaN）被前端守卫拦截', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(InvoiceReceived)
    await flushPromises()
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    wrapper.vm.form.supplierName = '供应商B'
    wrapper.vm.form.invoiceAmount = 'abc'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '收票金额必须大于0' }))
    expect(vi.mocked(saveInvoiceReceived)).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})

describe('finance/other-payment.vue 其他付款页（对齐 BizOtherPayment）', () => {
  it('三段校验 + 载荷 paymentAmount/payerName/paymentDate（封账校验依赖日期）', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(saveOtherPayment).mockResolvedValue({ code: 200 })
    const wrapper = mount(OtherPayment)
    await flushPromises()

    expect(wrapper.vm.form.paymentDate).toBe(today())

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入付款人' }))
    wrapper.vm.form.payerName = '财务部'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '付款金额必须大于0' }))

    wrapper.vm.form.paymentAmount = '800'
    wrapper.vm.form.remark = '检测费'
    await wrapper.vm.handleSubmit()
    await flushPromises()
    expect(vi.mocked(saveOtherPayment)).toHaveBeenCalledWith({
      projectId: 1, payerName: '财务部', paymentAmount: 800, paymentDate: today(), remark: '检测费',
    })
    wrapper.unmount()
  })
})

describe('finance/personal-reimbursement.vue 个人报销页（对齐 BizPersonalReimbursement，两段式）', () => {
  it('金额校验 + 载荷 totalAmount/reimbursementDate，save 后链式 submit', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(savePersonalReimbursement).mockResolvedValue({ code: 200, data: 77 })
    vi.mocked(submitPersonalReimbursement).mockResolvedValue({ code: 200 })
    const wrapper = mount(PersonalReimbursement)
    await flushPromises()

    expect(wrapper.vm.form.reimbursementDate).toBe(today())

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入报销金额' }))

    wrapper.vm.form.totalAmount = '120'
    wrapper.vm.form.remark = '市内打车'
    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(savePersonalReimbursement)).toHaveBeenCalledWith({
      totalAmount: 120, reimbursementDate: today(), remark: '市内打车',
    })
    // 两段式：save 返回 id 后必须链式 submit，否则记录永久 DRAFT
    expect(vi.mocked(submitPersonalReimbursement)).toHaveBeenCalledWith(77)
    wrapper.unmount()
  })
})

describe('finance/reserve-fund-apply.vue 备用金申请页（对齐 BizReserveFundApply，两段式）', () => {
  it('三段校验 + 载荷，save 后链式 submit（否则归还页 APPROVED 过滤永不可见）', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(saveReserveFundApply).mockResolvedValue({ code: 200, data: 99 })
    vi.mocked(submitReserveFundApply).mockResolvedValue({ code: 200 })
    const wrapper = mount(ReserveFundApply)
    await flushPromises()

    expect(wrapper.vm.form.applyDate).toBe(today())

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入申请人' }))
    wrapper.vm.form.applicant = '王五'
    wrapper.vm.form.applyAmount = '0'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '申请金额必须大于0' }))

    wrapper.vm.form.applyAmount = '2000'
    await wrapper.vm.handleSubmit()
    await flushPromises()
    expect(vi.mocked(saveReserveFundApply)).toHaveBeenCalledWith({
      projectId: 1, applicant: '王五', applyDate: today(), applyAmount: 2000,
    })
    expect(vi.mocked(submitReserveFundApply)).toHaveBeenCalledWith(99)
    wrapper.unmount()
  })
})

describe('finance/reserve-fund-return.vue 备用金归还页（对齐 BizReserveFundReturn）', () => {
  function mockPendingList() {
    vi.mocked(getReserveFundApplyPage).mockResolvedValue({
      code: 200,
      data: { total: 3, records: [
        { id: 11, applicant: '王五', applyAmount: 2000, returnedAmount: 500, offsetAmount: 0, applyDate: '2026-08-01', status: 'APPROVED' },
        { id: 12, applicant: '赵六', applyAmount: 1000, returnedAmount: 1000, offsetAmount: 0, applyDate: '2026-08-02', status: 'APPROVED' },
        // 已通过报销冲抵结清（returned+offset==apply），不应列入未结清
        { id: 13, applicant: '钱七', applyAmount: 800, returnedAmount: 300, offsetAmount: 500, applyDate: '2026-08-03', status: 'APPROVED' },
      ] },
    })
  }

  it('按 status=APPROVED 拉取并过滤未结清（returned+offset<apply，与后端口径一致）', async () => {
    mockPendingList()
    const wrapper = mount(ReserveFundReturn)
    await flushPromises()

    expect(vi.mocked(getReserveFundApplyPage)).toHaveBeenCalledWith({ page: 1, size: 100, status: 'APPROVED' })
    expect(wrapper.vm.pendingList.map((f: any) => f.id)).toEqual([11])
    wrapper.unmount()
  })

  it('选中回填剩余（扣 offsetAmount）；超额拦截；载荷 reserveApplyId/returnAmount/returnDate', async () => {
    mockPendingList()
    vi.mocked(saveReserveFundReturn).mockResolvedValue({ code: 200 })
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(ReserveFundReturn)
    await flushPromises()

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择备用金记录' }))

    wrapper.vm.selectFund(wrapper.vm.pendingList[0])
    expect(wrapper.vm.form.reserveApplyId).toBe(11)
    expect(wrapper.vm.form.returnAmount).toBe('1500') // 2000-500-0

    wrapper.vm.form.returnAmount = '2000'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '归还金额不能超过剩余未还金额' }))
    expect(vi.mocked(saveReserveFundReturn)).not.toHaveBeenCalled()

    wrapper.vm.form.returnAmount = '1500'
    await wrapper.vm.handleSubmit()
    await flushPromises()
    expect(vi.mocked(saveReserveFundReturn)).toHaveBeenCalledWith({
      reserveApplyId: 11, returnAmount: 1500, returnDate: today(),
    })
    wrapper.unmount()
  })
})

describe('material/return.vue 材料退货页（退货出库 RETURN 契约）', () => {
  it('校验链：项目/材料/数量>0；退货退款需有效合同ID与入库单价', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(MaterialReturn)
    await flushPromises()

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入材料名称' }))
    wrapper.vm.form.materialName = '钢筋'
    wrapper.vm.form.quantity = '0'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入退货数量' }))

    wrapper.vm.form.quantity = '2'
    wrapper.vm.form.returnType = 'RETURN_REFUND'
    wrapper.vm.form.contractId = 'abc' // 非数字：NaN 会序列化为 null 静默退化，必须拦截
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '退货退款需填写有效的采购合同ID' }))

    wrapper.vm.form.contractId = '555'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '退货退款需填写入库单价（用于计算退款金额）' }))
    expect(vi.mocked(saveMaterialOutbound)).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('仅退货：载荷 outboundType=RETURN + details 含 unitPrice，contractId=null', async () => {
    vi.mocked(saveMaterialOutbound).mockResolvedValue({ code: 200 })
    const wrapper = mount(MaterialReturn)
    await flushPromises()

    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    Object.assign(wrapper.vm.form, { materialName: '钢筋', specification: 'HRB400', unit: '吨', quantity: '2', unitPrice: '4000', outboundDate: '2026-08-16' })
    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(saveMaterialOutbound)).toHaveBeenCalledWith({
      projectId: 1,
      outboundType: 'RETURN',
      outboundDate: '2026-08-16',
      returnType: 'RETURN_ONLY',
      contractId: null,
      details: [{ materialName: '钢筋', specification: 'HRB400', unit: '吨', quantity: 2, unitPrice: 4000 }],
    })
    wrapper.unmount()
  })

  it('退货退款：contractId 转 Number，unitPrice 随明细提交（退款金额=数量×单价）', async () => {
    vi.mocked(saveMaterialOutbound).mockResolvedValue({ code: 200 })
    const wrapper = mount(MaterialReturn)
    await flushPromises()

    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    Object.assign(wrapper.vm.form, {
      materialName: '钢筋', quantity: '1', unitPrice: '4000',
      returnType: 'RETURN_REFUND', contractId: '555', outboundDate: '2026-08-16',
    })
    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(saveMaterialOutbound)).toHaveBeenCalledWith(expect.objectContaining({
      outboundType: 'RETURN', returnType: 'RETURN_REFUND', contractId: 555,
      details: [{ materialName: '钢筋', specification: '', unit: '', quantity: 1, unitPrice: 4000 }],
    }))
    wrapper.unmount()
  })
})
