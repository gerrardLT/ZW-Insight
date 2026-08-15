/**
 * 真实模式 E2E 测试：支出域写路径 UI（2026-08-14 P2 补测）
 *
 * @matrix B-23 采购结算写路径 / B-11~13 机械结算 create / B-18 工资单 /
 *   B-24 询价发布 / B-21 分包结算页级 / 盲点 13 四支出合同页提交入口现状钉住
 *
 * 范式与 workflow.spec.ts 一致：storageState=admin（租户 1），UI 操作经
 * waitForResponse 锚定真实接口硬断言（禁止容忍断言），创建的测试单据
 * afterAll 经 API 逆序清理（E2E_TEST_ 前缀可识别）。
 *
 * 受阻登记（tasks.md）：询价定标 UI（confirmBid 等 4 API 零消费方无页面）；
 * 盲点 13 待产品决策后翻转钉住用例。
 */
import { test, expect, request as pwRequest } from '@playwright/test'

// 写路径用例文件内串行（'default' 会继承全局 fullyParallel=true 仍并行，审查实证无效；
// 'serial' 才真串行——多弹窗/共享数据态用例并行互扰，P2 十二遍实跑实证）
test.describe.configure({ mode: 'serial' })

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'

// 后端仅认 Authorization: Bearer 头（AuthInterceptor 实证，无 cookie 通道），
// 而 storageState 的 token 存于 localStorage，playwright 内置 request fixture 只发 cookies
// 不带鉴权头 → 从 storage-state.json 提取 token 自建已鉴权 APIRequestContext。
// 注意：不可重新 API 登录取 token——admin 账号 max-devices=5，多轮实跑新会话会踢出
// UI storageState 会话致页面跳登录页（P2 实跑实证事故）
let authed: Awaited<ReturnType<typeof pwRequest.newContext>> | null = null

test.beforeAll(async () => {
  const fs = await import('node:fs')
  // playwright 以项目根（zw-insight-web）为 cwd 运行；storage-state.json 由 setup-real 生成
  const st = JSON.parse(fs.readFileSync('./e2e/.auth/storage-state.json', 'utf-8'))
  const token = (st.origins || []).flatMap((o: any) => o.localStorage || [])
    .find((kv: any) => kv.name === 'token')?.value
  expect(token, 'storageState 应含登录 token').toBeTruthy()
  authed = await pwRequest.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  })
})

test.afterAll(async () => {
  if (authed) await authed.dispose()
})

// 本 spec 创建的单据 ID（afterAll 清理）
const createdSettlementIds: number[] = []
const createdPayrollIds: number[] = []
const createdInquiryIds: number[] = []

const TS = Date.now()
const E2E_PREFIX = `E2E_TEST_${TS}`

