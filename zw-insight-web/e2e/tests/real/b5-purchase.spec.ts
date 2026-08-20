/**
 * 真实模式 E2E：B-5 采购管理账本补测（账本全量补齐 M5，2026-08）
 *
 * @matrix B-22-x 采购合同提交走 BPMN 自动完成至 EFFECTIVE（workflowInstanceId 非空，
 *   区别于劳务/分包直批）+ 非草稿重提交拦截 + EFFECTIVE 编辑守卫「仅草稿状态可编辑」/
 *   B-P-X2 入库单创建→提交直批 APPROVED→进入结算候选（available-inbounds）/
 *   B-23-x 结算创建 DRAFT（inboundAmount 自动回填=入库金额）→提交直批 APPROVED /
 *   B-S-X2 类 结算审批→合同 cumulativeSettlement 回写 /
 *   B-23-7 结算后入库单从候选排除 + 重复结算 API 直连拦截 /
 *   B-23-x APPROVED 结算单删除守卫「仅草稿状态可删除」/
 *   B-22-x 有结算合同删除现状钉住（引用守卫缺失实证，同 B-20-7）
 *
 * 实证（探测 5/5b，2026-08-20）：
 *   - 采购合同 submit → BPMN 流程（workflowInstanceId 非空）但自动完成至 EFFECTIVE，
 *     与劳务/分包合同直批（wfId=null）不同
 *   - EFFECTIVE 合同 PUT 编辑 → code=500「仅草稿状态可编辑」（后端守卫有效）
 *   - 入库单 submit 直批 APPROVED；结算单 submit 直批 APPROVED
 *   - 结算创建 DRAFT 时 inboundAmount 自动回填=入库单 totalAmount（1000 实证）
 *   - 结算审批后合同 cumulativeSettlement=1000 回写；重复结算不重复累计
 *   - 结算后 available-inbounds 返回 []（已结算入库单排除）
 *   - 同一入库单二次结算 API 直连 → code=500「该入库单已存在结算记录，不可重复结算」
 *   - APPROVED 结算单删除 → code=500「仅草稿状态可删除」
 *   - 现状钉住：有已审批结算的采购合同删除 code=200 放行（引用守卫缺失，同 B-20-7）
 *
 * 残留声明：APPROVED 结算单×1 删不掉（仅草稿可删，实证），E2E_TEST_ 前缀巡检兜底。
 * 清理：afterAll 逆序 best-effort 删除结算→入库→合同→预算→配置→项目。
 *
 * 纯前端守卫用例（B22/B23/B24 共 24 例 vitest）见
 * src/__tests__/purchase-matrix.component.test.ts；结算 UI 写路径与询价定标另见
 * expense-write.spec.ts（B-23 UI 写路径 + B-P-X1）。
 */
import { test, expect } from '@playwright/test'
import { authedApiContext, e2ePrefix, todayStr, type AuthedContext } from './real-helper'

test.describe.configure({ mode: 'serial' })

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'
const PREFIX = e2ePrefix()
const TODAY = todayStr()

let authed: AuthedContext | null = null
let projectId = ''
let cfgId = ''
let budgetId = ''
let contractId = ''
let inboundId = ''
let settlementId = ''
const CONTRACT_NAME = `${PREFIX}_钢筋采购合同`

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

test.beforeAll(async () => {
  authed = await authedApiContext()
  const cr = await apiJson('POST', '/api/v1/project', { projectName: `${PREFIX}_采购项目`, projectNature: '新建', projectType: '市政工程' })
  expect(cr.body?.code, '创建承载项目').toBe(200)
  const pg = await apiJson('GET', `/api/v1/project/page?pageNum=1&pageSize=5&projectName=${encodeURIComponent(`${PREFIX}_采购项目`)}`)
  projectId = String(pg.body?.data?.records?.[0]?.id || '')
  expect(projectId, '承载项目应可定位').toBeTruthy()
  const cfg = await apiJson('POST', '/api/v1/budget-control-configs', { projectId, controlMode: 'WARN_ONLY', warningThreshold: 80 })
  expect(cfg.body?.code, '创建预算管控配置').toBe(200)
  const eff = await apiJson('GET', `/api/v1/budget-control-configs/project/${projectId}`)
  cfgId = String(eff.body?.data?.id || '')
  // 采购合同前置：MATERIAL 预算（默认 BLOCK「该科目未设置预算额度」，WARN_ONLY+预算双保险）
  await apiJson('POST', '/api/v1/budget', {
    projectId, budgetType: 'ORIGINAL', totalAmount: 100000,
    details: [{ costCategory: 'MATERIAL', itemName: `${PREFIX}_钢筋`, unit: '吨', budgetQuantity: 1000, budgetUnitPrice: 100, budgetTotalPrice: 100000 }],
  })
  const bgPg = await apiJson('GET', `/api/v1/budget/page?page=1&size=20&projectId=${projectId}`)
  budgetId = String((bgPg.body?.data?.records || []).find((b: any) => b.budgetType === 'ORIGINAL')?.id || '')
  expect(budgetId, '预算应可定位').toBeTruthy()
  const bgSub = await apiJson('POST', `/api/v1/budget/${budgetId}/submit`)
  expect(bgSub.body?.code, '预算提交').toBe(200)
})

