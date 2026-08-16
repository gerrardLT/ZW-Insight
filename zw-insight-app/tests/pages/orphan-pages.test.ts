// @vitest-environment happy-dom
/**
 * 孤儿页补全后的页面级组件测试（2026-08-16 用户决策 B：补功能+补测试）
 *
 * 覆盖补全后的 6 页：finance/invoice-received、other-payment、
 * personal-reimbursement、reserve-fund-apply（对齐 BizReserveFundApply）、
 * reserve-fund-return（status=APPROVED 拉未还清+BizReserveFundReturn 载荷）、
 * material/return（退货出库 outboundType=RETURN+details 契约）。
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
  getReserveFundApplyPage: vi.fn(),
  saveReserveFundApply: vi.fn(),
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
  saveOtherPayment, savePersonalReimbursement, saveReserveFundApply,
  saveReserveFundReturn, saveMaterialOutbound,
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

describe('finance/invoice-received.vue 收票登记页', () => {
  it('默认票种/日期今天；三段校验 + 提交载荷金额转 Number', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(saveInvoiceReceived).mockResolvedValue({ code: 200 })
    const wrapper = mount(InvoiceReceived)
    await flushPromises()

    expect(wrapper.vm.form.invoiceType).toBe('增值税专票')
    expect(wrapper.vm.form.invoiceDate).toBe(today())

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入发票号码' }))
    wrapper.vm.form.invoiceNo = 'FP-001'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入金额' }))

    wrapper.vm.form.amount = '6666'
    wrapper.vm.form.supplierName = '供应商B'
    await wrapper.vm.handleSubmit()
    await flushPromises()
    expect(vi.mocked(saveInvoiceReceived)).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1, invoiceNo: 'FP-001', supplierName: '供应商B', amount: 6666,
    }))
    wrapper.unmount()
  })
})

describe('finance/other-payment.vue 其他付款页', () => {
  it('两段校验 + 提交载荷透传整个表单', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(saveOtherPayment).mockResolvedValue({ code: 200 })
    const wrapper = mount(OtherPayment)
    await flushPromises()

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入金额' }))

    wrapper.vm.form.amount = '800'
    wrapper.vm.form.feeType = '检测费'
    await wrapper.vm.handleSubmit()
    await flushPromises()
    expect(vi.mocked(saveOtherPayment)).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1, amount: '800', feeType: '检测费',
    }))
    wrapper.unmount()
  })
})

describe('finance/personal-reimbursement.vue 个人报销页', () => {
  it('默认类别交通+日期今天；两段校验 + 载荷金额转 Number 带附件', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(savePersonalReimbursement).mockResolvedValue({ code: 200 })
    const wrapper = mount(PersonalReimbursement)
    await flushPromises()

    expect(wrapper.vm.form.category).toBe('交通')
    expect(wrapper.vm.form.expenseDate).toBe(today())

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入金额' }))
    wrapper.vm.form.amount = '120'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入报销事由' }))

    wrapper.vm.form.reason = '市内打车'
    wrapper.vm.attachments = ['file://r1.jpg']
    await wrapper.vm.handleSubmit()
    await flushPromises()
    expect(vi.mocked(savePersonalReimbursement)).toHaveBeenCalledWith(expect.objectContaining({
      amount: 120, reason: '市内打车', category: '交通', attachments: ['file://r1.jpg'],
    }))
    wrapper.unmount()
  })
})

describe('finance/reserve-fund-apply.vue 备用金申请页（对齐 BizReserveFundApply）', () => {
  it('三段校验（项目/申请人/金额>0）+ 载荷 projectId/applicant/applyDate/applyAmount', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(saveReserveFundApply).mockResolvedValue({ code: 200 })
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
    wrapper.unmount()
  })
})

describe('finance/reserve-fund-return.vue 备用金归还页（对齐 BizReserveFundReturn）', () => {
  function mockPendingList() {
    vi.mocked(getReserveFundApplyPage).mockResolvedValue({
      code: 200,
      data: { records: [
        { id: 11, applicant: '王五', applyAmount: 2000, returnedAmount: 500, applyDate: '2026-08-01', status: 'APPROVED' },
        { id: 12, applicant: '赵六', applyAmount: 1000, returnedAmount: 1000, applyDate: '2026-08-02', status: 'APPROVED' },
      ] },
    })
  }

  it('按 status=APPROVED 拉取并过滤未还清（returnedAmount<applyAmount）', async () => {
    mockPendingList()
    const wrapper = mount(ReserveFundReturn)
    await flushPromises()

    expect(vi.mocked(getReserveFundApplyPage)).toHaveBeenCalledWith({ page: 1, size: 100, status: 'APPROVED' })
    // id=12 已还清被过滤
    expect(wrapper.vm.pendingList.map((f: any) => f.id)).toEqual([11])
    wrapper.unmount()
  })

  it('选中自动回填剩余金额；超额拦截；载荷 reserveApplyId/returnAmount/returnDate', async () => {
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
    expect(wrapper.vm.form.returnAmount).toBe('1500') // 2000-500

    // 超额拦截
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
  it('校验链：项目/材料/数量>0；退货退款必填采购合同ID', async () => {
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
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '退货退款需填写采购合同ID' }))
    expect(vi.mocked(saveMaterialOutbound)).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('仅退货：载荷 outboundType=RETURN + details，contractId=null', async () => {
    vi.mocked(saveMaterialOutbound).mockResolvedValue({ code: 200 })
    const wrapper = mount(MaterialReturn)
    await flushPromises()

    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    Object.assign(wrapper.vm.form, { materialName: '钢筋', specification: 'HRB400', unit: '吨', quantity: '2', outboundDate: '2026-08-16' })
    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(saveMaterialOutbound)).toHaveBeenCalledWith({
      projectId: 1,
      outboundType: 'RETURN',
      outboundDate: '2026-08-16',
      returnType: 'RETURN_ONLY',
      contractId: null,
      details: [{ materialName: '钢筋', specification: 'HRB400', unit: '吨', quantity: 2 }],
    })
    wrapper.unmount()
  })

  it('退货退款：contractId 转 Number 随载荷提交', async () => {
    vi.mocked(saveMaterialOutbound).mockResolvedValue({ code: 200 })
    const wrapper = mount(MaterialReturn)
    await flushPromises()

    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    Object.assign(wrapper.vm.form, {
      materialName: '钢筋', quantity: '1', returnType: 'RETURN_REFUND', contractId: '555', outboundDate: '2026-08-16',
    })
    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(saveMaterialOutbound)).toHaveBeenCalledWith(expect.objectContaining({
      outboundType: 'RETURN', returnType: 'RETURN_REFUND', contractId: 555,
    }))
    wrapper.unmount()
  })
})
