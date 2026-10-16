import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import Summary from '@/views/project/components/ProjectBusinessSummary.vue'

const api = vi.hoisted(() => ({ getProjectOverview: vi.fn(), getProjectCostControl: vi.fn(), getProjectReceiptRatios: vi.fn() }))
vi.mock('@/api/dashboard', () => api)
const id = '9223372036854775806'
const overview = (amount = 123) => ({ code: 200, data: { contract: { contractTotal: amount, receivedAmount: 0 }, output: {} } })
const cost = (projectId = id, accountCount = 1) => ({ code: 200, data: { projectId, accountCount, totals: { actualTotal: 45 } } })
const ratios = (opts: { bySettlementAvailable?: boolean; marginAvailable?: boolean } = {}) => ({
  code: 200,
  data: {
    asOf: '2026-10-16',
    collectionRateByContract: { value: 0.3, numerator: 30, denominator: 100, basis: 'RECEIVED_DIV_CONTRACT_AMOUNT', available: true },
    collectionRateByOutput: { value: 0.5, numerator: 30, denominator: 60, basis: 'RECEIVED_DIV_CUMULATIVE_OUTPUT', available: true },
    collectionRateByInvoice: { value: 0.6, numerator: 30, denominator: 50, basis: 'RECEIVED_DIV_CUMULATIVE_INVOICE', available: true },
    collectionRateBySettlement: opts.bySettlementAvailable
      ? { value: 0.8, numerator: 30, denominator: 37.5, basis: 'RECEIVED_DIV_CUMULATIVE_SETTLEMENT', available: true }
      : { value: null, numerator: 30, denominator: 0, basis: 'RECEIVED_DIV_CUMULATIVE_SETTLEMENT', available: false, unavailableReason: 'DENOMINATOR_ZERO' },
    outputGrossMargin: opts.marginAvailable
      ? { value: 300000, numerator: 300000, denominator: 800000, basis: 'CUMULATIVE_OUTPUT_MINUS_CBS_ACTUAL', available: true }
      : { value: null, available: false, unavailableReason: 'CBS_UNAVAILABLE' },
    settlementGrossMargin: { value: null, available: false, unavailableReason: 'CBS_UNAVAILABLE' },
    laborCostCompare: {
      wagePayrollApproved: 120000, pointWorkApproved: 30000, wageLikeTotal: 150000,
      laborSettlementTotal: 100000, difference: 50000, basis: 'WAGE_LIKE_NOT_IN_CBS',
      note: '工资性支出未入 CBS 权责成本'
    }
  }
})
beforeEach(() => {
  vi.resetAllMocks()
  api.getProjectOverview.mockResolvedValue(overview())
  api.getProjectCostControl.mockResolvedValue(cost())
  api.getProjectReceiptRatios.mockResolvedValue(ratios())
})
describe('项目经营摘要', () => {
  it('setup 即时加载，雪花 ID 原样透传；真实零与缺失金额分开', async () => {
    const w = mount(Summary, { props: { projectId: id } })
    expect(w.attributes('aria-busy')).toBe('true')
    await flushPromises()
    expect(api.getProjectOverview).toHaveBeenCalledWith(id)
    expect(api.getProjectCostControl).toHaveBeenCalledWith(id)
    expect(w.text()).toContain('123.00')
    expect(w.text()).toContain('0.00')
    expect(w.findAll('dd').map(x => x.text())).toContain('—')
    expect(w.text()).toContain('不等于审批支出或银行现金支付')
    w.unmount()
  })
  it('项目切换先清旧值，旧请求晚到不得覆盖新项目', async () => {
    let resolve!: (value: unknown) => void
    api.getProjectOverview.mockReturnValueOnce(new Promise(r => { resolve = r }))
    const w = mount(Summary, { props: { projectId: id } })
    api.getProjectCostControl.mockResolvedValue(cost('2'))
    api.getProjectOverview.mockResolvedValue(overview(456))
    await w.setProps({ projectId: '2' })
    await flushPromises()
    resolve(overview(999))
    await flushPromises()
    expect(w.text()).toContain('456.00')
    expect(w.text()).not.toContain('999.00')
    w.unmount()
  })
  it('单源失败保留另一源，原生按钮可重试', async () => {
    api.getProjectOverview.mockRejectedValueOnce(new Error('403'))
    const w = mount(Summary, { props: { projectId: id } })
    await flushPromises()
    expect(w.find('[role="alert"]').text()).toContain('加载失败')
    expect(w.text()).toContain('45.00')
    await w.get('button').trigger('click')
    await flushPromises()
    expect(w.find('[role="alert"]').exists()).toBe(false)
    w.unmount()
  })
  it('无 CBS 账户不伪造成本零；空响应展示空态', async () => {
    api.getProjectOverview.mockResolvedValue({ code: 200, data: null })
    api.getProjectCostControl.mockResolvedValue(cost(id, 0))
    const w = mount(Summary, { props: { projectId: id } })
    await flushPromises()
    expect(w.text()).toContain('暂无合同与产值数据')
    expect(w.text()).toContain('尚未编制 CBS 成本账户')
    // 成本组无数值（C5 回款组独立渲染，不伪装成本零）
    const costPanel = w.findAll('.summary-panel').find(p => p.text().includes('CBS 成本'))
    expect(costPanel?.findAll('dd')).toHaveLength(0)
    w.unmount()
  })
  it('不允许错项目成本或业务错误伪装为成功', async () => {
    api.getProjectOverview.mockResolvedValue({ code: 403, data: {} })
    api.getProjectCostControl.mockResolvedValue(cost('3'))
    const w = mount(Summary, { props: { projectId: id } })
    await flushPromises()
    expect(w.findAll('[role="alert"]')).toHaveLength(2)
    w.unmount()
  })
  it('无合法项目 ID 不请求全局汇总', async () => {
    const w = mount(Summary, { props: { projectId: '' } })
    await flushPromises()
    expect(api.getProjectOverview).not.toHaveBeenCalled()
    expect(api.getProjectCostControl).not.toHaveBeenCalled()
    expect(api.getProjectReceiptRatios).not.toHaveBeenCalled()
    w.unmount()
  })
  it('C5 - 回款率四口径展示后端值；零分母与无 CBS 毛利明示不可算', async () => {
    const w = mount(Summary, { props: { projectId: id } })
    await flushPromises()
    expect(api.getProjectReceiptRatios).toHaveBeenCalledWith(id)
    expect(w.text()).toContain('30.00%')
    expect(w.text()).toContain('50.00%')
    expect(w.text()).toContain('60.00%')
    // 零分母回款率与 CBS_UNAVAILABLE 毛利明示不可算，不用 0% 冒充
    expect(w.text()).toContain('不可算')
    expect(w.text()).toContain('分母为零')
    expect(w.text()).toContain('未建 CBS 成本账户')
    w.unmount()
  })
  it('C5 - 毛利可用展示金额；人工对照只读并列声明', async () => {
    api.getProjectReceiptRatios.mockResolvedValue(ratios({ marginAvailable: true, bySettlementAvailable: true }))
    const w = mount(Summary, { props: { projectId: id } })
    await flushPromises()
    expect(w.text()).toContain('300,000.00')
    expect(w.text()).toContain('80.00%')
    expect(w.text()).toContain('人工对照（只读）')
    expect(w.text()).toContain('150,000.00')
    expect(w.text()).toContain('未入 CBS 权责成本')
    w.unmount()
  })
  it('C5 - 回款比率接口失败不影响既有两组展示', async () => {
    api.getProjectReceiptRatios.mockRejectedValueOnce(new Error('500'))
    const w = mount(Summary, { props: { projectId: id } })
    await flushPromises()
    expect(w.text()).toContain('123.00')
    expect(w.text()).not.toContain('回款与毛利')
    w.unmount()
  })
})