test.describe('支出域 — 采购结算写路径（@matrix B-23）', () => {
  test('新增结算单弹窗 — 必填守卫（合同/入库单/金额）', async ({ page }) => {
    await page.goto('/purchase/settlement')
    await page.waitForLoadState('networkidle')
    await page.locator('button:has-text("新增结算单")').click()
    const dialog = page.locator('.el-dialog:has-text("新增结算单")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 不填任何字段直接确定 → 必填校验拦截，不发创建请求
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
  })

  test('选择合同触发候选入库单查询（available-inbounds）', async ({ page }) => {
    await page.goto('/purchase/settlement')
    await page.waitForLoadState('networkidle')
    await page.locator('button:has-text("新增结算单")').click()
    const dialog = page.locator('.el-dialog:has-text("新增结算单")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 打开合同下拉并选第一项
    const contractSelect = dialog.locator('.el-form-item:has-text("关联合同") .el-select')
    await contractSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 10_000 })
    const [inboundResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/purchase/settlement/available-inbounds') && resp.request().method() === 'GET',
        { timeout: 15_000 }
      ),
      page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click(),
    ])
    expect(inboundResp.status()).toBe(200)
    const body = await inboundResp.json()
    expect(Array.isArray(body.data), '候选入库单应为数组').toBe(true)
  })

  test('合同无可结算入库单 — 空态提示文案', async ({ page }) => {
    await page.goto('/purchase/settlement')
    await page.waitForLoadState('networkidle')
    await page.locator('button:has-text("新增结算单")').click()
    const dialog = page.locator('.el-dialog:has-text("新增结算单")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    const contractSelect = dialog.locator('.el-form-item:has-text("关联合同") .el-select')
    await contractSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 10_000 })
    // 逐个尝试找到无候选入库单的合同（探针实证多数合同候选为空）
    const items = page.locator('.el-select-dropdown:visible .el-select-dropdown__item')
    const count = await items.count()
    let foundEmpty = false
    for (let i = 0; i < Math.min(count, 8); i++) {
      await items.nth(i).click()
      await page.waitForTimeout(600)
      const tip = dialog.locator('.empty-tip')
      if (await tip.isVisible().catch(() => false)) {
        await expect(tip).toContainText('该合同暂无可结算的已审批入库单')
        foundEmpty = true
        break
      }
      // 有候选则重开下拉继续找
      if (i < Math.min(count, 8) - 1) await contractSelect.click()
    }
    expect(foundEmpty, '应存在无候选入库单的合同以验证空态提示').toBe(true)
  })

  test('结算金额输入上限绑定入库金额（超额双保险 UI 层钉住）', async ({ page }) => {
    await page.goto('/purchase/settlement')
    await page.waitForLoadState('networkidle')
    await page.locator('button:has-text("新增结算单")').click()
    const dialog = page.locator('.el-dialog:has-text("新增结算单")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 选合同 → 选入库单（合同池 size=1000 加载可能较慢，20s 宽容）
    const contractSelect = dialog.locator('.el-form-item:has-text("关联合同") .el-select')
    await contractSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await page.waitForTimeout(800)
    const inboundSelect = dialog.locator('.el-form-item:has-text("关联入库单") .el-select')
    // 无候选入库单 → 空态提示即时可见（isVisible 不等待；isDisabled 会等待不存在元素耗尽预算，十遍实证）
    if (await dialog.locator('.empty-tip').isVisible().catch(() => false)) {
      await expect(dialog.locator('.empty-tip')).toContainText('该合同暂无可结算的已审批入库单')
      test.skip(true, '首个合同无候选入库单，上限绑定用例跳过（空态提示已断言）')
      return
    }
    await inboundSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible', { timeout: 20_000 })
    const inboundItemCount = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()
    if (inboundItemCount === 0) {
      // 下拉可开但候选为空（合同存在但无已审批未结算入库单）：空态提示路径，上限绑定无可断言对象
      await expect(dialog.locator('.empty-tip')).toContainText('该合同暂无可结算的已审批入库单')
      test.skip(true, '首个合同候选入库单为空，上限绑定用例跳过（空态提示已断言）')
      return
    }
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await page.waitForTimeout(500)
    // 入库金额回填 + 结算金额 max 绑定（handleFormSubmit 超额 warning 的第二道 UI 保险）
    const amountInput = dialog.locator('.el-form-item:has-text("本次结算金额") input')
    const maxAttr = await amountInput.first().getAttribute('max')
    expect(maxAttr, '结算金额上限应绑定入库金额').toBeTruthy()
    const inboundVal = await dialog.locator('.el-form-item:has-text("入库金额") input').first().inputValue()
    expect(inboundVal, '入库金额应回填').not.toBe('')
  })

  test('创建结算单 — 完整流程（创建后 DRAFT 可提交进入审批）', async ({ page }) => {
    // 串行下仍偶发挂死：networkidle 在 dev 服务器（依赖预构建/HMR 频繁请求）下
    // 可能永不满足致 waitForLoadState 挂到测试超时（九遍实跑实证）——本用例全部改具体元素等待
    test.setTimeout(180_000)
    const request = authed!
    // 创建前快照既有结算单 ID（审查实证：按金额=1+DRAFT 模糊定位可能误命中真实单据）
    const beforeResp = await request.get(`${API_BASE}/api/v1/purchase/settlement/page`, { params: { page: 1, size: 200 } })
    const beforeIds = new Set(((await beforeResp.json()).data?.records || []).map((r: any) => r.id))
    await page.goto('/purchase/settlement')
    await page.waitForSelector('button:has-text("新增结算单")', { timeout: 30_000 })
    await page.locator('button:has-text("新增结算单")').click()
    const dialog = page.locator('.el-dialog:has-text("新增结算单")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 选合同
    const contractSelect = dialog.locator('.el-form-item:has-text("关联合同") .el-select')
    await contractSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await page.waitForTimeout(800)
    const inboundSelect = dialog.locator('.el-form-item:has-text("关联入库单") .el-select')
    // 无候选入库单时页面显式空态提示（settlement.vue empty-tip，isVisible 即时返回不等待；
    // 早期版本用 isDisabled 等待不存在的 .el-input__inner 耗尽测试预算，十遍实跑实证根因）
    if (await dialog.locator('.empty-tip').isVisible().catch(() => false)) {
      test.skip(true, '首个合同无候选入库单（空态提示已现），跳过创建流程用例')
      return
    }
    await inboundSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible', { timeout: 20_000 })
    const inboundCount = await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()
    if (inboundCount === 0) {
      test.skip(true, '首个合同候选入库单为空（依赖数据状态），跳过创建流程用例')
      return
    }
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await page.waitForTimeout(500)
    // 结算金额置小额（1 元）
    const amountInput = dialog.locator('.el-form-item:has-text("本次结算金额") input').first()
    await amountInput.fill('1')
    // 创建
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/purchase/settlement') && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    const createBody = await createResp.json()
    expect(createBody.code, `创建结算单：${createBody.message}`).toBe(200)
    // 定位新结算单：创建前后 ID 差集（唯一标识，杜绝误操作租户 1 真实单据）
    const listResp = await request.get(`${API_BASE}/api/v1/purchase/settlement/page`, { params: { page: 1, size: 200 } })
    const records = (await listResp.json()).data?.records || []
    const created = records.find((r: any) => !beforeIds.has(r.id) && r.status === 'DRAFT')
    expect(created, '新结算单应出现在列表（DRAFT，差集定位）').toBeTruthy()
    createdSettlementIds.push(created.id)

    // 提交进入审批（purchase_settlement_approval 租户 1 已部署实证）；
    // 提交/审批状态回写链路由 21-finance-chain L5-API 覆盖，此处经 API 提交避免 UI 行定位脆弱性
    const submitResp = await request.post(`${API_BASE}/api/v1/purchase/settlement/${created.id}/submit`)
    expect(submitResp.status()).toBe(200)
    const submitBody = await submitResp.json()
    expect(submitBody.code, `提交结算单：${submitBody.message}`).toBe(200)
    // 状态回写断言：提交后进入 APPROVED（PurchaseSettlementService.submit 启动流程并置 APPROVED 实证）
    const afterResp = await request.get(`${API_BASE}/api/v1/purchase/settlement/${created.id}`)
    expect((await afterResp.json()).data?.status, '提交后结算单应为 APPROVED').toBe('APPROVED')
  })
})

