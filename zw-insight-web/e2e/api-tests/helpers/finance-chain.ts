/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * 资金链测试数据工厂 + 审批驱动（2026-08-14 P0 补测基建）
 *
 * @matrix P0-资金链 | tests/frontend-test-case-matrix.md 附录二
 *   C-5 → 付款五分支路由 | C-FIN-X1 → 付款审批回写 | C-FIN-X2 → 开票勾稽
 *   C-FIN-X5 → 结算支出汇总勾稽 | C-FIN-X4/C-13 → 封账闭环
 *
 * 数据纪律（与 test-data.ts 一致）：
 * - 全部 E2E_TEST_ 前缀，cleaner 逆序回收
 * - 审批流回收走 withdrawApprovalByBusiness（O(1) businessKey 定位）
 * - 审批驱动：/todo 优先，候选组任务（无 assignee）兜底 SSH 查 ACT_RU_TASK
 *   （lifecycle-sim-v2.sh db_next_task 同源方案；SUPER_ADMIN 可 complete 任意任务）
 * - 租户 9999 运行（BPMN 部署有保障；封账为租户级隔离不污染租户 1）
 */
import type { ApiClient } from '../api-client'
import type { TestDataCleaner } from '../test-data'
import { PREFIX, withdrawApprovalByBusiness } from '../test-data'
import { queryMysql } from './redis-probe'

/** 五类支出合同（与 payment-apply.vue loadContracts switch 分支一致） */
export type ContractCategory = 'PURCHASE' | 'LABOR' | 'MACHINE' | 'SUBCONTRACT' | 'OTHER_EXPENSE'

export const ALL_CATEGORIES: ContractCategory[] = [
  'PURCHASE', 'LABOR', 'MACHINE', 'SUBCONTRACT', 'OTHER_EXPENSE',
]

/** 机械台账名称（fixture 机械合同按 machineName 匹配结算回写，后端 MachineWorkSettlementService 实证） */
export const machineLedgerName = (suffix = 'FINCHAIN') => `${PREFIX}_${suffix}_挖机`

export interface FinanceFixture {
  projectId: number
  /** 施工（收入）合同：供开票申请引用 */
  constructionContractId: number
  /** 五类支出合同 id */
  contracts: Record<ContractCategory, number>
}

/**
 * 一站式资金链数据工厂：项目 + WARN_ONLY 管控 + 施工合同 + 五类支出合同。
 * 全部注册 cleaner（逆序回收）。失败抛错（禁止半成品 fixture 静默继续）。
 */
