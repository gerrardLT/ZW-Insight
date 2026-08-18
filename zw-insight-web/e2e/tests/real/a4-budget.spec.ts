/**
 * 真实模式 E2E：A-4 预算账本补测（账本全量补齐 M2，2026-08）
 *
 * @matrix A11-05 草稿编辑 / A11-06 提交直批+明细合计回写 / A11-07 APPROVED 编辑拦截 /
 *   A11-08 草稿删除 / A11-09 同项目重复预算拦截 / A11-11 APPROVED 重提交拦截 /
 *   A12-05 变更单创建（必带明细）/ A12-07 变更提交 BPMN 缺失钉住 / A12-08 草稿变更删除 /
 *   A14-04 控制配置新增 / A14-07 编辑 / A14-08 删除 / A14-09 同项目重复配置拦截 /
 *   A-X15 明细合计覆盖 totalAmount / A-X16 A-X17 变更审批链受阻连带 / A-X19 删规则回落全局
 *
 * 实证（探测 2026-08）：
 *   - 预算 submit → 直批 APPROVED（无审批链）；存在明细时 totalAmount 以明细 Σ 覆盖
 *   - APPROVED PUT → code=500「仅草稿状态可编辑」；重提交「仅草稿状态可提交」
 *   - 同项目重复 ORIGINAL → code=500「该项目已存在原始预算，不可重复创建」
 *   - APPROVED 预算 DELETE 放行条件：E2eTestGuard 扫描明细 itemName 前缀
 *     （BudgetService.delete L222 双条件守卫）→ 明细挂 E2E_TEST_ 前缀即可完全清理
 *   - 租户 1 缺 budget_change_approval BPMN → 变更单 submit 失败、状态停留 DRAFT
 *     （DATA 受阻钉住，连带 A-X16/A-X17 无法走真实审批链）
 *   - 控制配置同项目重复创建 → 500 DuplicateKey（缺陷钉住，无业务文案）
 *
 * 纯前端守卫用例（A11-01~04/10/12、A12-01~04/06/09/10、A13 全部、A14-01~03/05/06/10）
 * 见 src/__tests__/budget-matrix / budget-change-form-matrix 组件测试。
 */
import { test, expect } from '@playwright/test'
import { authedApiContext, e2ePrefix, type AuthedContext } from './real-helper'

test.describe.configure({ mode: 'serial' })

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'
const PREFIX = e2ePrefix()

let authed: AuthedContext | null = null
let p1Id = ''   // 主链项目：预算 APPROVED + 变更单
let p2Id = ''   // 辅项目：草稿预算编辑/删除 + 控制配置
let budgetId = ''      // P1 ORIGINAL 预算（最终 APPROVED）
let detailId = ''      // P1 预算明细（变更单引用）
let p2BudgetId = ''    // P2 草稿预算（test4 内删除）
let changeId = ''      // 变更单（test5 内删除）
let cfgId = ''         // P2 控制配置（test6 内删除）
const p1Name = `${PREFIX}_预算项目A`
const p2Name = `${PREFIX}_预算项目B`

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

async function createProject(name: string): Promise<string> {
  const cr = await apiJson('POST', '/api/v1/project', { projectName: name, projectNature: '新建', projectType: '市政工程' })
  expect(cr.body?.code, `创建项目 ${name}`).toBe(200)
  const pg = await apiJson('GET', `/api/v1/project/page?pageNum=1&pageSize=5&projectName=${encodeURIComponent(name)}`)
  const id = String(pg.body?.data?.records?.[0]?.id || '')
  expect(id, `项目 ${name} 应可定位`).toBeTruthy()
  return id
}

/** 预算/变更/配置三端点分页口径均为 page/size（Controller @RequestParam 实证） */
async function findBudget(projectId: string) {
  const pg = await apiJson('GET', `/api/v1/budget/page?page=1&size=50&projectId=${projectId}`)
  return (pg.body?.data?.records || []).find((b: any) => b.budgetType === 'ORIGINAL')
}

async function findChange(projectId: string) {
  const pg = await apiJson('GET', `/api/v1/budget/change/page?page=1&size=50&projectId=${projectId}`)
  return (pg.body?.data?.records || []).find((c: any) => c.changeReason?.startsWith(PREFIX))
}

/** 配置列表仅支持 projectName/controlMode 筛选（无 projectId 参数），按 createdAt 倒序取首条匹配 */
async function findConfig(projectId: string) {
  const list = await apiJson('GET', '/api/v1/budget-control-configs?page=1&size=100')
  return ((list.body?.data?.records) || []).find((c: any) => String(c.projectId) === projectId)
}

// 雪花 ID 纪律（探测实证 2026-08）：创建 payload 一律传字符串 projectId（Jackson 接受
// string→Long）；Number() 会四舍五入雪花 ID（...778→...800）导致存值失真、精确筛选恒空；
// resp.json() 同损精度，id 定位一律用 String() 比较。

test.beforeAll(async () => {
  authed = await authedApiContext()
  p1Id = await createProject(p1Name)
  p2Id = await createProject(p2Name)
})

