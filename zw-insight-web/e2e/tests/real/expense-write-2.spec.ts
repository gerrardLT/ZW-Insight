/**
 * 真实模式 E2E 测试：预算/材料/分包支出域写路径 UI（前端补测四步收口 W3，2026-08）
 *
 * @matrix 预算编制必填/创建/提交直批 / 预算管控 BLOCK UI 拦截 Toast /
 *   B-1 入库提交→库存更新 / B-2-4 出库超量后端拦截 / 出库类型筛选抓包 /
 *   B-21 分包结算必填/创建/小计联动/提交状态流转
 *
 * 范式与 expense-write.spec.ts / finance-write.spec.ts 一致：storageState=admin
 * （租户 1），UI 操作经 waitForResponse 锚定真实接口硬断言（禁止容忍断言），
 * 创建的测试单据 afterAll 经 API 逆序清理（E2E_TEST_ 前缀可识别；W1 守卫放行后
 * APPROVED 单据亦可删——入库删除对称回滚库存，预算删除经明细 itemName marker）。
 *
 * 数据前提与自置：
 *   - 预算：每项目仅 1 条 ORIGINAL（BudgetService.save 实证），演示项目已有 →
 *     API 自置 E2E_TEST_ 承载项目，afterAll 删除（项目删除守卫已放行前缀）
 *   - BLOCK 负向：自置 E2E_TEST_ 项目 + 项目级 BLOCK 配置（不依赖演示项目状态，
 *     避免与 finance-write C6 的 EXEMPT 临时切换并行互扰，e2e-real workers=4 实证）；
 *     支出单据用 other-payment（表单最简且挂 @BudgetCheck，无科目额度必拦截）
 *   - 入库/出库/结算：用非 E2E 演示项目（真实合同/库存前提）
 */
import { test, expect, request as pwRequest } from '@playwright/test'
import { fetchAllProjects } from './real-helper'

// 写路径用例文件内串行（多弹窗/共享数据态用例并行互扰，expense-write 实证）
test.describe.configure({ mode: 'serial' })

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'

// 后端仅认 Authorization: Bearer 头（AuthInterceptor 实证）；不可重新 API 登录取
// token——admin max-devices=5，新会话会踢出 UI storageState 会话（P2 实跑事故）
let authed: Awaited<ReturnType<typeof pwRequest.newContext>> | null = null

test.beforeAll(async () => {
  const fs = await import('node:fs')
  const st = JSON.parse(fs.readFileSync('./e2e/.auth/storage-state.json', 'utf-8'))
  const token = (st.origins || []).flatMap((o: any) => o.localStorage || [])
    .find((kv: any) => kv.name === 'token')?.value
  expect(token, 'storageState 应含登录 token').toBeTruthy()
  authed = await pwRequest.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  })
})

const TS = Date.now()
const E2E_PREFIX = `E2E_TEST_${TS}`
const pad = (n: number) => String(n).padStart(2, '0')
const _d = new Date()
const TODAY = `${_d.getFullYear()}-${pad(_d.getMonth() + 1)}-${pad(_d.getDate())}`

// 本 spec 创建的单据/资源（afterAll 逆序清理）
// 注：超量出库用例改为钉住 save 阶段拦截（不落库），本 spec 不再产生出库单
const createdSettlementIds: number[] = []
const createdInboundIds: number[] = []
const createdBudgetIds: number[] = []
let budgetProjectId: string | null = null   // 预算承载项目（每项目仅 1 条 ORIGINAL）
let blockProjectId: string | null = null    // BLOCK 管控负向项目
let blockConfigId: string | null = null     // 其项目级预算管控配置
let budgetIdForSubmit: number | null = null // 预算创建→提交跨用例传递
let settlementIdForSubmit: number | null = null

