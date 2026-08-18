/**
 * 真实模式 E2E：A-3 合同管理账本补测（账本全量补齐 M2，2026-08）
 *
 * @matrix A7-05 提交→SUBMITTED / A7-06 非法状态提交拦截 / A7-07 删除草稿 /
 *   A9-01 页面加载合同信息与清单 / A9-07 上传解析成功统计 / A9-12 清除清单 /
 *   A10-12 产值提交审批闭环（驳回→重提→通过）/ A10-15 分页参数失配现状钉住 /
 *   A-X10 EFFECTIVE 前提钉住 / A-X11 BOQ completedQuantity 随审批累加 /
 *   A-X12 驳回后可重新提交 / A-X13 BLOCK 不拦收入合同 / A-X14 cumulativeOutput 逐期累加
 *
 * 实证（探测 2026-08）：
 *   - 合同 submit DRAFT→SUBMITTED（construction_contract_approval 两级：
 *     managerApproval→financeApproval，SUPER_ADMIN 可 complete 任意任务）；
 *     非草稿重提交 code=500「仅草稿状态可提交」
 *   - 产值无 DELETE 通道（OutputReportController 仅 GET/POST/submit）→ 本 spec
 *     产生的 APPROVED 产值残留无法清理（E2E_TEST_ 前缀可识别，巡检兜底）
 *   - 产值分页后端 page/size（默认 1/10），前端传 pageNum/pageSize → 分页失配钉住
 *   - BOQ 上传模板列：编码|名称|单位|数量|单价|合价（BoqServiceUploadFlowTest 实证），
 *     本 spec 内以 zlib 手工构造最小 xlsx（仓库无 xlsx 依赖，不为单测引入新依赖）
 *   - BOQ 操作仅限 EFFECTIVE/CHANGING 合同（ALLOWED_UPLOAD_STATUSES）；
 *     deleteBoq 有产值引用检查（被引用拦截）；totalAmount=Σ顶层条目合价；
 *     GET /boq 返回平铺列表（前端按 parentId 建树）；上传回写 contractAmount
 *
 * 残留声明：EFFECTIVE 合同（主链+清单清除合同 C）+ APPROVED 产值×2 + 项目无删除通道，
 * 均 E2E_TEST_ 前缀可识别（巡检兜底）
 *
 * 纯前端守卫用例（A9-02/03/04/05/06/09/10/11/13、A10-02~11/13/14）见
 * src/__tests__/contract-output-matrix / contract-pages / contract-index-matrix /
 * contract-form-matrix 组件测试。
 */
import { test, expect } from '@playwright/test'
import { deflateRawSync } from 'node:zlib'
import { authedApiContext, e2ePrefix, todayStr, type AuthedContext } from './real-helper'

test.describe.configure({ mode: 'serial' })

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'
const PREFIX = e2ePrefix()
const TODAY = todayStr()

let authed: AuthedContext | null = null
let projectId = ''
const projectName = `${PREFIX}_合同项目`
let contractAId = ''      // 主链合同（最终 EFFECTIVE，无删除通道 → 残留）
let configId = ''         // BLOCK 控制配置（afterAll 清理）
const partyA = `${PREFIX}_甲方`

// 雪花 ID 纪律（探测实证 2026-08）：创建 payload 一律传字符串 projectId（Jackson 接受
// string→Long）；Number() 会四舍五入雪花 ID（...778→...800）导致存值失真、精确筛选恒空；
// resp.json() 同损精度，id 定位一律用 String() 比较。

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

/** 按项目定位合同（创建响应 R<Void> 无 id） */
async function findContract(party: string) {
  const pg = await apiJson('GET', `/api/v1/contract/page?page=1&size=50&projectId=${projectId}`)
  return (pg.body?.data?.records || []).find((c: any) => c.partyAName === party)
}

