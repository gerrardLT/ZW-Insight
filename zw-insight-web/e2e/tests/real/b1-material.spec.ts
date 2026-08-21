/**
 * 真实模式 E2E：B-1 材料管理账本补测（账本全量补齐 M3，2026-08）
 *
 * @matrix B-M-X2 入库提交直批→库存/合同累计回写 /
 *   B-M-X3/B-2-9 退货→退款链路闭环（2026-08-21 缺口#4 解除后翻正向）/
 *   B-M-X4 调拨审批通过双向库存 / B-M-X5 直接出库联动 /
 *   B-2-4 PICK 扣库存+超量拦截 /
 *   B-3-3 同项目调拨 API 直连反向证据 / B-3-7 调拨提交审批→库存变更 /
 *   B-4-3 库存预警筛选通道 / 分页 page/size 生效+未知参数回落默认（失配消除）
 *
 * 实证（探测 2026-08）：
 *   - 入库 save 不动库存，submit 直批 APPROVED（无 BPMN）：库存/采购合同
 *     cumulativeInbound 回写；directOutbound=1 时同步生成 PICK APPROVED 出库单并回扣库存
 *   - 出库 save 即扣库存（PICK 超量「库存不足」/RETURN 超量「库存不足，无法退货」；
 *     数量≤0「出库数量必须大于0」）；submit 仅置 APPROVED；删除对称回填库存
 *   - RETURN 且 contractId!=null → save 时发布退货事件自动生成 PENDING 退款
 *     （unitPrice 作为入库单价计算退款金额）；删除退货单同步作废 PENDING 退款（CANCELED）
 *   - 调拨 save 仅落单据（同项目后端守卫「调出项目与调入项目不能相同」，盲点 11a），
 *     submit 启动 BPMN material_transfer_approval，审批通过回调双向库存变更
 *   - 2026-08-21 material_refund_approval 部署租户 1（缺口#4）→ 退货创建
 *     save 200 + PENDING 退款生成 + startProcess 成功（此前事务回滚 500 受阻解除）
 *   - 入库/出库主表无可控命名字段，E2E 前缀落在明细 materialName（E2eTestGuard 连明细扫描）
 *   - 系统默认预算控制配置为 BLOCK：无 MATERIAL 预算时创建采购合同被拦
 *     （「该科目未设置预算额度」实证）→ beforeAll 自置 MATERIAL 预算直批后放行
 *
 * 残留声明：APPROVED 入库×2、PICK 出库×1 + 直接出库生成×1、E2E_ 材料库存行、
 * APPROVED 预算×1、采购合同与项目×2（挂单据删不掉），均 E2E_TEST_ 前缀可识别（巡检兜底）
 *
 * 纯前端守卫用例（B1/B2/B3/B4/B5 共 33 例 vitest）见
 * src/__tests__/material-inbound-outbound-matrix /
 * material-transfer-stock-refund-matrix 组件测试。
 */
import { test, expect } from '@playwright/test'
import { authedApiContext, e2ePrefix, todayStr, type AuthedContext } from './real-helper'

test.describe.configure({ mode: 'serial' })

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'
const PREFIX = e2ePrefix()
const TODAY = todayStr()

let authed: AuthedContext | null = null
let projectAId = ''   // 主项目（入库/出库/退货/调出）
let projectBId = ''   // 调入项目
let purchaseContractId = ''
let budgetId = ''
const STEEL = `${PREFIX}_钢筋`      // 主材料（入库→出库→退货→调拨）
const CEMENT = `${PREFIX}_水泥`     // 直接出库材料
const SPEC = 'Φ20'

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

/** 专用项目内按特征定位单据（创建响应 R<Void> 无 id；同毫秒 createdAt 排序不稳，禁用首条启发式） */
async function findWhere(path: string, pred: (r: any) => boolean) {
  const pg = await apiJson('GET', path)
  expect(pg.body?.code, `分页查询 ${path}`).toBe(200)
  return (pg.body?.data?.records || []).find(pred)
}

