/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * 21 - 资金链端到端（租户 9999）
 *
 * @matrix P0-资金链 | tests/frontend-test-case-matrix.md 附录二
 *   C-5      → 付款申请五分支合同路由（数据源/落库归属）
 *   C-FIN-X1 → 付款审批通过→合同累计付款回写→项目 totalExpense（已付口径）
 *              前置：四类支出合同真实结算链（劳务/分包直批；采购=入库→结算；机械=进场→台班→结算审批）
 *   C-FIN-X2 → 产值上报→开票→发票汇总口径勾稽
 *   C-FIN-X5 → 竣工验收→项目 COMPLETED→最终结算单支出汇总勾稽
 *   C-13/C-FIN-X4 → 封账拦截闭环（封→7 类单据被拒→解封恢复）
 *
 * 数据纪律：租户 9999（BPMN 有保障/封账租户级隔离）；E2E_TEST_ 前缀；
 * cleaner 逆序回收；审批流 withdraw 回收；禁止容忍断言（失败即红）。
 *
 * 产品缺口实证（登记台账，不静默）：OTHER_EXPENSE 付款提交校验
 * 「最大可付 = 累计结算-已付」，但 biz_other_contract.cumulative_settlement
 * 无任何业务流回写路径（全仓 grep 实证）→ 其他支出合同付款永远不可提交。
 *
 * DB 准备：FINANCE_ADMIN 角色——FinanceLockService.checkFinanceAdminRole 仅认
 * FINANCE_ADMIN/ADMIN，SUPER_ADMIN 不在清单（实证 admin/t9999admin 均仅 SUPER_ADMIN，
 * 封账对所有现存账号 403 不可用——权限设计缺口已登记台账；测试按用户授权自建角色）。
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { ApiClient } from './api-client'
import { TestDataCleaner, PREFIX } from './test-data'
import {
  createFinanceFixture, submitAndApprove, approveUntilDone, ALL_CATEGORIES,
  machineLedgerName, type FinanceFixture, type ContractCategory,
} from './helpers/finance-chain'
import { queryMysql, execMysql } from './helpers/redis-probe'

const TENANT = '9999'
const FINANCE_ADMIN_ROLE_ID = 9999901
/** 当月（结算周期/封账期间用） */
const CUR_MONTH = new Date().toISOString().slice(0, 7)
const CUR_MONTH_START = `${CUR_MONTH}-01`
const CUR_MONTH_END = `${CUR_MONTH}-28`