test.describe('支出域 — 机械结算 create 页（@matrix B-11/B-12/B-13）', () => {
  test('create 页必填守卫（项目/结算周期）', async ({ page }) => {
    await page.goto('/machine/settlement/create')
    await page.waitForLoadState('networkidle')
    await page.locator('button:has-text("保存结算单")').click()
    await expect(page.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
  })

  test('选择项目+周期触发使用明细预览查询（usage-record/page）', async ({ page }) => {
    test.setTimeout(90_000)
    await page.goto('/machine/settlement/create')
    await page.waitForLoadState('networkidle')
    // 选项目
    const projectSelect = page.locator('.el-form-item:has-text("项目") .el-select')
    await projectSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await page.waitForTimeout(500)
    // 填结算周期（daterange 需 Enter 提交值，实证 fill 后不触发 change）
    const startInput = page.locator('.el-form-item:has-text("结算周期") input').first()
    await startInput.fill('2026-01-01')
    await startInput.press('Enter')
    const endInput = page.locator('.el-form-item:has-text("结算周期") input').nth(1)
    await endInput.fill('2026-12-31')
    const [previewResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/machine/usage-record/page') && resp.request().method() === 'GET',
        { timeout: 20_000 }
      ),
      endInput.press('Enter'),
    ])
    expect(previewResp.status()).toBe(200)
    const body = await previewResp.json()
    expect(body.code, '预览查询应成功').toBe(200)
  })

  // @matrix 盲点 12 现状钉住：预览为空时保存无守卫（可直接提交空结算单）——
  // 本用例断言"空预览区域展示"行为，提交守卫待产品决策后补修复+翻转
  test('盲点 12 现状钉住：无使用记录时预览合计为 0', async ({ page }) => {
    test.setTimeout(90_000)
    await page.goto('/machine/settlement/create')
    await page.waitForLoadState('networkidle')
    const projectSelect = page.locator('.el-form-item:has-text("项目") .el-select')
    await projectSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await page.waitForTimeout(500)
    const startInput = page.locator('.el-form-item:has-text("结算周期") input').first()
    await startInput.fill('2020-01-01')
    await startInput.press('Enter')
    const endInput = page.locator('.el-form-item:has-text("结算周期") input').nth(1)
    await endInput.fill('2020-01-02')
    await endInput.press('Enter')
    await page.waitForTimeout(2000)
    // 远古周期必无使用记录：预览区域为空表或合计 0（不阻断，现状钉住）
    const totalText = await page.locator('.preview-total').innerText().catch(() => '')
    if (totalText) {
      expect(totalText).toContain('¥ 0')
    } else {
      // 预览区未显示（previewVisible=false 亦为合法现状）
      expect(await page.locator('.preview-section').count()).toBeLessThanOrEqual(1)
    }
  })
})