test.afterAll(async () => {
  if (!authed) return
  // 逆序清理：结算 → 入库（APPROVED 回滚库存）→ 预算 → 配置 → 项目
  for (const id of createdSettlementIds.reverse()) {
    await authed.delete(`${API_BASE}/api/v1/subcontract/settlement/${id}`).catch(() => {})
  }
  for (const id of createdInboundIds.reverse()) {
    await authed.delete(`${API_BASE}/api/v1/material/inbound/${id}`).catch(() => {})
  }
  for (const id of createdBudgetIds.reverse()) {
    await authed.delete(`${API_BASE}/api/v1/budget/${id}`).catch(() => {})
  }
  if (blockConfigId) {
    await authed.delete(`${API_BASE}/api/v1/budget-control-configs/${blockConfigId}`).catch(() => {})
  }
  if (budgetProjectId) {
    await authed.delete(`${API_BASE}/api/v1/project/${budgetProjectId}`).catch(() => {})
  }
  if (blockProjectId) {
    await authed.delete(`${API_BASE}/api/v1/project/${blockProjectId}`).catch(() => {})
  }
  await authed.dispose()
})

/** 打开 el-select 并选第一个候选项 */
async function pickFirstOption(page: any, selectLocator: any, waitMs = 800) {
  await selectLocator.click()
  await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
  await page.waitForTimeout(waitMs)
}

/** filterable remote 项目下拉（ProjectSelector/预算页同款）：输入名称触发 remote-method 后选中 */
async function selectProjectByName(page: any, container: any, name: string) {
  const select = container.locator('.el-form-item:has(.el-form-item__label:text-is("项目")) .el-select')
  await select.click()
  await select.locator('input').first().fill(name)
  await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: name }).first().click()
  await page.waitForTimeout(800)
}

/** 解析演示项目（非 E2E 前缀首个），返回 {id, name}；项目表翻页全量拉取（2026-08-23 硬化） */
async function resolveDemoProject(request: any): Promise<{ id: string; name: string }> {
  const recs = await fetchAllProjects(request)
  const demo = recs.find((p: any) => !String(p.projectName).includes('E2E'))
  expect(demo, '演示数据前提：应存在非 E2E 项目').toBeTruthy()
  return { id: String(demo.id), name: demo.projectName }
}

/** ElMessageBox 确认框点「确定」 */
async function confirmMessageBox(page: any) {
  const box = page.locator('.el-message-box:visible')
  await expect(box).toBeVisible({ timeout: 10_000 })
  await box.locator('button:has-text("确定")').click()
}