async function getStock(materialName: string, projectId: string) {
  const pg = await apiJson('GET', `/api/v1/material/stock/page?page=1&size=50&projectId=${projectId}&materialName=${encodeURIComponent(materialName)}`)
  return (pg.body?.data?.records || []).find((s: any) => s.materialName === materialName && s.specification === SPEC)
}

/** 循环完成目标业务的全部待办（SUPER_ADMIN 可完成任意任务，a3-contract 实证范式） */
async function completeAllTodos(businessId: string | number, maxRounds = 6): Promise<void> {
  for (let i = 0; i < maxRounds; i++) {
    const resp = await authed!.get(`${API_BASE}/api/v1/workflow/approval/todo`, { params: { page: 1, size: 50 } })
    const todos = ((await resp.json()).data?.records || []).filter((t: any) => String(t.businessId) === String(businessId))
    if (todos.length === 0) return
    const c = await authed!.post(`${API_BASE}/api/v1/workflow/approval/complete`, {
      data: { taskId: todos[0].taskId, comment: 'E2E b1-material 审批推进' },
    })
    expect(c.status(), `完成审批任务 ${todos[0].taskId}`).toBe(200)
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error(`businessId=${businessId} 待办 ${maxRounds} 轮未清空`)
}

test.beforeAll(async () => {
  authed = await authedApiContext()
  const crA = await apiJson('POST', '/api/v1/project', { projectName: `${PREFIX}_材料项目A`, projectNature: '新建', projectType: '市政工程' })
  expect(crA.body?.code, '创建项目A').toBe(200)
  const crB = await apiJson('POST', '/api/v1/project', { projectName: `${PREFIX}_材料项目B`, projectNature: '新建', projectType: '市政工程' })
  expect(crB.body?.code, '创建项目B').toBe(200)
  const pg = await apiJson('GET', `/api/v1/project/page?pageNum=1&pageSize=20&projectName=${encodeURIComponent(PREFIX)}`)
  const recs = pg.body?.data?.records || []
  projectAId = String(recs.find((p: any) => p.projectName.endsWith('项目A'))?.id || '')
  projectBId = String(recs.find((p: any) => p.projectName.endsWith('项目B'))?.id || '')
  expect(projectAId && projectBId, '两个承载项目应可定位').toBeTruthy()
  // MATERIAL 预算前提（系统默认 BLOCK 配置：无预算拒建采购合同，实证「该科目未设置预算额度」）
  const bg = await apiJson('POST', '/api/v1/budget', {
    projectId: projectAId, budgetType: 'ORIGINAL', totalAmount: 200000,
    details: [
      { costCategory: 'MATERIAL', itemName: `${PREFIX}_材料项`, unit: '吨', budgetQuantity: 2000, budgetUnitPrice: 100, budgetTotalPrice: 200000 },
    ],
  })
  expect(bg.body?.code, '创建 MATERIAL 预算').toBe(200)
  const bgPg = await apiJson('GET', `/api/v1/budget/page?page=1&size=50&projectId=${projectAId}`)
  budgetId = String((bgPg.body?.data?.records || []).find((b: any) => b.budgetType === 'ORIGINAL')?.id || '')
  expect(budgetId, '预算应可定位').toBeTruthy()
  const bgSub = await apiJson('POST', `/api/v1/budget/${budgetId}/submit`)
  expect(bgSub.body?.code, '预算提交直批').toBe(200)
  // 采购合同前提（入库累计回写 + 退货退款联动）
  const pc = await apiJson('POST', '/api/v1/purchase/contract', {
    projectId: projectAId, contractName: `${PREFIX}_采购合同`, supplierName: `${PREFIX}_供应商`,
    contractAmount: 100000, signingDate: TODAY,
  })
  expect(pc.body?.code, `创建采购合同: ${pc.body?.message || ''}`).toBe(200)
  const pcPg = await apiJson('GET', `/api/v1/purchase/contract/page?page=1&size=50&projectId=${projectAId}`)
  purchaseContractId = String((pcPg.body?.data?.records || []).find((c: any) => c.contractName === `${PREFIX}_采购合同`)?.id || '')
  expect(purchaseContractId, '采购合同应可定位').toBeTruthy()
})

test.afterAll(async () => {
  if (!authed) return
  // APPROVED 单据/库存行/退款均无删除通道或删后留痕 → E2E_TEST_ 前缀残留（巡检兜底）；
  // 项目挂单据删不掉，best-effort 尝试（预算同残留）
  await authed.delete(`${API_BASE}/api/v1/project/${projectAId}`).catch(() => {})
  await authed.delete(`${API_BASE}/api/v1/project/${projectBId}`).catch(() => {})
  await authed.dispose()
})

test.describe('B-1 入库主链（直批 + 库存/合同回写 + 直接出库）', () => {
  test('@matrix B-M-X2 入库创建→提交直批 APPROVED→库存与合同累计入库回写', async () => {
    const cr = await apiJson('POST', '/api/v1/material/inbound', {
      projectId: projectAId, contractId: purchaseContractId, inboundDate: TODAY, directOutbound: 0,
      details: [{ materialName: STEEL, specification: SPEC, unit: '吨', unitPrice: 100, quantity: 10 }],
    })
    expect(cr.body?.code, '创建入库单').toBe(200)
    const inbound = await findWhere(`/api/v1/material/inbound/page?page=1&size=10&projectId=${projectAId}`,
      (r: any) => Number(r.totalAmount) === 1000 && r.status === 'DRAFT')
    expect(inbound, '入库单应可定位').toBeTruthy()
    expect(inbound.status).toBe('DRAFT')
    expect(Number(inbound.totalAmount), '明细金额合计').toBe(1000)
    // submit 前库存不应存在（save 不动库存实证）
    expect(await getStock(STEEL, projectAId), '提交前不应有库存行').toBeUndefined()
    const sub = await apiJson('POST', `/api/v1/material/inbound/${inbound.id}/submit`)
    expect(sub.body?.code, `入库提交（直批）: ${sub.body?.message || ''}`).toBe(200)
    const detail = await apiJson('GET', `/api/v1/material/inbound/${inbound.id}`)
    expect(detail.body?.data?.status).toBe('APPROVED')
    // 库存回写
    const stock = await getStock(STEEL, projectAId)
    expect(stock, '提交后应生成库存行').toBeTruthy()
    expect(Number(stock.stockQuantity), '库存数量').toBe(10)
    expect(Number(stock.totalInbound), '累计入库量').toBe(10)
    expect(Number(stock.avgUnitPrice), '加权均价').toBe(100)
    // 采购合同累计入库金额回写
    const pcDetail = await apiJson('GET', `/api/v1/purchase/contract/${purchaseContractId}`)
    expect(Number(pcDetail.body?.data?.cumulativeInbound), '合同累计入库金额').toBe(1000)
  })

  test('@matrix B-M-X5 直接出库入库单：提交后生成 PICK 出库且库存净零', async () => {
    const cr = await apiJson('POST', '/api/v1/material/inbound', {
      projectId: projectAId, contractId: purchaseContractId, inboundDate: TODAY, directOutbound: 1,
      details: [{ materialName: CEMENT, specification: SPEC, unit: '吨', unitPrice: 50, quantity: 5 }],
    })
    expect(cr.body?.code, '创建直接出库入库单').toBe(200)
    const inbound = await findWhere(`/api/v1/material/inbound/page?page=1&size=10&projectId=${projectAId}`,
      (r: any) => Number(r.totalAmount) === 250 && r.status === 'DRAFT')
    expect(inbound, '直接出库入库单应可定位').toBeTruthy()
    const sub = await apiJson('POST', `/api/v1/material/inbound/${inbound.id}/submit`)
    expect(sub.body?.code, `直接出库提交: ${sub.body?.message || ''}`).toBe(200)
    const stock = await getStock(CEMENT, projectAId)
    expect(stock, '直接出库仍应建库存行').toBeTruthy()
    expect(Number(stock.stockQuantity), '入后即出净零').toBe(0)
    expect(Number(stock.totalInbound)).toBe(5)
    expect(Number(stock.totalOutbound), '自动出库统计').toBe(5)
    // 生成的 PICK 出库单（状态 APPROVED）
    const ob = await findWhere(`/api/v1/material/outbound/page?page=1&size=10&projectId=${projectAId}&outboundType=PICK`,
      (r: any) => r.status === 'APPROVED')
    expect(ob, '应生成直接出库 PICK 单').toBeTruthy()
    expect(ob.status).toBe('APPROVED')
  })
})

test.describe('B-1 出库（PICK/RETURN/删除回填）', () => {
  test('@matrix B-2-4 PICK 出库扣库存 + 超量拦截 + 草稿删除回填', async () => {
    // 超量拦截（事务回滚，库存不受损）
    const over = await apiJson('POST', '/api/v1/material/outbound', {
      projectId: projectAId, outboundType: 'PICK', outboundDate: TODAY,
      details: [{ materialName: STEEL, specification: SPEC, unit: '吨', quantity: 999 }],
    })
    expect(over.body?.code, '超量 PICK 应拦截').not.toBe(200)
    expect(over.body?.message).toContain('库存不足')
    expect(Number((await getStock(STEEL, projectAId))?.stockQuantity), '拦截后库存不受损').toBe(10)
    // 正常领料 4
    const cr = await apiJson('POST', '/api/v1/material/outbound', {
      projectId: projectAId, outboundType: 'PICK', outboundDate: TODAY,
      details: [{ materialName: STEEL, specification: SPEC, unit: '吨', quantity: 4, unitPrice: 100 }],
    })
    expect(cr.body?.code, '创建 PICK 出库').toBe(200)
    const outbound = await findWhere(`/api/v1/material/outbound/page?page=1&size=10&projectId=${projectAId}&outboundType=PICK`,
      (r: any) => r.status === 'DRAFT')
    expect(outbound, 'PICK 草稿应可定位').toBeTruthy()
    expect(outbound.status).toBe('DRAFT')
    expect(Number((await getStock(STEEL, projectAId))?.stockQuantity), 'save 即扣库存').toBe(6)
    const sub = await apiJson('POST', `/api/v1/material/outbound/${outbound.id}/submit`)
    expect(sub.body?.code, '出库提交直批').toBe(200)
    // 草稿出库删除对称回填（2 → 删后恢复）
    const cr2 = await apiJson('POST', '/api/v1/material/outbound', {
      projectId: projectAId, outboundType: 'PICK', outboundDate: TODAY,
      details: [{ materialName: STEEL, specification: SPEC, unit: '吨', quantity: 2, unitPrice: 100 }],
    })
    expect(cr2.body?.code).toBe(200)
    const draft2 = await findWhere(`/api/v1/material/outbound/page?page=1&size=10&projectId=${projectAId}&outboundType=PICK`,
      (r: any) => r.status === 'DRAFT')
    expect(draft2, '草稿 PICK 应可定位').toBeTruthy()
    expect(Number((await getStock(STEEL, projectAId))?.stockQuantity)).toBe(4)
    const del = await apiJson('DELETE', `/api/v1/material/outbound/${draft2.id}`)
    expect(del.body?.code, '草稿出库删除').toBe(200)
    expect(Number((await getStock(STEEL, projectAId))?.stockQuantity), '删除后库存回填').toBe(6)
  })

  test('@matrix B-2-9/B-M-X3 RETURN+合同 退货→PENDING 退款生成→删退货作废退款（翻正向）', async () => {
    // 2026-08-21 material_refund_approval 部署租户 1（缺口#4）→ 退货创建不再事务回滚
    const before = Number((await getStock(STEEL, projectAId))?.stockQuantity)
    const cr = await apiJson('POST', '/api/v1/material/outbound', {
      projectId: projectAId, outboundType: 'RETURN', returnType: 'RETURN_REFUND',
      contractId: purchaseContractId, outboundDate: TODAY,
      details: [{ materialName: STEEL, specification: SPEC, unit: '吨', quantity: 3, unitPrice: 100 }],
    })
    expect(cr.body?.code, `退货创建（退款流程已部署）: ${cr.body?.message || ''}`).toBe(200)
    expect(Number((await getStock(STEEL, projectAId))?.stockQuantity), 'save 即扣库存').toBe(before - 3)
    // 出库主表无 totalAmount 字段（仅入库有，实证 MaterialOutboundService.save）→ 按状态定位
    const outbound = await findWhere(`/api/v1/material/outbound/page?page=1&size=10&projectId=${projectAId}&outboundType=RETURN`,
      (r: any) => r.status === 'DRAFT')
    expect(outbound, '退货单应可定位').toBeTruthy()
    // PENDING 退款自动生成：金额 = quantity × 入库单价（3 × 100 = 300）
    const refundPg = await apiJson('GET', `/api/v1/material/refund?page=1&size=20&contractId=${purchaseContractId}`)
    const refund = (refundPg.body?.data?.records || [])
      .find((r: any) => String(r.outboundId) === String(outbound.id) && r.status === 'PENDING')
    expect(refund, 'PENDING 退款应自动生成').toBeTruthy()
    expect(Number(refund.refundAmount), '退款金额 = quantity × 入库单价').toBe(300)
    // 删除退货单 → 库存回填 + 同步作废 PENDING 退款（CANCELED，防退款继续审批扣款）
    const del = await apiJson('DELETE', `/api/v1/material/outbound/${outbound.id}`)
    expect(del.body?.code, '退货单删除').toBe(200)
    expect(Number((await getStock(STEEL, projectAId))?.stockQuantity), '删除后库存回填').toBe(before)
    const refundAfterPg = await apiJson('GET', `/api/v1/material/refund?page=1&size=20&contractId=${purchaseContractId}`)
    const refundAfter = (refundAfterPg.body?.data?.records || []).find((r: any) => String(r.id) === String(refund.id))
    expect(refundAfter?.status, 'PENDING 退款应同步作废').toBe('CANCELED')
    // 单据作废但退款流程实例仍在运行 → 按业务撤回，避免待办残留（发起人幂等）
    const wd = await apiJson('POST', `/api/v1/workflow/approval/withdraw-by-business?businessType=MATERIAL_REFUND&businessId=${refund.id}`)
    expect(wd.body?.data, '退款流程应可撤回').toBe(true)
    // 无合同的退货不发布事件（正向对照钉住守卫边界）
    const noContract = await apiJson('POST', '/api/v1/material/outbound', {
      projectId: projectAId, outboundType: 'RETURN', outboundDate: TODAY,
      details: [{ materialName: STEEL, specification: SPEC, unit: '吨', quantity: 1, unitPrice: 100 }],
    })
    expect(noContract.body?.code, '无合同退货不触发退款流程应成功').toBe(200)
    const noContractOutbound = await findWhere(`/api/v1/material/outbound/page?page=1&size=10&projectId=${projectAId}&outboundType=RETURN`,
      (r: any) => r.status === 'DRAFT')
    expect(noContractOutbound, '无合同退货单应可定位').toBeTruthy()
    // 清理：草稿退货删除回填库存（避免残留干扰后续用例）
    const del2 = await apiJson('DELETE', `/api/v1/material/outbound/${noContractOutbound.id}`)
    expect(del2.body?.code, '草稿退货删除').toBe(200)
  })
})

test.describe('B-1 调拨（同项目守卫 + BPMN 双向库存）', () => {
  test('@matrix B-3-3 同项目调拨 API 直连拦截（后端守卫盲点 11a 反向证据）', async () => {
    const cr = await apiJson('POST', '/api/v1/material/transfer', {
      fromProjectId: projectAId, toProjectId: projectAId, transferDate: TODAY,
      details: [{ materialName: STEEL, specification: SPEC, unit: '吨', quantity: 1, unitPrice: 100 }],
    })
    expect(cr.body?.code, '同项目调拨应拦截').not.toBe(200)
    expect(cr.body?.message).toContain('不能相同')
  })

  test('@matrix B-3-7/B-M-X4 调拨提交→BPMN 审批→双向库存变更', async () => {
    const cr = await apiJson('POST', '/api/v1/material/transfer', {
      fromProjectId: projectAId, toProjectId: projectBId, transferDate: TODAY,
      details: [{ materialName: STEEL, specification: SPEC, unit: '吨', quantity: 2, unitPrice: 100 }],
    })
    expect(cr.body?.code, '创建调拨单').toBe(200)
    const transfer = await findWhere(`/api/v1/material/transfer/page?page=1&size=10&fromProjectId=${projectAId}`,
      (r: any) => r.status === 'DRAFT' && String(r.toProjectId) === projectBId)
    expect(transfer, '调拨单应可定位').toBeTruthy()
    expect(transfer.status, 'save 仅落单据').toBe('DRAFT')
    expect(Number((await getStock(STEEL, projectAId))?.stockQuantity), 'save 不动库存').toBe(6)
    const sub = await apiJson('POST', `/api/v1/material/transfer/${transfer.id}/submit`)
    expect(sub.body?.code, '调拨提交启动 BPMN（material_transfer_approval 未部署则此处失败→DATA 受阻登记）').toBe(200)
    const submitted = await apiJson('GET', `/api/v1/material/transfer/${transfer.id}`)
    expect(submitted.body?.data?.status).toBe('SUBMITTED')
    await completeAllTodos(transfer.id)
    const approved = await apiJson('GET', `/api/v1/material/transfer/${transfer.id}`)
    expect(approved.body?.data?.status, '审批通过应 APPROVED').toBe('APPROVED')
    // 双向库存：调出 6→4、调入新建行 2
    expect(Number((await getStock(STEEL, projectAId))?.stockQuantity), '调出方减').toBe(4)
    const toStock = await getStock(STEEL, projectBId)
    expect(toStock, '调入方应新建库存行').toBeTruthy()
    expect(Number(toStock.stockQuantity)).toBe(2)
    expect(Number(toStock.totalTransferIn)).toBe(2)
  })
})

test.describe('B-1 库存预警通道与分页口径', () => {
  test('@matrix B-4-3 库存预警筛选参数通道（warning=true 200 + 结构）', async () => {
    // 前端「仅看预警」传 warning=true（vitest B-4-3 钉参数透传）；后端通道实证
    const pg = await apiJson('GET', `/api/v1/material/stock/page?page=1&size=50&warning=true`)
    expect(pg.body?.code, 'warning 筛选通道').toBe(200)
    expect(Array.isArray(pg.body?.data?.records), '应返回分页结构').toBe(true)
  })

  test('@matrix 分页：page/size 生效 + pageNum/pageSize 回落默认（失配消除，outbound/transfer）', async () => {
    // outbound：page/size 生效；未知参数 pageNum/pageSize 被忽略回落默认，与显式默认口径一致。
    // 前端 2026-08-21 已对齐 page/size（vitest material-inbound-outbound-matrix B-2-10 钉住）
    const p1 = await apiJson('GET', '/api/v1/material/outbound/page?page=1&size=1')
    expect(p1.body?.data?.records?.length, 'page/size 口径应生效').toBe(1)
    const p2 = await apiJson('GET', '/api/v1/material/outbound/page?pageNum=1&pageSize=1')
    const p3 = await apiJson('GET', '/api/v1/material/outbound/page?page=1&size=10')
    const ids2 = (p2.body?.data?.records || []).map((r: any) => String(r.id))
    const ids3 = (p3.body?.data?.records || []).map((r: any) => String(r.id))
    expect(ids2.length, '回落默认分页 size=10 口径').toBeLessThanOrEqual(10)
    expect(ids2, 'outbound pageNum/pageSize 被忽略，应与默认口径一致').toEqual(ids3)
    // transfer 同构
    const t1 = await apiJson('GET', '/api/v1/material/transfer/page?page=1&size=1')
    expect(t1.body?.data?.records?.length, 'transfer page/size 口径应生效').toBe(1)
    const t2 = await apiJson('GET', '/api/v1/material/transfer/page?pageNum=1&pageSize=1')
    const t3 = await apiJson('GET', '/api/v1/material/transfer/page?page=1&size=10')
    const tids2 = (t2.body?.data?.records || []).map((r: any) => String(r.id))
    const tids3 = (t3.body?.data?.records || []).map((r: any) => String(r.id))
    expect(tids2.length, 'transfer 回落默认分页口径').toBeLessThanOrEqual(10)
    expect(tids2, 'transfer pageNum/pageSize 被忽略，应与默认口径一致').toEqual(tids3)
  })
})
