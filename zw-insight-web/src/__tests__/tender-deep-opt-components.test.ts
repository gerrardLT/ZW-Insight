/**
 * P1-M2 投标管理深挖组件单元测试
 * 覆盖 A1 开标登记 / A2 保证金申请与退还 / A3 详情抽屉 / B1 人员押证 / B2 费用 / B3 任务 / B4 落标原因
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

// API Mocks
const {
  mockCreateOpen,
  mockCreateDeposit,
  mockCreateRefund,
  mockGetOpenBid,
  mockGetDepositPage,
  mockGetRefundPage,
  mockGetPersonBindings,
  mockBindPerson,
  mockGetCertPage,
  mockGetFeePage,
  mockCreateFee,
  mockConfirmFeePayment,
  mockDeleteFee,
  mockGetTaskList,
  mockCreateTask,
  mockCompleteTask,
  mockDeleteTask
} = vi.hoisted(() => ({
  mockCreateOpen: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockCreateDeposit: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockCreateRefund: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockGetOpenBid: vi.fn(async (): Promise<any> => ({ code: 200, data: null })),
  mockGetDepositPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockGetRefundPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockGetPersonBindings: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockBindPerson: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockGetCertPage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockGetFeePage: vi.fn(async (): Promise<any> => ({ code: 200, data: { records: [], total: 0 } })),
  mockCreateFee: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockConfirmFeePayment: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDeleteFee: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockGetTaskList: vi.fn(async (): Promise<any> => ({ code: 200, data: [] })),
  mockCreateTask: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockCompleteTask: vi.fn(async (): Promise<any> => ({ code: 200 })),
  mockDeleteTask: vi.fn(async (): Promise<any> => ({ code: 200 }))
}))

vi.mock('@/api/tender', () => ({
  createTenderOpen: mockCreateOpen,
  getOpenBidByRegister: mockGetOpenBid,
  createTenderDeposit: mockCreateDeposit,
  createTenderRefund: mockCreateRefund,
  getTenderDepositPage: mockGetDepositPage,
  getTenderRefundPage: mockGetRefundPage,
  getPersonBindings: mockGetPersonBindings,
  bindPerson: mockBindPerson,
  getCertificatePage: mockGetCertPage,
  getTenderFeePage: mockGetFeePage,
  createTenderFee: mockCreateFee,
  confirmTenderFeePayment: mockConfirmFeePayment,
  deleteTenderFee: mockDeleteFee,
  getTenderTaskList: mockGetTaskList,
  createTenderTask: mockCreateTask,
  completeTenderTask: mockCompleteTask,
  deleteTenderTask: mockDeleteTask
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: { ...actual.ElMessageBox, confirm: vi.fn(async () => 'confirm') }
  }
})

import OpenBidDialog from '@/views/tender/components/OpenBidDialog.vue'
import DepositDialog from '@/views/tender/components/DepositDialog.vue'
import TenderDetailDrawer from '@/views/tender/components/TenderDetailDrawer.vue'

describe('OpenBidDialog 开标结果录入弹窗 (A1/B4)', () => {
  let wrapper: any = null

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    if (wrapper) {
      wrapper.unmount()
      wrapper = null
    }
  })

  it('中标模式：表单校验并提交 createTenderOpen', async () => {
    wrapper = mount(OpenBidDialog, {
      props: {
        modelValue: true,
        register: { id: 101, projectId: 90001, projectName: '测试项目' }
      },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    wrapper.vm.formData.isWon = 1
    wrapper.vm.formData.bidAmount = 15000000
    wrapper.vm.formData.winInfo = '中标通知书编号：TB-2026-001'

    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(mockCreateOpen).toHaveBeenCalledWith({
      registerId: 101,
      projectId: 90001,
      isWon: 1,
      bidAmount: 15000000,
      winInfo: '中标通知书编号：TB-2026-001',
      status: 'WON'
    })
    expect(wrapper.emitted('success')).toBeTruthy()
  })

  it('未中标模式 (B4)：携带落标原因分类提交', async () => {
    wrapper = mount(OpenBidDialog, {
      props: {
        modelValue: true,
        register: { id: 102, projectId: 90001 }
      },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    wrapper.vm.formData.isWon = 0
    wrapper.vm.formData.bidAmount = 16000000
    wrapper.vm.formData.lostReasonCategory = 'PRICE_OVER'
    wrapper.vm.formData.winInfo = '竞争对手报价低 3%'

    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(mockCreateOpen).toHaveBeenCalledWith({
      registerId: 102,
      projectId: 90001,
      isWon: 0,
      bidAmount: 16000000,
      winInfo: '竞争对手报价低 3%',
      status: 'LOST',
      lostReasonCategory: 'PRICE_OVER'
    })
  })
})

describe('DepositDialog 保证金弹窗 (A2)', () => {
  let wrapper: any = null

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    if (wrapper) {
      wrapper.unmount()
      wrapper = null
    }
  })

  it('申请模式：提交 createTenderDeposit', async () => {
    wrapper = mount(DepositDialog, {
      props: {
        modelValue: true,
        mode: 'apply',
        register: { id: 201, projectId: 90002, depositAmount: 500000 }
      },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    wrapper.vm.formData.depositAmount = 500000
    wrapper.vm.formData.paymentDate = '2026-10-10'

    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(mockCreateDeposit).toHaveBeenCalledWith({
      registerId: 201,
      projectId: 90002,
      depositAmount: 500000,
      paymentDate: '2026-10-10',
      status: 'DRAFT'
    })
    expect(wrapper.emitted('success')).toBeTruthy()
  })

  it('退还模式：提交 createTenderRefund', async () => {
    wrapper = mount(DepositDialog, {
      props: {
        modelValue: true,
        mode: 'refund',
        register: { id: 201, projectId: 90002 },
        depositApplyId: 888
      },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    wrapper.vm.formData.returnAmount = 500000
    wrapper.vm.formData.returnDate = '2026-10-20'

    await wrapper.vm.handleSubmit()
    await flushPromises()

    expect(mockCreateRefund).toHaveBeenCalledWith({
      depositApplyId: 888,
      returnAmount: 500000,
      returnDate: '2026-10-20'
    })
    expect(wrapper.emitted('success')).toBeTruthy()
  })
})

describe('TenderDetailDrawer 综合抽屉 (A3/B1/B2/B3)', () => {
  let wrapper: any = null

  beforeEach(() => {
    vi.clearAllMocks()
    mockGetOpenBid.mockResolvedValue({
      code: 200,
      data: { isWon: 1, bidAmount: 20000000, winInfo: '顺利中标' }
    })
    mockGetDepositPage.mockResolvedValue({
      code: 200,
      data: { records: [{ id: 1, depositAmount: 400000, status: 'PAID' }], total: 1 }
    })
    mockGetRefundPage.mockResolvedValue({
      code: 200,
      data: { records: [{ returnAmount: 400000, returnDate: '2026-10-15' }], total: 1 }
    })
    mockGetPersonBindings.mockResolvedValue({
      code: 200,
      data: [{ id: 10, personName: '张建造', certificateType: '一级建造师', bindingRole: '项目经理', status: 'LOCKED' }]
    })
    mockGetFeePage.mockResolvedValue({
      code: 200,
      data: { records: [{ id: 20, feeType: 'BID_DOC', feeAmount: 1000, status: 'PAID' }], total: 1 }
    })
    mockGetTaskList.mockResolvedValue({
      code: 200,
      data: [{ id: 30, taskType: 'COMMERCIAL', responsiblePerson: '李工', status: 'PENDING' }]
    })
  })

  afterEach(() => {
    if (wrapper) {
      wrapper.unmount()
      wrapper = null
    }
  })

  it('抽屉打开时联动拉取所有子模块数据', async () => {
    wrapper = mount(TenderDetailDrawer, {
      props: {
        modelValue: true,
        register: { id: 301, projectId: 90001, projectName: '示范工程' }
      },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    expect(mockGetOpenBid).toHaveBeenCalledWith(301)
    expect(mockGetDepositPage).toHaveBeenCalledWith({ registerId: 301, page: 1, size: 50 })
    expect(mockGetPersonBindings).toHaveBeenCalledWith(301)
    expect(mockGetFeePage).toHaveBeenCalledWith({ registerId: 301, page: 1, size: 50 })
    expect(mockGetTaskList).toHaveBeenCalledWith(301)
  })

  it('B1 押证绑定提交', async () => {
    wrapper = mount(TenderDetailDrawer, {
      props: {
        modelValue: true,
        register: { id: 301, projectId: 90001 }
      },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    wrapper.vm.bindingForm.personCertificateId = 55
    wrapper.vm.bindingForm.personName = '王工'
    wrapper.vm.bindingForm.certificateType = '安全员B证'
    wrapper.vm.bindingForm.bindingRole = '技术负责人'

    await wrapper.vm.submitBinding()
    await flushPromises()

    expect(mockBindPerson).toHaveBeenCalledWith(301, {
      projectId: 90001,
      personCertificateId: 55,
      personName: '王工',
      certificateType: '安全员B证',
      bindingRole: '技术负责人'
    })
  })

  it('B2 费用登记提交与确认支付', async () => {
    wrapper = mount(TenderDetailDrawer, {
      props: {
        modelValue: true,
        register: { id: 301, projectId: 90001 }
      },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    wrapper.vm.feeForm.feeType = 'DRAWING'
    wrapper.vm.feeForm.feeAmount = 500
    wrapper.vm.feeForm.paymentDate = '2026-10-08'

    await wrapper.vm.submitFee()
    await flushPromises()

    expect(mockCreateFee).toHaveBeenCalledWith({
      registerId: 301,
      projectId: 90001,
      feeType: 'DRAWING',
      feeAmount: 500,
      paymentDate: '2026-10-08',
      status: 'DRAFT'
    })

    await wrapper.vm.handlePayFee({ id: 20 })
    await flushPromises()
    expect(mockConfirmFeePayment).toHaveBeenCalledWith(20)
  })

  it('B3 任务分派提交与标记完成', async () => {
    wrapper = mount(TenderDetailDrawer, {
      props: {
        modelValue: true,
        register: { id: 301, projectId: 90001 }
      },
      global: { plugins: [ElementPlus] }
    })
    await flushPromises()

    wrapper.vm.taskForm.taskType = 'TECHNICAL'
    wrapper.vm.taskForm.responsiblePerson = '赵工'
    wrapper.vm.taskForm.deadline = '2026-10-25'

    await wrapper.vm.submitTask()
    await flushPromises()

    expect(mockCreateTask).toHaveBeenCalledWith({
      registerId: 301,
      taskType: 'TECHNICAL',
      responsiblePerson: '赵工',
      deadline: '2026-10-25',
      status: 'PENDING'
    })

    await wrapper.vm.handleCompleteTask({ id: 30 })
    await flushPromises()
    expect(mockCompleteTask).toHaveBeenCalledWith(30)
  })
})