test.describe('支出域 — 预算编制写路径（@matrix 预算必填/创建/提交直批）', () => {
  test('新增预算编制弹窗 — 必填守卫（空态确定不发 POST）', async ({ page }) => {
    let postCount = 0
    page.on('request', (r) => {
      if (r.url().includes('/v1/budget') && r.method() === 'POST') postCount++
    })
    await page.goto('/budget')
    await page.waitForSelector('button:has-text("新增预算编制")', { timeout: 30_000 })
    await page.locator('button:has-text("新增预算编制")').click()
    const dialog = page.locator('.el-dialog:has-text("新增预算编制")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
    await page.waitForTimeout(600)
    expect(postCount, '必填拦截下不应发出创建请求').toBe(0)
  })

  test('完整创建 DRAFT（含明细行）→ 提交直批 APPROVED', async ({ page }) => {
    test.setTimeout(180_000)
    const request = authed!
    // API 自置承载项目：每项目仅 1 条 ORIGINAL（BudgetService.save 实证），演示项目已有
    const prjName = `${E2E_PREFIX}_预算项目`
    const prjResp = await request.post(`${API_BASE}/api/v1/project`, {
      data: { projectName: prjName, projectType: 'BUILDING', projectAddress: 'E2E 预算测试', needTender: 0 },
    })
    expect((await prjResp.json()).code, '创建预算承载项目').toBe(200)
    const prjPage = await request.get(`${API_BASE}/api/v1/project/page`, { params: { page: 1, size: 20, projectName: prjName } })
    budgetProjectId = String(((await prjPage.json()).data?.records || []).find((p: any) => p.projectName === prjName)?.id)
    expect(budgetProjectId, '新建项目应可差集定位').toBeTruthy()

    const beforeResp = await request.get(`${API_BASE}/api/v1/budget/page`, { params: { page: 1, size: 50 } })
    const beforeIds = new Set(((await beforeResp.json()).data?.records || []).map((r: any) => r.id))

    await page.goto('/budget')
    await page.waitForSelector('button:has-text("新增预算编制")', { timeout: 30_000 })
    await page.locator('button:has-text("新增预算编制")').click()
    const dialog = page.locator('.el-dialog:has-text("新增预算编制")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 项目（filterable remote）
    await selectProjectByName(page, dialog, prjName)
    // 预算总额
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("预算总额")) input').first().fill('10000')
    // 明细行（itemName 带 E2E_TEST_ 前缀 → W1 补丁②清理守卫 marker）
    await dialog.locator('button:has-text("添加明细行")').click()
    const row = dialog.locator('.el-table__row').first()
    await row.locator('td').nth(1).locator('input').fill(`${E2E_PREFIX}_钢材`)
    await row.locator('td').nth(3).locator('input').fill('1')
    await row.locator('td').nth(4).locator('input').fill('10000')
    await expect(dialog.locator('text=明细合计')).toContainText('10,000')
    // 创建
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/budget') && !resp.url().includes('/submit') && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    expect((await createResp.json()).code, '创建预算').toBe(200)
    // 差集定位 + DRAFT 列表可见
    const pageResp = await request.get(`${API_BASE}/api/v1/budget/page`, { params: { page: 1, size: 50, projectId: budgetProjectId } })
    const created = ((await pageResp.json()).data?.records || []).find((r: any) => !beforeIds.has(r.id))
    expect(created, '新预算应出现在列表（差集定位）').toBeTruthy()
    expect(created.status, '新建预算应为 DRAFT').toBe('DRAFT')
    createdBudgetIds.push(created.id)
    budgetIdForSubmit = created.id

    // UI 提交 → BudgetService.submit 无流程依赖直批（22-budget-control.spec.ts 实证）
    const budgetRow = page.locator('.el-table__row', { hasText: prjName }).first()
    await expect(budgetRow).toBeVisible({ timeout: 15_000 })
    await expect(budgetRow.locator('.el-tag')).toContainText('草稿')
    const [submitResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes(`/v1/budget/${created.id}/submit`) && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      (async () => {
        await budgetRow.locator('button:has-text("提交")').click()
        await confirmMessageBox(page)
      })(),
    ])
    expect(submitResp.status()).toBe(200)
    expect((await submitResp.json()).code, '提交预算（直批）').toBe(200)
    const detail = await request.get(`${API_BASE}/api/v1/budget/${created.id}`)
    expect((await detail.json()).data?.status, '预算提交后应直批 APPROVED').toBe('APPROVED')
    // 明细回写：总额以明细合计为准（submit 汇总口径）
    expect(Number((await detail.json()).data?.totalAmount), '总额应为明细合计 10000').toBe(10000)
  })
})

