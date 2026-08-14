/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * 31 - 审批主链端到端（P1 补测，租户 9999）
 *
 * @matrix P1-审批主链 | tests/frontend-test-case-matrix.md 附录二
 *   D-32 退回上一节点/退回发起人/批量通过/终止（API 层，UI 交互在 workflow.spec.ts 扩展）
 *   D-33-12 审批通过→业务单据状态回写（施工合同 CONSTRUCTION_CONTRACT 两级审批实证）
 *   D-33-13/14 关联断言：my-initiated/urge 端点
 *
 * 流程语义实证（construction_contract_approval.bpmn20.xml 两级审批，
 * managerApproval→financeApproval 均 assignee=${initiator}）：
 *   - submit → SUBMITTED；onApproved → EFFECTIVE + 项目回写；onRejected → DRAFT（回退草稿可重提）
 *   - reject-start：流程回退发起人节点（第一级 userTask）+ ApprovalRejectEvent → 合同 DRAFT，
 *     待办保留于第一级（发起人可重新处理继续流程）
 *   - reject-previous：退回上一节点，同样发布 ApprovalRejectEvent → 合同 DRAFT，待办回退第一级
 *   - terminate：终止流程实例 → onRejected → DRAFT，待办清空
 *
 * 数据纪律：E2E_TEST_ 前缀 + cleaner 回收 + withdraw-by-business 兜底 + 禁止容忍断言
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { ApiClient } from './api-client'
import { TestDataCleaner, PREFIX } from './test-data'
import { withdrawApprovalByBusiness } from './test-data'
import { execMysql } from './helpers/redis-probe'

const TENANT = '9999'
const BUSINESS_TYPE = 'CONSTRUCTION_CONTRACT'

