/**
 * 真实模式 E2E 测试：财务域写路径 UI（前端补测四步收口 W2，2026-08）
 *
 * @matrix C-5 付款申请★ / C-1 开票申请 / C-2 收票登记 / C-4 回款登记 /
 *   C-6 其他费用付款 / C-7 项目报销 / C-8 备用金 / C-9 个人报销 /
 *   C-14 税率管理 / C-10~12 质保金·发票汇总·结算只读断言
 *   （frontend-test-case-matrix.md L756-996 财务域缺口）
 *
 * 范式与 expense-write.spec.ts 一致：storageState=admin（租户 1），UI 操作经
 * waitForResponse 锚定真实接口硬断言（禁止容忍断言），创建的测试单据
 * afterAll 经 API 逆序清理（E2E_TEST_ 前缀可识别；W1 守卫放行后全状态可删）。
 *
 * 受阻登记（tasks.md API-GAP-fin）：项目报销 / 个人报销 / 备用金申请后端无
 * DELETE 通道（Controller 实证），测试数据无法清理 → 此三页仅做必填守卫 +
 * 入口/状态渲染断言，不做真实建单；C-9-1「请求无 projectId 抓包」依赖真实
 * 建单一并受阻。报销/备用金写链路本身由 L5-API 21-finance-chain 覆盖。
 *
 * 实测修正（与计划差异，源码实证）：
 *   - 项目报销/个人报销/收票登记/其他付款 UI 均无编辑/删除入口（非"CRUD 闭环"）
 *   - 发票汇总为分组表 + 合计行（非三卡片），按实际结构断言
 *   - payment-apply 前端分页传 pageNum/pageSize 但后端 Controller 仅收 page/size
 *     → API 层差集定位直接用 page/size；UI 层列表恒为 size=10 首页（现状钉住）
 */
import { test, expect, request as pwRequest } from '@playwright/test'

// 写路径用例文件内串行（多弹窗/共享数据态用例并行互扰，expense-write 实证）
test.describe.configure({ mode: 'serial' })

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'

// 后端仅认 Authorization: Bearer 头（AuthInterceptor 实证）；不可重新 API 登录取
// token——admin max-devices=5，新会话会踢出 UI storageState 会话（P2 实跑事故）
let authed: Awaited<ReturnType<typeof pwRequest.newContext>> | null = null

// 税率字典前提：种子 SQL 无 biz_tax_rate 数据（grep deploy/db-init 实证），
// 环境启用税率列表恒为空 → C1 TaxRateSelector 下拉无候选。beforeAll 经 API
// 自置一条 E2E_TEST_ 预设税率，不依赖外部可变状态；afterAll 停用清理
let seedTaxRateId: number | null = null

// C6 预算管控前置：other-payment save 挂 @BudgetCheck，演示项目 BLOCK 且无 OTHER
// 科目额度 → 创建被拒「该科目未设置预算额度」（run10 实证）。用例开头将目标项目
// 切 EXEMPT，afterAll 恢复原模式（记录基线）
let budgetCfgRestore: { configId: string; controlMode: string; warningThreshold: number; projectId: string } | null = null

test.beforeAll(async () => {
  const fs = await import('node:fs')
  const st = JSON.parse(fs.readFileSync('./e2e/.auth/storage-state.json', 'utf-8'))
  const token = (st.origins || []).flatMap((o: any) => o.localStorage || [])
    .find((kv: any) => kv.name === 'token')?.value
  expect(token, 'storageState 应含登录 token').toBeTruthy()
  authed = await pwRequest.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  })
  const listResp = await authed.get(`${API_BASE}/api/v1/finance/tax-rate/list`)
  const rates: any[] = (await listResp.json()).data || []
  if (rates.length === 0) {
    const create = await authed.post(`${API_BASE}/api/v1/finance/tax-rate`, {
      data: { name: `E2E_TEST_${Date.now()}_预设税率`, rateValue: 13 },
    })
    expect((await create.json()).code, '预置启用税率（C1 TaxRateSelector 前提）').toBe(200)
    const allResp = await authed.get(`${API_BASE}/api/v1/finance/tax-rate/all`)
    const all: any[] = (await allResp.json()).data || []
    seedTaxRateId = all.find((r: any) => r.rateValue === 13 && String(r.name).startsWith('E2E_TEST_'))?.id ?? null
    expect(seedTaxRateId, '预置税率应可定位（/all 差集）').toBeTruthy()
  }
})

// 本 spec 创建的单据 ID（afterAll 逆序清理）
const createdPaymentApplyIds: number[] = []
const createdInvoiceApplyIds: number[] = []
const createdInvoiceReceivedIds: number[] = []
const createdPaymentReceivedIds: number[] = []
const createdOtherPaymentIds: number[] = []

