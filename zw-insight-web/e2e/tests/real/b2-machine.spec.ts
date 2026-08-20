/**
 * 真实模式 E2E：B-2 机械管理账本补测（账本全量补齐 M3，2026-08）
 *
 * @matrix B-6-8 机械合同提交直批 EFFECTIVE / B-7-7 台账删除引用守卫 /
 *   B-8-3 进场→IN_FIELD / B-8-6 重复进场拦截 / B-8-x 退场未结算拦截+结算后放行 /
 *   B-9-5 工作日志仅 IN_FIELD 可记 / B-J-X4 退场后记日志拦截 /
 *   B-10-2/B-10-7 维修 report→dispatch→complete 状态机 /
 *   B-12-6 结算创建聚合计价 / B-12-7 重复周期拦截 /
 *   B-11-6 结算总览回写 / B-J-X2/X3/X5 结算审批→日志 SETTLED+合同累计回写
 *
 * 实证（探测 2026-08）：
 *   - 机械合同 submit 直批 DRAFT→EFFECTIVE（无 BPMN）；但系统默认预算控制配置为
 *     BLOCK，submit 的 @BudgetCheck(MACHINE) 无预算时拦截 → beforeAll 自置
 *     MACHINE 预算直批后放行；单价=contractAmount，台班计价
 *     subtotal=ΣshiftCount×contractAmount（MachineWorkSettlementService L189-195）
 *   - 台账 save 强制 REGISTERED；delete @ReferenceCheck（进出场记录 biz_machine_entry +
 *     工作量记录 biz_machine_work_log 双引用守卫）
 *   - 进场仅 REGISTERED/OUT_FIELD；退场仅 IN_FIELD 且要求该机械工作量全部 SETTLED
 *   - 工作日志仅 IN_FIELD 可记；save 强制 settlementStatus=null 防伪造；
 *     SETTLED 日志不可编辑/删除
 *   - 维修 report→REPORTED（reportDate=今天）→dispatch 仅 REPORTED→complete 仅
 *     DISPATCHED/REPAIRING
 *   - 结算创建：周期倒置/重叠拒（countOverlapping）；排除已结算日志；无可结算日志拒；
 *     编号 JXJS-yyyyMM-序号；status 0 草稿；submit 仅 0/3→BPMN machine_settlement；
 *     审批通过回写日志 SETTLED + 按机械名称匹配合同累加 cumulativeSettlement
 *   - 审批链两节点（machine_settlement.bpmn）：managerApproval assignee=${initiator}
 *     （/todo 可见）→ financeApproval candidateGroups=FINANCE（无 assignee，/todo 仅
 *     assignee 过滤→不可见，后端无 claim 端点→SSH 定位 taskId+SUPER_ADMIN 直 complete）
 *
 * 残留声明：台账 A（被 SETTLED 日志+entry 引用删不掉）、entry×2、SETTLED 工作日志×1、
 * APPROVED 结算单×1（仅 0/3 可删）、APPROVED 预算×1，均 E2E_TEST_ 前缀可识别
 * （巡检兜底）；EFFECTIVE 合同 afterAll 经 E2eTestGuard marker 删除，项目 best-effort。
 *
 * 纯前端守卫用例（B6/B7/B8/B9/B10/B11/B12/B13 共 45 例 vitest）见
 * src/__tests__/machine-matrix / machine-settlement-matrix 组件测试。
 */
import { test, expect } from '@playwright/test'
import { authedApiContext, e2ePrefix, todayStr, runRemote, type AuthedContext } from './real-helper'

test.describe.configure({ mode: 'serial' })

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'
const PREFIX = e2ePrefix()
const TODAY = todayStr()

let authed: AuthedContext | null = null
let projectId = ''
let budgetId = ''
let contractId = ''     // 机械合同（EFFECTIVE，结算单价来源）
let ledgerAId = ''      // 主链台账（进场→日志→结算，最终残留）
const MACHINE_A = `${PREFIX}_挖掘机A`
const MACHINE_B = `${PREFIX}_挖掘机B`
const CONTRACT_NAME = `${PREFIX}_挖机租赁合同`
const UNIT_PRICE = 500  // contractAmount 即结算单价（台班计价实证）