export async function createFinanceFixture(
  client: ApiClient,
  cleaner: TestDataCleaner,
  suffix = 'FINCHAIN'
): Promise<FinanceFixture> {
  const name = `${PREFIX}_${suffix}`

  // 1. 项目
  const prjResp = await client.post('/api/v1/project', {
    projectName: name,
    projectType: 'BUILDING',
    projectAddress: 'P0资金链测试地址',
    needTender: 0,
  })
  if (prjResp.code !== 200) {
    throw new Error(`[fixture] 创建项目失败 code=${prjResp.code} msg=${prjResp.message}`)
  }
  const prjPage = await client.get('/api/v1/project/page', {
    page: 1, size: 10, projectName: name,
  })
  const project = (prjPage.data?.records || []).find((p: any) => p.projectName === name)
  if (!project) throw new Error(`[fixture] 项目创建后查询不到: ${name}`)
  const projectId = project.id
  cleaner.add('删除资金链项目', () => client.delete(`/api/v1/project/${projectId}`))

  // 2. 预算管控 WARN_ONLY（支出合同创建不被默认 BLOCK 拦截，合法业务配置）
  const cfgResp = await client.post('/api/v1/budget-control-configs', {
    projectId,
    controlMode: 'WARN_ONLY',
    warningThreshold: 80,
  })
  if (cfgResp.code === 200) {
    const effResp = await client.get(`/api/v1/budget-control-configs/project/${projectId}`)
    const cfgId = effResp.data?.id
    if (cfgId) {
      cleaner.add('删除预算管控配置', () =>
        client.delete(`/api/v1/budget-control-configs/${cfgId}`))
    }
  }

  // 3. 施工（收入）合同
  const cResp = await client.post('/api/v1/contract', {
    projectId,
    contractType: 'REGISTER',
    partyAName: 'E2E资金链甲方',
    signingDate: '2026-01-01',
    startDate: '2026-03-01',
    endDate: '2026-12-31',
    contractAmount: 2000000,
    taxRate: 9,
  })
  if (cResp.code !== 200) {
    throw new Error(`[fixture] 创建施工合同失败 code=${cResp.code} msg=${cResp.message}`)
  }
  const cPage = await client.get('/api/v1/contract/page', { page: 1, size: 10, projectId })
  const construction = (cPage.data?.records || []).find((r: any) => r.projectId === projectId)
  if (!construction) throw new Error('[fixture] 施工合同创建后查询不到')
  cleaner.add('删除施工合同', () => client.delete(`/api/v1/contract/${construction.id}`))

  // 4. 五类支出合同
  const contracts = {} as Record<ContractCategory, number>
  const ts = Date.now()

  const createAndLocate = async (
    category: ContractCategory,
    createPath: string,
    payload: any,
    locatePath: string,
    locateNameField: string,
    locateName: string,
    deletePath: (id: number) => string
  ) => {
    const resp = await client.post(createPath, payload)
    if (resp.code !== 200) {
      throw new Error(`[fixture] 创建${category}合同失败 code=${resp.code} msg=${resp.message}`)
    }
    const pageResp = await client.get(locatePath, { page: 1, size: 20, projectId })
    const found = (pageResp.data?.records || []).find(
      (r: any) => r[locateNameField] === locateName
    )
    if (!found) throw new Error(`[fixture] ${category}合同创建后查询不到: ${locateName}`)
    contracts[category] = found.id
    cleaner.add(`删除${category}合同`, () => client.delete(deletePath(found.id)))
  }

  await createAndLocate('PURCHASE', '/api/v1/purchase/contract', {
    projectId,
    contractName: `${name}_采购`,
    contractCode: `E2E_FPC_${ts}`,
    partyAName: 'E2E甲方',
    partyBName: 'E2E供应商',
    supplierName: 'E2E供应商',
    contractAmount: 500000,
    signingDate: '2026-01-15',
  }, '/api/v1/purchase/contract/page', 'contractName', `${name}_采购`,
  (id) => `/api/v1/purchase/contract/${id}`)

  await createAndLocate('LABOR', '/api/v1/labor/contract', {
    projectId,
    contractName: `${name}_劳务`,
    contractCode: `E2E_FLC_${ts}`,
    teamName: `${name}_班组`,
    contractAmount: 400000,
    startDate: '2026-01-01',
    endDate: '2026-12-31',
  }, '/api/v1/labor/contract/page', 'contractName', `${name}_劳务`,
  (id) => `/api/v1/labor/contract/${id}`)

  await createAndLocate('MACHINE', '/api/v1/machine/contract', {
    projectId,
    contractName: `${name}_机械`,
    contractCode: `E2E_FMC_${ts}`,
    machineName: machineLedgerName(suffix), // 结算回写按 machineName 匹配台账（后端实证）
    supplierName: 'E2E机械供应商',
    rentalType: 'SHIFT', // 台班计价：subtotal = shiftCount × contractAmount（contractAmount 作单价）
    contractAmount: 1000, // 单价 1000 元/台班，保持结算额可控
    startDate: '2026-01-01',
    endDate: '2026-12-31',
  }, '/api/v1/machine/contract/page', 'contractName', `${name}_机械`,
  (id) => `/api/v1/machine/contract/${id}`)

  await createAndLocate('SUBCONTRACT', '/api/v1/subcontract/contract', {
    projectId,
    contractName: `${name}_分包`,
    contractCode: `E2E_FSC_${ts}`,
    subcontractorName: 'E2E分包商',
    contractAmount: 600000,
    startDate: '2026-01-01',
    endDate: '2026-12-31',
  }, '/api/v1/subcontract/contract/page', 'contractName', `${name}_分包`,
  (id) => `/api/v1/subcontract/contract/${id}`)

  await createAndLocate('OTHER_EXPENSE', '/api/v1/contract/other', {
    projectId,
    contractName: `${name}_其他支出`,
    contractCategory: 'OTHER_EXPENSE',
    partyAName: 'E2E甲方',
    partyBName: 'E2E乙方',
    contractAmount: 200000,
    signingDate: '2026-02-01',
  }, '/api/v1/contract/other', 'contractName', `${name}_其他支出`,
  (id) => `/api/v1/contract/other/${id}`)

  return { projectId, constructionContractId: construction.id, contracts }
}