describe('31 - 审批主链端到端（租户 9999）', () => {
  let client: ApiClient
  let cleaner: TestDataCleaner
  let projectId: number
  const contractIds: number[] = []

  async function createContract(seq: number): Promise<number> {
    const resp = await client.post('/api/v1/contract', {
      projectId,
      contractType: 'REGISTER',
      partyAName: `${PREFIX}_甲方`,
      signingDate: '2026-08-01',
      startDate: '2026-08-01',
      endDate: '2026-12-31',
      contractAmount: 100000 + seq * 10000,
      taxRate: 9,
    })
    expect(resp.code, `创建施工合同${seq}：${resp.message}`).toBe(200)
    const page = await client.get('/api/v1/contract/page', { page: 1, size: 20, projectId })
    const found = (page.data?.records || []).find((c: any) =>
      Number(c.contractAmount) === 100000 + seq * 10000 && c.status === 'DRAFT')
    expect(found, `合同${seq}应可查到`).toBeDefined()
    contractIds.push(found.id)
    cleaner.add(`删除合同${seq}`, () => client.delete(`/api/v1/contract/${found.id}`))
    return found.id
  }

  async function submitContract(id: number): Promise<void> {
    const resp = await client.post(`/api/v1/contract/${id}/submit`)
    expect(resp.code, `合同提交：${resp.message}`).toBe(200)
    cleaner.add(`撤回合同${id}审批流`, () => withdrawApprovalByBusiness(client, BUSINESS_TYPE, id))
  }

  /** 按 businessId 查待办任务 */
  async function findTodos(businessId: number): Promise<any[]> {
    const resp = await client.get('/api/v1/workflow/approval/todo', { page: 1, size: 50 })
    expect(resp.code).toBe(200)
    return (resp.data?.records || []).filter((t: any) => String(t.businessId) === String(businessId))
  }

  async function getContractStatus(id: number): Promise<string> {
    const resp = await client.get(`/api/v1/contract/${id}`)
    expect(resp.code).toBe(200)
    return resp.data?.status
  }

  beforeAll(async () => {
    client = new ApiClient()
    const login = await client.login('t9999admin', '123456')
    expect(String(login.tenantId)).toBe(TENANT)
    cleaner = new TestDataCleaner()

    const prjName = `${PREFIX}_审批主链`
    const prjResp = await client.post('/api/v1/project', {
      projectName: prjName, projectType: 'BUILDING', projectAddress: 'P1审批主链测试', needTender: 0,
    })
    expect(prjResp.code, '创建审批主链测试项目').toBe(200)
    const prjPage = await client.get('/api/v1/project/page', { page: 1, size: 10, projectName: prjName })
    const prj = (prjPage.data?.records || []).find((p: any) => p.projectName === prjName)
    expect(prj).toBeDefined()
    projectId = prj.id
    cleaner.add('删除审批主链测试项目', () => client.delete(`/api/v1/project/${projectId}`))
    // 兜底：EFFECTIVE 合同 API 不可删（仅草稿可删实证），批量通过用例会产生生效合同，
    // SSH 直删（仅 biz_% tenant 9999，先于项目删除执行：最后注册 = LIFO 最先）
    cleaner.add('兜底清理生效合同', async () => {
      execMysql(`DELETE FROM biz_construction_contract WHERE tenant_id=9999 AND project_id=${projectId}`)
    })
  }, 120_000)

  afterAll(async () => {
    if (!cleaner) return
    await cleaner.cleanup(client)
  }, 120_000)

  // ============ D-32 退回发起人（reject-start） ============
  describe('D-32 退回发起人', () => {
    let cid: number

    // @matrix D-32/D-33-12
    it('提交后待办可查（第一级 assignee=发起人）', async () => {
      cid = await createContract(1)
      await submitContract(cid)
      expect(await getContractStatus(cid)).toBe('SUBMITTED')
      const todos = await findTodos(cid)
      expect(todos.length, '提交后应有待办').toBeGreaterThan(0)
      expect(todos[0].taskDefinitionKey).toBe('managerApproval')
    })

    // @matrix D-32
    it('reject-start：合同回退 DRAFT，流程回退发起人节点（待办保留于第一级）', async () => {
      const todos = await findTodos(cid)
      const resp = await client.post('/api/v1/workflow/approval/reject-start', {
        taskId: todos[0].taskId, comment: 'P1测试退回发起人',
      })
      expect(resp.code, `reject-start：${resp.message}`).toBe(200)
      expect(await getContractStatus(cid), '退回发起人后合同应回退 DRAFT').toBe('DRAFT')
      // 语义实证（ApprovalService.rejectToStart L214-228）：moveActivityIdTo 回退第一级 userTask
      // 且发布 ApprovalRejectEvent，待办保留于第一级供发起人重新处理
      const afterTodos = await findTodos(cid)
      expect(afterTodos.length, '退回后待办应保留于第一级').toBeGreaterThan(0)
      expect(afterTodos[0].taskDefinitionKey).toBe('managerApproval')
      // 清理：终止残留流程实例（用保留的待办 terminate）
      const termResp = await client.post('/api/v1/workflow/approval/terminate', {
        taskId: afterTodos[0].taskId, comment: 'P1清理',
      })
      expect(termResp.code).toBe(200)
    })
  })

  // ============ D-32 退回上一节点（reject-previous，两级流程） ============
  describe('D-32 退回上一节点（两级流程）', () => {
    let cid: number

    // @matrix D-32
    it('第一级通过进入第二级（财务审核）', async () => {
      cid = await createContract(2)
      await submitContract(cid)
      let todos = await findTodos(cid)
      expect(todos[0].taskDefinitionKey).toBe('managerApproval')
      const completeResp = await client.post('/api/v1/workflow/approval/complete', {
        taskId: todos[0].taskId, comment: 'P1测试一级通过',
      })
      expect(completeResp.code, `第一级通过：${completeResp.message}`).toBe(200)
      todos = await findTodos(cid)
      expect(todos.length, '应进入第二级').toBeGreaterThan(0)
      expect(todos[0].taskDefinitionKey, '第二级应为财务审核').toBe('financeApproval')
      // 流程未结束，合同仍 SUBMITTED
      expect(await getContractStatus(cid)).toBe('SUBMITTED')
    })

    // @matrix D-32
    it('reject-previous：第二级退回第一级，同样触发驳回事件合同回 DRAFT', async () => {
      let todos = await findTodos(cid)
      const resp = await client.post('/api/v1/workflow/approval/reject-previous', {
        taskId: todos[0].taskId, comment: 'P1测试退回上一级',
      })
      expect(resp.code, `reject-previous：${resp.message}`).toBe(200)
      todos = await findTodos(cid)
      expect(todos.length, '退回后应仍有待办（第一级）').toBeGreaterThan(0)
      expect(todos[0].taskDefinitionKey, '应退回第一级').toBe('managerApproval')
      // 语义实证（ApprovalService.rejectToPrevious L157-184）：退回同样发布 ApprovalRejectEvent
      expect(await getContractStatus(cid), '驳回事件应使合同回 DRAFT').toBe('DRAFT')
      // 清理：终止残留流程实例
      const termResp = await client.post('/api/v1/workflow/approval/terminate', {
        taskId: todos[0].taskId, comment: 'P1清理',
      })
      expect(termResp.code).toBe(200)
    })
  })

  // ============ D-32 终止流程（terminate） ============
  describe('D-32 终止流程', () => {
    let cid: number

    // @matrix D-32
    it('terminate：流程终止，合同回退 DRAFT，待办清空', async () => {
      cid = await createContract(3)
      await submitContract(cid)
      const todos = await findTodos(cid)
      expect(todos.length).toBeGreaterThan(0)
      const resp = await client.post('/api/v1/workflow/approval/terminate', {
        taskId: todos[0].taskId, comment: 'P1测试终止',
      })
      expect(resp.code, `terminate：${resp.message}`).toBe(200)
      expect(await getContractStatus(cid), '终止后合同应回退 DRAFT').toBe('DRAFT')
      expect((await findTodos(cid)).length, '终止后待办应清空').toBe(0)
    })
  })

  // ============ D-32 批量通过（batch-approve） ============
  describe('D-32 批量通过', () => {
    // @matrix D-32/D-33-12
    it('两单批量通过两级审批后均 EFFECTIVE', async () => {
      const cidA = await createContract(4)
      const cidB = await createContract(5)
      await submitContract(cidA)
      await submitContract(cidB)

      // 第一级批量通过
      let todosA = await findTodos(cidA)
      let todosB = await findTodos(cidB)
      expect(todosA.length).toBeGreaterThan(0)
      expect(todosB.length).toBeGreaterThan(0)
      const batchResp = await client.post('/api/v1/workflow/approval/batch-approve', {
        taskIds: [todosA[0].taskId, todosB[0].taskId],
        comment: 'P1测试批量通过',
      })
      expect(batchResp.code, `批量通过第一级：${batchResp.message}`).toBe(200)

      // 第二级批量通过
      todosA = await findTodos(cidA)
      todosB = await findTodos(cidB)
      expect(todosA.length, '应进入第二级').toBeGreaterThan(0)
      expect(todosB.length, '应进入第二级').toBeGreaterThan(0)
      const batchResp2 = await client.post('/api/v1/workflow/approval/batch-approve', {
        taskIds: [todosA[0].taskId, todosB[0].taskId],
        comment: 'P1测试批量通过二级',
      })
      expect(batchResp2.code, `批量通过第二级：${batchResp2.message}`).toBe(200)

      // 回写断言：两单均 EFFECTIVE
      expect(await getContractStatus(cidA), '批量通过后合同A应 EFFECTIVE').toBe('EFFECTIVE')
      expect(await getContractStatus(cidB), '批量通过后合同B应 EFFECTIVE').toBe('EFFECTIVE')
      expect((await findTodos(cidA)).length).toBe(0)
      expect((await findTodos(cidB)).length).toBe(0)
    }, 120_000)
  })

  // ============ 催办与我发起的 ============
  describe('催办与我发起的', () => {
    let cid: number

    // @matrix D-33-14（关联断言）
    it('urge 催办与计数', async () => {
      cid = await createContract(6)
      await submitContract(cid)
      const todos = await findTodos(cid)
      expect(todos.length).toBeGreaterThan(0)
      const urgeResp = await client.post(`/api/v1/workflow/approval/urge/${todos[0].taskId}`)
      expect(urgeResp.code, `催办：${urgeResp.message}`).toBe(200)
      const countResp = await client.get(`/api/v1/workflow/approval/urge/count/${todos[0].taskId}`)
      expect(countResp.code).toBe(200)
      expect(Number(countResp.data), '催办计数应≥1').toBeGreaterThanOrEqual(1)
    })

    // @matrix D-33-13（关联断言）
    it('my-initiated 含已发起单据', async () => {
      const resp = await client.get('/api/v1/workflow/approval/my-initiated', { page: 1, size: 50 })
      expect(resp.code).toBe(200)
      const found = (resp.data?.records || []).some((t: any) => String(t.businessId) === String(cid))
      expect(found, '我发起的应含刚提交的合同').toBe(true)
    })

    // @matrix D-32（负向）
    it('负向：complete 不存在的 taskId 被拒', async () => {
      const resp = await client.post('/api/v1/workflow/approval/complete', {
        taskId: 'non-existent-task-id', comment: 'P1负向',
      })
      expect(resp.code).not.toBe(200)
    })
  })
})