test.afterAll(async () => {
  if (!authed) return
  // 逆序清理：付款申请（先撤流程再删，W1 守卫放行后 APPROVED 亦可删）
  for (const id of createdPaymentApplyIds.reverse()) {
    await authed.post(`${API_BASE}/api/v1/workflow/approval/withdraw-by-business?businessType=PAYMENT_APPLY&businessId=${id}`).catch(() => {})
    await authed.delete(`${API_BASE}/api/v1/finance/payment-apply/${id}`).catch(() => {})
  }
  for (const id of createdInvoiceApplyIds.reverse()) {
    await authed.post(`${API_BASE}/api/v1/workflow/approval/withdraw-by-business?businessType=INVOICE_APPLY&businessId=${id}`).catch(() => {})
    await authed.delete(`${API_BASE}/api/v1/finance/invoice-apply/${id}`).catch(() => {})
  }
  for (const id of createdInvoiceReceivedIds.reverse()) {
    await authed.delete(`${API_BASE}/api/v1/finance/invoice-received/${id}`).catch(() => {})
  }
  for (const id of createdPaymentReceivedIds.reverse()) {
    await authed.delete(`${API_BASE}/api/v1/finance/payment-received/${id}`).catch(() => {})
  }
  for (const id of createdOtherPaymentIds.reverse()) {
    await authed.delete(`${API_BASE}/api/v1/finance/other-payment/${id}`).catch(() => {})
  }
  if (seedTaxRateId) {
    await authed.delete(`${API_BASE}/api/v1/finance/tax-rate/${seedTaxRateId}`).catch(() => {})
  }
  // 恢复 C6 临时切换的预算管控模式（PUT 复用同一配置，唯一键 uk_tenant_project 不可重建）
  if (budgetCfgRestore) {
    await authed.put(`${API_BASE}/api/v1/budget-control-configs/${budgetCfgRestore.configId}`, {
      data: {
        projectId: budgetCfgRestore.projectId,
        controlMode: budgetCfgRestore.controlMode,
        warningThreshold: budgetCfgRestore.warningThreshold,
      },
    }).catch(() => {})
  }
  await authed.dispose()
})

const TS = Date.now()
const E2E_PREFIX = `E2E_TEST_${TS}`
const pad = (n: number) => String(n).padStart(2, '0')
const _d = new Date()
const TODAY = `${_d.getFullYear()}-${pad(_d.getMonth() + 1)}-${pad(_d.getDate())}`

/** 打开 el-select 并选第一个候选项（下拉选中后自动收起，:visible 唯一定位） */
async function pickFirstOption(page: any, selectLocator: any, waitMs = 800) {
  await selectLocator.click()
  await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
  await page.waitForTimeout(waitMs)
}

/** 打开 el-select 并选指定文本的候选项 */
async function pickOptionByText(page: any, selectLocator: any, text: string, waitMs = 800) {
  await selectLocator.click()
  await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
  await page.locator(`.el-select-dropdown:visible .el-select-dropdown__item:has-text("${text}")`).first().click()
  await page.waitForTimeout(waitMs)
}

/** filterable remote 项目下拉：输入名称触发 remote-method 后选中匹配项
 *  （首项项目可能无目标类别合同致关联合同候选为空，三遍实跑实证 → 按名称定向） */
async function selectProjectByName(page: any, container: any, name: string) {
  const select = container.locator('.el-form-item:has(.el-form-item__label:text-is("项目")) .el-select')
  await select.click()
  await select.locator('input').first().fill(name)
  await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
  await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: name }).first().click()
  await page.waitForTimeout(800)
}

/** 审批闭环：循环 complete 目标业务待办直至清空（SUPER_ADMIN 可完成任意任务） */
async function completeAllTodos(request: any, businessId: number, maxRounds = 6): Promise<boolean> {
  for (let i = 0; i < maxRounds; i++) {
    const resp = await request.get(`${API_BASE}/api/v1/workflow/approval/todo`, { params: { page: 1, size: 50 } })
    const todos = ((await resp.json()).data?.records || []).filter((t: any) => String(t.businessId) === String(businessId))
    if (todos.length === 0) return true
    const c = await request.post(`${API_BASE}/api/v1/workflow/approval/complete`, {
      data: { taskId: todos[0].taskId, comment: 'E2E finance-write 审批推进' },
    })
    expect(c.ok(), `完成审批任务 ${todos[0].taskId}`).toBeTruthy()
    await new Promise((r) => setTimeout(r, 500))
  }
  return false
}

