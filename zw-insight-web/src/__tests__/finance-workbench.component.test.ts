/**
 * 财务待办首屏组件测试（S3.1）。
 *
 * 覆盖：4 卡与 TOP5 渲染、空数据、接口失败显式重试、卡片跳转正确 Tab。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockGetFinanceWorkbench, mockPush } = vi.hoisted(() => ({
  mockGetFinanceWorkbench: vi.fn(),
  mockPush: vi.fn(),
}))

vi.mock('@/api/finance', () => ({
  getFinanceWorkbench: mockGetFinanceWorkbench,
}))
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
}))

import FinanceWorkbench from '@/views/finance/workbench/index.vue'

let wrapper: any = null

function payload() {
  return {
    code: 200,
    data: {
      payable: {
        pendingApprovalCount: 2,
        pendingApprovalAmount: 30000,
        approvedUnpaidCount: 3,
        approvedUnpaidAmount: 50000,
        overdueCount: 1,
        overdueAmount: 8000,
        unclaimedInvoiceCount: 2,
        unclaimedInvoiceAmount: 6000,
      },
      receivable: {
        openCount: 4,
        openAmount: 100000,
        overdueCount: 2,
        overdueAmount: 40000,
      },
      topOverduePayments: [
        { id: 1, projectName: '滨江花园一期', supplierName: '供应商A', amount: 8000, paymentDate: '2026-09-01', overdueDays: 5 },
      ],
      topOverdueReceivables: [
        { id: 2, projectName: '城南市政道路', counterpartyName: '建设单位B', amount: 40000, dueDate: '2026-09-10', overdueDays: 10 },
      ],
    },
  }
}

async function mountPage() {
  wrapper = mount(FinanceWorkbench, { global: { plugins: [ElementPlus] } })
  await flushPromises()
  return wrapper
}

describe('finance/workbench/index.vue 财务待办（S3.1）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetFinanceWorkbench.mockResolvedValue(payload())
  })

  afterEach(() => {
    if (wrapper) {
      try { wrapper.unmount() } catch { /* 忽略 */ }
      wrapper = null
    }
  })

  it('渲染 4 张摘要卡与两条 TOP5', async () => {
    const w = await mountPage()
    const text = w.text()
    expect(text).toContain('待审批付款')
    expect(text).toContain('已批未付')
    expect(text).toContain('逾期未付')
    expect(text).toContain('应收逾期')
    expect(text).toContain('滨江花园一期')
    expect(text).toContain('建设单位B')
    expect(text).toContain('5 天')
    expect(text).toContain('10 天')
  })

  it('空数据展示空态，不伪造数字', async () => {
    mockGetFinanceWorkbench.mockResolvedValue({
      code: 200,
      data: {
        payable: {},
        receivable: {},
        topOverduePayments: [],
        topOverdueReceivables: [],
      },
    })
    const w = await mountPage()
    expect(w.text()).toContain('无已批未付的付款申请')
    expect(w.text()).toContain('无逾期应收')
  })

  it('接口失败显示错误与重试，重试再次请求', async () => {
    mockGetFinanceWorkbench.mockRejectedValueOnce(new Error('workbench down'))
    const w = await mountPage()
    expect(w.text()).toContain('财务待办加载失败')
    expect(w.text()).toContain('workbench down')

    mockGetFinanceWorkbench.mockResolvedValueOnce(payload())
    const retry = w.findAll('button').find((btn: any) => btn.text() === '重试')
    expect(retry).toBeTruthy()
    await retry!.trigger('click')
    await flushPromises()
    expect(mockGetFinanceWorkbench).toHaveBeenCalledTimes(2)
  })

  it('点击摘要卡跳转到对应 Tab', async () => {
    const w = await mountPage()
    const cards = w.findAll('.summary-card')
    expect(cards.length).toBeGreaterThanOrEqual(4)

    await cards[0].trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/finance/hub/payable-group?tab=payment-apply&status=SUBMITTED')

    mockPush.mockClear()
    await cards[3].trigger('click')
    expect(mockPush).toHaveBeenCalledWith('/finance/hub/receivable-group?tab=receivable')
  })
})