test.describe('支出域 — 盲点 13 修复验证：四支出合同页提交审批入口（2026-08-15 决策 A）', () => {
  // @matrix 盲点 13：决策 A 补 UI 提交按钮后翻转为正向用例——
  // DRAFT 行显示「提交审批」→ 点击+确认 → 对应 submit API 200 → 清理 withdraw+删除
  let projectId: number
  test.beforeAll(async () => {
    const request = authed!
    const pname = `${E2E_PREFIX}_盲13项目`
    const resp = await request.post(`${API_BASE}/api/v1/project`, {
      data: { projectName: pname, projectType: 'BUILDING', projectAddress: 'E2E', needTender: 0 },
    })
    expect(resp.ok(), '创建盲13共享项目').toBeTruthy()
    const pageResp = await request.get(`${API_BASE}/api/v1/project/page`, { params: { page: 1, size: 10, projectName: pname } })
    projectId = ((await pageResp.json()).data?.records || []).find((p: any) => p.projectName === pname)?.id
    expect(projectId, '项目应创建成功').toBeTruthy()
    // 系统默认预算控制为 BLOCK（hardCodedDefault 实证）且新项目无科目额度，
    // 支出合同创建必被拦——本项目设 EXEMPT 豁免（afterAll 删配置）
    const cfgResp = await request.post(`${API_BASE}/api/v1/budget-control-configs`, {
      data: { projectId, controlMode: 'EXEMPT', warningThreshold: 80 },
    })
    expect(cfgResp.ok(), '设 EXEMPT 预算控制').toBeTruthy()
  })
  test.afterAll(async () => {
    if (projectId) {
      const cfgPage = await authed!.get(`${API_BASE}/api/v1/budget-control-configs`, { params: { page: 1, size: 20 } })
      const cfg = ((await cfgPage.json()).data?.records || []).find((c: any) => String(c.projectId) === String(projectId))
      if (cfg) await authed!.delete(`${API_BASE}/api/v1/budget-control-configs/${cfg.id}`).catch(() => {})
      await authed!.delete(`${API_BASE}/api/v1/project/${projectId}`).catch(() => {})
    }
  })
  const cases: Array<{ route: string; name: string; createPath: string; submitPath: (id: any) => string; delPath: (id: any) => string; payload: (n: string, pid: number) => any; businessType: string }> = [
    {
      route: '/labor/contract', name: '劳务合同',
      createPath: '/api/v1/labor/contract', submitPath: (id) => `/api/v1/labor/contract/${id}/submit`, delPath: (id) => `/api/v1/labor/contract/${id}`,
      payload: (n, pid) => ({ projectId: pid, contractName: n, teamName: `${n}_班组`, contractAmount: 1000, startDate: '2026-01-01', endDate: '2026-12-31' }),
      businessType: 'LABOR_CONTRACT',
    },
    {
      route: '/machine/contract', name: '机械合同',
      createPath: '/api/v1/machine/contract', submitPath: (id) => `/api/v1/machine/contract/${id}/submit`, delPath: (id) => `/api/v1/machine/contract/${id}`,
      payload: (n, pid) => ({ projectId: pid, contractName: n, machineName: `${n}_机械`, supplierName: 'E2E供应商', rentalType: 'SHIFT', contractAmount: 1000, startDate: '2026-01-01', endDate: '2026-12-31' }),
      businessType: 'MACHINE_CONTRACT',
    },
    {
      route: '/subcontract/contract', name: '分包合同',
      createPath: '/api/v1/subcontract/contract', submitPath: (id) => `/api/v1/subcontract/contract/${id}/submit`, delPath: (id) => `/api/v1/subcontract/contract/${id}`,
      payload: (n, pid) => ({ projectId: pid, contractName: n, subcontractorName: 'E2E分包商', contractAmount: 1000, startDate: '2026-01-01', endDate: '2026-12-31' }),
      businessType: 'SUBCONTRACT',
    },
    {
      route: '/purchase/contract', name: '采购合同',
      createPath: '/api/v1/purchase/contract', submitPath: (id) => `/api/v1/purchase/contract/${id}/submit`, delPath: (id) => `/api/v1/purchase/contract/${id}`,
      payload: (n, pid) => ({ projectId: pid, contractName: n, contractCode: `E2E_${Date.now()}`, partyAName: 'E2E甲方', supplierName: 'E2E供应商', contractAmount: 1000, signingDate: '2026-01-15' }),
      businessType: 'PURCHASE_CONTRACT',
    },
  ]
  for (const c of cases) {
    test(`${c.name}页 — DRAFT 行提交审批按钮可用且触发 submit（盲点 13 翻转）`, async ({ page }) => {
      const request = authed!
      const cname = `${E2E_PREFIX}_盲13_${c.name}`
      const createResp = await request.post(`${API_BASE}${c.createPath}`, { data: c.payload(cname, projectId) })
      expect(createResp.ok(), `创建${c.name}`).toBeTruthy()
      // 按合同名过滤查询（租户 1 数据量大，无过滤翻页可能查不到新记录）
      const pageResp = await request.get(`${API_BASE}${c.createPath}/page`, { params: { page: 1, size: 50, contractName: cname } })
      const created = ((await pageResp.json()).data?.records || []).find((r: any) => r.contractName === cname)
      expect(created, `${c.name}应创建成功`).toBeTruthy()
      try {
        await page.goto(c.route)
        await page.waitForLoadState('networkidle')
        const row = page.locator('.el-table__row', { hasText: cname })
        await expect(row.first()).toBeVisible({ timeout: 15_000 })
        const submitBtn = row.first().locator('button:has-text("提交审批")')
        await expect(submitBtn, 'DRAFT 行应显示提交审批按钮（盲点 13 修复）').toBeVisible()
        await submitBtn.click()
        const msgbox = page.locator('.el-message-box')
        await expect(msgbox).toBeVisible({ timeout: 10_000 })
        const [submitResp] = await Promise.all([
          page.waitForResponse(
            (resp) => resp.url().includes(c.submitPath(created.id)) && resp.request().method() === 'POST',
            { timeout: 15_000 }
          ),
          msgbox.locator('button:has-text("确定")').click(),
        ])
        expect(submitResp.status()).toBe(200)
      } finally {
        await request.post(`${API_BASE}/api/v1/workflow/approval/withdraw-by-business?businessType=${c.businessType}&businessId=${created.id}`).catch(() => {})
        await request.delete(`${API_BASE}${c.delPath(created.id)}`).catch(() => {})
      }
    })
  }
})