test.describe('支出域 — 预算管控 BLOCK UI 拦截（@matrix A-14 UI 层）', () => {
  test('BLOCK 项目创建支出单据被拒 — 错误 Toast 可见且无落库', async ({ page }) => {
    test.setTimeout(150_000)
    const request = authed!
    // 自置 BLOCK 项目 + 项目级配置（不依赖演示项目状态，避免与 C6 EXEMPT 切换互扰）
    const prjName = `${E2E_PREFIX}_管控项目`
    const prjResp = await request.post(`${API_BASE}/api/v1/project`, {
      data: { projectName: prjName, projectType: 'BUILDING', projectAddress: 'E2E 管控测试', needTender: 0 },
    })
    expect((await prjResp.json()).code, '创建管控测试项目').toBe(200)
    const prjPage = await request.get(`${API_BASE}/api/v1/project/page`, { params: { page: 1, size: 20, projectName: prjName } })
    blockProjectId = String(((await prjPage.json()).data?.records || []).find((p: any) => p.projectName === prjName)?.id)
    expect(blockProjectId).toBeTruthy()
    const cfgResp = await request.post(`${API_BASE}/api/v1/budget-control-configs`, {
      data: { projectId: blockProjectId, controlMode: 'BLOCK', warningThreshold: 80 },
    })
    expect((await cfgResp.json()).code, '创建项目级 BLOCK 配置').toBe(200)
    const effResp = await request.get(`${API_BASE}/api/v1/budget-control-configs/project/${blockProjectId}`)
    const eff = (await effResp.json()).data
    expect(eff?.controlMode, 'BLOCK 配置应生效').toBe('BLOCK')
    expect(Number(eff?.isDefault), '应为项目级配置').toBe(0)
    blockConfigId = String(eff.id)

    // UI 创建 other-payment（挂 @BudgetCheck，新项目无科目额度必拦截）
    const beforeResp = await request.get(`${API_BASE}/api/v1/finance/other-payment`, { params: { page: 1, size: 200 } })
    const beforeIds = new Set(((await beforeResp.json()).data?.records || []).map((r: any) => r.id))
    await page.goto('/finance/other-payment')
    await page.waitForSelector('button:has-text("新增其他费用付款")', { timeout: 30_000 })
    await page.locator('button:has-text("新增其他费用付款")').click()
    const dialog = page.locator('.el-dialog:has-text("新增其他费用付款")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await selectProjectByName(page, dialog, prjName)
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("付款人")) input').first().fill(`${E2E_PREFIX}_付款人`)
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("付款金额")) input').first().fill('1')
    const dateInput = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("付款日期")) input').first()
    await dateInput.fill(TODAY)
    await dateInput.press('Enter')
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/finance/other-payment') && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    const body = await createResp.json()
    expect(body.code, 'BLOCK + 无科目额度应拦截创建').not.toBe(200)
    expect(String(body.message), '拦截信息应含预算语义').toMatch(/预算/)
    // 前端错误 Toast 可见（axios 拦截器 ElMessage.error）
    await expect(page.locator('.el-message--error').first()).toContainText('预算', { timeout: 10_000 })
    // 无落库（差集为空）
    const afterResp = await request.get(`${API_BASE}/api/v1/finance/other-payment`, { params: { page: 1, size: 200 } })
    const newOnes = ((await afterResp.json()).data?.records || []).filter((r: any) => !beforeIds.has(r.id))
    expect(newOnes.length, 'BLOCK 拦截下不应落库').toBe(0)
  })
})

