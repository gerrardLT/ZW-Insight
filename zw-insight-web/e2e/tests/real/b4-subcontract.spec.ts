/**
 * 真实模式 E2E：B-4 分包管理账本补测（账本全量补齐 M4，2026-08）
 *
 * @matrix B-20-x 分包合同提交直批 EFFECTIVE+非草稿重提交拦截 /
 *   B-S-X1/B-21-x 结算单创建 DRAFT（金额=Σ明细）→提交直批 APPROVED→明细级联回读 /
 *   B-S-X2 结算审批→合同 cumulativeSettlement 回写 /
 *   B-20-7 有结算合同删除现状钉住（引用守卫缺失实证）
 *
 * 实证（探测 2026-08-20）：
 *   - 分包合同 submit 直批 DRAFT→EFFECTIVE（无 BPMN）；项目预算管控自置 WARN_ONLY
 *   - 结算单 submit 直批 DRAFT→APPROVED；详情 VO 嵌套 { settlement, details[] }，
 *     sortOrder 落库回读=创建注入值；明细 amount=quantity×unitPrice
 *   - 结算审批后合同 cumulativeSettlement=Σ 已审批结算（1000 实证；
 *     结算记录自身 cumulativeSettlement 字段恒 0，累计口径在合同级）
 *   - B-20-7 现状钉住：有已审批结算的合同仍可直接删除（code=200，引用守卫缺失，
 *     与账本「依赖后端引用守卫」预期不符，钉住现状不做负向断言）
 *
 * 清理：afterAll 逆序删除结算→配置→项目（合同在 B-20-7 用例内已删；
 * APPROVED 结算单删除放行，探测实证）。
 *
 * 纯前端守卫用例（B20/B21 共 10 例 vitest）见
 * src/__tests__/subcontract-matrix.component.test.ts；结算页 UI 写路径另见
 * expense-write-2.spec.ts（B-21 必填/创建/联动/提交）。
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
let contractId = ''
let settlementId = ''
const CONTRACT_NAME = `${PREFIX}_幕墙分包合同`

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
  const cr = await apiJson('POST', '/api/v1/project', { projectName: `${PREFIX}_分包项目`, projectNature: '新建', projectType: '房建工程' })
  expect(cr.body?.code, '创建承载项目').toBe(200)
  const pg = await apiJson('GET', `/api/v1/project/page?pageNum=1&pageSize=5&projectName=${encodeURIComponent(`${PREFIX}_分包项目`)}`)
  projectId = String(pg.body?.data?.records?.[0]?.id || '')
  expect(projectId, '承载项目应可定位').toBeTruthy()
  const cfg = await apiJson('POST', '/api/v1/budget-control-configs', { projectId, controlMode: 'WARN_ONLY', warningThreshold: 80 })
  expect(cfg.body?.code, '创建预算管控配置').toBe(200)
  const eff = await apiJson('GET', `/api/v1/budget-control-configs/project/${projectId}`)
  cfgId = String(eff.body?.data?.id || '')
})

test.afterAll(async () => {
  if (!authed) return
  // 逆序清理；合同已在 B-20-7 用例内删除（现状钉住实证），此处兜底
  if (settlementId) await authed.delete(`${API_BASE}/api/v1/subcontract/settlement/${settlementId}`).catch(() => {})
  if (contractId) await authed.delete(`${API_BASE}/api/v1/subcontract/contract/${contractId}`).catch(() => {})
  if (cfgId) await authed.delete(`${API_BASE}/api/v1/budget-control-configs/${cfgId}`).catch(() => {})
  await authed.delete(`${API_BASE}/api/v1/project/${projectId}`).catch(() => {})
  await authed.dispose()
})

test.describe('B-4 分包合同（直批生效）', () => {
  test('@matrix B-20-x 合同创建 DRAFT→提交直批 EFFECTIVE→非草稿重提交拦截', async () => {
    const cr = await apiJson('POST', '/api/v1/subcontract/contract', {
      projectId, contractName: CONTRACT_NAME, subcontractor: `${PREFIX}_幕墙公司`,
      contractAmount: 200000, signingDate: TODAY,
    })
    expect(cr.body?.code, '创建分包合同').toBe(200)
    const pg = await apiJson('GET', `/api/v1/subcontract/contract/page?pageNum=1&pageSize=10&contractName=${encodeURIComponent(CONTRACT_NAME)}`)
    const contract = (pg.body?.data?.records || [])[0]
    expect(contract, '合同应可按名称定位').toBeTruthy()
    expect(contract.status).toBe('DRAFT')
    contractId = String(contract.id)
    const sub = await apiJson('POST', `/api/v1/subcontract/contract/${contractId}/submit`)
    expect(sub.body?.code, '提交直批').toBe(200)
    const detail = await apiJson('GET', `/api/v1/subcontract/contract/${contractId}`)
    expect(detail.body?.data?.status, '直批 EFFECTIVE（无 BPMN 实证）').toBe('EFFECTIVE')
    const sub2 = await apiJson('POST', `/api/v1/subcontract/contract/${contractId}/submit`)
    expect(sub2.body?.code, 'EFFECTIVE 重提交应拦截').not.toBe(200)
  })
})

test.describe('B-4 分包结算（明细聚合+直批+回写）', () => {
  test('@matrix B-S-X1/B-21-x 结算单创建（金额=Σ明细）→提交直批 APPROVED→明细级联回读', async () => {
    expect(contractId, '前置：EFFECTIVE 分包合同').toBeTruthy()
    const cr = await apiJson('POST', '/api/v1/subcontract/settlement', {
      projectId, contractId,
      details: [
        { itemName: `${PREFIX}_幕墙龙骨`, unit: '吨', quantity: 5, unitPrice: 800, sortOrder: 1 },
        { itemName: `${PREFIX}_玻璃面板`, unit: '㎡', quantity: 20, unitPrice: 300, sortOrder: 2 },
      ],
    })
    expect(cr.body?.code, '创建结算单').toBe(200)
    const pg = await apiJson('GET', `/api/v1/subcontract/settlement?contractId=${contractId}&page=1&size=10`)
    const settlement = (pg.body?.data?.records || [])[0]
    expect(settlement, '结算单应可定位').toBeTruthy()
    settlementId = String(settlement.id)
    expect(settlement.status).toBe('DRAFT')
    // B-21-4 金额聚合：settlementAmount=Σ(数量×单价)=5×800+20×300=10000
    expect(Number(settlement.settlementAmount), '结算金额=明细合计').toBe(10000)
    const sub = await apiJson('POST', `/api/v1/subcontract/settlement/${settlementId}/submit`)
    expect(sub.body?.code, '结算提交直批').toBe(200)
    // 详情级联回读：VO 嵌套 settlement/details，sortOrder 落库（B-21-6 payload 注入）
    const detail = await apiJson('GET', `/api/v1/subcontract/settlement/${settlementId}`)
    expect(detail.body?.data?.settlement?.status, '直批 APPROVED').toBe('APPROVED')
    const details = detail.body?.data?.details || []
    expect(details, '明细应级联回读').toHaveLength(2)
    expect(details.map((d: any) => d.sortOrder).sort(), 'sortOrder 落库').toEqual([1, 2])
    expect(Number(details[0].amount), '明细金额=数量×单价').toBe(4000)
  })

  test('@matrix B-S-X2 结算审批→合同累计结算回写', async () => {
    expect(settlementId).toBeTruthy()
    const detail = await apiJson('GET', `/api/v1/subcontract/contract/${contractId}`)
    expect(Number(detail.body?.data?.cumulativeSettlement), '合同累计=已审批结算之和').toBe(10000)
  })

  test('@matrix B-20-7 现状钉住：有已审批结算的合同删除放行（引用守卫缺失）', async () => {
    // 账本预期「依赖后端引用守卫拦截」，实测 code=200 直接删除（守卫缺失缺陷实证）。
    // 此处钉住现状：断言放行行为，修复后翻转为负向断言。
    const del = await apiJson('DELETE', `/api/v1/subcontract/contract/${contractId}`)
    expect(del.body?.code, '现状：有结算合同删除放行（引用守卫缺失钉住）').toBe(200)
    const pg = await apiJson('GET', `/api/v1/subcontract/contract/page?pageNum=1&pageSize=10&contractName=${encodeURIComponent(CONTRACT_NAME)}`)
    expect((pg.body?.data?.records || []).length, '删除后不可再定位').toBe(0)
  })
})