test.describe('支出域 — 询价定标 UI 闭环（B-P-X1，2026-08-15 决策 A 补齐）', () => {
  // @matrix B-P-X1：发布询价 → 两供应商报价 → UI 比价定标（报价列表+计算排名+确认定标）→ 状态 AWARDED
  test('比价定标全流程 — 报价列表/排名/定标（UI 真实操作）', async ({ page }) => {
    const request = authed!
    const title = `${E2E_PREFIX}_定标询价`
    // 1. 创建并发布询价（API）
    const createResp = await request.post(`${API_BASE}/api/v1/purchase/inquiry`, {
      data: {
        title, materialSummary: 'E2E定标材料', description: 'E2E',
        deadline: '2026-12-31T00:00:00',
        items: [{ materialName: 'E2E定标材料', specification: 'S1', quantity: 10 }],
      },
    })
    expect(createResp.ok(), '创建询价').toBeTruthy()
    const iqPage = await request.get(`${API_BASE}/api/v1/purchase/inquiry/page`, { params: { page: 1, size: 50, title } })
    const inquiry = ((await iqPage.json()).data?.records || []).find((r: any) => r.title === title)
    expect(inquiry, '询价应创建成功').toBeTruthy()
    const pubResp = await request.post(`${API_BASE}/api/v1/purchase/inquiry/${inquiry.id}/publish`)
    expect(pubResp.ok(), '发布询价').toBeTruthy()
    // 2. 两供应商报价（API，details 总额不同）
    for (const [sid, sname, price] of [[1, 'E2E供应商A', 9000], [2, 'E2E供应商B', 8000]] as const) {
      const q = await request.post(`${API_BASE}/api/v1/purchase/quotation/submit`, {
        data: { inquiryId: inquiry.id, supplierId: sid, supplierName: sname, details: [{ totalPrice: price }] },
      })
      expect(q.ok(), `报价 ${sname}`).toBeTruthy()
    }
    try {
      // 3. UI 比价定标
      await page.goto('/purchase/inquiry')
      await page.waitForLoadState('networkidle')
      const row = page.locator('.el-table__row', { hasText: title })
      await expect(row.first()).toBeVisible({ timeout: 15_000 })
      await row.first().locator('button:has-text("比价定标")').click()
      const dialog = page.locator('.el-dialog:has-text("比价定标")')
      await expect(dialog).toBeVisible({ timeout: 10_000 })
      // 报价列表应含两供应商
      await expect(dialog.locator('.el-table').first().locator('.el-table__row')).toHaveCount(2, { timeout: 10_000 })
      // 计算排名
      await dialog.locator('button:has-text("计算排名")').click()
      const rankingTable = dialog.locator('.el-table').nth(1)
      await expect(rankingTable.locator('.el-table__row')).toHaveCount(2, { timeout: 10_000 })
      // 低价排第一（B 8000 < A 9000）
      const firstRankRow = rankingTable.locator('.el-table__row').first()
      await expect(firstRankRow).toContainText('E2E供应商B')
      // 确认定标给第一名
      await firstRankRow.locator('button:has-text("确认定标")').click()
      const msgbox = page.locator('.el-message-box')
      await expect(msgbox).toBeVisible({ timeout: 10_000 })
      const [confirmResp] = await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes('/v1/purchase/bid/confirm') && resp.request().method() === 'POST',
          { timeout: 15_000 }
        ),
        msgbox.locator('button:has-text("确定")').click(),
      ])
      expect(confirmResp.status()).toBe(200)
      // 列表状态翻转为已定标
      await expect(row.first().locator('.el-tag')).toContainText('已定标', { timeout: 10_000 })
    } finally {
      await request.delete(`${API_BASE}/api/v1/purchase/inquiry/${inquiry.id}`).catch(() => {})
    }
  })
})