test.describe('财务域 — C5 付款申请★ 完整写流程（@matrix C-5-1/2/3）', () => {
  test('新增付款申请弹窗 — 必填守卫（空态确定不发 POST）', async ({ page }) => {
    let postCount = 0
    page.on('request', (r) => {
      if (r.url().includes('/v1/finance/payment-apply') && r.method() === 'POST') postCount++
    })
    await page.goto('/finance/payment-apply')
    await page.waitForSelector('button:has-text("新增付款申请")', { timeout: 30_000 })
    await page.locator('button:has-text("新增付款申请")').click()
    const dialog = page.locator('.el-dialog:has-text("新增付款申请")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 不填任何字段直接确定 → 必填校验拦截
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
    await page.waitForTimeout(600)
    expect(postCount, '必填拦截下不应发出创建请求').toBe(0)
  })

  test('合同类型路由抓包 — PURCHASE 走 getPurchaseContractPage（C-5-2）', async ({ page }) => {
    test.setTimeout(90_000)
    await page.goto('/finance/payment-apply')
    await page.waitForSelector('button:has-text("新增付款申请")', { timeout: 30_000 })
    await page.locator('button:has-text("新增付款申请")').click()
    const dialog = page.locator('.el-dialog:has-text("新增付款申请")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // loadContracts 无项目时 early return → 必须先选项目
    await pickFirstOption(page, dialog.locator('.el-form-item:has(.el-form-item__label:text-is("项目")) .el-select'))
    // 选「采购合同」→ 路由 getPurchaseContractPage（/v1/purchase/contract/page）
    const [contractResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/purchase/contract/page') && resp.request().method() === 'GET',
        { timeout: 20_000 }
      ),
      pickOptionByText(page, dialog.locator('.el-form-item:has(.el-form-item__label:text-is("合同类型")) .el-select'), '采购合同', 0),
    ])
    expect(contractResp.status()).toBe(200)
    const body = await contractResp.json()
    expect(body.code, '采购合同分页查询应成功').toBe(200)
  })

  test('完整创建 → DRAFT → UI 提交 → 审批闭环 APPROVED → 状态条件渲染', async ({ page }) => {
    // networkidle 在 dev/CI 可能挂死（expense-write 实证）→ 全部具体元素等待
    test.setTimeout(180_000)
    const request = authed!
    // 创建前 ID 快照（差集定位，杜绝误命中租户 1 真实单据）
    const beforeResp = await request.get(`${API_BASE}/api/v1/finance/payment-apply/page`, { params: { page: 1, size: 200 } })
    const beforeIds = new Set(((await beforeResp.json()).data?.records || []).map((r: any) => r.id))
    // API 预查有可付余额的合同（可付 = 累计结算 - 累计已付，奖惩净额仅劳务/分包适用，
    // PaymentApplyService.validatePaymentLimit 实证）。历轮实跑审批回写会消耗采购合同余额
    // （run6 撞「最大可付金额：0.00」），按 PURCHASE→LABOR→MACHINE 顺序找余额 >= 1 元者。
    // 前提守卫（2026-08-20 修复）：候选合同必须能解析到存在的项目——历史残留的
    // E2E_TEST_ 孤儿合同（挂项目已被删除）会带审批回写余额，命中即致定位项目失败
    const prAll = await request.get(`${API_BASE}/api/v1/project/page`, { params: { page: 1, size: 200 } })
    const projectMap = new Map(((await prAll.json()).data?.records || []).map((p: any) => [String(p.id), p.projectName]))
    const CATS = [
      { cat: 'PURCHASE', label: '采购合同', url: '/api/v1/purchase/contract/page' },
      { cat: 'LABOR', label: '劳务合同', url: '/api/v1/labor/contract/page' },
      { cat: 'MACHINE', label: '机械合同', url: '/api/v1/machine/contract/page' },
    ]
    let targetPc: any = null
    let targetCat: any = null
    for (const c of CATS) {
      const resp = await request.get(`${API_BASE}${c.url}`, { params: { page: 1, size: 200 } })
      const recs = (await resp.json()).data?.records || []
      targetPc = recs.find((r: any) => Number(r.cumulativeSettlement ?? 0) - Number(r.cumulativePaid ?? 0) >= 1
        && (r.projectName || projectMap.has(String(r.projectId))))
      if (targetPc) { targetCat = c; break }
    }
    expect(targetPc, '演示数据前提：应存在可付余额 >= 1 元且项目可解析的采购/劳务/机械合同').toBeTruthy()
    let targetProjectName = targetPc.projectName
    if (!targetProjectName) {
      targetProjectName = projectMap.get(String(targetPc.projectId))
    }
    expect(targetProjectName, '应定位到合同所属项目').toBeTruthy()
    expect(targetPc.contractName, '合同应有名称（UI 按名定向选择）').toBeTruthy()

    await page.goto('/finance/payment-apply')
    await page.waitForSelector('button:has-text("新增付款申请")', { timeout: 30_000 })
    await page.locator('button:has-text("新增付款申请")').click()
    const dialog = page.locator('.el-dialog:has-text("新增付款申请")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 项目（定向有余额合同的项目）→ 合同类型（预查命中类别）→ 关联合同 → 收款单位 → 金额 1 元 → 日期
    await selectProjectByName(page, dialog, targetProjectName)
    await pickOptionByText(page, dialog.locator('.el-form-item:has(.el-form-item__label:text-is("合同类型")) .el-select'), targetCat.label, 1200)
    await pickOptionByText(page, dialog.locator('.el-form-item:has(.el-form-item__label:text-is("关联合同")) .el-select'), targetPc.contractName, 1200)
    await pickFirstOption(page, dialog.locator('.el-form-item:has(.el-form-item__label:text-is("收款单位")) .el-select'))
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("付款金额")) input').first().fill('1')
    const dateInput = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("付款日期")) input').first()
    await dateInput.fill(TODAY)
    await dateInput.press('Enter')
    // 创建
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/finance/payment-apply') && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    const createBody = await createResp.json()
    expect(createBody.code, `创建付款申请：${createBody.message}`).toBe(200)
    // 差集定位新单据（后端 orderByDesc createdAt，必在首页）
    const listResp = await request.get(`${API_BASE}/api/v1/finance/payment-apply/page`, { params: { page: 1, size: 200 } })
    const created = ((await listResp.json()).data?.records || []).find((r: any) => !beforeIds.has(r.id) && r.status === 'DRAFT')
    expect(created, '新付款申请应出现在列表（DRAFT，差集定位）').toBeTruthy()
    createdPaymentApplyIds.push(created.id)

    // UI 提交：reload 后首行即新单据（createdAt desc 实证）
    await page.goto('/finance/payment-apply')
    await page.waitForSelector('.el-table__row', { timeout: 30_000 })
    const firstRow = page.locator('.el-table__row').first()
    await expect(firstRow.locator('.el-tag')).toContainText('草稿', { timeout: 10_000 })
    await firstRow.locator('button:has-text("提交")').click()
    const msgbox = page.locator('.el-message-box')
    await expect(msgbox).toBeVisible({ timeout: 10_000 })
    await expect(msgbox).toContainText('确定要提交该付款申请吗')
    const [submitResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes(`/v1/finance/payment-apply/${created.id}/submit`) && resp.request().method() === 'PUT',
        { timeout: 20_000 }
      ),
      msgbox.locator('button:has-text("确定")').click(),
    ])
    expect(submitResp.status()).toBe(200)
    expect((await submitResp.json()).code, '提交付款申请').toBe(200)
    // 状态回写：提交后离开 DRAFT（startProcess 置 SUBMITTED）
    const afterSubmit = await request.get(`${API_BASE}/api/v1/finance/payment-apply/${created.id}`)
    expect((await afterSubmit.json()).data?.status, '提交后不应仍是 DRAFT').not.toBe('DRAFT')

    // 审批闭环：admin 超管循环完成任务推进至 APPROVED（payment_apply_approval）
    const allDone = await completeAllTodos(request, created.id)
    expect(allDone, '审批待办应全部完成').toBe(true)
    const afterApproval = await request.get(`${API_BASE}/api/v1/finance/payment-apply/${created.id}`)
    expect((await afterApproval.json()).data?.status, '审批完成后应为 APPROVED').toBe('APPROVED')

    // 状态条件渲染：APPROVED 行无提交/删除按钮（仅查看）
    await page.goto('/finance/payment-apply')
    await page.waitForSelector('.el-table__row', { timeout: 30_000 })
    const approvedRow = page.locator('.el-table__row').first()
    await expect(approvedRow.locator('.el-tag')).toContainText('已通过', { timeout: 10_000 })
    await expect(approvedRow.locator('button:has-text("提交")')).toHaveCount(0)
    await expect(approvedRow.locator('button:has-text("删除")')).toHaveCount(0)
  })
})