test.afterAll(async () => {
  if (!authed) return
  // 逆序清理；APPROVED 结算单删除会被守卫拦截（best-effort）；
  // 合同已在「现状钉住」用例内删除，此处兜底
  if (settlementId) await authed.delete(`${API_BASE}/api/v1/purchase/settlement/${settlementId}`).catch(() => {})
  if (inboundId) await authed.delete(`${API_BASE}/api/v1/material/inbound/${inboundId}`).catch(() => {})
  if (contractId) await authed.delete(`${API_BASE}/api/v1/purchase/contract/${contractId}`).catch(() => {})
  if (budgetId) await authed.delete(`${API_BASE}/api/v1/budget/${budgetId}`).catch(() => {})
  if (cfgId) await authed.delete(`${API_BASE}/api/v1/budget-control-configs/${cfgId}`).catch(() => {})
  await authed.delete(`${API_BASE}/api/v1/project/${projectId}`).catch(() => {})
  await authed.dispose()
})

test.describe('B-5 采购合同（BPMN 自动完成至生效）', () => {
  test('@matrix B-22-x 合同创建 DRAFT→提交走 BPMN 自动完成 EFFECTIVE→非草稿重提交拦截', async () => {
    const cr = await apiJson('POST', '/api/v1/purchase/contract', {
      projectId, contractName: CONTRACT_NAME, supplierName: `${PREFIX}_钢材商`,
      contractAmount: 50000, signingDate: TODAY,
    })
    expect(cr.body?.code, '创建采购合同').toBe(200)
    const pg = await apiJson('GET', `/api/v1/purchase/contract/page?pageNum=1&pageSize=10&contractName=${encodeURIComponent(CONTRACT_NAME)}`)
    const contract = (pg.body?.data?.records || [])[0]
    expect(contract, '合同应可按名称定位').toBeTruthy()
    expect(contract.status).toBe('DRAFT')
    contractId = String(contract.id)
    const sub = await apiJson('POST', `/api/v1/purchase/contract/${contractId}/submit`)
    expect(sub.body?.code, '提交').toBe(200)
    const detail = await apiJson('GET', `/api/v1/purchase/contract/${contractId}`)
    expect(detail.body?.data?.status, 'BPMN 自动完成至 EFFECTIVE').toBe('EFFECTIVE')
    expect(detail.body?.data?.workflowInstanceId, 'BPMN 流程实例非空（区别于劳务/分包直批）').toBeTruthy()
    const sub2 = await apiJson('POST', `/api/v1/purchase/contract/${contractId}/submit`)
    expect(sub2.body?.code, 'EFFECTIVE 重提交应拦截').not.toBe(200)
  })

  test('@matrix B-22-6 EFFECTIVE 合同编辑拦截「仅草稿状态可编辑」（后端守卫正向断言）', async () => {
    expect(contractId, '前置：EFFECTIVE 采购合同').toBeTruthy()
    const put = await apiJson('PUT', `/api/v1/purchase/contract/${contractId}`, {
      projectId, contractName: `${CONTRACT_NAME}_改`, supplierName: `${PREFIX}_钢材商`,
      contractAmount: 60000, signingDate: TODAY,
    })
    expect(put.body?.code, 'EFFECTIVE 编辑应拦截').not.toBe(200)
    expect(String(put.body?.message || put.body?.msg || '')).toContain('仅草稿状态可编辑')
  })
})