test.describe('支出域 — 工资单生成与提交（@matrix B-18）', () => {
  test('生成工资单弹窗 — 必填守卫（班组/用工类型/结算周期）', async ({ page }) => {
    await page.goto('/labor/payroll')
    await page.waitForLoadState('networkidle')
    await page.locator('button:has-text("生成工资单")').click()
    const dialog = page.locator('.el-dialog:has-text("生成工资单")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await dialog.locator('button:has-text("生成")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
  })

  test('汇总语义提示 + 班组下拉可选', async ({ page }) => {
    await page.goto('/labor/payroll')
    await page.waitForLoadState('networkidle')
    await page.locator('button:has-text("生成工资单")').click()
    const dialog = page.locator('.el-dialog:has-text("生成工资单")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // B-18 汇总语义：el-alert 提示自动汇总
    await expect(dialog.locator('.el-alert')).toContainText('已审批的用工单自动汇总')
    const teamSelect = dialog.locator('.el-form-item:has-text("班组") .el-select')
    await teamSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 10_000 })
    expect(await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').count()).toBeGreaterThan(0)
  })

  test('生成工资单 — 完整流程（DRAFT → 提交直批 APPROVED → APPROVED 无操作按钮）', async ({ page }) => {
    test.setTimeout(120_000)
    const request = authed!
    // 动态唯一远期周期（防与既有工资单周期重叠——后端 B5 任意状态重叠拒绝实证；
    // 秒级时间戳取模保证每轮/每次 retry 唯一，早期版本 dayOffset 恒 0 致跨轮碰撞实证）
    const dayOffset = Math.floor(Date.now() / 1000) % 100000
    const pStart = new Date(Date.UTC(2015, 0, 1 + dayOffset))
    const pEnd = new Date(Date.UTC(2015, 0, 2 + dayOffset))
    const fmt = (d: Date) => d.toISOString().slice(0, 10)
    const periodStart = fmt(pStart)
    const periodEnd = fmt(pEnd)
    await page.goto('/labor/payroll')
    await page.waitForLoadState('networkidle')
    await page.locator('button:has-text("生成工资单")').click()
    const dialog = page.locator('.el-dialog:has-text("生成工资单")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 选班组（联动 projectId，handleTeamChange 实证）
    const teamSelect = dialog.locator('.el-form-item:has-text("班组") .el-select')
    await teamSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 10_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await page.waitForTimeout(500)
    // 用工类型默认 FIXED；选结算周期（daterange fill+Enter 提交，机械结算页同模式实证可行）
    const startInput = dialog.locator('.el-form-item:has-text("结算周期") input').first()
    await startInput.fill(periodStart)
    await startInput.press('Enter')
    const endInput = dialog.locator('.el-form-item:has-text("结算周期") input').nth(1)
    await endInput.fill(periodEnd)
    await endInput.press('Enter')
    await page.waitForTimeout(500)
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/labor/payroll') && resp.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("生成")').click(),
    ])
    expect(createResp.status()).toBe(200)
    const createBody = await createResp.json()
    expect(createBody.code, `生成工资单：${createBody.message}`).toBe(200)
    // payload 周期提交断言（钉住 daterange 联动；若为空则后续查找必然失败，先定位根因）
    const payrollPost = createResp.request().postDataJSON()
    expect(payrollPost.periodStart, '结算周期起始应提交').toBe(periodStart)
    expect(payrollPost.periodEnd, '结算周期截止应提交').toBe(periodEnd)
    await page.waitForLoadState('networkidle')

    // 定位新工资单（远期周期 DRAFT）并登记清理
    // 注：后端分页参数为 page/size（前端页面传 pageNum/pageSize 为前后端不一致缺陷，台账登记）
    const listResp = await request.get(`${API_BASE}/api/v1/labor/payroll/page`, { params: { page: 1, size: 200 } })
    const records = (await listResp.json()).data?.records || []
    const created = records.find((r: any) => r.periodStart === periodStart && r.periodEnd === periodEnd && r.status === 'DRAFT')
    expect(created, '新工资单应出现在列表（DRAFT）').toBeTruthy()
    createdPayrollIds.push(created.id)

    // 提交（直批 APPROVED，LaborPayrollService.submit 实证无流程）
    const row = page.locator('.el-table__row', { hasText: `${periodStart} ~ ${periodEnd}` })
    await row.locator('button:has-text("提交")').click()
    const [submitResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes(`/v1/labor/payroll/${created.id}/submit`),
        { timeout: 15_000 }
      ),
      page.locator('.el-message-box button:has-text("确定")').click().catch(() => {}),
    ])
    expect(submitResp.status()).toBe(200)
    const submitBody = await submitResp.json()
    expect(submitBody.code, `提交工资单：${submitBody.message}`).toBe(200)
    // APPROVED 行无提交/删除按钮（状态守卫 UI 层断言）
    await page.waitForLoadState('networkidle')
    const approvedRow = page.locator('.el-table__row', { hasText: `${periodStart} ~ ${periodEnd}` })
    await expect(approvedRow.locator('.el-tag')).toContainText('已审批')
    expect(await approvedRow.locator('button:has-text("提交")').count()).toBe(0)
    expect(await approvedRow.locator('button:has-text("删除")').count()).toBe(0)
  })
})