// 雪花 ID 纪律：创建 payload 大 ID 一律字符串；resp.json() 同损精度，
// id 定位一律 String() 归一化比较或按业务名重新定位。

async function apiJson(method: 'GET' | 'POST' | 'PUT' | 'DELETE', path: string, data?: any) {
  const resp = method === 'GET'
    ? await authed!.get(`${API_BASE}${path}`)
    : method === 'DELETE'
      ? await authed!.delete(`${API_BASE}${path}`)
      : method === 'PUT'
        ? await authed!.put(`${API_BASE}${path}`, { data })
        : await authed!.post(`${API_BASE}${path}`, { data })
  return { status: resp.status(), body: await resp.json().catch(() => null) }
}

async function findMachineContract(name: string) {
  const pg = await apiJson('GET', `/api/v1/machine/contract/page?page=1&size=50&projectId=${projectId}`)
  return (pg.body?.data?.records || []).find((c: any) => c.contractName === name)
}

async function findLedger(machineName: string) {
  const pg = await apiJson('GET', `/api/v1/machine/ledger/page?page=1&size=50&machineName=${encodeURIComponent(machineName)}`)
  return (pg.body?.data?.records || []).find((l: any) => l.machineName === machineName)
}

/** 循环完成目标业务的全部待办（SUPER_ADMIN 可完成任意任务，a3-contract 实证范式） */
async function completeAllTodos(businessId: string | number, maxRounds = 6): Promise<void> {
  for (let i = 0; i < maxRounds; i++) {
    const resp = await authed!.get(`${API_BASE}/api/v1/workflow/approval/todo`, { params: { page: 1, size: 50 } })
    const todos = ((await resp.json()).data?.records || []).filter((t: any) => String(t.businessId) === String(businessId))
    if (todos.length === 0) return
    const c = await authed!.post(`${API_BASE}/api/v1/workflow/approval/complete`, {
      data: { taskId: todos[0].taskId, comment: 'E2E b2-machine 审批推进' },
    })
    expect(c.status(), `完成审批任务 ${todos[0].taskId}`).toBe(200)
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error(`businessId=${businessId} 待办 ${maxRounds} 轮未清空`)
}

/**
 * 完成 financeApproval 候选组任务（实证 2026-08）：
 * machine_settlement.bpmn 第二节点 candidateGroups=FINANCE 无 assignee，
 * /todo 接口仅按 assignee 过滤（ApprovalService.getMyTodoTasks）→ admin 待办不可见，
 * 且后端无 claim/候选任务查询端点。故经 SSH 按流程实例定位运行中任务 taskId，
 * SUPER_ADMIN 直接 API complete（assertTaskAssignee 对 SUPER_ADMIN 放行）。
 * 与 auth-real.setup 从 Redis 取验证码同档：审批动作本身走真实 API。
 */
async function completeFinanceTodo(workflowInstanceId: string): Promise<void> {
  const out = runRemote(
    `docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -N -e "SELECT ID_ FROM ACT_RU_TASK WHERE PROC_INST_ID_='${workflowInstanceId}';" 2>/dev/null`
  )
  const taskIds = out.trim().split('\n').filter(Boolean)
  expect(taskIds.length, `流程实例 ${workflowInstanceId} 应有运行中任务`).toBeGreaterThan(0)
  for (const taskId of taskIds) {
    const c = await authed!.post(`${API_BASE}/api/v1/workflow/approval/complete`, {
      data: { taskId, comment: 'E2E b2-machine 审批推进（FINANCE 候选组任务）' },
    })
    expect(c.status(), `完成 financeApproval 任务 ${taskId}`).toBe(200)
    await new Promise((r) => setTimeout(r, 500))
  }
}

test.beforeAll(async () => {
  authed = await authedApiContext()
  const cr = await apiJson('POST', '/api/v1/project', { projectName: `${PREFIX}_机械项目`, projectNature: '新建', projectType: '市政工程' })
  expect(cr.body?.code, '创建承载项目').toBe(200)
  const pg = await apiJson('GET', `/api/v1/project/page?pageNum=1&pageSize=5&projectName=${encodeURIComponent(`${PREFIX}_机械项目`)}`)
  projectId = String(pg.body?.data?.records?.[0]?.id || '')
  expect(projectId, '承载项目应可定位').toBeTruthy()
  // MACHINE 预算前提（系统默认 BLOCK：无预算时合同 submit 的 @BudgetCheck 拦截）
  const bg = await apiJson('POST', '/api/v1/budget', {
    projectId, budgetType: 'ORIGINAL', totalAmount: 100000,
    details: [
      { costCategory: 'MACHINE', itemName: `${PREFIX}_机械项`, unit: '台班', budgetQuantity: 200, budgetUnitPrice: 500, budgetTotalPrice: 100000 },
    ],
  })
  expect(bg.body?.code, '创建 MACHINE 预算').toBe(200)
  const bgPg = await apiJson('GET', `/api/v1/budget/page?page=1&size=50&projectId=${projectId}`)
  budgetId = String((bgPg.body?.data?.records || []).find((b: any) => b.budgetType === 'ORIGINAL')?.id || '')
  expect(budgetId, '预算应可定位').toBeTruthy()
  const bgSub = await apiJson('POST', `/api/v1/budget/${budgetId}/submit`)
  expect(bgSub.body?.code, '预算提交直批').toBe(200)
})

test.afterAll(async () => {
  if (!authed) return
  // EFFECTIVE 合同经 E2eTestGuard marker（contractName 前缀）可删；台账/日志/结算残留见文件头
  if (contractId) await authed.delete(`${API_BASE}/api/v1/machine/contract/${contractId}`).catch(() => {})
  await authed.delete(`${API_BASE}/api/v1/project/${projectId}`).catch(() => {})
  await authed.dispose()
})

test.describe('B-2 机械合同（直批生效）', () => {
  test('@matrix B-6-8 合同创建 DRAFT→提交直批 EFFECTIVE→非草稿重提交拦截', async () => {
    const cr = await apiJson('POST', '/api/v1/machine/contract', {
      projectId, contractName: CONTRACT_NAME, supplierName: `${PREFIX}_供应商`,
      machineName: MACHINE_A, rentalType: '台班', signingDate: TODAY,
      startDate: TODAY, contractAmount: UNIT_PRICE,
    })
    expect(cr.body?.code, '创建机械合同').toBe(200)
    const contract = await findMachineContract(CONTRACT_NAME)
    expect(contract, '合同应可按名称定位').toBeTruthy()
    expect(contract.status).toBe('DRAFT')
    contractId = String(contract.id)
    const sub = await apiJson('POST', `/api/v1/machine/contract/${contractId}/submit`)
    expect(sub.body?.code, '提交直批').toBe(200)
    const detail = await apiJson('GET', `/api/v1/machine/contract/${contractId}`)
    expect(detail.body?.data?.status).toBe('EFFECTIVE')
    // 非草稿重提交拦截
    const sub2 = await apiJson('POST', `/api/v1/machine/contract/${contractId}/submit`)
    expect(sub2.body?.code, 'EFFECTIVE 重提交应拦截').not.toBe(200)
    expect(sub2.body?.message).toContain('仅草稿状态可提交')
  })
})

test.describe('B-2 台账与进出场状态机', () => {
  test('@matrix B-7-x 台账创建强制 REGISTERED + 无引用删除成功', async () => {
    const crA = await apiJson('POST', '/api/v1/machine/ledger', {
      machineName: MACHINE_A, machineCode: `${PREFIX}_JX01`, machineType: '土方机械', ownerType: 'OWN',
    })
    expect(crA.body?.code, '创建台账A').toBe(200)
    const crB = await apiJson('POST', '/api/v1/machine/ledger', {
      machineName: MACHINE_B, machineCode: `${PREFIX}_JX02`, machineType: '土方机械', ownerType: 'RENT',
    })
    expect(crB.body?.code, '创建台账B').toBe(200)
    const ledgerA = await findLedger(MACHINE_A)
    const ledgerB = await findLedger(MACHINE_B)
    expect(ledgerA?.status, 'save 强制 REGISTERED').toBe('REGISTERED')
    expect(ledgerB?.status).toBe('REGISTERED')
    ledgerAId = String(ledgerA.id)
    // 台账B：REGISTERED 状态记日志拦截（B-9-5 反向：仅 IN_FIELD 可记）→ 随后无引用删除成功
    const log = await apiJson('POST', '/api/v1/machine/work-log', {
      machineId: String(ledgerB.id), projectId, workDate: TODAY, shiftCount: 1,
    })
    expect(log.body?.code, '非在场机械记日志应拦截').not.toBe(200)
    expect(log.body?.message).toContain('仅在场机械可记录工作日志')
    const del = await apiJson('DELETE', `/api/v1/machine/ledger/${ledgerB.id}`)
    expect(del.body?.code, '无引用台账删除').toBe(200)
    expect(await findLedger(MACHINE_B), '删除后不可再定位').toBeUndefined()
  })

  test('@matrix B-7-7 台账删除引用守卫（进出场记录引用拦截）', async () => {
    // 先造 entry 引用（进场同时推进状态机，下一测试断言）
    const entry = await apiJson('POST', '/api/v1/machine/entry/in', { machineId: ledgerAId, projectId, entryDate: TODAY })
    expect(entry.body?.code, '进场').toBe(200)
    const del = await apiJson('DELETE', `/api/v1/machine/ledger/${ledgerAId}`)
    expect(del.body?.code, '被引用台账删除应拦截').not.toBe(200)
    expect(del.body?.message, '引用守卫文案').toMatch(/引用|关联/)
    expect(await findLedger(MACHINE_A), '拦截后台账应完好').toBeTruthy()
  })

  test('@matrix B-8-3/B-8-6 进场→IN_FIELD + 重复进场拦截', async () => {
    const ledger = await findLedger(MACHINE_A)
    expect(ledger.status, '进场后台账应 IN_FIELD').toBe('IN_FIELD')
    const dup = await apiJson('POST', '/api/v1/machine/entry/in', { machineId: ledgerAId, projectId, entryDate: TODAY })
    expect(dup.body?.code, 'IN_FIELD 重复进场应拦截').not.toBe(200)
    expect(dup.body?.message).toContain('仅已登记或已退场的机械可进场')
  })
})

test.describe('B-2 工作日志与退场守卫', () => {
  test('@matrix B-9-5/B-J-X1 IN_FIELD 记日志成功 + 未结算退场拦截', async () => {
    const cr = await apiJson('POST', '/api/v1/machine/work-log', {
      machineId: ledgerAId, projectId, workDate: TODAY, shiftCount: 2, oilConsumption: 30,
    })
    expect(cr.body?.code, '在场机械记日志').toBe(200)
    const pg = await apiJson('GET', `/api/v1/machine/work-log/page?page=1&size=50&machineId=${ledgerAId}`)
    const logRec = (pg.body?.data?.records || [])[0]
    expect(logRec, '工作日志应可定位').toBeTruthy()
    expect(logRec.settlementStatus, '新日志不得为 SETTLED（实证：save 置 null 后 DB 默认值回落 UNSETTLED）').not.toBe('SETTLED')
    // 退场拦截：存在未结算工作量记录
    const out = await apiJson('POST', '/api/v1/machine/entry/out', { machineId: ledgerAId, projectId, entryDate: TODAY })
    expect(out.body?.code, '未结算退场应拦截').not.toBe(200)
    expect(out.body?.message).toContain('未结算的工作量记录')
  })
})

test.describe('B-2 维修状态机', () => {
  test('@matrix B-10-2/B-10-7 report→dispatch→complete + 状态守卫', async () => {
    const cr = await apiJson('POST', '/api/v1/machine/repair/report', {
      machineId: ledgerAId, projectId, faultDescription: `${PREFIX}_液压故障`,
    })
    expect(cr.body?.code, '报修').toBe(200)
    const pg = await apiJson('GET', `/api/v1/machine/repair/page?page=1&size=50&machineId=${ledgerAId}`)
    const repair = (pg.body?.data?.records || []).find((r: any) => String(r.faultDescription).includes(PREFIX))
    expect(repair, '维修记录应可定位').toBeTruthy()
    expect(repair.repairStatus).toBe('REPORTED')
    expect(repair.reportDate, 'reportDate 强制今天').toBe(TODAY)
    const dispatch = await apiJson('POST', `/api/v1/machine/repair/${repair.id}/dispatch?repairPerson=${encodeURIComponent(`${PREFIX}_维修工`)}`)
    expect(dispatch.body?.code, '派工').toBe(200)
    const complete = await apiJson('POST', `/api/v1/machine/repair/${repair.id}/complete`, { repairCost: 500 })
    expect(complete.body?.code, '完成维修').toBe(200)
    const after = await apiJson('GET', `/api/v1/machine/repair/page?page=1&size=50&machineId=${ledgerAId}`)
    expect((after.body?.data?.records || []).find((r: any) => String(r.id) === String(repair.id))?.repairStatus).toBe('COMPLETED')
    // 状态守卫：COMPLETED 不可再派工（仅 REPORTED 可派工）
    const guard = await apiJson('POST', `/api/v1/machine/repair/${repair.id}/dispatch?repairPerson=x`)
    expect(guard.body?.code, 'COMPLETED 派工应拦截').not.toBe(200)
    expect(guard.body?.message).toContain('仅已报修状态可派工')
    // 维修记录无守卫直接删除（清理）
    const del = await apiJson('DELETE', `/api/v1/machine/repair/${repair.id}`)
    expect(del.body?.code, '维修记录删除').toBe(200)
  })
})

test.describe('B-2 机械结算（聚合+BPMN+回写）', () => {
  let settlementId = ''

  test('@matrix B-12-6/B-12-7 结算创建聚合计价 + 周期倒置/重叠/空周期拦截', async () => {
    // 周期倒置
    const inv = await apiJson('POST', '/api/v1/machine/settlement', { projectId, periodStart: TODAY, periodEnd: '2020-01-01' })
    expect(inv.body?.code, '周期倒置应拦截').not.toBe(200)
    // 空周期（无日志区间）
    const empty = await apiJson('POST', '/api/v1/machine/settlement', { projectId, periodStart: '2020-01-01', periodEnd: '2020-01-02' })
    expect(empty.body?.code, '无可结算日志应拦截').not.toBe(200)
    expect(empty.body?.message).toContain('无可结算的工作量记录')
    // 正常创建：shiftCount 2 × contractAmount 500 = 1000（台班计价，机械名称匹配合同）
    const cr = await apiJson('POST', '/api/v1/machine/settlement', { projectId, periodStart: TODAY, periodEnd: TODAY })
    expect(cr.body?.code, '创建结算单').toBe(200)
    expect(cr.body?.data?.settlementId, '创建响应应返回结算单ID').toBeTruthy()
    const pg = await apiJson('GET', `/api/v1/machine/settlement?projectId=${projectId}&page=1&size=20`)
    const settlement = (pg.body?.data?.records || []).find((s: any) => s.periodStart === TODAY && String(s.projectId) === projectId)
    expect(settlement, '结算单应可定位').toBeTruthy()
    settlementId = String(settlement.id)
    expect(settlement.settlementCode, '编号 JXJS- 前缀').toMatch(/^JXJS-/)
    expect(settlement.status, '草稿 0').toBe(0)
    expect(Number(settlement.totalAmount), '聚合金额=ΣshiftCount×合同单价').toBe(1000)
    // 周期重叠拦截
    const dup = await apiJson('POST', '/api/v1/machine/settlement', { projectId, periodStart: TODAY, periodEnd: TODAY })
    expect(dup.body?.code, '重叠周期应拦截').not.toBe(200)
    expect(dup.body?.message).toContain('结算周期不能重叠')
  })

  test('@matrix B-J-X2/X3/X5/B-11-6 提交 BPMN→审批通过→日志 SETTLED+合同累计+总览回写', async () => {
    expect(settlementId).toBeTruthy()
    const sub = await apiJson('POST', `/api/v1/machine/settlement/${settlementId}/submit`)
    expect(sub.body?.code, '结算提交（machine_settlement BPMN 未部署则此处失败→DATA 受阻登记）').toBe(200)
    // 第一节点 managerApproval assignee=${initiator}：/todo 接口可见，常规推进
    await completeAllTodos(settlementId)
    // 第二节点 financeApproval 候选组 FINANCE：经流程实例定位 taskId 后 SUPER_ADMIN 直 complete
    const mid = await apiJson('GET', `/api/v1/machine/settlement/${settlementId}`)
    const wfId = mid.body?.data?.workflowInstanceId
    expect(wfId, '提交后应有 workflowInstanceId').toBeTruthy()
    await completeFinanceTodo(wfId)
    const detail = await apiJson('GET', `/api/v1/machine/settlement/${settlementId}`)
    expect(detail.body?.data?.status, '审批通过应 status=2').toBe(2)
    // 工作日志回写 SETTLED
    const pg = await apiJson('GET', `/api/v1/machine/work-log/page?page=1&size=50&machineId=${ledgerAId}`)
    expect((pg.body?.data?.records || [])[0]?.settlementStatus, '日志应回写 SETTLED').toBe('SETTLED')
    // 合同累计结算回写（按机械名称匹配 EFFECTIVE 合同）
    const contract = await apiJson('GET', `/api/v1/machine/contract/${contractId}`)
    expect(Number(contract.body?.data?.cumulativeSettlement), '合同累计结算').toBe(1000)
    // 总览：totalSettled=已审批明细合计
    const summary = await apiJson('GET', `/api/v1/machine/settlement/summary?projectId=${projectId}`)
    expect(summary.body?.code).toBe(200)
    expect(Number(summary.body?.data?.totalSettledAmount), '总览已结算金额（实证字段 totalSettledAmount）').toBe(1000)
    expect(summary.body?.data?.settlementCount, '已审批结算单数量').toBe(1)
    // 已结算日志不可删除（B4 修复钉住）
    const logId = String((pg.body?.data?.records || [])[0]?.id)
    const delLog = await apiJson('DELETE', `/api/v1/machine/work-log/${logId}`)
    expect(delLog.body?.code, 'SETTLED 日志删除应拦截').not.toBe(200)
    expect(delLog.body?.message).toContain('已结算的工作日志不可删除')
  })

  test('@matrix B-8-x/B-J-X4 结算后退场放行 + 退场后记日志拦截', async () => {
    const out = await apiJson('POST', '/api/v1/machine/entry/out', { machineId: ledgerAId, projectId, entryDate: TODAY })
    expect(out.body?.code, '全部结算后退场应放行').toBe(200)
    const ledger = await findLedger(MACHINE_A)
    expect(ledger.status).toBe('OUT_FIELD')
    const log = await apiJson('POST', '/api/v1/machine/work-log', {
      machineId: ledgerAId, projectId, workDate: TODAY, shiftCount: 1,
    })
    expect(log.body?.code, '退场后记日志应拦截').not.toBe(200)
    expect(log.body?.message).toContain('仅在场机械可记录工作日志')
  })
})