test.describe('财务域 — C1 开票申请（@matrix C-1-1/2）', () => {
  test('新增开票申请弹窗 — 必填守卫', async ({ page }) => {
    await page.goto('/finance/invoice-apply')
    await page.waitForSelector('button:has-text("新增开票申请")', { timeout: 30_000 })
    await page.locator('button:has-text("新增开票申请")').click()
    const dialog = page.locator('.el-dialog:has-text("新增开票申请")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
  })

  test('完整创建 DRAFT — TaxRateSelector 走 UI 选择', async ({ page }) => {
    test.setTimeout(120_000)
    const request = authed!
    const beforeResp = await request.get(`${API_BASE}/api/v1/finance/invoice-apply/page`, { params: { page: 1, size: 200 } })
    const beforeIds = new Set(((await beforeResp.json()).data?.records || []).map((r: any) => r.id))
    // API 预查施工合同定位项目（首项项目可能无施工合同 → 关联合同候选为空致下拉超时，run6 实跑实证；
    // 合同记录 projectName 不下发且可能指向已删项目 → 逐条经项目表解析，优先非 E2E 演示项目）
    const ctResp = await request.get(`${API_BASE}/api/v1/contract/page`, { params: { page: 1, size: 100 } })
    const ctRecords = (await ctResp.json()).data?.records || []
    expect(ctRecords.length, '演示数据前提：应存在施工合同').toBeGreaterThan(0)
    const pr = await request.get(`${API_BASE}/api/v1/project/page`, { params: { page: 1, size: 200 } })
    const pmap = new Map(((await pr.json()).data?.records || []).map((p: any) => [String(p.id), p.projectName]))
    const resolved = ctRecords
      .map((r: any) => ({ name: pmap.get(String(r.projectId)) }))
      .filter((x: any) => x.name)
    const c1Project = resolved.find((x: any) => !String(x.name).includes('E2E')) || resolved[0]
    expect(c1Project, '应定位到施工合同所属项目').toBeTruthy()
    const c1ProjectName = c1Project.name

    await page.goto('/finance/invoice-apply')
    await page.waitForSelector('button:has-text("新增开票申请")', { timeout: 30_000 })
    await page.locator('button:has-text("新增开票申请")').click()
    const dialog = page.locator('.el-dialog:has-text("新增开票申请")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 项目（定向有施工合同的项目）
    await selectProjectByName(page, dialog, c1ProjectName)
    // 关联合同：ContractSelector（watch projectId 重载，getContractPage）
    await pickFirstOption(page, dialog.locator('.el-form-item:has(.el-form-item__label:text-is("关联合同")) .el-select'), 1000)
    // 开票金额 1 元
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("开票金额")) input').first().fill('1')
    // 税率：TaxRateSelector 下拉选预设税率（onMounted 加载 /v1/finance/tax-rate/list）
    const taxSelect = dialog.locator('.tax-rate-selector__select')
    await pickFirstOption(page, taxSelect, 400)
    const rateInputVal = await dialog.locator('.tax-rate-selector__input input').first().inputValue()
    expect(rateInputVal, '选择预设税率后数值应自动填入').not.toBe('')
    // 申请日期
    const dateInput = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("申请日期")) input').first()
    await dateInput.fill(TODAY)
    await dateInput.press('Enter')
    // 创建
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/finance/invoice-apply') && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    expect((await createResp.json()).code, '创建开票申请').toBe(200)
    const listResp = await request.get(`${API_BASE}/api/v1/finance/invoice-apply/page`, { params: { page: 1, size: 200 } })
    const created = ((await listResp.json()).data?.records || []).find((r: any) => !beforeIds.has(r.id) && r.status === 'DRAFT')
    expect(created, '新开票申请应出现在列表（DRAFT，差集定位）').toBeTruthy()
    createdInvoiceApplyIds.push(created.id)
  })
})