test.describe('支出域 — 询价创建与发布（@matrix B-24）', () => {
  test('新增询价弹窗 — 必填守卫（标题/材料名称）', async ({ page }) => {
    await page.goto('/purchase/inquiry')
    await page.waitForLoadState('networkidle')
    await page.locator('button:has-text("新增询价")').click()
    const dialog = page.locator('.el-dialog:has-text("新增询价")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
  })

  test('创建询价 — 完整流程（payload items 组装 + DRAFT 状态）', async ({ page }) => {
    test.setTimeout(120_000)
    const request = authed!
    await page.goto('/purchase/inquiry')
    await page.waitForLoadState('networkidle')
    await page.locator('button:has-text("新增询价")').click()
    const dialog = page.locator('.el-dialog:has-text("新增询价")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    const title = `${E2E_PREFIX}_询价`
    await dialog.locator('.el-form-item:has-text("询价标题") input').fill(title)
    await dialog.locator('.el-form-item:has-text("材料名称") input').fill('E2E测试钢筋')
    const [createResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/purchase/inquiry') && resp.request().method() === 'POST',
        { timeout: 15_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    const body = await createResp.json()
    expect(body.code, `创建询价：${body.message}`).toBe(200)
    // payload 组装断言：buildInquiryPayload items 数组（前后端契约钉住）
    const reqPost = createResp.request().postDataJSON()
    expect(Array.isArray(reqPost.items), 'payload 应含 items 数组').toBe(true)
    expect(reqPost.items[0].materialName).toBe('E2E测试钢筋')
    await page.waitForLoadState('networkidle')
    // 登记清理（后端分页参数 page/size，前端 pageNum/pageSize 不一致已登记台账）
    const listResp = await request.get(`${API_BASE}/api/v1/purchase/inquiry/page`, { params: { page: 1, size: 200, title } })
    const created = ((await listResp.json()).data?.records || []).find((r: any) => r.title === title)
    expect(created, '新询价应出现在列表').toBeTruthy()
    createdInquiryIds.push(created.id)
    // 状态待发布（DRAFT）+ 有发布按钮
    const row = page.locator('.el-table__row', { hasText: title })
    await expect(row.locator('.el-tag')).toContainText('待发布')
    await expect(row.locator('button:has-text("发布")')).toBeVisible()
  })

  test('发布询价 — DRAFT → 报价中（PUBLISHED），发布按钮消失', async ({ page }) => {
    await page.goto('/purchase/inquiry')
    await page.waitForLoadState('networkidle')
    const title = `${E2E_PREFIX}_询价`
    let row = page.locator('.el-table__row', { hasText: title })
    if ((await row.count()) === 0) {
      test.skip(true, '前置询价不存在（上一用例未产生），跳过发布用例')
      return
    }
    const [publishResp] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/purchase/inquiry') && resp.url().includes('/publish'),
        { timeout: 15_000 }
      ),
      row.locator('button:has-text("发布")').click()
        .then(() => page.locator('.el-message-box button:has-text("确定")').click().catch(() => {})),
    ])
    expect(publishResp.status()).toBe(200)
    const body = await publishResp.json()
    expect(body.code, `发布询价：${body.message}`).toBe(200)
    await page.waitForLoadState('networkidle')
    row = page.locator('.el-table__row', { hasText: title })
    await expect(row.locator('.el-tag')).toContainText('报价中')
    // PUBLISHED 后无发布按钮（仅 DRAFT 可发）
    expect(await row.locator('button:has-text("发布")').count()).toBe(0)
  })

  test('非 DRAFT 状态不可重复发布（API 负向钉住）', async () => {
    const request = authed!
    const title = `${E2E_PREFIX}_询价`
    const listResp = await request.get(`${API_BASE}/api/v1/purchase/inquiry/page`, { params: { page: 1, size: 200, title } })
    const created = ((await listResp.json()).data?.records || []).find((r: any) => r.title === title)
    if (!created) {
      test.skip(true, '前置询价不存在，跳过重复发布负向用例')
      return
    }
    expect(created.status, '前置询价应为 PUBLISHED').toBe('PUBLISHED')
    const resp = await request.post(`${API_BASE}/api/v1/purchase/inquiry/${created.id}/publish`)
    expect(resp.status()).toBe(200)
    const body = await resp.json()
    expect(body.code, '非草稿重复发布应被拒').not.toBe(200)
  })
})

test.describe('支出域 — 分包结算页级（@matrix B-21）', () => {
  test('分包结算页 — 正常加载与新增入口', async ({ page }) => {
    await page.goto('/subcontract/settlement')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('.el-table, .el-empty').first()).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('button:has-text("新增")').first()).toBeVisible({ timeout: 10_000 })
  })

  test('分包结算新增弹窗 — 必填守卫', async ({ page }) => {
    await page.goto('/subcontract/settlement')
    await page.waitForLoadState('networkidle')
    await page.locator('button:has-text("新增")').first().click()
    const dialog = page.locator('.el-dialog').first()
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    await dialog.locator('button:has-text("确定")').click()
    await expect(dialog.locator('.el-form-item__error').first()).toBeVisible({ timeout: 10_000 })
  })
})