test.afterAll(async () => {
  if (!authed) return
  // 逆序清理（子→父）：配置→变更单→预算→项目
  if (cfgId) await authed.delete(`${API_BASE}/api/v1/budget-control-configs/${cfgId}`).catch(() => {})
  if (changeId) await authed.delete(`${API_BASE}/api/v1/budget/change/${changeId}`).catch(() => {})
  // APPROVED 预算可删前提：明细 itemName 挂 E2E_TEST_ 前缀（守卫扫描明细）
  if (budgetId) await authed.delete(`${API_BASE}/api/v1/budget/${budgetId}`).catch(() => {})
  await authed.delete(`${API_BASE}/api/v1/project/${p1Id}`).catch(() => {})
  await authed.delete(`${API_BASE}/api/v1/project/${p2Id}`).catch(() => {})
  await authed.dispose()
})

test.describe('A-4 预算编制主链（直批 + 守卫）', () => {
  test('@matrix A11-06/A-X15 创建预算提交直批 APPROVED，明细合计覆盖 totalAmount', async () => {
    const cr = await apiJson('POST', '/api/v1/budget', {
      projectId: p1Id, budgetType: 'ORIGINAL', totalAmount: 999,
      details: [
        { costCategory: 'MATERIAL', itemName: `${PREFIX}_材料项`, unit: 'm³', budgetQuantity: 10, budgetUnitPrice: 100, budgetTotalPrice: 1000 },
      ],
    })
    expect(cr.body?.code, '创建 ORIGINAL 预算').toBe(200)
    const budget = await findBudget(p1Id)
    expect(budget, '预算应可按项目定位').toBeTruthy()
    expect(budget.status).toBe('DRAFT')
    budgetId = String(budget.id)
    const sub = await apiJson('POST', `/api/v1/budget/${budgetId}/submit`)
    expect(sub.body?.code, '预算提交（直批无审批链）').toBe(200)
    const detail = await apiJson('GET', `/api/v1/budget/${budgetId}`)
    expect(detail.body?.data?.status, '提交后应直批 APPROVED').toBe('APPROVED')
    expect(Number(detail.body?.data?.totalAmount), '明细合计 1000 应覆盖录入值 999').toBe(1000)
    // 明细行定位（变更单引用前提）
    const details = await apiJson('GET', `/api/v1/budget/${budgetId}/details`)
    const row = (details.body?.data || []).find((d: any) => d.itemName === `${PREFIX}_材料项`)
    expect(row, '预算明细应可定位').toBeTruthy()
    detailId = String(row.id)
  })

  test('@matrix A11-07/A11-11 APPROVED 编辑/重提交双守卫拦截', async () => {
    expect(budgetId).toBeTruthy()
    const put = await apiJson('PUT', `/api/v1/budget/${budgetId}`, {
      id: budgetId, projectId: p1Id, budgetType: 'ORIGINAL', totalAmount: 1,
    })
    expect(put.status, 'APPROVED 编辑 HTTP').toBe(200)
    expect(put.body?.code, 'APPROVED 编辑应拦截').not.toBe(200)
    expect(put.body?.message).toContain('仅草稿状态可编辑')
    const sub = await apiJson('POST', `/api/v1/budget/${budgetId}/submit`)
    expect(sub.body?.code, 'APPROVED 重提交应拦截').not.toBe(200)
    expect(sub.body?.message).toContain('仅草稿状态可提交')
  })

  test('@matrix A11-09 同项目重复 ORIGINAL 预算拦截', async () => {
    const cr = await apiJson('POST', '/api/v1/budget', {
      projectId: p1Id, budgetType: 'ORIGINAL', totalAmount: 1,
    })
    expect(cr.body?.code, '重复预算应拦截').not.toBe(200)
    expect(cr.body?.message).toContain('该项目已存在原始预算，不可重复创建')
  })

  test('@matrix A11-05/A11-08 草稿预算编辑成功并删除', async () => {
    const cr = await apiJson('POST', '/api/v1/budget', {
      projectId: p2Id, budgetType: 'ORIGINAL', totalAmount: 500,
    })
    expect(cr.body?.code, '创建 P2 草稿预算').toBe(200)
    const budget = await findBudget(p2Id)
    expect(budget?.status).toBe('DRAFT')
    p2BudgetId = String(budget.id)
    const put = await apiJson('PUT', `/api/v1/budget/${p2BudgetId}`, {
      id: p2BudgetId, projectId: p2Id, budgetType: 'ORIGINAL', totalAmount: 600,
    })
    expect(put.body?.code, '草稿编辑应成功').toBe(200)
    const after = await apiJson('GET', `/api/v1/budget/${p2BudgetId}`)
    expect(Number(after.body?.data?.totalAmount), '编辑后总额应更新').toBe(600)
    const del = await apiJson('DELETE', `/api/v1/budget/${p2BudgetId}`)
    expect(del.body?.code, '草稿删除应成功').toBe(200)
    p2BudgetId = ''
    expect(await findBudget(p2Id), '删除后不可再定位').toBeUndefined()
  })
})