test.describe('财务域 — C2 收票登记（@matrix C-2-1/2/3）', () => {
  test('新增收票登记弹窗 — 必填守卫', async ({ page }) => {
    await page.goto('/finance/invoice-received')
    await page.waitForSelector('button:has-text("新增收票登记")', { timeout: 30_000 })
    await page.locator('button:has-text("新增收票登记")').click()
    const dialog = page.locator('.el-dialog:has-text("新增收票登记")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
  })

  test('新增成功 + 无编辑/删除入口 + 税率列百分比展示', async ({ page }) => {
    test.setTimeout(120_000)
    const request = authed!
    const beforeResp = await request.get(`${API_BASE}/api/v1/finance/invoice-received`, { params: { page: 1, size: 200 } })
    const beforeIds = new Set(((await beforeResp.json()).data?.records || []).map((r: any) => r.id))

    await page.goto('/finance/invoice-received')
    await page.waitForSelector('button:has-text("新增收票登记")', { timeout: 30_000 })
    await page.locator('button:has-text("新增收票登记")').click()
    const dialog = page.locator('.el-dialog:has-text("新增收票登记")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await pickFirstOption(page, dialog.locator('.el-form-item:has(.el-form-item__label:text-is("项目")) .el-select'))
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("发票金额")) input').first().fill('1')
    const dateInput = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("收票日期")) input').first()
    await dateInput.fill(TODAY)
    await dateInput.press('Enter')
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/finance/invoice-received') && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    expect((await createResp.json()).code, '创建收票登记').toBe(200)
    const listResp = await request.get(`${API_BASE}/api/v1/finance/invoice-received`, { params: { page: 1, size: 200 } })
    const created = ((await listResp.json()).data?.records || []).find((r: any) => !beforeIds.has(r.id))
    expect(created, '新收票登记应出现在列表（差集定位）').toBeTruthy()
    createdInvoiceReceivedIds.push(created.id)

    // C-2-3：无编辑/删除入口（源码实证 invoice-received.vue 无操作列）
    await expect(page.locator('.el-table th:has-text("操作")')).toHaveCount(0)
    await expect(page.locator('.el-table button:has-text("编辑")')).toHaveCount(0)
    await expect(page.locator('.el-table button:has-text("删除")')).toHaveCount(0)
    // 税率列百分比展示一致性（`${val}%` 格式钉住）
    const taxRateCell = page.locator('.el-table__row').first().locator('td').filter({ hasText: '%' }).first()
    if (await taxRateCell.count()) {
      expect(await taxRateCell.innerText()).toMatch(/\d+(\.\d+)?%/)
    }
  })
})

test.describe('财务域 — C4 回款登记（@matrix C-4-1/2/3）', () => {
  test('新增回款登记弹窗 — 必填守卫', async ({ page }) => {
    await page.goto('/finance/payment-received')
    await page.waitForSelector('button:has-text("新增回款登记")', { timeout: 30_000 })
    await page.locator('button:has-text("新增回款登记")').click()
    const dialog = page.locator('.el-dialog:has-text("新增回款登记")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
  })

  test('新增（收款方式默认银行转账）→ 编辑回填改金额保存', async ({ page }) => {
    test.setTimeout(150_000)
    const request = authed!
    const beforeResp = await request.get(`${API_BASE}/api/v1/finance/payment-received/page`, { params: { page: 1, size: 200 } })
    const beforeIds = new Set(((await beforeResp.json()).data?.records || []).map((r: any) => r.id))

    await page.goto('/finance/payment-received')
    await page.waitForSelector('button:has-text("新增回款登记")', { timeout: 30_000 })
    await page.locator('button:has-text("新增回款登记")').click()
    const dialog = page.locator('.el-dialog:has-text("新增回款登记")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await pickFirstOption(page, dialog.locator('.el-form-item:has(.el-form-item__label:text-is("项目")) .el-select'))
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("回款金额")) input').first().fill('1')
    // 收款方式默认值断言（formData.receiveType 默认「银行转账」）
    await expect(dialog.locator('.el-form-item:has(.el-form-item__label:text-is("收款方式")) .el-select')).toContainText('银行转账')
    const dateInput = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("回款日期")) input').first()
    await dateInput.fill(TODAY)
    await dateInput.press('Enter')
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/finance/payment-received') && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    expect((await createResp.json()).code, '创建回款登记').toBe(200)
    const listResp = await request.get(`${API_BASE}/api/v1/finance/payment-received/page`, { params: { page: 1, size: 200 } })
    const created = ((await listResp.json()).data?.records || []).find((r: any) => !beforeIds.has(r.id))
    expect(created, '新回款登记应出现在列表（差集定位）').toBeTruthy()
    createdPaymentReceivedIds.push(created.id)

    // 编辑：reload 后首行为新单据（createdAt desc）→ 编辑回填 → 改金额 2 元 → PUT
    await page.goto('/finance/payment-received')
    await page.waitForSelector('.el-table__row', { timeout: 30_000 })
    await page.locator('.el-table__row').first().locator('button:has-text("编辑")').click()
    const editDialog = page.locator('.el-dialog:has-text("编辑回款登记")')
    await expect(editDialog).toBeVisible({ timeout: 10_000 })
    // 回填断言：金额应为创建值 1（el-input-number precision=2 展示 `1.00`，数值比对）
    const amountInput = editDialog.locator('.el-form-item:has(.el-form-item__label:text-is("回款金额")) input').first()
    await expect(amountInput).not.toHaveValue('', { timeout: 10_000 })
    expect(Number(await amountInput.inputValue()), '编辑回填金额应为创建值 1').toBe(1)
    await amountInput.fill('2')
    const [updateResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/finance/payment-received') && resp.request().method() === 'PUT',
        { timeout: 20_000 }
      ),
      editDialog.locator('button:has-text("确定")').click(),
    ])
    expect(updateResp.status()).toBe(200)
    // 回写断言：API 读回金额 = 2
    await page.waitForTimeout(800)
    const afterResp = await request.get(`${API_BASE}/api/v1/finance/payment-received/page`, { params: { page: 1, size: 200 } })
    const after = ((await afterResp.json()).data?.records || []).find((r: any) => String(r.id) === String(created.id))
    expect(Number(after?.receiveAmount), '编辑后金额应回写为 2').toBe(2)
  })
})