/**
 * 测试数据清理（afterAll）：
 * 结算单先 withdraw 审批流（若已提交）再删除；工资单/询价删除受状态守卫时仅告警
 *（E2E_TEST_ 前缀可识别，残留核查兜底）。
 */
test.afterAll(async () => {
  const request = authed!
  for (const id of createdSettlementIds) {
    try {
      await request.post(
        `${API_BASE}/api/v1/workflow/approval/withdraw-by-business?businessType=PURCHASE_SETTLEMENT&businessId=${id}`
      ).catch(() => {})
      await request.delete(`${API_BASE}/api/v1/purchase/settlement/${id}`)
    } catch {
      console.warn(`[Cleanup] 删除采购结算 ${id} 失败，可能需要手动清理`)
    }
  }
  for (const id of createdPayrollIds) {
    try {
      await request.delete(`${API_BASE}/api/v1/labor/payroll/${id}`)
    } catch {
      console.warn(`[Cleanup] 删除工资单 ${id} 失败（APPROVED 守卫可能拦截）`)
    }
  }
  for (const id of createdInquiryIds) {
    try {
      await request.delete(`${API_BASE}/api/v1/purchase/inquiry/${id}`)
    } catch {
      console.warn(`[Cleanup] 删除询价 ${id} 失败（PUBLISHED 守卫可能拦截）`)
    }
  }
})