/**
 * 驱动指定单据的审批直至完成（多级循环 complete）。
 *
 * 1. /todo 按 businessType+businessId 匹配任务
 * 2. 匹配不到 → SSH 查 ACT_RU_TASK（候选组任务无 assignee 不入待办）
 * 3. 首轮即无任务 → 抛错（禁止静默通过；流程可能未启动）
 *
 * @returns 完成的级数
 */
export async function approveUntilDone(
  client: ApiClient,
  businessType: string,
  businessId: number | string,
  tenantId = '9999',
  maxLevels = 5
): Promise<number> {
  let handled = 0

  for (let level = 0; level < maxLevels; level++) {
    let taskId: string | null = null

    // 通道 1：待办列表
    const todoResp = await client.get('/api/v1/workflow/approval/todo', {
      page: 1, size: 50,
    })
    const todos: any[] = todoResp.data?.records || todoResp.data || []
    const matched = todos.find(
      (t) => String(t.businessType) === businessType && String(t.businessId) === String(businessId)
    )
    if (matched) {
      taskId = String(matched.taskId || matched.id)
    }

    // 通道 2：候选组任务兜底（ACT_RU_TASK + businessKey 精确定位）
    if (!taskId) {
      const businessKey = `${businessType}:${businessId}`
      const out = queryMysql(
        `SELECT t.ID_ FROM ACT_RU_TASK t JOIN ACT_RU_EXECUTION e ON t.PROC_INST_ID_=e.PROC_INST_ID_ ` +
        `WHERE e.BUSINESS_KEY_='${businessKey}' AND t.TENANT_ID_='${tenantId}' ` +
        `ORDER BY t.CREATE_TIME_ ASC LIMIT 1`
      ).trim()
      if (out) taskId = out.split('\n')[0].trim()
    }

    if (!taskId) {
      if (handled === 0) {
        throw new Error(
          `[approveUntilDone] ${businessType}:${businessId} 无任何待办任务——` +
          `流程未启动或已完成，禁止静默通过`
        )
      }
      break // 全部级完成
    }

    const completeResp = await client.post('/api/v1/workflow/approval/complete', {
      taskId,
      comment: 'E2E P0 资金链自动化审批',
    })
    if (completeResp.code !== 200) {
      throw new Error(
        `[approveUntilDone] complete 失败 taskId=${taskId} code=${completeResp.code} msg=${completeResp.message}`
      )
    }
    handled++
    await new Promise((r) => setTimeout(r, 1000)) // 等待监听器回写
  }

  return handled
}

/**
 * 提交单据并驱动审批通过；注册 withdraw 回收（防流程残留）。
 * @param submitFn 调用方传入的提交动作（各业务 submit 端点不同）
 */
export async function submitAndApprove(
  client: ApiClient,
  cleaner: TestDataCleaner,
  businessType: string,
  businessId: number | string,
  submitFn: () => Promise<{ code: number; message: string }>,
  tenantId = '9999'
): Promise<void> {
  const submitResp = await submitFn()
  if (submitResp.code !== 200) {
    throw new Error(
      `[submitAndApprove] ${businessType}:${businessId} 提交失败 code=${submitResp.code} msg=${submitResp.message}`
    )
  }
  // 提交成功即注册审批流回收（后注册先执行：清理时先 withdraw 再删单据）
  cleaner.add(`回收${businessType}审批流`, () =>
    withdrawApprovalByBusiness(client, businessType, businessId))
  await approveUntilDone(client, businessType, businessId, tenantId)
}