test.describe('支出域 — 材料入库写路径（@matrix B-1）', () => {
  test('新增入库单弹窗 — 必填守卫', async ({ page }) => {
    await page.goto('/material/inbound')
    await page.waitForSelector('button:has-text("新增入库单")', { timeout: 30_000 })
    await page.locator('button:has-text("新增入库单")').click()
    const dialog = page.locator('.el-dialog:has-text("新增入库单")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
  })

  test('完整创建 DRAFT → 提交 APPROVED → 库存更新断言', async ({ page }) => {
    test.setTimeout(180_000)
    const request = authed!
    const demo = await resolveDemoProject(request)
    const materialName = `${E2E_PREFIX}_螺纹钢`

    const beforeResp = await request.get(`${API_BASE}/api/v1/material/inbound/page`, { params: { page: 1, size: 50 } })
    const beforeIds = new Set(((await beforeResp.json()).data?.records || []).map((r: any) => r.id))

    await page.goto('/material/inbound')
    await page.waitForSelector('button:has-text("新增入库单")', { timeout: 30_000 })
    await page.locator('button:has-text("新增入库单")').click()
    const dialog = page.locator('.el-dialog:has-text("新增入库单")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await selectProjectByName(page, dialog, demo.name)
    const dateInput = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("入库日期")) input').first()
    await dateInput.fill(TODAY)
    await dateInput.press('Enter')
    // 明细：新增弹窗默认 1 行（newDetail），材料名带 E2E_TEST_ 前缀（W1 补丁②清理 marker）
    const row = dialog.locator('.el-table__row').first()
    await row.locator('td').nth(0).locator('input').fill(materialName)
    await row.locator('td').nth(3).locator('input').fill('5')
    await row.locator('td').nth(4).locator('input').fill('1')
    await expect(row.locator('td').nth(5)).toContainText('5.00')
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/material/inbound') && !resp.url().includes('/submit') && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    expect((await createResp.json()).code, '创建入库单').toBe(200)
    const pageResp = await request.get(`${API_BASE}/api/v1/material/inbound/page`, { params: { page: 1, size: 50 } })
    const created = ((await pageResp.json()).data?.records || []).find((r: any) => !beforeIds.has(r.id))
    expect(created, '新入库单应出现在列表（差集定位）').toBeTruthy()
    expect(created.status, '新建入库单应为 DRAFT').toBe('DRAFT')
    createdInboundIds.push(created.id)

    // UI 提交：确认框提示「提交后将更新库存与合同累计入库」（inbound.vue 实证）
    const inboundRow = page.locator('.el-table__row', { hasText: String(created.inboundCode) }).first()
    await expect(inboundRow).toBeVisible({ timeout: 15_000 })
    const [submitResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes(`/v1/material/inbound/${created.id}/submit`) && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      (async () => {
        await inboundRow.locator('button:has-text("提交")').click()
        await confirmMessageBox(page)
      })(),
    ])
    expect(submitResp.status()).toBe(200)
    expect((await submitResp.json()).code, '提交入库单（直批）').toBe(200)
    // B1：提交后库存更新（save 不动库存，submit 才更新——MaterialInboundService 实证）
    const stockResp = await request.get(`${API_BASE}/api/v1/material/stock/page`, { params: { page: 1, size: 200, projectId: demo.id } })
    const stock = ((await stockResp.json()).data?.records || []).find((s: any) => s.materialName === materialName)
    expect(stock, '提交后应生成库存记录').toBeTruthy()
    expect(Number(stock.stockQuantity), '库存数量应为入库量 5').toBe(5)
    expect(Number(stock.totalInbound), '累计入库应为 5').toBe(5)
  })
})