/** 循环完成目标业务的全部待办（SUPER_ADMIN 可完成任意任务，finance-write 实证范式） */
async function completeAllTodos(businessId: string | number, maxRounds = 6): Promise<void> {
  for (let i = 0; i < maxRounds; i++) {
    const resp = await authed!.get(`${API_BASE}/api/v1/workflow/approval/todo`, { params: { page: 1, size: 50 } })
    const todos = ((await resp.json()).data?.records || []).filter((t: any) => String(t.businessId) === String(businessId))
    if (todos.length === 0) return
    const c = await authed!.post(`${API_BASE}/api/v1/workflow/approval/complete`, {
      data: { taskId: todos[0].taskId, comment: 'E2E a3-contract 审批推进' },
    })
    expect(c.status(), `完成审批任务 ${todos[0].taskId}`).toBe(200)
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error(`businessId=${businessId} 待办 ${maxRounds} 轮未清空`)
}

/** 定位目标业务的第一个待办 */
async function findFirstTodo(businessId: string | number) {
  await new Promise((r) => setTimeout(r, 500))
  const resp = await authed!.get(`${API_BASE}/api/v1/workflow/approval/todo`, { params: { page: 1, size: 50 } })
  return ((await resp.json()).data?.records || []).find((t: any) => String(t.businessId) === String(businessId))
}

// ---------- 最小 xlsx 构造（zip store + deflate，无外部依赖） ----------
function crc32(buf: Buffer): number {
  let crc = 0xffffffff
  for (let n = 0; n < buf.length; n++) {
    let c = (crc ^ buf[n]) & 0xff
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1
    crc = c
  }
  return (crc ^ 0xffffffff) >>> 0
}

function buildZip(entries: { name: string; data: Buffer }[]): Buffer {
  const parts: Buffer[] = []
  const central: Buffer[] = []
  let offset = 0
  for (const e of entries) {
    const nameB = Buffer.from(e.name, 'utf-8')
    const compressed = deflateRawSync(e.data)
    const crc = crc32(e.data)
    const lh = Buffer.alloc(30)
    lh.writeUInt32LE(0x04034b50, 0); lh.writeUInt16LE(20, 4)
    lh.writeUInt16LE(8, 8) // deflate
    lh.writeUInt32LE(crc, 14); lh.writeUInt32LE(compressed.length, 18); lh.writeUInt32LE(e.data.length, 22)
    lh.writeUInt16LE(nameB.length, 26)
    parts.push(lh, nameB, compressed)
    const ch = Buffer.alloc(46)
    ch.writeUInt32LE(0x02014b50, 0); ch.writeUInt16LE(20, 4); ch.writeUInt16LE(20, 6)
    ch.writeUInt16LE(8, 10)
    ch.writeUInt32LE(crc, 16); ch.writeUInt32LE(compressed.length, 20); ch.writeUInt32LE(e.data.length, 24)
    ch.writeUInt16LE(nameB.length, 28)
    ch.writeUInt32LE(offset, 42)
    central.push(ch, nameB)
    offset += 30 + nameB.length + compressed.length
  }
  const centralBuf = Buffer.concat(central)
  const eocd = Buffer.alloc(22)
  eocd.writeUInt32LE(0x06054b50, 0)
  eocd.writeUInt16LE(entries.length, 8); eocd.writeUInt16LE(entries.length, 10)
  eocd.writeUInt32LE(centralBuf.length, 12); eocd.writeUInt32LE(offset, 16)
  return Buffer.concat([...parts, centralBuf, eocd])
}

const COLS = 'ABCDEF'
function cellXml(col: number, row: number, val: string | number): string {
  const ref = `${COLS[col]}${row}`
  return typeof val === 'number'
    ? `<c r="${ref}"><v>${val}</v></c>`
    : `<c r="${ref}" t="inlineStr"><is><t>${val}</t></is></c>`
}

/** BOQ 模板：列0编码|列1名称|列2单位|列3数量|列4单价|列5合价（后端 BoqServiceUploadFlowTest 实证） */
function buildBoqXlsx(rows: (string | number)[][]): Buffer {
  const header = ['项目编码', '项目名称', '单位', '数量', '单价', '合价']
  const rowXmls: string[] = []
  ;[header, ...rows].forEach((cells, i) => {
    rowXmls.push(`<row r="${i + 1}">${cells.map((v, c) => cellXml(c, i + 1, v)).join('')}</row>`)
  })
  const sheet = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>${rowXmls.join('')}</sheetData></worksheet>`
  const workbook = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>`
  const contentTypes = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>`
  const rootRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>`
  const wbRels = `<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>`
  return buildZip([
    { name: '[Content_Types].xml', data: Buffer.from(contentTypes, 'utf-8') },
    { name: '_rels/.rels', data: Buffer.from(rootRels, 'utf-8') },
    { name: 'xl/workbook.xml', data: Buffer.from(workbook, 'utf-8') },
    { name: 'xl/_rels/workbook.xml.rels', data: Buffer.from(wbRels, 'utf-8') },
    { name: 'xl/worksheets/sheet1.xml', data: Buffer.from(sheet, 'utf-8') },
  ])
}

async function getBoqFlatItems(cid: string) {
  const flat = await apiJson('GET', `/api/v1/contracts/${cid}/boq/flat`)
  expect(flat.body?.code, 'BOQ flat 查询').toBe(200)
  return flat.body?.data || []
}

test.beforeAll(async () => {
  authed = await authedApiContext()
  const cr = await apiJson('POST', '/api/v1/project', { projectName, projectNature: '新建', projectType: '市政工程' })
  expect(cr.body?.code, '创建承载项目').toBe(200)
  const pg = await apiJson('GET', `/api/v1/project/page?pageNum=1&pageSize=5&projectName=${encodeURIComponent(projectName)}`)
  projectId = String(pg.body?.data?.records?.[0]?.id || '')
  expect(projectId, '承载项目应可定位').toBeTruthy()
  // A-X13 前提：项目级 BLOCK 配置（收入合同提交不应被拦）
  const cc = await apiJson('POST', '/api/v1/budget-control-configs', { projectId, controlMode: 'BLOCK', warningThreshold: 80 })
  expect(cc.body?.code, '创建 BLOCK 控制配置').toBe(200)
  const list = await apiJson('GET', '/api/v1/budget-control-configs?page=1&size=100')
  configId = String(((list.body?.data?.records) || []).find((c: any) => String(c.projectId) === projectId)?.id || '')
  expect(configId, 'BLOCK 配置应可定位').toBeTruthy()
})

test.afterAll(async () => {
  if (!authed) return
  if (configId) await authed.delete(`${API_BASE}/api/v1/budget-control-configs/${configId}`).catch(() => {})
  // 主链合同 EFFECTIVE、产值 APPROVED 均无删除通道 → 残留以 E2E_TEST_ 前缀可识别（巡检兜底）；
  // 项目因挂合同删不掉，一并残留
  await authed.delete(`${API_BASE}/api/v1/project/${projectId}`).catch(() => {})
  await authed.dispose()
})

test.describe('A-3 合同主链（提交→审批→EFFECTIVE）', () => {
  test('@matrix A7-05/A-X13 BLOCK 项目收入合同提交不受控 DRAFT→SUBMITTED', async () => {
    const cr = await apiJson('POST', '/api/v1/contract', {
      projectId, contractType: 'REGISTER', partyAName: partyA,
      contractAmount: 1000000, taxRate: 9, signingDate: TODAY, startDate: TODAY, endDate: TODAY,
    })
    expect(cr.body?.code, '创建施工合同').toBe(200)
    const contract = await findContract(partyA)
    expect(contract, '合同应可按甲方定位').toBeTruthy()
    expect(contract.status).toBe('DRAFT')
    contractAId = String(contract.id)
    // A-X13：项目配 BLOCK 且无预算，收入合同提交不被管控拦截
    const sub = await apiJson('POST', `/api/v1/contract/${contractAId}/submit`)
    expect(sub.body?.code, 'BLOCK 配置下收入合同提交应放行').toBe(200)
    const detail = await apiJson('GET', `/api/v1/contract/${contractAId}`)
    expect(detail.body?.data?.status).toBe('SUBMITTED')
  })

  test('@matrix A7-06 非草稿重提交拦截', async () => {
    expect(contractAId).toBeTruthy()
    const sub = await apiJson('POST', `/api/v1/contract/${contractAId}/submit`)
    expect(sub.body?.code, 'SUBMITTED 重提交应拦截').not.toBe(200)
    expect(sub.body?.message).toContain('仅草稿状态可提交')
  })

  test('@matrix A-X10 审批两级通过→EFFECTIVE（产值可选前提钉住）', async () => {
    expect(contractAId).toBeTruthy()
    await completeAllTodos(contractAId)
    const detail = await apiJson('GET', `/api/v1/contract/${contractAId}`)
    expect(detail.body?.data?.status, '审批通过后合同应 EFFECTIVE').toBe('EFFECTIVE')
    // 产值下拉数据源口径：status=EFFECTIVE 过滤可查到本合同（vitest A10-02 钉前端参数）
    const eff = await apiJson('GET', `/api/v1/contract/page?page=1&size=50&projectId=${projectId}&status=EFFECTIVE`)
    expect((eff.body?.data?.records || []).some((c: any) => String(c.id) === contractAId)).toBe(true)
  })
})

test.describe('A-3 BOQ 上传与产值审批闭环', () => {
  test('@matrix A9-07/A9-01 xlsx 上传解析成功（统计+树形落地）', async () => {
    expect(contractAId).toBeTruthy()
    const xlsx = buildBoqXlsx([
      // 后端 totalAmount=Σ顶层条目 totalPrice（BoqService 实证）→ 父行必须带合价
      ['1', `${PREFIX}_土建`, '', '', '', 2000],
      ['1.1', `${PREFIX}_挖方`, 'm³', 10, 100, 1000],
      ['1.2', `${PREFIX}_回填`, 'm³', 5, 200, 1000],
    ])
    const resp = await authed!.post(`${API_BASE}/api/v1/contracts/${contractAId}/boq/upload`, {
      multipart: { file: { name: 'boq.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: xlsx } },
    })
    expect(resp.status(), 'BOQ 上传 HTTP').toBe(200)
    const body = await resp.json()
    expect(body.code, 'BOQ 上传解析').toBe(200)
    expect(body.data?.totalItems, '总条目数').toBe(3)
    expect(body.data?.totalAmount, '合计金额').toBe(2000)
    expect(body.data?.levelCount, '层级数').toBeGreaterThanOrEqual(2)
    // A9-01：接口回读（实证：GET /boq 返回平铺列表，前端按 parentId 建树；根=parentId 0/空）
    const tree = await apiJson('GET', `/api/v1/contracts/${contractAId}/boq`)
    expect(tree.body?.code).toBe(200)
    const items = tree.body?.data || []
    expect(items.length, '条目总数').toBe(3)
    const roots = items.filter((i: any) => !i.parentId || String(i.parentId) === '0')
    expect(roots.length, '顶层条目数').toBe(1)
    expect(items.filter((i: any) => String(i.parentId) === String(roots[0].id)).length, '子节点数量').toBe(2)
  })

  test('@matrix A10-12/A-X11/A-X12 产值清单模式：提交→驳回→重提→通过，completedQuantity 累加', async () => {
    expect(contractAId).toBeTruthy()
    const items = await getBoqFlatItems(contractAId)
    const dig = items.find((i: any) => i.itemName === `${PREFIX}_挖方`)
    expect(dig, '挖方清单行应存在').toBeTruthy()
    // 创建清单模式产值（details 仅含完成量>0 行，vitest A10-10 钉前端过滤）
    const cr = await apiJson('POST', '/api/v1/contract/output', {
      projectId, contractId: contractAId,
      reportPeriod: '2026-08', currentOutput: 400, confirmDate: TODAY,
      details: [{ boqItemId: dig.id, quantity: 4, amount: 400 }],
    })
    expect(cr.body?.code, '创建产值').toBe(200)
    const pg = await apiJson('GET', `/api/v1/contract/output?contractId=${contractAId}&page=1&size=50`)
    const report = (pg.body?.data?.records || []).find((r: any) => r.reportPeriod === '2026-08')
    expect(report, '产值记录应可定位').toBeTruthy()
    expect(report.status).toBe('DRAFT')
    // 提交 → SUBMITTED
    const sub = await apiJson('POST', `/api/v1/contract/output/${report.id}/submit`)
    expect(sub.body?.code, '产值提交').toBe(200)
    // A-X12：退回到发起人 → REJECTED
    const todo = await findFirstTodo(report.id)
    expect(todo, '产值提交后应产生待办').toBeTruthy()
    const rej = await apiJson('POST', '/api/v1/workflow/approval/reject-start', { taskId: todo.taskId, comment: 'E2E 驳回测试' })
    expect(rej.body?.code, 'reject-start 驳回').toBe(200)
    await new Promise((r) => setTimeout(r, 500))
    let detail = await apiJson('GET', `/api/v1/contract/output?contractId=${contractAId}&page=1&size=50`)
    expect((detail.body?.data?.records || []).find((r: any) => r.id === report.id)?.status, '驳回后应 REJECTED').toBe('REJECTED')
    // 重新提交 → 审批通过 → APPROVED
    const sub2 = await apiJson('POST', `/api/v1/contract/output/${report.id}/submit`)
    expect(sub2.body?.code, 'REJECTED 重新提交').toBe(200)
    await completeAllTodos(report.id)
    detail = await apiJson('GET', `/api/v1/contract/output?contractId=${contractAId}&page=1&size=50`)
    const approved = (detail.body?.data?.records || []).find((r: any) => r.id === report.id)
    expect(approved?.status, '审批通过后应 APPROVED').toBe('APPROVED')
    expect(Number(approved?.cumulativeOutput), '首期累计产值').toBe(400)
    // A-X11：清单 completedQuantity 随审批累加（dig.id 经 resp.json() 已失真，
    // 按 itemName 重新定位，避免雪花 ID 精度比对陷阱）
    const after = await getBoqFlatItems(contractAId)
    const digAfter = after.find((i: any) => i.itemName === `${PREFIX}_挖方`)
    expect(Number(digAfter?.completedQuantity), 'completedQuantity 应累加 4').toBe(4)
  })

  test('@matrix A-X14 第二期纯金额产值通过→累计 450', async () => {
    expect(contractAId).toBeTruthy()
    const cr = await apiJson('POST', '/api/v1/contract/output', {
      projectId, contractId: contractAId,
      reportPeriod: '2026-09', currentOutput: 50, confirmDate: TODAY,
    })
    expect(cr.body?.code, '创建第二期产值').toBe(200)
    const pg = await apiJson('GET', `/api/v1/contract/output?contractId=${contractAId}&page=1&size=50`)
    const report = (pg.body?.data?.records || []).find((r: any) => r.reportPeriod === '2026-09')
    expect(report, '第二期产值应可定位').toBeTruthy()
    const sub = await apiJson('POST', `/api/v1/contract/output/${report.id}/submit`)
    expect(sub.body?.code).toBe(200)
    await completeAllTodos(report.id)
    const after = await apiJson('GET', `/api/v1/contract/output?contractId=${contractAId}&page=1&size=50`)
    const approved = (after.body?.data?.records || []).find((r: any) => r.id === report.id)
    expect(approved?.status).toBe('APPROVED')
    expect(Number(approved?.cumulativeOutput), '两期累计 400+50').toBe(450)
  })

  test('@matrix A9-12 清除清单：被产值引用拦截（实证）+ 无引用清除成功', async () => {
    expect(contractAId).toBeTruthy()
    // 实证（2026-08）：APPROVED 产值引用清单 → deleteBoq 引用检查拦截
    // （BoqService.hasOutputReportReference，文案「该合同的清单条目已被产值上报引用，无法删除」）
    const blocked = await apiJson('DELETE', `/api/v1/contracts/${contractAId}/boq`)
    expect(blocked.body?.code, '被引用清除应拦截').not.toBe(200)
    expect(blocked.body?.message).toContain('已被产值上报引用')
    const kept = await apiJson('GET', `/api/v1/contracts/${contractAId}/boq`)
    expect((kept.body?.data || []).length, '拦截后清单应完好').toBe(3)
    // 正向分支：无引用的 EFFECTIVE 合同清除成功（实证：BOQ 操作仅限 EFFECTIVE/CHANGING
    // 状态，BoqService.ALLOWED_UPLOAD_STATUSES；DRAFT 上传/清除均拒）
    const partyC = `${PREFIX}_清单清除合同`
    const cr = await apiJson('POST', '/api/v1/contract', {
      projectId, contractType: 'REGISTER', partyAName: partyC,
      contractAmount: 100, taxRate: 9, signingDate: TODAY,
    })
    expect(cr.body?.code, '创建清单清除合同').toBe(200)
    const contractC = await findContract(partyC)
    // DRAFT 上传被拒（状态守卫实证）
    const xlsx = buildBoqXlsx([['1', `${PREFIX}_单项`, 'm³', 1, 1, 1]])
    const upDraft = await authed!.post(`${API_BASE}/api/v1/contracts/${contractC.id}/boq/upload`, {
      multipart: { file: { name: 'boq.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: xlsx } },
    })
    expect((await upDraft.json()).code, 'DRAFT 上传应被状态守卫拒绝').not.toBe(200)
    // 推进至 EFFECTIVE 后上传+清除成功（该合同无删除通道，与主链同档残留）
    const sub = await apiJson('POST', `/api/v1/contract/${contractC.id}/submit`)
    expect(sub.body?.code, '合同 C 提交').toBe(200)
    await completeAllTodos(contractC.id)
    const up = await authed!.post(`${API_BASE}/api/v1/contracts/${contractC.id}/boq/upload`, {
      multipart: { file: { name: 'boq.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: xlsx } },
    })
    expect((await up.json()).code, 'EFFECTIVE 上传清单').toBe(200)
    const del = await apiJson('DELETE', `/api/v1/contracts/${contractC.id}/boq`)
    expect(del.body?.code, '无引用清除清单').toBe(200)
    const tree = await apiJson('GET', `/api/v1/contracts/${contractC.id}/boq`)
    expect(tree.body?.data || [], '清除后清单应为空').toEqual([])
  })
})

test.describe('A-3 合同删除守卫与产值分页口径', () => {
  test('@matrix A7-07 草稿合同删除成功', async () => {
    const partyB = `${PREFIX}_乙方草稿`
    const cr = await apiJson('POST', '/api/v1/contract', {
      projectId, contractType: 'REGISTER', partyAName: partyB,
      contractAmount: 100, taxRate: 9, signingDate: TODAY,
    })
    expect(cr.body?.code, '创建草稿合同').toBe(200)
    const contract = await findContract(partyB)
    expect(contract?.status).toBe('DRAFT')
    const del = await apiJson('DELETE', `/api/v1/contract/${contract.id}`)
    expect(del.body?.code, 'DRAFT 删除（E2E_TEST_ 前缀放行）').toBe(200)
    expect(await findContract(partyB), '删除后不可再定位').toBeUndefined()
  })

  test('@matrix A10-15 产值分页参数失配现状钉住（后端 page/size vs 前端 pageNum/pageSize）', async () => {
    // 后端口径 page/size 生效：size=1 恒返回 1 条（产值表含演示+残留数据 ≥2 条）
    const p1 = await apiJson('GET', '/api/v1/contract/output?page=1&size=1')
    expect(p1.body?.code).toBe(200)
    expect(p1.body?.data?.records?.length, 'page/size 口径应生效').toBe(1)
    // 前端口径 pageNum/pageSize 不被识别：size=1 的请求参数被忽略，返回默认 10 条口径
    const p2 = await apiJson('GET', '/api/v1/contract/output?pageNum=1&pageSize=1')
    expect(p2.body?.code).toBe(200)
    expect(p2.body?.data?.records?.length, '现状钉住：pageNum/pageSize 失配回落默认分页').toBeGreaterThan(1)
  })
})