test.describe('财务域 — C6 其他费用付款（@matrix C-6-1/2/3）', () => {
  test('列表分页参数抓包 — page/size（与 payment-apply 的 pageNum 差异钉住）', async ({ page }) => {
    const [listResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/finance/other-payment') && resp.request().method() === 'GET',
        { timeout: 30_000 }
      ),
      page.goto('/finance/other-payment'),
    ])
    expect(listResp.status()).toBe(200)
    const url = new URL(listResp.url())
    expect(url.searchParams.get('page'), 'other-payment 分页参数应为 page').toBeTruthy()
    expect(url.searchParams.get('size'), 'other-payment 分页参数应为 size').toBeTruthy()
  })

  test('必填守卫 + 新增成功 + 无编辑/删除入口', async ({ page }) => {
    test.setTimeout(120_000)
    const request = authed!
    await page.goto('/finance/other-payment')
    await page.waitForSelector('button:has-text("新增其他费用付款")', { timeout: 30_000 })
    await page.locator('button:has-text("新增其他费用付款")').click()
    const dialog = page.locator('.el-dialog:has-text("新增其他费用付款")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 必填守卫
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })

    // 完整创建（备注带 E2E_TEST_ 标记）
    // 预算管控前置（run10 实证）：other-payment save 挂 @BudgetCheck，演示项目 BLOCK
    // 且无 OTHER 科目额度 → code=500「该科目未设置预算额度」。定位一个有项目级配置
    // （生效配置 isDefault=0）的非 E2E 项目，临时切 EXEMPT，afterAll PUT 恢复基线
    const prjResp = await request.get(`${API_BASE}/api/v1/project/page`, { params: { page: 1, size: 200 } })
    const projects = ((await prjResp.json()).data?.records || [])
      .filter((p: any) => !String(p.projectName).includes('E2E'))
    let exemptProjectName: string | null = null
    for (const p of projects) {
      const effResp = await request.get(`${API_BASE}/api/v1/budget-control-configs/project/${p.id}`)
      const eff = (await effResp.json()).data
      if (eff && Number(eff.isDefault) === 0) {
        exemptProjectName = p.projectName
        budgetCfgRestore = {
          configId: String(eff.id),
          controlMode: eff.controlMode,
          warningThreshold: eff.warningThreshold ?? 80,
          projectId: String(p.id),
        }
        const switchResp = await request.put(`${API_BASE}/api/v1/budget-control-configs/${eff.id}`, {
          data: { projectId: String(p.id), controlMode: 'EXEMPT', warningThreshold: eff.warningThreshold ?? 80 },
        })
        expect((await switchResp.json()).code, '临时切 EXEMPT（C6 创建前提）').toBe(200)
        break
      }
    }
    expect(exemptProjectName, '应定位到有项目级预算管控配置的非 E2E 项目').toBeTruthy()

    const beforeResp = await request.get(`${API_BASE}/api/v1/finance/other-payment`, { params: { page: 1, size: 200 } })
    const beforeIds = new Set(((await beforeResp.json()).data?.records || []).map((r: any) => r.id))
    await selectProjectByName(page, dialog, exemptProjectName!)
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("付款人")) input').first().fill(`${E2E_PREFIX}_付款人`)
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("付款金额")) input').first().fill('1')
    const dateInput = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("付款日期")) input').first()
    await dateInput.fill(TODAY)
    await dateInput.press('Enter')
    const remarkInput = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("备注")) textarea, .el-form-item:has(.el-form-item__label:text-is("备注")) input').first()
    if (await remarkInput.count()) await remarkInput.fill(`${E2E_PREFIX}_其他付款`)
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/finance/other-payment') && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    expect((await createResp.json()).code, '创建其他费用付款').toBe(200)
    const listResp = await request.get(`${API_BASE}/api/v1/finance/other-payment`, { params: { page: 1, size: 200 } })
    const created = ((await listResp.json()).data?.records || []).find((r: any) => !beforeIds.has(r.id))
    expect(created, '新其他付款应出现在列表（差集定位）').toBeTruthy()
    createdOtherPaymentIds.push(created.id)
    // C-6-3：无编辑/删除入口（源码实证 other-payment.vue 无操作列）
    await expect(page.locator('.el-table button:has-text("编辑")')).toHaveCount(0)
    await expect(page.locator('.el-table button:has-text("删除")')).toHaveCount(0)
  })
})