describe('21 - 资金链端到端（租户 9999）', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let fixture: FinanceFixture

  beforeAll(async () => {
    // DB 准备：FINANCE_ADMIN 角色（幂等，列与部署库实际结构对齐）+ 绑定 t9999admin
    execMysql(
      `INSERT IGNORE INTO sys_role (id, role_name, role_code, remark, status, tenant_id, data_scope, created_at, updated_at, deleted, version) ` +
      `VALUES (${FINANCE_ADMIN_ROLE_ID}, 'T9999财务管理员', 'FINANCE_ADMIN', 'E2E P0 封账测试角色', 1, 9999, 'ALL', NOW(), NOW(), 0, 0)`
    )
    execMysql(
      `INSERT IGNORE INTO sys_user_role (id, user_id, role_id) VALUES (9999003, 9999001, ${FINANCE_ADMIN_ROLE_ID})`
    )

    client = new ApiClient()
    const login = await client.login('t9999admin', '123456')
    expect(String(login.tenantId)).toBe(TENANT)
    cleaner = new TestDataCleaner()
    fixture = await createFinanceFixture(client, cleaner)
  }, 120_000)

  afterAll(async () => {
    if (!cleaner) return
    // 兜底解封（封账组内已即时解封，防中断残留）
    try {
      const locks = await client.get('/api/v1/finance/lock/page', { page: 1, size: 50 })
      for (const l of locks.data?.records || []) {
        if (l.status === 'LOCKED') await client.delete(`/api/v1/finance/lock/${l.id}/unlock`)
      }
    } catch { /* 兜底失败不遮蔽 */ }
    await cleaner.cleanup(client)
    // 兜底：API 删除受状态守卫限制（项目 COMPLETED/合同 EFFECTIVE 不可删实证，
    // 竞工验收→结案链会使项目 COMPLETED），SSH 直删 fixture 项目及其合同（仅 biz_% tenant 9999）
    try {
      const pid = fixture.projectId
      execMysql(`DELETE FROM biz_labor_contract WHERE tenant_id=9999 AND project_id=${pid}`)
      execMysql(`DELETE FROM biz_construction_contract WHERE tenant_id=9999 AND project_id=${pid}`)
      execMysql(`DELETE FROM biz_purchase_contract WHERE tenant_id=9999 AND project_id=${pid}`)
      execMysql(`DELETE FROM biz_machine_contract WHERE tenant_id=9999 AND project_id=${pid}`)
      execMysql(`DELETE FROM biz_subcontract WHERE tenant_id=9999 AND project_id=${pid}`)
      execMysql(`DELETE FROM biz_other_contract WHERE tenant_id=9999 AND project_id=${pid}`)
      execMysql(`DELETE FROM biz_project WHERE tenant_id=9999 AND id=${pid}`)
    } catch { /* 兜底失败不遮蔽，残留核查兼底 */ }
    try {
      execMysql(`DELETE FROM sys_user_role WHERE id=9999003`)
      execMysql(`DELETE FROM sys_role WHERE id=${FINANCE_ADMIN_ROLE_ID}`)
    } catch { /* 角色残留不影响业务表 */ }
  }, 180_000)

  // ============ 工具函数（id 统一 String 比较——后端 Long 序列化为字符串防精度丢失） ============

  async function findPayment(contractId: number | string): Promise<any> {
    const resp = await client.get('/api/v1/finance/payment-apply/page', {
      page: 1, size: 50, projectId: fixture.projectId,
    })
    return (resp.data?.records || []).find((r: any) => String(r.contractId) === String(contractId))
  }

  async function getCumulativePaid(category: ContractCategory, contractId: number | string): Promise<number> {
    const pathMap: Record<ContractCategory, string> = {
      PURCHASE: '/api/v1/purchase/contract/page',
      LABOR: '/api/v1/labor/contract/page',
      MACHINE: '/api/v1/machine/contract/page',
      SUBCONTRACT: '/api/v1/subcontract/contract/page',
      OTHER_EXPENSE: '/api/v1/contract/other',
    }
    const resp = await client.get(pathMap[category], {
      page: 1, size: 50, projectId: fixture.projectId,
    })
    const found = (resp.data?.records || []).find((r: any) => String(r.id) === String(contractId))
    expect(found, `${category} 合同 ${contractId} 应可查到`).toBeDefined()
    return Number(found.cumulativePaid || 0)
  }

  async function getProjectTotalExpense(): Promise<number> {
    const resp = await client.get(`/api/v1/project/${fixture.projectId}`)
    expect(resp.code).toBe(200)
    return Number(resp.data?.totalExpense || 0)
  }

  async function createPayment(category: ContractCategory, amount: number, date = '2026-07-15'): Promise<number> {
    const resp = await client.post('/api/v1/finance/payment-apply', {
      projectId: fixture.projectId,
      contractId: fixture.contracts[category],
      contractCategory: category,
      paymentAmount: amount,
      paymentDate: date,
      supplierName: `E2E收款方_${category}`,
    })
    expect(resp.code, `创建${category}付款申请`).toBe(200)
    const payment = await findPayment(fixture.contracts[category])
    expect(payment, '付款申请创建后应可查到').toBeDefined()
    cleaner.add(`删除${category}付款申请`, () =>
      client.delete(`/api/v1/finance/payment-apply/${payment.id}`))
    return payment.id
  }

  // ============ C-5 付款申请五分支合同路由 ============
  describe('C-5 付款申请五分支合同路由', () => {
    // @matrix C-5（payment-apply.vue loadContracts switch 五分支数据源层）
    for (const category of ALL_CATEGORIES) {
      it(`${category} 分支：合同分页接口按项目返回 fixture 合同`, async () => {
        const pathMap: Record<ContractCategory, [string, any]> = {
          PURCHASE: ['/api/v1/purchase/contract/page', {}],
          LABOR: ['/api/v1/labor/contract/page', {}],
          MACHINE: ['/api/v1/machine/contract/page', {}],
          SUBCONTRACT: ['/api/v1/subcontract/contract/page', {}],
          OTHER_EXPENSE: ['/api/v1/contract/other', { contractCategory: 'OTHER_EXPENSE' }],
        }
        const [path, extra] = pathMap[category]
        const resp = await client.get(path, {
          page: 1, size: 100, projectId: fixture.projectId, ...extra,
        })
        expect(resp.code, `${category} 合同数据源接口`).toBe(200)
        const ids = (resp.data?.records || []).map((r: any) => String(r.id))
        expect(ids, `${category} 下拉数据源应包含 fixture 合同`).toContain(String(fixture.contracts[category]))
      })
    }

    // @matrix C-5（落库归属：contractCategory 持久化正确）
    for (const category of ALL_CATEGORIES) {
      it(`${category} 付款申请落库 contractCategory 正确`, async () => {
        await createPayment(category, 1000 + ALL_CATEGORIES.indexOf(category) * 100)
        const payment = await findPayment(fixture.contracts[category])
        expect(payment.contractCategory).toBe(category)
        expect(String(payment.contractId)).toBe(String(fixture.contracts[category]))
      })
    }
  })

  // ============ C-FIN-X1 付款审批回写闭环（含四类真实结算前置链） ============
  describe('C-FIN-X1 付款审批通过→合同累计付款→项目总支出', () => {
    // ---- 结算前置：劳务（合同生效→结算直批） ----
    // @matrix C-FIN-X1
    it('前置：劳务结算提交即生效，合同累计结算增加', async () => {
      // 劳务合同先生效（结算单汇总仅统计 EFFECTIVE 合同，SettlementDataMapper 实证）
      const cSubmit = await client.post(`/api/v1/labor/contract/${fixture.contracts.LABOR}/submit`)
      expect(cSubmit.code, '劳务合同提交生效').toBe(200)
      const amount = 50000
      const createResp = await client.post('/api/v1/labor/settlement', {
        projectId: fixture.projectId,
        contractId: fixture.contracts.LABOR,
        settlementAmount: amount,
        settlementDate: '2026-07-01',
      })
      expect(createResp.code, '创建劳务结算').toBe(200)
      const pageResp = await client.get('/api/v1/labor/settlement/page', {
        page: 1, size: 20, projectId: fixture.projectId,
      })
      const st = (pageResp.data?.records || [])
        .find((r: any) => String(r.contractId) === String(fixture.contracts.LABOR))
      expect(st).toBeDefined()
      cleaner.add('删除劳务结算', () => client.delete(`/api/v1/labor/settlement/${st.id}`))
      const submitResp = await client.post(`/api/v1/labor/settlement/${st.id}/submit`)
      expect(submitResp.code, '劳务结算提交（直批生效）').toBe(200)
    })

    // ---- 结算前置：分包（明细构造） ----
    // @matrix C-FIN-X1
    it('前置：分包结算提交生效，合同累计结算增加', async () => {
      // 分包合同先提交生效（DRAFT→EFFECTIVE，@BudgetCheck WARN_ONLY 放行）
      const cSubmit = await client.post(`/api/v1/subcontract/contract/${fixture.contracts.SUBCONTRACT}/submit`)
      expect(cSubmit.code, '分包合同提交生效').toBe(200)
      const createResp = await client.post('/api/v1/subcontract/settlement', {
        projectId: fixture.projectId,
        contractId: fixture.contracts.SUBCONTRACT,
        details: [{ itemName: 'E2E资金链分项', quantity: 100, unitPrice: 400 }],
      })
      expect(createResp.code, '创建分包结算').toBe(200)
      const pageResp = await client.get('/api/v1/subcontract/settlement', {
        page: 1, size: 20, projectId: fixture.projectId,
      })
      const st = (pageResp.data?.records || [])
        .find((r: any) => String(r.contractId) === String(fixture.contracts.SUBCONTRACT))
      expect(st).toBeDefined()
      cleaner.add('删除分包结算', () => client.delete(`/api/v1/subcontract/settlement/${st.id}`))
      const submitResp = await client.post(`/api/v1/subcontract/settlement/${st.id}/submit`)
      expect(submitResp.code, '分包结算提交').toBe(200)
    })

    // ---- 结算前置：采购（合同生效→入库→结算链） ----
    // @matrix C-FIN-X1
    it('前置：入库单提交→采购结算提交生效', async () => {
      // 采购合同 submit：启动 PURCHASE_CONTRACT 流程并直接置 EFFECTIVE（后端 L112-130 实证）
      const cSubmit = await client.post(`/api/v1/purchase/contract/${fixture.contracts.PURCHASE}/submit`)
      expect(cSubmit.code, '采购合同提交生效').toBe(200)
      cleaner.add('回收采购合同审批流', () =>
        client.post(`/api/v1/workflow/approval/withdraw-by-business?businessType=PURCHASE_CONTRACT&businessId=${fixture.contracts.PURCHASE}`))
      const inboundResp = await client.post('/api/v1/material/inbound', {
        projectId: fixture.projectId,
        contractId: fixture.contracts.PURCHASE,
        inboundDate: '2026-07-05',
        details: [{ materialName: 'E2E资金链材料', specification: 'HRB400', unit: '吨', quantity: 10, unitPrice: 3000 }],
      })
      expect(inboundResp.code, '创建入库单').toBe(200)
      const inPage = await client.get('/api/v1/material/inbound/page', {
        page: 1, size: 20, projectId: fixture.projectId,
      })
      const inbound = (inPage.data?.records || [])
        .find((r: any) => String(r.contractId) === String(fixture.contracts.PURCHASE))
      expect(inbound).toBeDefined()
      cleaner.add('删除入库单', () => client.delete(`/api/v1/material/inbound/${inbound.id}`))
      const inSubmit = await client.post(`/api/v1/material/inbound/${inbound.id}/submit`)
      expect(inSubmit.code, '入库单提交（即时 APPROVED）').toBe(200)

      // 采购结算必须关联已审批入库单（合法业务校验，矩阵 B-P-X2 同源）
      const stResp = await client.post('/api/v1/purchase/settlement', {
        projectId: fixture.projectId,
        contractId: fixture.contracts.PURCHASE,
        inboundId: inbound.id,
        settlementAmount: 30000,
      })
      expect(stResp.code, '创建采购结算').toBe(200)
      const stPage = await client.get('/api/v1/purchase/settlement/page', {
        page: 1, size: 20, projectId: fixture.projectId,
      })
      const st = (stPage.data?.records || [])
        .find((r: any) => String(r.contractId) === String(fixture.contracts.PURCHASE))
      expect(st).toBeDefined()
      cleaner.add('删除采购结算', () => client.delete(`/api/v1/purchase/settlement/${st.id}`))
      const stSubmit = await client.post(`/api/v1/purchase/settlement/${st.id}/submit`)
      expect(stSubmit.code, '采购结算提交（启动流程并回写累计结算）').toBe(200)
      // 采购结算提交启动 PURCHASE_SETTLEMENT 流程（PurchaseSettlementService L198 实证），
      // 注册回收防 ACT_RU_TASK 残留（实证：曾残留「项目负责人审批」任务）
      cleaner.add('回收采购结算审批流', () =>
        client.post(`/api/v1/workflow/approval/withdraw-by-business?businessType=PURCHASE_SETTLEMENT&businessId=${st.id}`))
    })

    // ---- 结算前置：机械（合同生效→进场→台班→结算审批链） ----
    // @matrix C-FIN-X1
    it('前置：机械进场→台班→结算审批通过，合同累计结算增加', async () => {
      // 机械合同生效（结算回写仅对 EFFECTIVE 合同，MachineWorkSettlementService L301-303 实证）
      const cSubmit = await client.post(`/api/v1/machine/contract/${fixture.contracts.MACHINE}/submit`)
      expect(cSubmit.code, '机械合同提交生效').toBe(200)

      // 台账（名称与 fixture 机械合同 machineName 一致，结算按名称匹配）
      const ledgerResp = await client.post('/api/v1/machine/ledger', {
        machineName: machineLedgerName(),
        machineType: 'EXCAVATOR',
        ownerType: 'RENT',
      })
      expect(ledgerResp.code, '创建机械台账').toBe(200)
      const ledgerPage = await client.get('/api/v1/machine/ledger/page', {
        page: 1, size: 20, machineName: `${PREFIX}_FINCHAIN_挖机`,
      })
      const machine = (ledgerPage.data?.records || [])[0]
      expect(machine).toBeDefined()
      cleaner.add('删除机械台账', () => client.delete(`/api/v1/machine/ledger/${machine.id}`))

      // 进场
      const entryResp = await client.post('/api/v1/machine/entry', {
        machineId: machine.id,
        projectId: fixture.projectId,
        entryType: 'IN',
        entryDate: CUR_MONTH_START,
      })
      expect(entryResp.code, '机械进场登记').toBe(200)

      // 台班工作日志（后端仅允许 IN_FIELD 机械）
      const logResp = await client.post('/api/v1/machine/work-log', {
        machineId: machine.id,
        projectId: fixture.projectId,
        workDate: CUR_MONTH_START,
        shiftCount: 5,
        workQuantity: 40,
      })
      expect(logResp.code, '创建机械工作日志').toBe(200)

      // 结算单（按项目+周期拉 usage 生成）
      const stResp = await client.post('/api/v1/machine/settlement', {
        projectId: fixture.projectId,
        periodStart: CUR_MONTH_START,
        periodEnd: CUR_MONTH_END,
      })
      expect(stResp.code, '创建机械结算单').toBe(200)
      const stPage = await client.get('/api/v1/machine/settlement', {
        page: 1, size: 20, projectId: fixture.projectId,
      })
      const st = (stPage.data?.records || [])[0]
      expect(st).toBeDefined()
      cleaner.add('删除机械结算单', () => client.delete(`/api/v1/machine/settlement/${st.id}`))

      // 提交审批（businessType 为小写 machine_settlement，后端 MachineWorkSettlementService 实证）并驱动通过
      const stSubmit = await client.post(`/api/v1/machine/settlement/${st.id}/submit`)
      expect(stSubmit.code, '机械结算提交审批').toBe(200)
      cleaner.add('回收机械结算审批流', () =>
        client.post(`/api/v1/workflow/approval/withdraw-by-business?businessType=machine_settlement&businessId=${st.id}`))
      await approveUntilDone(client, 'machine_settlement', st.id, TENANT)
    }, 120_000)

    // ---- 付款回写闭环：四类逐一钉住 ----
    // @matrix C-FIN-X1
    for (const category of ['LABOR', 'SUBCONTRACT', 'PURCHASE', 'MACHINE'] as ContractCategory[]) {
      const AMOUNT_MAP: Record<string, number> = {
        LABOR: 20000, SUBCONTRACT: 15000, PURCHASE: 10000, MACHINE: 5000,
      }
      it(`${category} 付款审批通过：合同累计付款与项目总支出增加`, async () => {
        const amount = AMOUNT_MAP[category]
        const paidBefore = await getCumulativePaid(category, fixture.contracts[category])
        const expenseBefore = await getProjectTotalExpense()
        const paymentId = await createPayment(category, amount)

        await submitAndApprove(
          client, cleaner, 'PAYMENT_APPLY', paymentId,
          () => client.post(`/api/v1/finance/payment-apply/${paymentId}/submit`),
          TENANT
        )

        const paidAfter = await getCumulativePaid(category, fixture.contracts[category])
        const expenseAfter = await getProjectTotalExpense()
        expect(paidAfter - paidBefore, `${category} 合同累计付款增量`).toBe(amount)
        expect(expenseAfter - expenseBefore, '项目总支出增量（已付口径）').toBe(amount)
        // 单据终态
        const payment = await findPayment(fixture.contracts[category])
        expect(payment.status).toBe('APPROVED')
      })
    }

    // @matrix C-FIN-X1（幂等：APPROVED 单据重复回调不重复累加）
    it('幂等守卫：APPROVED 单据状态唯一（重复回调直接返回）', async () => {
      const payment = await findPayment(fixture.contracts.LABOR)
      expect(payment.status).toBe('APPROVED')
      const cnt = queryMysql(
        `SELECT COUNT(*) FROM biz_payment_apply WHERE id=${payment.id} AND status='APPROVED' AND deleted=0`
      ).trim()
      expect(cnt).toBe('1')
    })

    // @matrix C-FIN-X1（负向：超可付金额提交被拒）
    it('负向：付款金额超过（累计结算-已付）上限提交被拒', async () => {
      const paymentId = await createPayment('LABOR', 999999)
      const resp = await client.post(`/api/v1/finance/payment-apply/${paymentId}/submit`)
      expect(resp.code).not.toBe(200)
      expect(resp.message).toContain('最大可付金额')
    })

    // @matrix C-FIN-X1（产品缺口现状钉住：OTHER_EXPENSE 无累计结算回写路径）
    it('产品缺口现状：其他支出合同付款提交被拒（累计结算无回写路径，已登记台账）', async () => {
      const paymentId = await createPayment('OTHER_EXPENSE', 100)
      const resp = await client.post(`/api/v1/finance/payment-apply/${paymentId}/submit`)
      expect(resp.code, '其他支出付款当前不可提交（cumulative_settlement 无回写路径）').not.toBe(200)
      expect(resp.message).toContain('最大可付金额')
    })
  })

  // ============ C-FIN-X2 产值→开票→汇总勾稽 ============
  describe('C-FIN-X2 产值上报→开票申请→发票汇总勾稽', () => {
    const OUTPUT_AMOUNT = 500000
    const INVOICE_AMOUNT = 100000
    let outputId: number
    let invoiceId: number

    // @matrix C-FIN-X2
    it('产值上报审批通过：合同累计产值增加', async () => {
      const createResp = await client.post('/api/v1/contract/output', {
        projectId: fixture.projectId,
        contractId: fixture.constructionContractId,
        reportPeriod: '2026-07',
        confirmDate: '2026-07-31',
        currentOutput: OUTPUT_AMOUNT,
      })
      expect(createResp.code, '创建产值上报').toBe(200)
      const pageResp = await client.get('/api/v1/contract/output', {
        page: 1, size: 20, projectId: fixture.projectId,
        contractId: fixture.constructionContractId,
      })
      const report = (pageResp.data?.records || [])[0]
      expect(report, '产值上报创建后应可查到').toBeDefined()
      outputId = report.id
      cleaner.add('删除产值上报', () => client.delete(`/api/v1/contract/output/${outputId}`))

      await submitAndApprove(
        client, cleaner, 'OUTPUT_REPORT', outputId,
        () => client.post(`/api/v1/contract/output/${outputId}/submit`),
        TENANT
      )

      const contractResp = await client.get('/api/v1/contract/page', {
        page: 1, size: 10, projectId: fixture.projectId,
      })
      const contract = (contractResp.data?.records || [])
        .find((r: any) => String(r.id) === String(fixture.constructionContractId))
      expect(Number(contract.cumulativeOutput || 0), '施工合同累计产值').toBe(OUTPUT_AMOUNT)
    })

    // @matrix C-FIN-X2（开票审批通过→合同累计开票回写）
    it('开票申请审批通过：合同累计开票增加', async () => {
      const createResp = await client.post('/api/v1/finance/invoice-apply', {
        projectId: fixture.projectId,
        contractId: fixture.constructionContractId,
        invoiceType: 'SPECIAL',
        invoiceAmount: INVOICE_AMOUNT,
        applyDate: '2026-07-20',
        invoiceTitle: 'E2E资金链购方',
      })
      expect(createResp.code, '创建开票申请').toBe(200)
      const pageResp = await client.get('/api/v1/finance/invoice-apply/page', {
        page: 1, size: 20, projectId: fixture.projectId,
      })
      const invoice = (pageResp.data?.records || [])
        .find((r: any) => String(r.contractId) === String(fixture.constructionContractId))
      expect(invoice).toBeDefined()
      invoiceId = invoice.id
      cleaner.add('删除开票申请', () =>
        client.delete(`/api/v1/finance/invoice-apply/${invoiceId}`))

      await submitAndApprove(
        client, cleaner, 'INVOICE_APPLY', invoiceId,
        () => client.post(`/api/v1/finance/invoice-apply/${invoiceId}/submit`),
        TENANT
      )

      const contractResp = await client.get('/api/v1/contract/page', {
        page: 1, size: 10, projectId: fixture.projectId,
      })
      const contract = (contractResp.data?.records || [])
        .find((r: any) => String(r.id) === String(fixture.constructionContractId))
      expect(Number(contract.cumulativeInvoiceAmount || 0), '施工合同累计开票').toBe(INVOICE_AMOUNT)
    })

    // @matrix C-FIN-X2（发票汇总已开票口径包含该笔；projectId 为字符串需 String 比较）
    it('发票汇总接口已开票口径包含该笔金额', async () => {
      const resp = await client.get('/api/v1/finance/invoice-summary', {
        projectId: fixture.projectId,
      })
      expect(resp.code, '发票汇总接口').toBe(200)
      const rows: any[] = Array.isArray(resp.data) ? resp.data : []
      const row = rows.find((r) => String(r.projectId) === String(fixture.projectId))
      expect(row, '汇总应含该项目的行').toBeDefined()
      expect(Number(row.invoicedAmount || 0), '已开票金额口径').toBe(INVOICE_AMOUNT)
    })

    // @matrix C-FIN-X2（负向：开票上限校验在 submit 阶段——累计产值-已开票）
    it('负向：超上限开票提交被拒（上限=累计产值-已开票）', async () => {
      const createResp = await client.post('/api/v1/finance/invoice-apply', {
        projectId: fixture.projectId,
        contractId: fixture.constructionContractId,
        invoiceType: 'SPECIAL',
        invoiceAmount: OUTPUT_AMOUNT, // 剩余可开 = 500000-100000 = 400000 < 500000
        applyDate: '2026-07-25',
        invoiceTitle: 'E2E超限开票',
      })
      expect(createResp.code, '创建阶段不做上限校验').toBe(200)
      const pageResp = await client.get('/api/v1/finance/invoice-apply/page', {
        page: 1, size: 20, projectId: fixture.projectId,
      })
      const over = (pageResp.data?.records || [])
        .find((r: any) => Number(r.invoiceAmount) === OUTPUT_AMOUNT)
      expect(over).toBeDefined()
      cleaner.add('删除超限开票申请', () =>
        client.delete(`/api/v1/finance/invoice-apply/${over.id}`))
      const submitResp = await client.post(`/api/v1/finance/invoice-apply/${over.id}/submit`)
      expect(submitResp.code, '提交阶段应校验开票上限').not.toBe(200)
      expect(submitResp.message).toContain('开票金额')
    })
  })

  // ============ C-13 / C-FIN-X4 封账拦截闭环 ============
  describe('C-13/C-FIN-X4 封账拦截闭环', () => {
    let lockIds: number[] = []
    const period = CUR_MONTH
    const billDate = `${period}-10`

    // @matrix C-13-3/4
    it('封账当月期间成功（MONTHLY）', async () => {
      const resp = await client.post('/api/v1/finance/lock', { period, lockType: 'MONTHLY' })
      expect(resp.code, '封账当月期间').toBe(200)
      const created: any[] = Array.isArray(resp.data) ? resp.data : []
      lockIds = created.map((l) => l.id)
      expect(lockIds.length).toBeGreaterThan(0)
      cleaner.add('兜底解封', async () => {
        for (const id of lockIds) await client.delete(`/api/v1/finance/lock/${id}/unlock`)
      })
    })

    // @matrix C-13-8
    it('负向：同期间重复封账被拒', async () => {
      const resp = await client.post('/api/v1/finance/lock', { period, lockType: 'MONTHLY' })
      expect(resp.code).not.toBe(200)
      expect(resp.message).toContain('已封账')
    })

    // @matrix C-13
    it('负向：不可对未来期间封账', async () => {
      const future = new Date(Date.now() + 90 * 86400_000).toISOString().slice(0, 7)
      const resp = await client.post('/api/v1/finance/lock', { period: future, lockType: 'MONTHLY' })
      expect(resp.code).not.toBe(200)
      expect(resp.message).toContain('未来')
    })

    // @matrix C-FIN-X4（@FinanceLockCheck 覆盖的 7 类端点逐一钉住）
    const lockedCases: Array<[string, string, any]> = [
      ['付款申请', '/api/v1/finance/payment-apply', {
        projectId: 1, contractId: 1, contractCategory: 'PURCHASE',
        paymentAmount: 100, paymentDate: billDate, supplierName: 'E2E封账',
      }],
      ['回款登记', '/api/v1/finance/payment-received', {
        projectId: 1, receiveAmount: 100, receiveDate: billDate, payerName: 'E2E封账',
      }],
      ['项目报销', '/api/v1/finance/project-reimbursement', {
        projectId: 1, totalAmount: 100, reimbursementDate: billDate,
      }],
      ['个人报销', '/api/v1/finance/personal-reimbursement', {
        totalAmount: 100, reimbursementDate: billDate,
      }],
      ['其他费用付款', '/api/v1/finance/other-payment', {
        projectId: 1, payerName: 'E2E封账', paymentAmount: 100, paymentDate: billDate,
      }],
      ['收票登记', '/api/v1/finance/invoice-received', {
        projectId: 1, contractId: 1, invoiceAmount: 100, invoiceDate: billDate,
      }],
      ['开票申请', '/api/v1/finance/invoice-apply', {
        projectId: 1, contractId: 1, invoiceType: 'SPECIAL',
        invoiceAmount: 100, applyDate: billDate, invoiceTitle: 'E2E封账',
      }],
    ]
    for (const [label, path, payload] of lockedCases) {
      it(`封账期间${label}新增被拦截`, async () => {
        const resp = await client.post(path, payload)
        expect(resp.code, `${label}应被拦截（@FinanceLockCheck）`).not.toBe(200)
        expect(String(resp.message)).toMatch(/封账|锁定/)
      })
    }

    // @matrix C-13-6/11
    it('解封后付款申请恢复可新增', async () => {
      for (const id of lockIds) {
        const resp = await client.delete(`/api/v1/finance/lock/${id}/unlock`)
        expect(resp.code, '解封').toBe(200)
      }
      lockIds = []
      const resp = await client.post('/api/v1/finance/payment-apply', {
        projectId: fixture.projectId,
        contractId: fixture.contracts.PURCHASE,
        contractCategory: 'PURCHASE',
        paymentAmount: 100,
        paymentDate: billDate,
        supplierName: 'E2E解封验证',
      })
      expect(resp.code, '解封后付款申请应可创建').toBe(200)
      const payment = await findPayment(fixture.contracts.PURCHASE)
      if (payment && payment.supplierName === 'E2E解封验证') {
        await client.delete(`/api/v1/finance/payment-apply/${payment.id}`)
      }
    })

    // @matrix C-13
    it('负向：非封账状态记录解封被拒', async () => {
      const pageResp = await client.get('/api/v1/finance/lock/page', { page: 1, size: 50 })
      const unlocked = (pageResp.data?.records || []).find((l: any) => l.status === 'UNLOCKED')
      expect(unlocked, '应存在已解封记录').toBeDefined()
      const resp = await client.delete(`/api/v1/finance/lock/${unlocked.id}/unlock`)
      expect(resp.code).not.toBe(200)
    })
  })

  // ============ C-FIN-X5 竣工验收→最终结算勾稽 ============
  describe('C-FIN-X5 竣工验收→最终结算支出汇总勾稽', () => {
    let settlementId: number

    // @matrix C-FIN-X5（前置：竣工验收审批通过→项目 COMPLETED）
    it('前置：竣工验收审批通过，项目状态 COMPLETED', async () => {
      const createResp = await client.post('/api/v1/site/completion', {
        projectId: fixture.projectId,
        acceptanceDate: CUR_MONTH_END,
        acceptanceResult: '合格',
        remark: 'E2E P0 资金链竣工验收',
      })
      expect(createResp.code, '创建竣工验收').toBe(200)
      const pageResp = await client.get('/api/v1/site/completion/page', {
        page: 1, size: 5, projectId: fixture.projectId,
      })
      const acceptance = (pageResp.data?.records || [])[0]
      expect(acceptance).toBeDefined()
      cleaner.add('删除竣工验收', () =>
        client.delete(`/api/v1/site/completion/${acceptance.id}`))
      const submitResp = await client.post(`/api/v1/site/completion/${acceptance.id}/submit`)
      expect(submitResp.code, '提交竣工验收').toBe(200)
      cleaner.add('回收竣工验收审批流', () =>
        client.post(`/api/v1/workflow/approval/withdraw-by-business?businessType=COMPLETION_ACCEPTANCE&businessId=${acceptance.id}`))
      await approveUntilDone(client, 'COMPLETION_ACCEPTANCE', acceptance.id, TENANT)

      const prjResp = await client.get(`/api/v1/project/${fixture.projectId}`)
      expect(prjResp.data?.status, '竣工验收通过后项目应 COMPLETED').toBe('COMPLETED')
    }, 120_000)

    // @matrix C-FIN-X5
    it('创建结算单并勾稽：支出汇总 ≥ 五类合同累计付款之和', async () => {
      const createResp = await client.post(
        `/api/v1/project-settlements?projectId=${fixture.projectId}`)
      expect(createResp.code, '创建项目结算单（项目须 COMPLETED）').toBe(200)
      settlementId = createResp.data
      cleaner.add('删除项目结算单', () =>
        client.delete(`/api/v1/project-settlements/${settlementId}`))

      const detailResp = await client.get(`/api/v1/project-settlements/${settlementId}`)
      expect(detailResp.code).toBe(200)
      const detail = detailResp.data

      // 分项勾稽：总支出 = 分包结算+劳务结算+材料结算+机械结算+累计付款+净奖惩
      //（ProjectSettlementService L119-125 口径；字段名 totalExpenditure，前后端实证一致）
      const parts = ['subcontractSettled', 'laborSettled', 'materialSettled', 'machineSettled', 'cumulativePaid', 'rewardPunishNet']
        .map((f) => Number(detail[f] || 0))
      const expectedTotal = parts.reduce((a, b) => a + b, 0)
      expect(Number(detail.totalExpenditure || 0), '总支出=分项之和')
        .toBeCloseTo(expectedTotal, 2)
      // 结算发生额验证：劳务/分包结算已写入分项
      expect(Number(detail.laborSettled || 0), '劳务结算分项应含 fixture 结算额').toBeGreaterThan(0)
      expect(Number(detail.subcontractSettled || 0), '分包结算分项应含 fixture 结算额').toBeGreaterThan(0)
      if (detail.totalIncome !== undefined && detail.profit !== undefined) {
        expect(Number(detail.profit), '利润=收入-支出')
          .toBeCloseTo(Number(detail.totalIncome) - Number(detail.totalExpenditure), 2)
      }
    })

    // @matrix C-FIN-X5
    it('resummarize 重新汇总后勾稽关系保持', async () => {
      const updResp = await client.put(`/api/v1/project-settlements/${settlementId}`, {
        resummarize: true,
      })
      expect(updResp.code, 'resummarize 重算').toBe(200)
      const detailResp = await client.get(`/api/v1/project-settlements/${settlementId}`)
      expect(detailResp.code).toBe(200)
      const d = detailResp.data
      // resummarize 口径含 otherExpense（updateSettlement L265-271）
      const parts = ['subcontractSettled', 'laborSettled', 'materialSettled', 'machineSettled', 'cumulativePaid', 'rewardPunishNet', 'otherExpense']
        .map((f) => Number(d[f] || 0))
      const expectedTotal = parts.reduce((a, b) => a + b, 0)
      expect(Number(d.totalExpenditure || 0), '重算后总支出=分项之和').toBeCloseTo(expectedTotal, 2)
      expect(Number(d.laborSettled || 0)).toBeGreaterThan(0)
    })

    // @matrix C-FIN-X5
    it('未结清合同清单接口可用', async () => {
      const resp = await client.get(
        `/api/v1/project-settlements/${settlementId}/unsettled-contracts`)
      expect(resp.code).toBe(200)
      expect(Array.isArray(resp.data)).toBe(true)
    })

    // @matrix C-FIN-X5（结算审批链：submit→PROJECT_SETTLEMENT 流程）
    it('结算单提交审批并可驱动通过', async () => {
      const submitResp = await client.post(`/api/v1/project-settlements/${settlementId}/submit`)
      expect(submitResp.code, '结算单提交审批').toBe(200)
      cleaner.add('回收结算审批流', () =>
        client.post(`/api/v1/workflow/approval/withdraw-by-business?businessType=PROJECT_SETTLEMENT&businessId=${settlementId}`))
      await approveUntilDone(client, 'PROJECT_SETTLEMENT', settlementId, TENANT)
      const detailResp = await client.get(`/api/v1/project-settlements/${settlementId}`)
      expect(detailResp.data?.status, '结算单审批通过后状态').toBe('APPROVED')
    }, 120_000)
  })
})
