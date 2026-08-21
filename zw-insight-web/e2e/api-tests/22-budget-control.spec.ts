/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * 22 - 预算管控端到端（P1 补测，租户 9999）
 *
 * @matrix P1-预算管控 | tests/frontend-test-case-matrix.md 附录二
 *   A-14   → BLOCK/WARN_ONLY/EXEMPT 拦截语义端到端
 *   A-X15  → 预算批准→变更可选（预算 submit 直批 APPROVED 实证）
 *   A-X16/X17 → 变更审批链正向闭环：budget_change_approval BPMN 已补齐（台账缺口#2 解除），
 *               submit→SUBMITTED→withdraw→WITHDRAWN 状态机 + DRAFT 撤回负向
 *   A-X18/X19 → BLOCK 拦截支出合同提交、删除项目级配置回落全局
 *
 * 前置数据纪律（BudgetBlockIntegrationTest 同源模式）：
 * - 预算明细 biz_budget_detail 无写 API，SSH 直插（固定 ID 段 99990201+）
 * - BLOCK/WARN_ONLY/EXEMPT 切换仅作用于本 spec 创建的测试项目（项目级配置），
 *   用例内即时切回 + afterAll 兜底删除，防污染其他用例/批次
 * - E2E_TEST_ 前缀 + cleaner 逆序回收 + 禁止容忍断言
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { ApiClient } from './api-client'
import { TestDataCleaner, PREFIX } from './test-data'
import { execMysql, queryMysql } from './helpers/redis-probe'

const TENANT = '9999'
const LABOR_BUDGET = 100000   // LABOR 科目额度
const MATERIAL_BUDGET = 100000 // MATERIAL 科目额度
// 固定 ID：租户 9999 隔离，与雪花 ID 无碰撞（BudgetBlockIntegrationTest 同模式）
const BUDGET_QUERY_ID = 99990201
const DETAIL_ID_LABOR = 99990202
const DETAIL_ID_MATERIAL = 99990203