test.describe('财务域 — C7 项目报销（@matrix C-7-1/3；建单受阻 API-GAP-fin）', () => {
  // 项目报销后端无 DELETE 通道（ProjectReimbursementController 实证仅 page/save/submit），
  // 测试数据无法清理 → 不做真实建单；必填守卫 + 入口/状态渲染断言不受影响
  test('新增项目报销弹窗 — 必填守卫（空态确定不发 POST）', async ({ page }) => {
    let postCount = 0
    page.on('request', (r) => {
      if (r.url().includes('/v1/finance/project-reimbursement') && r.method() === 'POST') postCount++
    })
    await page.goto('/finance/project-reimbursement')
    await page.waitForSelector('button:has-text("新增项目报销")', { timeout: 30_000 })
    await page.locator('button:has-text("新增项目报销")').click()
    const dialog = page.locator('.el-dialog:has-text("新增项目报销")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
    await page.waitForTimeout(600)
    expect(postCount, '必填拦截下不应发出创建请求').toBe(0)
  })

  test('无编辑/删除入口 + 提交按钮仅 DRAFT 行（状态条件渲染）', async ({ page }) => {
    await page.goto('/finance/project-reimbursement')
    await page.waitForSelector('.el-table__row, .el-table__empty-block', { timeout: 30_000 })
    // C-7-3 实测修正：UI 无编辑/删除入口（源码实证仅提交按钮）
    await expect(page.locator('.el-table button:has-text("编辑")')).toHaveCount(0)
    await expect(page.locator('.el-table button:has-text("删除")')).toHaveCount(0)
    // 状态条件渲染：逐行核对「提交」按钮仅出现在草稿行
    const rows = page.locator('.el-table__row')
    const rowCount = await rows.count()
    if (rowCount === 0) {
      test.skip(true, '无演示数据行，状态渲染无可断言对象（tasks.md DATA 受阻登记）')
    }
    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i)
      const statusText = (await row.locator('.el-tag').first().innerText()).trim()
      const submitCount = await row.locator('button:has-text("提交")').count()
      if (statusText === '草稿') {
        expect(submitCount, `第 ${i + 1} 行草稿应有提交按钮`).toBe(1)
      } else {
        expect(submitCount, `第 ${i + 1} 行「${statusText}」不应有提交按钮`).toBe(0)
      }
    }
  })
})

test.describe('财务域 — C8 备用金（@matrix C-8-1/3；建单受阻 API-GAP-fin）', () => {
  // 备用金申请后端无 DELETE 通道（ReserveFundController 实证仅 /return/{id} 可删）→ 不真实建单
  test('借支申请弹窗 — 必填守卫', async ({ page }) => {
    await page.goto('/finance/reserve-fund')
    await page.waitForSelector('button:has-text("新增备用金申请")', { timeout: 30_000 })
    await page.locator('button:has-text("新增备用金申请")').click()
    const dialog = page.locator('.el-dialog:has-text("新增备用金申请")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
  })

  test('归还按钮仅 APPROVED 行可见（C-8-3 状态条件渲染）', async ({ page }) => {
    await page.goto('/finance/reserve-fund')
    await page.waitForSelector('.el-table__row, .el-table__empty-block', { timeout: 30_000 })
    const rows = page.locator('.el-table__row')
    const rowCount = await rows.count()
    if (rowCount === 0) {
      test.skip(true, '无备用金演示数据（tasks.md DATA 受阻登记）')
    }
    let approvedSeen = false
    for (let i = 0; i < rowCount; i++) {
      const row = rows.nth(i)
      const statusText = (await row.locator('.el-tag').first().innerText()).trim()
      const returnCount = await row.locator('button:has-text("归还")').count()
      if (statusText === '已通过') {
        approvedSeen = true
        expect(returnCount, `第 ${i + 1} 行 APPROVED 应有归还按钮`).toBe(1)
      } else {
        expect(returnCount, `第 ${i + 1} 行「${statusText}」不应有归还按钮`).toBe(0)
      }
    }
    // 无 APPROVED 行则归还可见性前提缺失——显式受阻登记，非静默通过
    test.skip(!approvedSeen, '无 APPROVED 备用金行，归还按钮正向断言前提缺失（tasks.md DATA 受阻登记）')
  })
})

test.describe('财务域 — C9 个人报销（@matrix C-9-1；建单受阻 API-GAP-fin）', () => {
  // 个人报销后端无 DELETE 通道 → 不真实建单；「请求无 projectId 抓包」依赖建单一并受阻
  test('新增个人报销弹窗 — 必填守卫 + 无项目字段', async ({ page }) => {
    let postCount = 0
    page.on('request', (r) => {
      if (r.url().includes('/v1/finance/personal-reimbursement') && r.method() === 'POST') postCount++
    })
    await page.goto('/finance/personal-reimbursement')
    await page.waitForSelector('button:has-text("新增个人报销")', { timeout: 30_000 })
    await page.locator('button:has-text("新增个人报销")').click()
    const dialog = page.locator('.el-dialog:has-text("新增个人报销")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 个人报销无项目字段（UI 层钉住：弹窗内不存在 label 为「项目」的表单项）
    await expect(dialog.locator('.el-form-item__label:text-is("项目")')).toHaveCount(0)
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
    await page.waitForTimeout(600)
    expect(postCount, '必填拦截下不应发出创建请求').toBe(0)
  })

  test('无编辑/删除入口（现状钉住）', async ({ page }) => {
    await page.goto('/finance/personal-reimbursement')
    await page.waitForSelector('.el-table__row, .el-table__empty-block', { timeout: 30_000 })
    const rowCount = await page.locator('.el-table__row').count()
    if (rowCount === 0) {
      test.skip(true, '无个人报销演示数据，入口缺失断言无可断言对象（tasks.md DATA 受阻登记）')
    }
    await expect(page.locator('.el-table button:has-text("编辑")')).toHaveCount(0)
    await expect(page.locator('.el-table button:has-text("删除")')).toHaveCount(0)
  })
})

test.describe('财务域 — C14 税率管理（@matrix C-14-1/2/3）', () => {
  test('必填守卫 + 越界/超精度输入 UI 钳制（min=0.01 max=99.99 precision=2）', async ({ page }) => {
    test.setTimeout(90_000)
    await page.goto('/finance/tax-rate')
    await page.waitForSelector('button:has-text("新增税率")', { timeout: 30_000 })
    await page.locator('button:has-text("新增税率")').click()
    const dialog = page.locator('.el-dialog:has-text("新增税率")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 空态确定 → 税率名称/数值必填错误
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })

    // 负向钳制：el-input-number blur 时把越界/超精度值收敛到合法区间（C-14-1/2 负向）
    const rateInput = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("税率数值")) input').first()
    await rateInput.fill('0')
    await rateInput.press('Tab')
    await expect(rateInput).toHaveValue('0.01')
    await rateInput.fill('100')
    await rateInput.press('Tab')
    await expect(rateInput).toHaveValue('99.99')
    await rateInput.fill('1.234')
    await rateInput.press('Tab')
    await expect(rateInput).toHaveValue('1.23')
    await dialog.locator('button:has-text("取消")').click()
  })

  test('99.99 边界值创建 → 列表可见 → 停用闭环（C-14-3）', async ({ page }) => {
    test.setTimeout(90_000)
    const rateName = `${E2E_PREFIX}_税率99.99`
    await page.goto('/finance/tax-rate')
    await page.waitForSelector('button:has-text("新增税率")', { timeout: 30_000 })
    await page.locator('button:has-text("新增税率")').click()
    const dialog = page.locator('.el-dialog:has-text("新增税率")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("税率名称")) input').first().fill(rateName)
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("税率数值")) input').first().fill('99.99')
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/finance/tax-rate') && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    expect((await createResp.json()).code, '创建边界税率').toBe(200)
    // 列表可见 + 税率数值展示 `99.99%`（formatRate 一致性）
    const targetRow = page.locator('.el-table__row', { hasText: rateName }).first()
    await expect(targetRow).toBeVisible({ timeout: 15_000 })
    await expect(targetRow).toContainText('99.99%')

    // 停用闭环：停用 = 逻辑删除（deleteTaxRate），即本用例自清理
    await targetRow.locator('button:has-text("停用")').click()
    const msgbox = page.locator('.el-message-box')
    await expect(msgbox).toBeVisible({ timeout: 10_000 })
    await expect(msgbox).toContainText('确定要停用税率')
    const [delResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/finance/tax-rate/') && resp.request().method() === 'DELETE',
        { timeout: 20_000 }
      ),
      msgbox.locator('button:has-text("确定停用")').click(),
    ])
    expect(delResp.status()).toBe(200)
    // 回写断言：该行状态变「停用」，停用按钮消失
    await expect(targetRow.locator('.el-tag')).toContainText('停用', { timeout: 15_000 })
    await expect(targetRow.locator('button:has-text("停用")')).toHaveCount(0)
  })
})