test.describe('A-4 预算变更单（BPMN 缺失钉住）', () => {
  test('@matrix A12-05 变更单创建必须带明细（budgetDetailId+originalAmount）', async () => {
    expect(budgetId && detailId).toBeTruthy()
    // 缺明细必填字段 → 校验拦截
    const bad = await apiJson('POST', '/api/v1/budget/change', {
      projectId: p1Id, budgetId: budgetId, changeReason: `${PREFIX}_缺字段`,
      details: [{ budgetDetailId: detailId, adjustAmount: 100 }],
    })
    expect(bad.body?.code, '缺 originalAmount 应校验拦截').not.toBe(200)
    // 完整明细 → 创建成功（totalAdjustAmount 后端汇总）
    const cr = await apiJson('POST', '/api/v1/budget/change', {
      projectId: p1Id, budgetId: budgetId, changeReason: `${PREFIX}_变更`,
      totalAdjustAmount: 200,
      details: [{ budgetDetailId: detailId, costCategory: 'MATERIAL', itemName: `${PREFIX}_材料项`, originalAmount: 1000, adjustAmount: 200 }],
    })
    expect(cr.body?.code, '创建变更单').toBe(200)
    const change = await findChange(p1Id)
    expect(change, '变更单应可定位').toBeTruthy()
    expect(change.status).toBe('DRAFT')
    changeId = String(change.id)
  })

  test('@matrix A12-07/A-X16/A-X17 变更提交受阻钉住（租户缺 budget_change_approval BPMN）', async () => {
    expect(changeId).toBeTruthy()
    const sub = await apiJson('POST', `/api/v1/budget/change/${changeId}/submit`)
    // DATA 受阻钉住：流程定义缺失 → 提交不成功；部署 BPMN 后本断言应变红提醒解除
    expect(sub.status !== 200 || sub.body?.code !== 200, '变更提交现状受阻（BPMN 缺失）').toBe(true)
    const after = await apiJson('GET', `/api/v1/budget/change/${changeId}`)
    expect(after.body?.data?.status, '受阻后状态应停留 DRAFT').toBe('DRAFT')
  })

  test('@matrix A12-08 DRAFT 变更撤回拦截后删除成功', async () => {
    expect(changeId).toBeTruthy()
    const wd = await apiJson('POST', `/api/v1/budget/change/${changeId}/withdraw`)
    expect(wd.body?.code, 'DRAFT 撤回应拦截').not.toBe(200)
    expect(wd.body?.message).toContain('仅已提交状态可撤回')
    const del = await apiJson('DELETE', `/api/v1/budget/change/${changeId}`)
    expect(del.body?.code, 'DRAFT 变更删除（E2E_TEST_ 前缀放行）').toBe(200)
    changeId = ''
  })
})

test.describe('A-4 预算控制配置 CRUD', () => {
  test('@matrix A14-04/A14-07 配置新增并编辑', async () => {
    const cr = await apiJson('POST', '/api/v1/budget-control-configs', {
      projectId: p2Id, controlMode: 'WARN_ONLY', warningThreshold: 80,
    })
    expect(cr.body?.code, '创建控制配置').toBe(200)
    const cfg = await findConfig(p2Id)
    expect(cfg, '配置应可定位').toBeTruthy()
    cfgId = String(cfg.id)
    const put = await apiJson('PUT', `/api/v1/budget-control-configs/${cfgId}`, {
      projectId: p2Id, controlMode: 'BLOCK', warningThreshold: 90,
    })
    expect(put.body?.code, '编辑配置').toBe(200)
    const eff = await apiJson('GET', `/api/v1/budget-control-configs/project/${p2Id}`)
    expect(eff.body?.data?.controlMode, '生效配置应为 BLOCK').toBe('BLOCK')
    expect(Number(eff.body?.data?.warningThreshold)).toBe(90)
  })

  test('@matrix A14-09 同项目重复配置拦截（DuplicateKey 缺陷现状钉住）', async () => {
    expect(cfgId).toBeTruthy()
    const dup = await apiJson('POST', '/api/v1/budget-control-configs', {
      projectId: p2Id, controlMode: 'WARN_ONLY', warningThreshold: 80,
    })
    // 缺陷钉住：后端未做业务校验，直接 DuplicateKey 500（无业务文案）
    expect(dup.status !== 200 || dup.body?.code !== 200, '同项目重复配置应被拦截').toBe(true)
  })

  test('@matrix A14-08 配置删除成功，生效配置回落全局默认（A-X19 实证）', async () => {
    expect(cfgId).toBeTruthy()
    const del = await apiJson('DELETE', `/api/v1/budget-control-configs/${cfgId}`)
    expect(del.body?.code, '删除配置').toBe(200)
    cfgId = ''
    // 实证（2026-08）：项目级规则删除后 getEffectiveConfig 回落全局默认（isDefault=1），非 null
    const eff = await apiJson('GET', `/api/v1/budget-control-configs/project/${p2Id}`)
    expect(eff.body?.data?.isDefault, '回落后应为全局默认规则').toBe(1)
    expect(eff.body?.data?.projectId ?? null, '全局规则无项目绑定').toBeNull()
  })
})
