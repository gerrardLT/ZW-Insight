/**
 * 财务管理四页矩阵组件测试（账本全量补齐 M6 C-1，2026-08）
 *
 * 覆盖账本 C1 开票申请 / C6 其他费用付款 / C7 项目报销 / C11 竣工结算 的纯前端守卫增量。
 * 与既有测试边界（不重复）：
 *   - finance-pages-2.component.test.ts：收票/回款/报销/结算/税率 基础加载与调用
 *   - invoice-apply/invoice-summary/other-payment/payment-apply/personal-reimbursement/
 *     reserve-fund/retention/tax-rate-selector 各单页既有测试
 *   - E2E finance-write.spec.ts：真实模式写路径（2026-08-20 已解除 5 处条件 skip）
 *
 * @matrix C-1-3/C-1-6/C-1-7/C-1-8/C-1-10
 * @matrix C-6-2/C-6-3/C-6-4/C-6-5
 * @matrix C-7-1/C-7-4
 * @matrix C-11-2/C-11-3/C-11-4/C-11-5/C-11-6
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const invoiceApplySrc = readFileSync(resolve(__dirname, '../views/finance/invoice-apply.vue'), 'utf-8')
const otherPaymentSrc = readFileSync(resolve(__dirname, '../views/finance/other-payment.vue'), 'utf-8')
const reimbSrc = readFileSync(resolve(__dirname, '../views/finance/project-reimbursement.vue'), 'utf-8')
const settleSrc = readFileSync(resolve(__dirname, '../views/finance/settlement/index.vue'), 'utf-8')

const mocks = vi.hoisted(() => ({
  mockInvoiceApplyPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockInvoiceApplyDetail: vi.fn(async (): Promise<any> => ({ code: 200, data: null })),
  mockInvoiceApplyCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockInvoiceApplyDelete: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockInvoiceApplySubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockOtherPaymentPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockOtherPaymentCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockReimbPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockReimbCreate: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockReimbSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSettlePage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockSettleCreate: vi.fn(async (): Promise<any> => ({ code: 200, data: { id: 1 } })),
  mockSettleSubmit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockSettleExport: vi.fn(async (): Promise<any> => 'xlsxBinary'),
  mockProjectList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockPush: vi.fn(),
  mockSuccess: vi.fn(),
  mockWarning: vi.fn(),
  mockError: vi.fn(),
  mockInfo: vi.fn(),
}))

vi.mock('@/api/finance', () => ({
  getInvoiceApplyPage: mocks.mockInvoiceApplyPage,
  getInvoiceApplyDetail: mocks.mockInvoiceApplyDetail,
  createInvoiceApply: mocks.mockInvoiceApplyCreate,
  deleteInvoiceApply: mocks.mockInvoiceApplyDelete,
  submitInvoiceApply: mocks.mockInvoiceApplySubmit,
  getOtherPaymentPage: mocks.mockOtherPaymentPage,
  createOtherPayment: mocks.mockOtherPaymentCreate,
  getProjectReimbursementPage: mocks.mockReimbPage,
  createProjectReimbursement: mocks.mockReimbCreate,
  submitProjectReimbursement: mocks.mockReimbSubmit,
}))
vi.mock('@/api/settlement', () => ({
  getSettlementPage: mocks.mockSettlePage,
  createSettlement: mocks.mockSettleCreate,
  submitSettlement: mocks.mockSettleSubmit,
  exportSettlement: mocks.mockSettleExport,
}))
vi.mock('@/api/project', () => ({
  getProjectList: mocks.mockProjectList,
}))
vi.mock('vue-router', async (importOriginal) => {
  const actual: any = await importOriginal()
  return { ...actual, useRouter: () => ({ push: mocks.mockPush }) }
})
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: mocks.mockSuccess, warning: mocks.mockWarning, error: mocks.mockError, info: mocks.mockInfo },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') },
  }
})

import InvoiceApply from '@/views/finance/invoice-apply.vue'
import OtherPayment from '@/views/finance/other-payment.vue'
import ProjectReimbursement from '@/views/finance/project-reimbursement.vue'
import Settlement from '@/views/finance/settlement/index.vue'
import ElementPlus from 'element-plus'

const globalCfg = {
  plugins: [ElementPlus],
  stubs: { TaxRateSelector: true, ContractSelector: true },
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.mockInvoiceApplyPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
  mocks.mockOtherPaymentPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
  mocks.mockReimbPage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
  mocks.mockSettlePage.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
  mocks.mockSettleExport.mockResolvedValue('xlsxBinary')
})

afterEach(() => {
  document.body.innerHTML = ''
})

describe('finance/invoice-apply.vue 开票申请（C1）', () => {
  async function mountPage() {
    const w = mount(InvoiceApply, { global: globalCfg })
    await flushPromises()
    return w
  }

  it('@matrix C-1-3 金额 input-number min=0 precision=2 源码钉住（负值拦截）', () => {
    expect(invoiceApplySrc).toContain('v-model="formData.invoiceAmount" :min="0" :precision="2"')
  })

  it('@matrix C-1-6 提交按钮仅 DRAFT/REJECTED 渲染（非法状态无提交入口）', () => {
    expect(invoiceApplySrc).toContain('v-if="row.status === \'DRAFT\' || row.status === \'REJECTED\'" link type="success" @click="handleSubmitApply(row)"')
  })

  it('@matrix C-1-7 删除按钮仅 DRAFT 渲染（已审批单无删除入口）', () => {
    expect(invoiceApplySrc).toContain('v-if="row.status === \'DRAFT\'" link type="danger" @click="handleDelete(row)"')
  })

  it('@matrix C-1-8 查看详情为真实接口：handleView 调 getInvoiceApplyDetail(row.id) 打开抽屉（2026-08-28 补全，原「开发中」stub 已移除）', async () => {
    mocks.mockInvoiceApplyDetail.mockResolvedValueOnce({ code: 200, data: { id: 1, invoiceAmount: 100, status: 'DRAFT' } })
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    await st.handleView({ id: 1 })
    await flushPromises()
    expect(mocks.mockInvoiceApplyDetail).toHaveBeenCalledWith(1)
    expect(st.detailVisible).toBe(true)
    expect(st.detailData.id).toBe(1)
    expect(mocks.mockInfo).not.toHaveBeenCalledWith('查看详情功能开发中')
  })

  it('@matrix C-1-10 statusMap：SUBMITTED 与 APPROVING 均映射「审批中」warning', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    expect(st.getStatusLabel('SUBMITTED')).toBe('审批中')
    expect(st.getStatusLabel('APPROVING')).toBe('审批中')
    expect(st.getStatusType('SUBMITTED')).toBe('warning')
    expect(st.getStatusType('APPROVING')).toBe('warning')
    expect(st.getStatusLabel('DRAFT')).toBe('草稿')
    expect(st.getStatusLabel('APPROVED')).toBe('已通过')
    expect(st.getStatusLabel('REJECTED')).toBe('已驳回')
  })

  it('@matrix C-1-6 行提交：confirm → submitInvoiceApply(row.id) → 提示「已提交审批，审批通过后生效」', async () => {
    const w = await mountPage()
    await w.vm.$.setupState.handleSubmitApply({ id: 33 })
    await flushPromises()
    expect(mocks.mockInvoiceApplySubmit).toHaveBeenCalledWith(33)
    expect(mocks.mockSuccess).toHaveBeenCalledWith('已提交审批，审批通过后生效')
  })
})

describe('finance/other-payment.vue 其他费用付款（C6）', () => {
  async function mountPage() {
    const w = mount(OtherPayment, { global: globalCfg })
    await flushPromises()
    return w
  }

  it('@matrix C-6-2 必填四条 projectId/payerName/paymentAmount/paymentDate', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    expect(Object.keys(st.formRules)).toEqual(['projectId', 'payerName', 'paymentAmount', 'paymentDate'])
    expect(st.formRules.payerName[0].message).toBe('请输入付款人')
    expect(st.formRules.paymentAmount[0].message).toBe('请输入付款金额')
  })

  it('@matrix C-6-4 statusMap 仅 DRAFT/APPROVED 两态，未知状态透传 status 兜底', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    expect(st.getStatusLabel('DRAFT')).toBe('草稿')
    expect(st.getStatusLabel('APPROVED')).toBe('已通过')
    expect(st.getStatusLabel('APPROVING')).toBe('APPROVING')
    expect(st.getStatusType('APPROVING')).toBe('info')
  })

  it('@matrix C-6-5 金额 input-number min=0 precision=2 源码钉住', () => {
    expect(otherPaymentSrc).toContain('v-model="formData.paymentAmount" :min="0" :precision="2"')
  })

  it('@matrix C-6-3 页面无操作列（仅新增+查看，无编辑/删除/提交入口）现状钉住', () => {
    expect(otherPaymentSrc).not.toContain('label="操作"')
  })
})

describe('finance/project-reimbursement.vue 项目报销（C7）', () => {
  async function mountPage() {
    const w = mount(ProjectReimbursement, { global: globalCfg })
    await flushPromises()
    return w
  }

  it('@matrix C-7-1 offsetReserve 开关：el-switch active/inactive=1/0 + 冲抵金额表单项 v-if 联动源码钉住', () => {
    expect(reimbSrc).toContain(':active-value="1" :inactive-value="0"')
    expect(reimbSrc).toContain('v-if="formData.offsetReserve === 1"')
  })

  it('@matrix C-7-1 offsetReserve=1 时弹窗出现「冲抵金额」表单项，=0 时隐藏（运行时）', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    const countOf = (html: string) => (html.match(/冲抵金额/g) || []).length
    st.handleAdd()
    await nextTick()
    expect(st.formData.offsetReserve).toBe(0)
    // 关闭态仅列表表头 1 处；开启弹窗且 offsetReserve=0 时表单项不渲染
    expect(countOf(w.html())).toBe(1)
    st.formData.offsetReserve = 1
    await nextTick()
    // 表单项 label 追加，共 2 处（表头+弹窗表单项）
    expect(countOf(w.html())).toBe(2)
  })

  it('@matrix C-7-1 列表冲抵金额列：offsetReserve=1 显示 formatMoney，否则显示 "-" 源码钉住', () => {
    expect(reimbSrc).toContain("row.offsetReserve === 1 ? formatMoney(row.offsetAmount) : '-'")
  })

  it('@matrix C-7-4 提交按钮仅 DRAFT 渲染 + 行提交调 submitProjectReimbursement', async () => {
    expect(reimbSrc).toContain('v-if="row.status === \'DRAFT\'" link type="success" @click="handleSubmitRow(row)"')
    const w = await mountPage()
    await w.vm.$.setupState.handleSubmitRow({ id: 51 })
    await flushPromises()
    expect(mocks.mockReimbSubmit).toHaveBeenCalledWith(51)
    expect(mocks.mockSuccess).toHaveBeenCalledWith('提交成功')
  })
})

describe('finance/settlement/index.vue 竣工结算（C11）', () => {
  async function mountPage(records: any[] = []) {
    mocks.mockSettlePage.mockResolvedValue({ code: 200, data: { records, total: records.length } })
    const w = mount(Settlement, { global: globalCfg })
    await flushPromises()
    return w
  }

  it('@matrix C-11-2/C-11-3 负利润/负利润率行 text-danger 红色渲染（种子 99361 同构数据）', async () => {
    const w = await mountPage([
      { id: 1, settlementCode: 'JS20260620NEG', totalIncome: 800000, totalExpenditure: 850000, profit: -50000, profitRate: -6.25, status: 'APPROVED' },
      { id: 2, settlementCode: 'JS20260615001', totalIncome: 33500000, totalExpenditure: 29500000, profit: 4000000, profitRate: 11.94, status: 'APPROVED' },
    ])
    const dangerSpans = w.findAll('span.text-danger')
    expect(dangerSpans.length).toBe(2)
    expect(dangerSpans[0].text()).toContain('-50,000.00')
    expect(dangerSpans[1].text()).toContain('-6.25%')
  })

  it('@matrix C-11-6 状态四态映射 DRAFT/SUBMITTED/APPROVED/REJECTED + 未知透传', async () => {
    const w = await mountPage()
    const st: any = w.vm.$.setupState
    expect(st.getStatusLabel('DRAFT')).toBe('草稿')
    expect(st.getStatusLabel('SUBMITTED')).toBe('审批中')
    expect(st.getStatusLabel('APPROVED')).toBe('已通过')
    expect(st.getStatusLabel('REJECTED')).toBe('已驳回')
    expect(st.getStatusLabel('UNKNOWN')).toBe('UNKNOWN')
    expect(st.getStatusType('SUBMITTED')).toBe('warning')
    expect(st.getStatusType('REJECTED')).toBe('danger')
  })

  it('@matrix C-11-4 导出 Excel blob 下载：文件名 结算报告_{code||id}.xlsx + revokeObjectURL', async () => {
    const createSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock')
    const revokeSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const w = await mountPage()
    await w.vm.$.setupState.handleExport({ id: 99361, settlementCode: 'JS20260620NEG' })
    await flushPromises()
    expect(mocks.mockSettleExport).toHaveBeenCalledWith(99361)
    expect(createSpy).toHaveBeenCalled()
    expect(revokeSpy).toHaveBeenCalledWith('blob:mock')
    expect(settleSrc).toContain('`结算报告_${row.settlementCode || row.id}.xlsx`')
    createSpy.mockRestore()
    revokeSpy.mockRestore()
  })

  it('@matrix C-11-5 导出失败提示 ElMessage.error("导出失败")', async () => {
    mocks.mockSettleExport.mockRejectedValue(new Error('500'))
    const w = await mountPage()
    await w.vm.$.setupState.handleExport({ id: 5, settlementCode: 'X' })
    await flushPromises()
    expect(mocks.mockError).toHaveBeenCalledWith('导出失败')
  })
})