test.describe('财务域 — C10/C11/C12 只读断言（质保金预警 / 发票汇总 / 结算利润）', () => {
  test('C10 质保金到期预警 alert — 与 expiring API 双向一致', async ({ page }) => {
    test.setTimeout(90_000)
    const request = authed!
    const resp = await request.get(`${API_BASE}/api/v1/finance/retention/expiring`, { params: { days: 30 } })
    expect(resp.status()).toBe(200)
    const expiring: any[] = (await resp.json()).data || []
    await page.goto('/finance/retention')
    await page.waitForSelector('.el-table', { timeout: 30_000 })
    const alert = page.locator('.el-alert--warning:has-text("质保金将在 30 天内到期")')
    if (expiring.length > 0) {
      await expect(alert).toBeVisible({ timeout: 10_000 })
      await expect(alert).toContainText(`有 ${expiring.length} 笔质保金将在 30 天内到期`)
    } else {
      await expect(alert).toHaveCount(0)
    }
  })

  test('C11 发票汇总 — 分组表渲染 + 合计行', async ({ page }) => {
    test.setTimeout(90_000)
    const [summaryResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/finance/invoice-summary') && resp.request().method() === 'GET',
        { timeout: 30_000 }
      ),
      page.goto('/finance/invoice-summary'),
    ])
    expect(summaryResp.status()).toBe(200)
    expect((await summaryResp.json()).code, '发票汇总查询').toBe(200)
    await page.waitForSelector('.el-table', { timeout: 30_000 })
    // 多级表头「已开票/已收票」分组 + show-summary 合计行
    await expect(page.locator('.el-table th:has-text("已开票")')).toBeVisible()
    await expect(page.locator('.el-table th:has-text("已收票")')).toBeVisible()
    await expect(page.locator('.el-table__footer')).toBeVisible()
    await expect(page.locator('.el-table__footer')).toContainText('合计')
  })

  test('C12 项目最终结算 — 利润负值红色渲染', async ({ page }) => {
    test.setTimeout(90_000)
    const request = authed!
    const resp = await request.get(`${API_BASE}/api/v1/project-settlements`, { params: { page: 1, size: 100 } })
    expect(resp.status()).toBe(200)
    const records = (await resp.json()).data?.records || []
    const negative = records.find((r: any) => Number(r.profit) < 0)
    await page.goto('/finance/settlement')
    await page.waitForSelector('.el-table__row, .el-table__empty-block', { timeout: 30_000 })
    if (!negative) {
      // 无负利润结算单 → 红色渲染前提缺失，显式受阻登记（DATA），非静默通过
      test.skip(true, '无利润为负的结算单，红色渲染无可断言对象（tasks.md DATA 受阻登记）')
    }
    const targetRow = page.locator('.el-table__row', { hasText: negative.settlementCode }).first()
    await expect(targetRow).toBeVisible({ timeout: 15_000 })
    await expect(targetRow.locator('.text-danger').first()).toBeVisible()
  })
})
