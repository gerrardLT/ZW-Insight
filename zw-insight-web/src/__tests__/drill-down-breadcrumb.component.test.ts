/**
 * DrillDownBreadcrumb.vue 单据穿透链组件测试（驾驶舱 P2-4，UI §13/§16）
 *
 * @matrix 穿透链：
 *   - 项目级进入 → 拉取成本分类（口径 basis 如实展示）
 *   - 类别级进入 → 直接拉供应商构成（四类映射不伪造）
 *   - 供应商行点击 → 合同清单（supplierName 透传）
 *   - 合同 → 原始单据 → 审批轨迹（workflowInstanceId 才显示入口，无则不伪造）
 *   - 附件环节实证说明固定展示
 *   - 取数失败显式报错不静默
 *
 * 模式与 cockpit-index.component.test.ts 一致：真实 Element Plus 挂载 +
 * teleport 内联 stub（happy-dom），mock API 层。
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import ElementPlus from 'element-plus'

const { mockCostCategories, mockSuppliers, mockAccountTxn, mockContracts,
  mockContractDocs, mockTrace, mockError } = vi.hoisted(() => ({
  mockCostCategories: vi.fn(async (): Promise<any> => ({ code: 200, data: { rows: [] } })),
  mockSuppliers: vi.fn(async (): Promise<any> => ({ code: 200, data: { rows: [] } })),
  mockAccountTxn: vi.fn(async (): Promise<any> => ({ code: 200, data: { rows: [] } })),
  mockContracts: vi.fn(async (): Promise<any> => ({ code: 200, data: { rows: [] } })),
  mockContractDocs: vi.fn(async (): Promise<any> => ({ code: 200, data: { rows: [] } })),
  mockTrace: vi.fn(async (): Promise<any> => ({ code: 200, data: {} })),
  mockError: vi.fn(),
}))

vi.mock('@/api/cockpit', () => ({
  getDrillCostCategories: mockCostCategories,
  getDrillSuppliers: mockSuppliers,
  getDrillAccountTxn: mockAccountTxn,
  getDrillContracts: mockContracts,
  getDrillContractDocs: mockContractDocs,
}))
vi.mock('@/api/workflow', () => ({ getApprovalTrace: mockTrace }))
vi.mock('element-plus', async (importOriginal) => {
  const actual: any = await importOriginal()
  return {
    ...actual,
    ElMessage: { success: vi.fn(), error: mockError, warning: vi.fn(), info: vi.fn() },
  }
})

import DrillDownBreadcrumb from '@/components/DrillDownBreadcrumb.vue'

const CATEGORY_ROWS = {
  code: 200,
  data: {
    hasCbs: true,
    basis: undefined,
    attachmentNote: '原始附件环节当前系统未支持：付款/结算/收票/合同单据均无附件上传功能，不伪造跳转',
    rows: [
      {
        costCategory: 'MATERIAL', categoryName: '材料费', contractCategory: 'PURCHASE',
        accountCount: 3, baseline: 20000000, current: 21000000, commitment: 18000000,
        actual: 15000000, forecast: 22000000,
        contractAmount: 22000000, contractSettlement: 15500000, contractPaid: 12000000,
        basis: 'CBS 实际=结算+材料出库归集（CostRollUpTask）',
      },
    ],
  },
}

const SUPPLIER_ROWS = {
  code: 200,
  data: {
    categoryName: '材料（采购合同）',
    rows: [
      {
        supplierName: '甲钢铁公司', contractCount: 2, contractAmount: 22000000,
        settlementTotal: 15500000, paidTotal: 12000000, invoiceTotal: 11000000,
      },
    ],
  },
}

const CONTRACT_ROWS = {
  code: 200,
  data: {
    rows: [
      {
        id: 501, contractCode: 'CG-2026-018', contractName: '钢筋采购合同',
        supplierName: '甲钢铁公司', signingDate: '2026-03-01', contractAmount: 12000000,
        cumulativeSettlement: 9000000, cumulativePaid: 7000000,
        cumulativeInvoiceReceived: 6500000, status: 'EFFECTIVE',
        workflowInstanceId: 'pi-contract-1',
      },
    ],
  },
}

const DOC_ROWS = {
  code: 200,
  data: {
    rows: [
      {
        docType: 'SETTLEMENT', docTypeName: '结算单', docId: 901, docNo: 'CGJS-001',
        docDate: '2026-06-10', dateLabel: '结算日期', amount: 3000000, status: 'APPROVED',
        workflowInstanceId: 'pi-settle-1',
      },
      {
        docType: 'PAYMENT', docTypeName: '付款申请', docId: 902, docNo: null,
        docDate: '2026-06-20', dateLabel: '计划付款日期', amount: 2000000, status: 'APPROVED',
        payStatus: 'PARTIAL_PAID', workflowInstanceId: 'pi-pay-1',
      },
    ],
  },
}

const TRACE_DATA = {
  code: 200,
  data: {
    processInstanceId: 'pi-pay-1', processName: '付款审批流程', status: 'COMPLETED',
    startUserName: '张三', startTime: '2026-06-18T09:00:00',
    approvalRecords: [
      {
        taskName: '财务复核', assigneeName: '李四', result: 'approved',
        resultText: '已通过', comment: '同意', endTime: '2026-06-19T10:00:00',
      },
    ],
  },
}

async function mountChain(entry: any) {
  const wrapper = mount(DrillDownBreadcrumb, {
    attachTo: document.body,
    props: { modelValue: true, entry },
    global: {
      plugins: [ElementPlus],
      stubs: { teleport: true, IconArrowRight: true },
    },
  })
  await flushPromises()
  return wrapper
}

describe('DrillDownBreadcrumb 单据穿透链（§13）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('P-01 项目级进入：拉取成本分类并展示口径与分类行', async () => {
    mockCostCategories.mockResolvedValue(CATEGORY_ROWS)
    const wrapper = await mountChain({ projectId: 1, projectName: '滨江花园一期' })

    expect(mockCostCategories).toHaveBeenCalledWith({ projectId: 1 })
    const text = wrapper.text()
    expect(text).toContain('材料费')
    expect(text).toContain('CBS 实际=结算+材料出库归集')
    // 附件环节实证说明固定展示（不伪造跳转）
    expect(text).toContain('原始附件环节当前系统未支持')
    wrapper.unmount()
  })

  it('P-02 类别级进入（成本中心入口）：直达供应商构成并带上面包屑上级', async () => {
    mockCostCategories.mockResolvedValue(CATEGORY_ROWS)
    mockSuppliers.mockResolvedValue(SUPPLIER_ROWS)
    const wrapper = await mountChain({
      projectId: 1, projectName: '滨江花园一期',
      contractCategory: 'PURCHASE', categoryLabel: '材料',
    })

    expect(mockSuppliers).toHaveBeenCalledWith({ projectId: 1, contractCategory: 'PURCHASE' })
    expect(wrapper.text()).toContain('甲钢铁公司')
    // 面包屑含两级：项目 + 类别
    const crumbs = wrapper.findAll('.dd-breadcrumb span')
    expect(crumbs.map(c => c.text()).join('|')).toContain('材料')
    wrapper.unmount()
  })

  it('P-03 供应商行点击 → 合同清单（supplierName 精确透传）', async () => {
    mockSuppliers.mockResolvedValue(SUPPLIER_ROWS)
    mockContracts.mockResolvedValue(CONTRACT_ROWS)
    const wrapper = await mountChain({
      projectId: 1, projectName: '滨江花园一期',
      contractCategory: 'PURCHASE', categoryLabel: '材料',
    })

    // 行点击（el-table @row-click）
    const tables = wrapper.findAllComponents({ name: 'ElTable' })
    const supplierTable = tables[tables.length - 1]
    supplierTable.vm.$emit('row-click', SUPPLIER_ROWS.data.rows[0])
    await flushPromises()

    expect(mockContracts).toHaveBeenCalledWith({
      projectId: 1, contractCategory: 'PURCHASE', supplierName: '甲钢铁公司',
    })
    expect(wrapper.text()).toContain('CG-2026-018')
    wrapper.unmount()
  })

  it('P-04 供应商→合同→原始单据→审批轨迹全链路：付款行「查看」拉 trace 并渲染时间线', async () => {
    mockSuppliers.mockResolvedValue(SUPPLIER_ROWS)
    mockContracts.mockResolvedValue(CONTRACT_ROWS)
    mockContractDocs.mockResolvedValue(DOC_ROWS)
    mockTrace.mockResolvedValue(TRACE_DATA)
    const wrapper = await mountChain({
      projectId: 1, projectName: '滨江花园一期',
      contractCategory: 'PURCHASE', categoryLabel: '材料',
    })
    // 供应商行点击 → 合同清单
    const tables = wrapper.findAllComponents({ name: 'ElTable' })
    tables[tables.length - 1].vm.$emit('row-click', SUPPLIER_ROWS.data.rows[0])
    await flushPromises()

    // 合同行点「构成单据」→ 原始单据级
    const drillBtn = wrapper.findAll('button').find(b => b.text().includes('构成单据'))
    expect(drillBtn).toBeTruthy()
    await drillBtn!.trigger('click')
    await flushPromises()

    expect(mockContractDocs).toHaveBeenCalledWith({
      contractCategory: 'PURCHASE', contractId: 501,
    })
    const text = wrapper.text()
    expect(text).toContain('结算单')
    expect(text).toContain('CGJS-001')
    // 支付状态如实呈现（V2026_64 部分支付档）
    expect(text).toContain('部分支付')

    const traceBtn = wrapper.findAll('button').find(b => b.text() === '查看')
    await traceBtn!.trigger('click')
    await flushPromises()

    // 首个「查看」属结算单行（CGJS-001，pi-settle-1）；付款行同构可继续穿透
    expect(mockTrace).toHaveBeenCalledWith('pi-settle-1')
    expect(wrapper.text()).toContain('财务复核')
    expect(wrapper.text()).toContain('李四')
    wrapper.unmount()
  })

  it('P-05 取数失败：显式 ElMessage.error，不装作「无数据」静默', async () => {
    mockCostCategories.mockRejectedValue(new Error('500 服务异常'))
    const wrapper = await mountChain({ projectId: 1, projectName: '测试项目' })

    expect(mockError).toHaveBeenCalled()
    const msg = String(mockError.mock.calls[0]?.[0] || '')
    expect(msg).toContain('穿透取数失败')
    wrapper.unmount()
  })

  it('P-06 无流程实例的单据不伪造审批入口（收票行显示「无」而非可点按钮）', async () => {
    mockSuppliers.mockResolvedValue(SUPPLIER_ROWS)
    mockContracts.mockResolvedValue(CONTRACT_ROWS)
    mockContractDocs.mockResolvedValue({
      code: 200,
      data: {
        rows: [
          {
            docType: 'INVOICE', docTypeName: '收票登记', docId: 903, docNo: null,
            docDate: '2026-06-15', dateLabel: '收票日期', amount: 1500000,
            status: 'APPROVED', workflowInstanceId: null,
          },
        ],
      },
    })
    const wrapper = await mountChain({
      projectId: 1, projectName: '滨江花园一期',
      contractCategory: 'PURCHASE', categoryLabel: '材料',
    })
    const tables = wrapper.findAllComponents({ name: 'ElTable' })
    tables[tables.length - 1].vm.$emit('row-click', SUPPLIER_ROWS.data.rows[0])
    await flushPromises()
    const drillBtn = wrapper.findAll('button').find(b => b.text().includes('构成单据'))
    await drillBtn!.trigger('click')
    await flushPromises()

    // 审批轨迹列：无流程实例→纯文本「无」（无「查看」按钮，不得伪造入口）
    expect(wrapper.findAll('button').some(b => b.text() === '查看')).toBe(false)
    expect(wrapper.text()).toContain('收票登记')
    expect(wrapper.text()).toContain('单据#903') // 表无单号列：以主键呈现不合成假编号
    wrapper.unmount()
  })
})