test.describe('支出域 — 材料出库写路径（@matrix B-2-4/出库筛选）', () => {
  test('超量出库保存即被后端拦截 — 库存不足 Toast（B-2-4 前端盲点钉住为后端守卫可见）', async ({ page }) => {
    test.setTimeout(150_000)
    const request = authed!
    const demo = await resolveDemoProject(request)

    await page.goto('/material/outbound')
    await page.waitForSelector('button:has-text("新增出库单")', { timeout: 30_000 })
    await page.locator('button:has-text("新增出库单")').click()
    const dialog = page.locator('.el-dialog:has-text("新增出库单")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 项目：原生 el-select（projectOptions onMounted 预载）
    const prjSelect = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("项目")) .el-select')
    await prjSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: demo.name }).first().click()
    await page.waitForTimeout(500)
    // 出库类型默认领料 PICK
    await expect(dialog.locator('.el-form-item:has(.el-form-item__label:text-is("出库类型")) .el-select')).toContainText('领料')
    // 明细：quantity=999 必超库存（演示库存个位数实证）
    await dialog.locator('button:has-text("添加明细")').click()
    const row = dialog.locator('.el-table__row').first()
    await row.locator('td').nth(0).locator('input').fill(`${E2E_PREFIX}_超量材料`)
    await row.locator('td').nth(3).locator('input').fill('999')
    await row.locator('td').nth(4).locator('input').fill('1')
    const beforeResp = await request.get(`${API_BASE}/api/v1/material/outbound/page`, { params: { pageNum: 1, pageSize: 50 } })
    const beforeIds = new Set(((await beforeResp.json()).data?.records || []).map((r: any) => r.id))
    // 产品契约实证（MaterialOutboundService.save 2026-08 代码取证）：PICK 出库在 save 阶段
    // 即校验库存并扣减（方法注释+B3 删除对称回填注释双实证），submit 仅 DRAFT→APPROVED 无库存校验。
    // 故超量领料的拦截点是保存（非提交），本用例钉住真实守卫位置。
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/material/outbound') && !resp.url().includes('/submit') && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    const body = await createResp.json()
    expect(body.code, '超量领料保存应被后端拦截（save 阶段查库存）').not.toBe(200)
    expect(String(body.message), '拦截信息应为库存不足').toContain('库存不足')
    await expect(page.locator('.el-message--error').first()).toContainText('库存不足', { timeout: 10_000 })
    // 无落库：被拒的保存不应产生出库单（事务回滚 + 差集校验）
    const pageResp = await request.get(`${API_BASE}/api/v1/material/outbound/page`, { params: { pageNum: 1, pageSize: 50 } })
    const diff = ((await pageResp.json()).data?.records || []).filter((r: any) => !beforeIds.has(r.id))
    expect(diff, '被拦截的超量出库单不应落库').toHaveLength(0)
  })

  test('出库类型筛选抓包 — PICK/RETURN 参数下发', async ({ page }) => {
    await page.goto('/material/outbound')
    await page.waitForSelector('button:has-text("新增出库单")', { timeout: 30_000 })
    const typeSelect = page.locator('.el-form-item:has(.el-form-item__label:text-is("类型")) .el-select')
    // 领料 PICK
    await typeSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
    const [pickResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/material/outbound/page') && resp.request().method() === 'GET',
        { timeout: 20_000 }
      ),
      (async () => {
        await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("领料")').first().click()
        await page.locator('button:has-text("搜索")').first().click()
      })(),
    ])
    expect(pickResp.status()).toBe(200)
    expect(new URL(pickResp.url()).searchParams.get('outboundType'), '领料筛选应下发 PICK').toBe('PICK')
    // 退货 RETURN
    await typeSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
    const [retResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/material/outbound/page') && resp.request().method() === 'GET',
        { timeout: 20_000 }
      ),
      (async () => {
        await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("退货")').first().click()
        await page.locator('button:has-text("搜索")').first().click()
      })(),
    ])
    expect(retResp.status()).toBe(200)
    expect(new URL(retResp.url()).searchParams.get('outboundType'), '退货筛选应下发 RETURN').toBe('RETURN')
  })
})

