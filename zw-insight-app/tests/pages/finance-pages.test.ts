// @vitest-environment happy-dom
/**
 * finance 域页面级组件测试（2026-08-16 P3 方向2 批 6）
 *
 * 仅覆盖 pages.json 已注册的 4 个可达页：invoice-apply / payment-apply /
 * payment-received / reimbursement。
 * 豁免（已登记台账待决策）：invoice-received / other-payment /
 * personal-reimbursement / reserve-fund-apply / reserve-fund-return 五页
 * 未注册路由（孤儿页），其中 reserve-fund-apply 引用的 applyReserveFund、
 * reserve-fund-return 引用的 getPendingReserveFunds/returnReserveFund 在
 * api/common.ts 不存在（双端断链，与 material/return.vue 同类）。
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/common', () => ({
  getProjectList: vi.fn(),
  saveInvoiceApply: vi.fn(),
  savePaymentApply: vi.fn(),
  savePaymentReceived: vi.fn(),
  saveReimbursement: vi.fn(),
}))

import InvoiceApply from '@/pages/finance/invoice-apply.vue'
import PaymentApply from '@/pages/finance/payment-apply.vue'
import PaymentReceived from '@/pages/finance/payment-received.vue'
import Reimbursement from '@/pages/finance/reimbursement.vue'
import {
  getProjectList, saveInvoiceApply, savePaymentApply,
  savePaymentReceived, saveReimbursement,
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

describe('finance/invoice-apply.vue 开票申请页', () => {
  it('默认开票类型增值税专用发票 + 申请日期为今天；三段校验', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    const wrapper = mount(InvoiceApply)
    await flushPromises()

    expect(wrapper.vm.form.invoiceType).toBe('增值税专用发票')
    expect(wrapper.vm.form.applyDate).toBe(today())

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入开票金额' }))
    wrapper.vm.form.amount = '1000'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入购方名称' }))
    expect(vi.mocked(saveInvoiceApply)).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('提交载荷金额转 Number', async () => {
    vi.mocked(saveInvoiceApply).mockResolvedValue({ code: 200 })
    const wrapper = mount(InvoiceApply)
    await flushPromises()

    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    Object.assign(wrapper.vm.form, { amount: '8888.5', buyerName: '甲方公司' })
    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(vi.mocked(saveInvoiceApply)).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1, amount: 8888.5, invoiceType: '增值税专用发票', buyerName: '甲方公司',
    }))
    wrapper.unmount()
  })
})

describe('finance/payment-apply.vue 付款申请页', () => {
  it('三段校验 + 提交载荷默认银行转账', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(savePaymentApply).mockResolvedValue({ code: 200 })
    const wrapper = mount(PaymentApply)
    await flushPromises()

    expect(wrapper.vm.form.payMethod).toBe('银行转账')
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入付款金额' }))
    wrapper.vm.form.amount = '5000'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入收款方' }))

    wrapper.vm.form.payee = '供应商A'
    await wrapper.vm.handleSubmit()
    await flushPromises()
    expect(vi.mocked(savePaymentApply)).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1, amount: 5000, payee: '供应商A', payMethod: '银行转账',
    }))
    wrapper.unmount()
  })
})

describe('finance/payment-received.vue 收款登记页', () => {
  it('默认收款日期为今天；两段校验 + 提交载荷', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(savePaymentReceived).mockResolvedValue({ code: 200 })
    const wrapper = mount(PaymentReceived)
    await flushPromises()

    expect(wrapper.vm.form.receivedDate).toBe(today())

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入回款金额' }))

    wrapper.vm.form.amount = '20000'
    wrapper.vm.form.payer = '业主方'
    await wrapper.vm.handleSubmit()
    await flushPromises()
    expect(vi.mocked(savePaymentReceived)).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1, amount: 20000, payer: '业主方',
    }))
    wrapper.unmount()
  })
})

describe('finance/reimbursement.vue 报销申请页', () => {
  it('默认差旅费 + 今天；三段校验，发票数空值转 0', async () => {
    const toast = vi.fn()
    ;(getUni() as any).showToast = toast
    vi.mocked(saveReimbursement).mockResolvedValue({ code: 200 })
    const wrapper = mount(Reimbursement)
    await flushPromises()

    expect(wrapper.vm.form.expenseType).toBe('差旅费')
    expect(wrapper.vm.form.expenseDate).toBe(today())

    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请选择项目' }))
    wrapper.vm.selectProject({ id: 1, projectName: 'P1' })
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请输入报销金额' }))
    wrapper.vm.form.amount = '300'
    await wrapper.vm.handleSubmit()
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ title: '请填写费用说明' }))

    wrapper.vm.form.description = '出差打车'
    await wrapper.vm.handleSubmit()
    await flushPromises()
    expect(vi.mocked(saveReimbursement)).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1, amount: 300, expenseType: '差旅费', description: '出差打车', invoiceCount: 0,
    }))
    wrapper.unmount()
  })
})