describe('22 - 预算管控端到端（租户 9999）', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let projectId: number
  let budgetId: number
  let configId: number | undefined
  let baselineConfigMode: string // 无项目级配置时的全局生效模式（回落断言基准）

  /** 查当前项目生效配置 */
  async function getEffective(): Promise<any> {
    const resp = await client.get(`/api/v1/budget-control-configs/project/${projectId}`)
    expect(resp.code, `查询生效配置：${resp.message}`).toBe(200)
    return resp.data
  }

  /** 创建/更新项目级配置并记录 configId（同一项目仅一条配置：
   * sys_budget_control_config 唯一键 uk_tenant_project(tenant_id,project_id)，
   * 逻辑删除后重建会唯一键冲突 500（实证），故模式切换走 update 复用同一配置） */
  async function setConfig(controlMode: string, warningThreshold = 80): Promise<void> {
    if (configId) {
      const upd = await client.put(`/api/v1/budget-control-configs/${configId}`, {
        projectId, controlMode, warningThreshold,
      })
      expect(upd.code, `更新配置为 ${controlMode}：${upd.message}`).toBe(200)
    } else {
      const resp = await client.post('/api/v1/budget-control-configs', {
        projectId, controlMode, warningThreshold,
      })
      expect(resp.code, `创建配置 ${controlMode}：${resp.message}`).toBe(200)
      const eff = await getEffective()
      configId = eff.id
      cleaner.add('删除预算控制配置', () =>
        client.delete(`/api/v1/budget-control-configs/${configId}`))
    }
  }

  /** 创建劳务合同（LABOR 科目触发 @BudgetCheck），返回响应 */
  async function createLaborContract(amount: number, seq: number): Promise<any> {
    return client.post('/api/v1/labor/contract', {
      projectId,
      contractName: `${PREFIX}_预算管控_劳务${seq}`,
      contractCode: `${PREFIX}_BL${seq}`,
      teamName: `${PREFIX}_班组${seq}`,
      contractAmount: amount,
      startDate: '2026-01-01',
      endDate: '2026-12-31',
    })
  }

  /** 清理指定名称前缀的劳务合同 */
  async function cleanupLaborContracts(): Promise<void> {
    const page = await client.get('/api/v1/labor/contract/page', {
      page: 1, size: 50, projectId,
    })
    for (const c of page.data?.records || []) {
      if (String(c.contractName || '').includes(`${PREFIX}_预算管控`)) {
        await client.delete(`/api/v1/labor/contract/${c.id}`)
      }
    }
  }

  beforeAll(async () => {
    client = new ApiClient()
    const login = await client.login('t9999admin', '123456')
    expect(String(login.tenantId)).toBe(TENANT)
    cleaner = new TestDataCleaner()

    // 1. 创建测试项目
    const prjName = `${PREFIX}_预算管控`
    const prjResp = await client.post('/api/v1/project', {
      projectName: prjName, projectType: 'BUILDING', projectAddress: 'P1预算管控测试', needTender: 0,
    })
    expect(prjResp.code, '创建预算管控测试项目').toBe(200)
    const prjPage = await client.get('/api/v1/project/page', { page: 1, size: 10, projectName: prjName })
    const prj = (prjPage.data?.records || []).find((p: any) => p.projectName === prjName)
    expect(prj).toBeDefined()
    projectId = prj.id
    cleaner.add('删除预算管控测试项目', async () => {
      const resp = await client.delete(`/api/v1/project/${projectId}`)
      if (resp.code !== 200) {
        // API 删除受状态守卫限制（仅草稿可删实证），SSH 兜底（仅 biz_% tenant 9999）
        execMysql(`DELETE FROM biz_project WHERE tenant_id=9999 AND id=${projectId}`)
      }
    })

    // 2. 记录无项目级配置时的全局生效模式（回落断言基准）
    const baselineEff = await getEffective()
    baselineConfigMode = baselineEff.controlMode

    // 3. 创建预算（DRAFT）
    const budgetResp = await client.post('/api/v1/budget', {
      projectId, budgetType: 'ORIGINAL', totalAmount: LABOR_BUDGET + MATERIAL_BUDGET,
    })
    expect(budgetResp.code, '创建目标成本').toBe(200)
    const budgetPage = await client.get('/api/v1/budget/page', { page: 1, size: 10, projectId })
    const budget = (budgetPage.data?.records || [])[0]
    expect(budget).toBeDefined()
    budgetId = budget.id
    cleaner.add('删除预算', async () => {
      const resp = await client.delete(`/api/v1/budget/${budgetId}`)
      if (resp.code !== 200) {
        // APPROVED 预算 API 删除被拒（实证残留），SSH 兜底（含明细）
        execMysql(`DELETE FROM biz_budget_detail WHERE tenant_id=9999 AND budget_id=${budgetId}`)
        execMysql(`DELETE FROM biz_budget WHERE tenant_id=9999 AND id=${budgetId}`)
      }
    })

    // 4. SSH 直插预算明细（biz_budget_detail 无写 API，BudgetBlockIntegrationTest 同模式；
    //    SSH 偶发抖动实证，单次重试兜底）
    const detailSql =
      `INSERT INTO biz_budget_detail (id, budget_id, cost_category, item_name, budget_quantity, budget_unit_price, budget_total_price, tenant_id, created_by, created_at, updated_at, deleted, version) VALUES ` +
      `(${DETAIL_ID_LABOR}, ${budgetId}, 'LABOR', 'P1预算管控-人工', 1, ${LABOR_BUDGET}, ${LABOR_BUDGET}, ${TENANT}, 0, NOW(), NOW(), 0, 0), ` +
      `(${DETAIL_ID_MATERIAL}, ${budgetId}, 'MATERIAL', 'P1预算管控-材料', 1, ${MATERIAL_BUDGET}, ${MATERIAL_BUDGET}, ${TENANT}, 0, NOW(), NOW(), 0, 0)`
    try {
      execMysql(detailSql)
    } catch (e) {
      // 重试前清可能部分插入的行（防主键冲突）
      execMysql(`DELETE FROM biz_budget_detail WHERE id IN (${DETAIL_ID_LABOR}, ${DETAIL_ID_MATERIAL})`)
      execMysql(detailSql)
    }
    cleaner.add('删除直插预算明细', async () => {
      execMysql(`DELETE FROM biz_budget_detail WHERE id IN (${DETAIL_ID_LABOR}, ${DETAIL_ID_MATERIAL})`)
    })

    // 5. 提交预算：直批 APPROVED（BudgetService.submit 无流程依赖实证）+ 回写项目预算金额
    const submitResp = await client.post(`/api/v1/budget/${budgetId}/submit`)
    expect(submitResp.code, `预算提交（直批）：${submitResp.message}`).toBe(200)
  }, 120_000)

  afterAll(async () => {
    if (!cleaner) return
    try { await cleanupLaborContracts() } catch { /* 兜底清理失败不遮蔽 */ }
    await cleaner.cleanup(client)
    // 兜底：直插明细残留清理（cleaner 已含，此处防异常路径）
    try {
      execMysql(`DELETE FROM biz_budget_detail WHERE id IN (${DETAIL_ID_LABOR}, ${DETAIL_ID_MATERIAL})`)
    } catch { /* 忽略 */ }
  }, 120_000)

  // ============ A-14 BLOCK 拦截 ============
  describe('A-14 BLOCK 模式拦截', () => {
    // @matrix A-14/A-X18
    it('设置项目级 BLOCK 配置并实时生效', async () => {
      await setConfig('BLOCK', 80)
      const eff = await getEffective()
      expect(eff.controlMode).toBe('BLOCK')
      expect(String(eff.projectId)).toBe(String(projectId))
    })

    // @matrix A-14
    it('BLOCK：创建超额度劳务合同被拒（含预算提示）', async () => {
      const resp = await createLaborContract(LABOR_BUDGET + 50000, 1)
      expect(resp.code, 'BLOCK 超额度应被拒').not.toBe(200)
      expect(String(resp.message)).toMatch(/预算/)
    })

    // @matrix A-14
    it('BLOCK：额度内创建劳务合同放行', async () => {
      const resp = await createLaborContract(50000, 2)
      expect(resp.code, `额度内应放行：${resp.message}`).toBe(200)
    })
  })

  // ============ A-14 WARN_ONLY ============
  describe('A-14 WARN_ONLY 模式', () => {
    // @matrix A-14
    it('WARN_ONLY：超额度创建合同放行 200', async () => {
      await setConfig('WARN_ONLY', 80)
      const eff = await getEffective()
      expect(eff.controlMode).toBe('WARN_ONLY')
      // 先清理 BLOCK 用例残留合同，避免累计已发生额干扰
      await cleanupLaborContracts()
      const resp = await createLaborContract(LABOR_BUDGET + 50000, 3)
      // 探针实证（2026-08-14）：warning 不在响应体（message=操作成功），
      // 放行语义以 code=200 断言；warning 前端可见性已登记台账（产品改进项）
      expect(resp.code, `WARN_ONLY 超额度应放行：${resp.message}`).toBe(200)
    })

    // @matrix A-14
    it('WARN_ONLY：放行后合同可查询（真实落库）', async () => {
      const page = await client.get('/api/v1/labor/contract/page', { page: 1, size: 20, projectId })
      const found = (page.data?.records || [])
        .find((c: any) => String(c.contractName || '').includes(`${PREFIX}_预算管控_劳务3`))
      expect(found, 'WARN_ONLY 放行的合同应真实落库').toBeDefined()
    })
  })

  // ============ A-14 EXEMPT ============
  describe('A-14 EXEMPT 模式', () => {
    // @matrix A-14
    it('EXEMPT：超额度创建合同直接放行', async () => {
      await setConfig('EXEMPT', 80)
      const eff = await getEffective()
      expect(eff.controlMode).toBe('EXEMPT')
      await cleanupLaborContracts()
      const resp = await createLaborContract(LABOR_BUDGET * 3, 4)
      expect(resp.code, `EXEMPT 应直接放行：${resp.message}`).toBe(200)
    })

    // @matrix A-X19
    it('删除项目级配置后回落全局生效', async () => {
      expect(configId).toBeDefined()
      const delResp = await client.delete(`/api/v1/budget-control-configs/${configId}`)
      expect(delResp.code, '删除项目级配置').toBe(200)
      const eff = await getEffective()
      // 回落：生效配置不再是刚删除的项目级配置（id 不同，回落全局默认）
      expect(String(eff.id)).not.toBe(String(configId))
      expect(['BLOCK', 'WARN_ONLY', 'EXEMPT']).toContain(eff.controlMode)
      baselineConfigMode = eff.controlMode
      configId = undefined
      await cleanupLaborContracts()
    })
  })

  // ============ A-X15~X19 预算变更链 ============
  describe('A-X15~X19 预算变更链', () => {
    let changeId: number

    // @matrix A-X15
    it('预算已批准（submit 直批，变更前置）', async () => {
      const budgetPage = await client.get('/api/v1/budget/page', { page: 1, size: 10, projectId })
      const budget = (budgetPage.data?.records || [])[0]
      expect(budget.status, '预算应为 APPROVED（submit 直批实证）').toBe('APPROVED')
      // 回写项目预算金额断言（BudgetService.submit L134-138 实证）
      const prjResp = await client.get(`/api/v1/project/${projectId}`)
      expect(Number(prjResp.data.budgetAmount), '项目预算金额应回写为明细合计')
        .toBe(LABOR_BUDGET + MATERIAL_BUDGET)
    })

    // @matrix A-X15
    // changeReason 带 E2E_TEST_ 前缀：delete 守卫仅 DRAFT 放行，前缀经 E2eTestGuard 旁路保证清理
    it('创建变更单（仅 APPROVED 预算可选）', async () => {
      const resp = await client.post('/api/v1/budget/change', {
        projectId,
        budgetId,
        changeReason: `E2E_TEST_${Date.now()}_P1预算管控测试变更`,
        details: [{
          budgetDetailId: DETAIL_ID_LABOR,
          costCategory: 'LABOR',
          itemName: 'P1预算管控-人工',
          originalAmount: LABOR_BUDGET,
          adjustAmount: 20000,
        }],
      })
      expect(resp.code, `创建变更单：${resp.message}`).toBe(200)
      const page = await client.get('/api/v1/budget/change', { page: 1, size: 10, projectId })
      const change = (page.data?.records || [])[0]
      expect(change).toBeDefined()
      expect(change.status).toBe('DRAFT')
      changeId = change.id
      cleaner.add('删除变更单', () => client.delete(`/api/v1/budget/change/${changeId}`))
    })

    // @matrix A-X17（withdraw 状态机负向：DRAFT 不可撤回；必须在 submit 前执行）
    it('负向：DRAFT 状态变更单撤回被拒（仅 SUBMITTED 可撤回）', async () => {
      const resp = await client.post(`/api/v1/budget/change/${changeId}/withdraw`)
      expect(resp.code).not.toBe(200)
      expect(String(resp.message)).toContain('仅已提交状态可撤回')
    })

    // @matrix A-X17（update 守卫：仅 DRAFT 可编辑，必须在 submit 前执行）
    it('变更单更新与 trace 查询', async () => {
      const updResp = await client.put(`/api/v1/budget/change/${changeId}`, {
        projectId,
        budgetId,
        changeReason: `E2E_TEST_${Date.now()}_P1预算管控测试变更-更新`,
        details: [{
          budgetDetailId: DETAIL_ID_LABOR,
          costCategory: 'LABOR',
          itemName: 'P1预算管控-人工',
          originalAmount: LABOR_BUDGET,
          adjustAmount: 30000,
        }],
      })
      expect(updResp.code, `更新变更单：${updResp.message}`).toBe(200)
      const traceResp = await client.get('/api/v1/budget/change/trace', { projectId })
      expect(traceResp.code, '变更轨迹查询').toBe(200)
      expect(Array.isArray(traceResp.data)).toBe(true)
    })

    // @matrix A-X16（缺口#2 解除翻正向：budget_change_approval BPMN 已补齐并部署，run 32423703432）
    it('变更单提交：budget_change_approval 流程启动成功（SUBMITTED）', async () => {
      const resp = await client.post(`/api/v1/budget/change/${changeId}/submit`)
      expect(resp.code, `submit：${resp.message}`).toBe(200)
      const page = await client.get('/api/v1/budget/change', { page: 1, size: 10, projectId })
      const change = (page.data?.records || []).find((c: any) => c.id === changeId)
      expect(change?.status, '提交后状态应为 SUBMITTED').toBe('SUBMITTED')
      expect(change?.workflowInstanceId, '流程实例应已创建').toBeTruthy()
    })

    // @matrix A-X17（withdraw 状态机正向：SUBMITTED 可撤回→WITHDRAWN）
    it('提交后撤回成功（SUBMITTED→WITHDRAWN）', async () => {
      const resp = await client.post(`/api/v1/budget/change/${changeId}/withdraw`)
      expect(resp.code, `withdraw：${resp.message}`).toBe(200)
      const page = await client.get('/api/v1/budget/change', { page: 1, size: 10, projectId })
      const change = (page.data?.records || []).find((c: any) => c.id === changeId)
      expect(change?.status, '撤回后状态应为 WITHDRAWN').toBe('WITHDRAWN')
    })
  })
})