test.describe('支出域 — 分包结算写路径（@matrix B-21）', () => {
  test('新增结算单弹窗 — 必填守卫（项目/合同/明细）', async ({ page }) => {
    await page.goto('/subcontract/settlement')
    await page.waitForSelector('button:has-text("新增结算单")', { timeout: 30_000 })
    await page.locator('button:has-text("新增结算单")').click()
    const dialog = page.locator('.el-dialog:has-text("新增结算单")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
  })

  test('完整创建 DRAFT — 明细小计联动展示', async ({ page }) => {
    test.setTimeout(180_000)
    const request = authed!
    // API 预定位有分包合同的演示项目（SubcontractSelector 按 projectId 重载候选）
    const ctResp = await request.get(`${API_BASE}/api/v1/subcontract/contract/page`, { params: { page: 1, size: 100 } })
    const contracts = ((await ctResp.json()).data?.records || []).filter((c: any) => c.projectId)
    expect(contracts.length, '演示数据前提：应存在分包合同').toBeGreaterThan(0)
    // 项目表翻页全量拉取（2026-08-23 硬化，run 32644242233 实证：E2E 残留项目把种子项目挤出首页）
    const pmap = new Map((await fetchAllProjects(request)).map((p: any) => [String(p.id), p.projectName]))
    const resolved = contracts
      .map((c: any) => ({ name: pmap.get(String(c.projectId)) }))
      .filter((x: any) => x.name && !String(x.name).includes('E2E'))
    expect(resolved.length, '分包合同应可解析到非 E2E 项目').toBeGreaterThan(0)
    const targetProject = resolved[0].name

    const beforeResp = await request.get(`${API_BASE}/api/v1/subcontract/settlement`, { params: { page: 1, size: 50 } })
    const beforeIds = new Set(((await beforeResp.json()).data?.records || []).map((r: any) => r.id))

    await page.goto('/subcontract/settlement')
    await page.waitForSelector('button:has-text("新增结算单")', { timeout: 30_000 })
    await page.locator('button:has-text("新增结算单")').click()
    const dialog = page.locator('.el-dialog:has-text("新增结算单")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await selectProjectByName(page, dialog, targetProject)
    // 分包合同：watch projectId 重载候选后选第一项
    await pickFirstOption(page, dialog.locator('.el-form-item:has(.el-form-item__label:text-is("分包合同")) .el-select'), 1000)
    // 明细行（itemName 带 E2E_TEST_ 前缀 → W1 补丁②清理守卫 marker）
    await dialog.locator('button:has-text("添加明细行")').click()
    const row = dialog.locator('.el-table__row').first()
    await row.locator('td').nth(0).locator('input').fill(`${E2E_PREFIX}_混凝土工程`)
    await row.locator('td').nth(2).locator('input').fill('2')
    await row.locator('td').nth(3).locator('input').fill('5')
    // 小计/合计联动渲染（quantity×unitPrice）
    await expect(row.locator('td').nth(4)).toContainText('10.00')
    await expect(dialog.locator('text=合计')).toContainText('10.00')
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/subcontract/settlement') && !resp.url().includes('/submit') && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    expect((await createResp.json()).code, '创建分包结算单').toBe(200)
    const pageResp = await request.get(`${API_BASE}/api/v1/subcontract/settlement`, { params: { page: 1, size: 50 } })
    const created = ((await pageResp.json()).data?.records || []).find((r: any) => !beforeIds.has(r.id))
    expect(created, '新结算单应出现在列表（差集定位）').toBeTruthy()
    expect(created.status, '新建结算单应为 DRAFT').toBe('DRAFT')
    expect(Number(created.settlementAmount), '结算金额应为明细合计 10').toBe(10)
    createdSettlementIds.push(created.id)
    settlementIdForSubmit = created.id
  })

  test('提交状态流转 APPROVED + 明细级联回读', async ({ page }) => {
    test.setTimeout(120_000)
    const request = authed!
    test.skip(!settlementIdForSubmit, '前置创建用例未产出结算单，无可提交对象')
    const id = settlementIdForSubmit!
    await page.goto('/subcontract/settlement')
    await page.waitForSelector('.el-table__row', { timeout: 30_000 })
    const row = page.locator('.el-table__row', { hasText: String(id) }).first()
    await expect(row).toBeVisible({ timeout: 15_000 })
    const [submitResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes(`/v1/subcontract/settlement/${id}/submit`) && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      (async () => {
        await row.locator('button:has-text("提交")').click()
        await confirmMessageBox(page)
      })(),
    ])
    expect(submitResp.status()).toBe(200)
    expect((await submitResp.json()).code, '提交结算单（直批）').toBe(200)
    const detail = await request.get(`${API_BASE}/api/v1/subcontract/settlement/${id}`)
    const data = (await detail.json()).data
    // 详情 VO 为嵌套结构（SubcontractSettlementDetailVO 实证）：主表字段在 data.settlement，明细在 data.details
    expect(data?.settlement?.status, '结算单提交后应为 APPROVED').toBe('APPROVED')
    // 明细级联：后端回读含明细行（itemName marker）
    expect((data?.details || []).length, '结算明细应级联回读').toBeGreaterThan(0)
    expect(String(data.details[0].itemName)).toContain('E2E_TEST_')
  })
})