test.describe('B-5 采购入库（直批进入结算候选）', () => {
  test('@matrix B-P-X2 入库单创建→提交直批 APPROVED→进入结算候选', async () => {
    expect(contractId, '前置：EFFECTIVE 采购合同').toBeTruthy()
    const cr = await apiJson('POST', '/api/v1/material/inbound', {
      projectId, contractId, inboundDate: TODAY, directOutbound: 0,
      details: [{ materialName: `${PREFIX}_钢筋`, specification: 'Φ20', unit: '吨', unitPrice: 100, quantity: 10 }],
    })
    expect(cr.body?.code, '创建入库单').toBe(200)
    const pg = await apiJson('GET', `/api/v1/material/inbound/page?page=1&size=10&projectId=${projectId}`)
    const inbound = (pg.body?.data?.records || [])[0]
    expect(inbound, '入库单应可定位').toBeTruthy()
    inboundId = String(inbound.id)
    expect(inbound.status).toBe('DRAFT')
    const sub = await apiJson('POST', `/api/v1/material/inbound/${inboundId}/submit`)
    expect(sub.body?.code, '入库提交直批').toBe(200)
    const detail = await apiJson('GET', `/api/v1/material/inbound/${inboundId}`)
    expect(detail.body?.data?.status, '直批 APPROVED').toBe('APPROVED')
    const avail = await apiJson('GET', `/api/v1/purchase/settlement/available-inbounds?contractId=${contractId}`)
    expect((avail.body?.data || []).map((r: any) => String(r.id)), '已审批入库单进入结算候选').toContain(inboundId)
  })
})

test.describe('B-5 采购结算（直批+累计回写+重复结算拦截）', () => {
  test('@matrix B-23-x 结算创建 DRAFT（入库金额自动回填）→提交直批 APPROVED', async () => {
    expect(inboundId, '前置：APPROVED 入库单').toBeTruthy()
    const cr = await apiJson('POST', '/api/v1/purchase/settlement', {
      contractId, inboundId, settlementAmount: 1000, settlementDate: TODAY,
    })
    expect(cr.body?.code, '创建结算单').toBe(200)
    const pg = await apiJson('GET', `/api/v1/purchase/settlement/page?page=1&size=10&contractId=${contractId}`)
    const settlement = (pg.body?.data?.records || [])[0]
    expect(settlement, '结算单应可定位').toBeTruthy()
    settlementId = String(settlement.id)
    expect(settlement.status).toBe('DRAFT')
    expect(Number(settlement.inboundAmount), '入库金额自动回填=入库单 totalAmount').toBe(1000)
    const sub = await apiJson('POST', `/api/v1/purchase/settlement/${settlementId}/submit`)
    expect(sub.body?.code, '结算提交直批').toBe(200)
    const pg2 = await apiJson('GET', `/api/v1/purchase/settlement/page?page=1&size=10&contractId=${contractId}`)
    expect((pg2.body?.data?.records || [])[0]?.status, '直批 APPROVED').toBe('APPROVED')
  })

  test('@matrix B-S-X2 类 结算审批→合同累计结算回写', async () => {
    expect(settlementId).toBeTruthy()
    const detail = await apiJson('GET', `/api/v1/purchase/contract/${contractId}`)
    expect(Number(detail.body?.data?.cumulativeSettlement), '合同累计=已审批结算之和').toBe(1000)
  })

  test('@matrix B-23-7 结算后入库单从候选排除 + 重复结算拦截（累计不重复）', async () => {
    const avail = await apiJson('GET', `/api/v1/purchase/settlement/available-inbounds?contractId=${contractId}`)
    expect(avail.body?.data || [], '已结算入库单从候选排除').toHaveLength(0)
    const dup = await apiJson('POST', '/api/v1/purchase/settlement', {
      contractId, inboundId, settlementAmount: 500, settlementDate: TODAY,
    })
    expect(dup.body?.code, '重复结算应拦截').not.toBe(200)
    expect(String(dup.body?.message || dup.body?.msg || '')).toContain('不可重复结算')
    const detail = await apiJson('GET', `/api/v1/purchase/contract/${contractId}`)
    expect(Number(detail.body?.data?.cumulativeSettlement), '累计不重复').toBe(1000)
  })

  test('@matrix B-23-x APPROVED 结算单删除拦截「仅草稿状态可删除」', async () => {
    const del = await apiJson('DELETE', `/api/v1/purchase/settlement/${settlementId}`)
    expect(del.body?.code, 'APPROVED 结算删除应拦截').not.toBe(200)
    expect(String(del.body?.message || del.body?.msg || '')).toContain('仅草稿状态可删除')
  })

  test('@matrix B-22-x 现状钉住：有已审批结算的合同删除放行（引用守卫缺失）', async () => {
    // 账本预期「依赖后端引用守卫拦截」，实测 code=200 直接删除（守卫缺失缺陷实证，
    // 与 B-20-7 同款）。此处钉住现状：断言放行行为，修复后翻转为负向断言。
    const del = await apiJson('DELETE', `/api/v1/purchase/contract/${contractId}`)
    expect(del.body?.code, '现状：有结算合同删除放行（引用守卫缺失钉住）').toBe(200)
    const pg = await apiJson('GET', `/api/v1/purchase/contract/page?pageNum=1&pageSize=10&contractName=${encodeURIComponent(CONTRACT_NAME)}`)
    expect((pg.body?.data?.records || []).length, '删除后不可再定位').toBe(0)
  })
})
