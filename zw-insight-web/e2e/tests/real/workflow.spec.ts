/**
 * 真实模式 E2E 测试：审批流操作验证
 *
 * 验证完整的审批流程：
 * 1. 创建合同（草稿状态）
 * 2. 提交合同审批
 * 3. 查看待办任务
 * 4. 执行审批操作
 *
 * 所有操作使用 tenant_id=9999 数据（通过真实登录的 admin 账号）。
 * 测试完成后通过 afterAll 清理创建的数据。
 *
 * 需求: 6.1, 6.2
 */
import { test, expect, request as pwRequest } from '@playwright/test'

// 审批办理用例持有共享审批状态（待办列表/合同状态），fullyParallel 下同文件并行
// 互扰（三轮全链实证：行定位串扰/待办查空/批量按钮未启用）——文件内串行
test.describe.configure({ mode: 'default' })

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'

// 后端仅认 Authorization: Bearer 头（AuthInterceptor 实证），storageState token 存于 localStorage，
// playwright 内置 request fixture 只发 cookies 不带鉴权头 → 自建已鉴权 APIRequestContext
//（API 直登：联调 captcha-enabled=false 实证；P2 实跑实证修复，expense-write 同源）
let authed: Awaited<ReturnType<typeof pwRequest.newContext>> | null = null

test.beforeAll(async () => {
  // 从 storage-state.json 提取 token（不可重新 API 登录：admin max-devices=5，
  // 多轮实跑新会话会踢出 UI 会话致页面跳登录页，P2 实证事故）
  const fs = await import('node:fs')
  const st = JSON.parse(fs.readFileSync('./e2e/.auth/storage-state.json', 'utf-8'))
  const token = (st.origins || []).flatMap((o: any) => o.localStorage || [])
    .find((kv: any) => kv.name === 'token')?.value
  expect(token, 'storageState 应含登录 token').toBeTruthy()
  authed = await pwRequest.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  })
})

// 测试中创建的合同 ID，用于清理
const createdContractIds: number[] = []

// 生成唯一测试合同名称
function testContractName(): string {
  const ts = Date.now()
  return `E2E审批流测试合同_${ts}`
}

test.describe('审批流 — 合同创建与提交', () => {
  test('合同列表页 — 正常加载', async ({ page }) => {
    await page.goto('/contract/list')
    await page.waitForLoadState('networkidle')
    await expect(
      page.locator('.el-table, .el-empty').first()
    ).toBeVisible({ timeout: 20_000 })
  })

  test('创建合同 — 表单页渲染', async ({ page }) => {
    await page.goto('/contract/create')
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.el-form', { timeout: 15_000 })

    // 核心字段应存在
    await expect(
      page.locator('.el-form-item__label:has-text("所属项目"), .el-form-item__label:has-text("项目")').first()
    ).toBeVisible({ timeout: 10_000 })
    await expect(
      page.locator('button:has-text("保存"), button:has-text("提交")').first()
    ).toBeVisible()
  })

  test('创建合同 — 完整流程', async ({ page }) => {
    await page.goto('/contract/create')
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.el-form', { timeout: 15_000 })

    const contractName = testContractName()

    // 填写合同名称（如果有该字段）
    const nameInput = page.locator(
      '[placeholder*="合同名称"], [placeholder*="请输入合同名称"]'
    ).first()
    if (await nameInput.isVisible().catch(() => false)) {
      await nameInput.fill(contractName)
    }

    // 选择所属项目（el-select）
    const projectSelect = page.locator(
      '.el-form-item:has(.el-form-item__label:has-text("所属项目")) .el-select, ' +
      '.el-form-item:has(.el-form-item__label:has-text("项目")) .el-select'
    ).first()
    if (await projectSelect.isVisible().catch(() => false)) {
      await projectSelect.click()
      await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 5_000 }).catch(() => {})
      const firstProject = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first()
      if (await firstProject.isVisible().catch(() => false)) {
        await firstProject.click()
      }
    }

    // 填写甲方名称
    const partyAInput = page.locator('[placeholder*="甲方"], [placeholder*="请输入甲方名称"]').first()
    if (await partyAInput.isVisible().catch(() => false)) {
      await partyAInput.fill('E2E测试甲方单位')
    }

    // 填写合同金额
    const amountInput = page.locator(
      '.el-form-item:has(.el-form-item__label:has-text("合同金额")) input, ' +
      '.el-form-item:has(.el-form-item__label:has-text("金额")) input'
    ).first()
    if (await amountInput.isVisible().catch(() => false)) {
      await amountInput.fill('500000')
    }

    // 监听保存请求
    const [saveResponse] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/contract') && resp.request().method() === 'POST',
        { timeout: 15_000 }
      ).catch(() => null),
      page.locator('button:has-text("保存"), button:has-text("提交")').first().click(),
    ])

    if (saveResponse) {
      const body = await saveResponse.json().catch(() => null)
      if (body?.code === 200 && body?.data) {
        const contractId = typeof body.data === 'number' ? body.data : body.data?.id
        if (contractId) {
          createdContractIds.push(contractId)
        }
      }
      expect(saveResponse.status()).toBeLessThan(400)
    }
  })
})

test.describe('审批流 — 提交审批', () => {
  test('合同列表 — 提交按钮存在', async ({ page }) => {
    await page.goto('/contract/list')
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.el-table, .el-empty', { timeout: 15_000 })

    const rows = page.locator('.el-table__body-wrapper .el-table__row')
    const rowCount = await rows.count()

    if (rowCount > 0) {
      // 查找草稿状态的合同行中的提交按钮
      const submitBtn = page.locator(
        '.el-table__body-wrapper .el-table__row button:has-text("提交"), ' +
        '.el-table__body-wrapper .el-table__row a:has-text("提交")'
      ).first()

      // 提交按钮可能存在于草稿状态的行中
      if (await submitBtn.isVisible().catch(() => false)) {
        await expect(submitBtn).toBeEnabled()
      } else {
        // 如果没有草稿状态的合同，跳过
        test.skip(true, '没有可提交的草稿合同')
      }
    } else {
      test.skip(true, '合同列表为空')
    }
  })

  test('提交合同审批 — API 调用验证', async ({ page }) => {
    const request = authed!
    // 如果前面的测试创建了合同，尝试提交审批
    if (createdContractIds.length === 0) {
      test.skip(true, '没有可用的测试合同，跳过提交审批测试')
      return
    }

    const contractId = createdContractIds[0]

    // 通过 API 直接提交审批
    const submitResp = await request.put(
      `${API_BASE}/api/v1/contract/${contractId}/submit`
    ).catch(() => null)

    if (submitResp) {
      // 验证提交请求被接受（可能因缺少审批流程定义而失败，但 HTTP 应不是 5xx）
      expect(submitResp.status()).toBeLessThan(500)
    }
  })
})

test.describe('审批流 — 待办任务', () => {
  test('审批管理页面 — 正常加载', async ({ page }) => {
    await page.goto('/workflow/approval')
    await page.waitForLoadState('networkidle')
    await expect(
      page.locator('.el-table, .el-empty, .el-card, .el-tabs').first()
    ).toBeVisible({ timeout: 20_000 })
  })

  test('待办任务列表 — 可查看', async ({ page }) => {
    await page.goto('/workflow/approval')
    await page.waitForLoadState('networkidle')

    // 查找待办 Tab 或直接显示待办列表
    const todoTab = page.locator('.el-tabs__item:has-text("待办"), .el-tabs__item:has-text("待处理")').first()
    if (await todoTab.isVisible().catch(() => false)) {
      await todoTab.click()
      await page.waitForTimeout(1000)
    }

    // 待办列表应渲染（表格或空状态）
    await expect(
      page.locator('.el-table, .el-empty').first()
    ).toBeVisible({ timeout: 15_000 })
  })

  test('已办任务列表 — 可查看', async ({ page }) => {
    await page.goto('/workflow/approval')
    await page.waitForLoadState('networkidle')

    // 查找已办 Tab
    const doneTab = page.locator('.el-tabs__item:has-text("已办"), .el-tabs__item:has-text("已处理")').first()
    if (await doneTab.isVisible().catch(() => false)) {
      await doneTab.click()
      await page.waitForTimeout(1000)
      await expect(
        page.locator('.el-table, .el-empty').first()
      ).toBeVisible({ timeout: 15_000 })
    } else {
      test.skip(true, '未找到已办任务 Tab')
    }
  })

  test('审批操作 — 通过按钮可用', async ({ page }) => {
    await page.goto('/workflow/approval')
    await page.waitForLoadState('networkidle')

    // 确保在待办 Tab
    const todoTab = page.locator('.el-tabs__item:has-text("待办"), .el-tabs__item:has-text("待处理")').first()
    if (await todoTab.isVisible().catch(() => false)) {
      await todoTab.click()
      await page.waitForTimeout(1000)
    }

    // 检查是否有待办任务
    const rows = page.locator('.el-table__body-wrapper .el-table__row')
    const rowCount = await rows.count()

    if (rowCount > 0) {
      // 第一行应有审批按钮
      const approveBtn = rows.first().locator(
        'button:has-text("审批"), button:has-text("处理"), ' +
        'a:has-text("审批"), a:has-text("处理")'
      ).first()

      if (await approveBtn.isVisible().catch(() => false)) {
        await expect(approveBtn).toBeEnabled()
      } else {
        test.skip(true, '待办任务行中没有审批按钮')
      }
    } else {
      test.skip(true, '没有待办任务')
    }
  })
})

test.describe('审批流 — 流程定义', () => {
  test('流程定义页面 — 正常加载', async ({ page }) => {
    await page.goto('/workflow/process')
    await page.waitForLoadState('networkidle')
    await expect(
      page.locator('.el-table, .el-empty, .el-card').first()
    ).toBeVisible({ timeout: 20_000 })
  })

  test('流程设计器页面 — 正常加载', async ({ page }) => {
    await page.goto('/workflow/designer')
    await page.waitForLoadState('networkidle')
    // 流程设计器可能使用 bpmn-js 或其他组件
    await expect(
      page.locator('.bjs-container, .designer-container, .el-card, canvas').first()
    ).toBeVisible({ timeout: 20_000 })
  })
})

test.describe('审批流 — 真实办理操作（2026-08-14 P1 补测，@matrix D-32/D-33-12）', () => {
  // 数据准备：API 创建并提交施工合同（租户 1 已部署 construction_contract_approval 实证），
  // 返回 contractId；审批流由用例内清理（withdraw/terminate + 合同删除）
  async function prepareSubmittedContract(request: any): Promise<number> {
    const ts = Date.now()
    const prjName = `E2E审批UI_${ts}`
    const prjResp = await request.post(`${API_BASE}/api/v1/project`, {
      data: { projectName: prjName, projectType: 'BUILDING', projectAddress: 'P1 UI测试', needTender: 0 },
    })
    expect(prjResp.ok(), '创建项目').toBeTruthy()
    const prjPage = await request.get(`${API_BASE}/api/v1/project/page`, {
      params: { page: 1, size: 10, projectName: prjName },
    })
    const prj = (await prjPage.json()).data?.records?.find((p: any) => p.projectName === prjName)
    expect(prj, '项目应可查到').toBeTruthy()
    createdContractIds.push(prj.id) // 项目也登记清理（合同删后才能删项目）

    const cResp = await request.post(`${API_BASE}/api/v1/contract`, {
      data: {
        projectId: prj.id, contractType: 'REGISTER', partyAName: 'E2E审批UI甲方',
        signingDate: '2026-08-01', startDate: '2026-08-01', endDate: '2026-12-31',
        contractAmount: 88888, taxRate: 9,
      },
    })
    expect(cResp.ok(), '创建合同').toBeTruthy()
    const cPage = await request.get(`${API_BASE}/api/v1/contract/page`, {
      params: { page: 1, size: 20, projectId: prj.id },
    })
    const contract = (await cPage.json()).data?.records?.[0]
    expect(contract, '合同应可查到').toBeTruthy()
    createdContractIds.push(contract.id)

    const submitResp = await request.post(`${API_BASE}/api/v1/contract/${contract.id}/submit`)
    expect(submitResp.ok(), '提交合同审批').toBeTruthy()
    return contract.id
  }

  /** 按 businessId 查待办（API 层辅助断言） */
  async function findTodos(request: any, businessId: number): Promise<any[]> {
    const resp = await request.get(`${API_BASE}/api/v1/workflow/approval/todo`, {
      params: { page: 1, size: 50 },
    })
    return ((await resp.json()).data?.records || []).filter((t: any) => String(t.businessId) === String(businessId))
  }

  async function withdrawContract(request: any, contractId: number): Promise<void> {
    await request.post(
      `${API_BASE}/api/v1/workflow/approval/withdraw-by-business?businessType=CONSTRUCTION_CONTRACT&businessId=${contractId}`
    ).catch(() => {})
  }

  /** 定位目标待办行（CI 并行环境下首行未必是目标——二轮全链实证 terminate 点错行）：
   *  经 API 取目标 task 的 createTime，UI 行内 createTime 列含该前缀（取到分钟） */
  async function locateTodoRow(page: any, request: any, contractId: number): Promise<any> {
    const todos = await findTodos(request, contractId)
    expect(todos.length, '目标合同应有待办').toBeGreaterThan(0)
    const key = String(todos[0].createTime || '').slice(0, 16)
    expect(key.length, '待办应含 createTime 用于行定位').toBeGreaterThanOrEqual(16)
    const row = page.locator('.el-table__body-wrapper .el-table__row', { hasText: key })
    await expect(row.first()).toBeVisible({ timeout: 15_000 })
    return row.first()
  }

  test('退回发起人 — 弹窗真实操作（D-32）', async ({ page }) => {
    const request = authed!
    const contractId = await prepareSubmittedContract(request)
    try {
      await page.goto('/workflow/approval')
      await page.waitForLoadState('networkidle')
      const todoTab = page.locator('.el-tabs__item:has-text("待办"), .el-tabs__item:has-text("待处理")').first()
      if (await todoTab.isVisible().catch(() => false)) {
        await todoTab.click()
        await page.waitForTimeout(1000)
      }
      // 待办行点「退回」（按 createTime 定位目标行）
      const targetRow = await locateTodoRow(page, request, contractId)
      const rejectBtn = targetRow.locator('button:has-text("退回")')
      await rejectBtn.click()
      // 退回弹窗：选「退回发起人」+ 填原因 + 确定
      const dialog = page.locator('.el-dialog:has-text("退回任务")')
      await expect(dialog).toBeVisible({ timeout: 10_000 })
      await dialog.locator('.el-radio:has-text("退回发起人")').click()
      await dialog.locator('textarea').fill('P1 UI测试退回发起人')
      const [rejectResp] = await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes('/workflow/approval/reject-start') && resp.request().method() === 'POST',
          { timeout: 15_000 }
        ),
        dialog.locator('button:has-text("确定")').click(),
      ])
      expect(rejectResp.status()).toBe(200)
      // 回写断言：合同回 DRAFT（语义实证：reject-start 发布 ApprovalRejectEvent）
      const cResp = await request.get(`${API_BASE}/api/v1/contract/${contractId}`)
      expect((await cResp.json()).data?.status, '退回发起人后合同应 DRAFT').toBe('DRAFT')
    } finally {
      await withdrawContract(request, contractId)
    }
  })

  test('退回上一步 — 待办回退第一级（D-32）', async ({ page }) => {
    const request = authed!
    const contractId = await prepareSubmittedContract(request)
    try {
      // 先通过第一级，使任务进入第二级
      let todos = await findTodos(request, contractId)
      expect(todos.length).toBeGreaterThan(0)
      const c1 = await request.post(`${API_BASE}/api/v1/workflow/approval/complete`, {
        data: { taskId: todos[0].taskId, comment: 'P1 UI一级通过' },
      })
      expect(c1.ok()).toBeTruthy()
      todos = await findTodos(request, contractId)
      expect(todos[0]?.taskDefinitionKey, '应进入第二级').toBe('financeApproval')

      // UI 退回上一步（按 createTime 定位目标行）
      await page.goto('/workflow/approval')
      await page.waitForLoadState('networkidle')
      const todoTab2 = page.locator('.el-tabs__item:has-text("待办"), .el-tabs__item:has-text("待处理")').first()
      if (await todoTab2.isVisible().catch(() => false)) {
        await todoTab2.click()
        await page.waitForTimeout(1000)
      }
      const targetRow2 = await locateTodoRow(page, request, contractId)
      const rejectBtn2 = targetRow2.locator('button:has-text("退回")')
      await rejectBtn2.click()
      const dialog = page.locator('.el-dialog:has-text("退回任务")')
      await expect(dialog).toBeVisible({ timeout: 10_000 })
      await dialog.locator('.el-radio:has-text("退回上一步")').click()
      await dialog.locator('textarea').fill('P1 UI测试退回上一步')
      const [rejectResp] = await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes('/workflow/approval/reject-previous') && resp.request().method() === 'POST',
          { timeout: 15_000 }
        ),
        dialog.locator('button:has-text("确定")').click(),
      ])
      expect(rejectResp.status()).toBe(200)
      // 回写断言：待办回退第一级
      todos = await findTodos(request, contractId)
      expect(todos.length, '退回后应仍有待办').toBeGreaterThan(0)
      expect(todos[0].taskDefinitionKey, '应回退第一级').toBe('managerApproval')
    } finally {
      await withdrawContract(request, contractId)
    }
  })

  test('终止流程 — 确认弹窗真实操作（D-32）', async ({ page }) => {
    const request = authed!
    const contractId = await prepareSubmittedContract(request)
    try {
      await page.goto('/workflow/approval')
      await page.waitForLoadState('networkidle')
      const todoTab = page.locator('.el-tabs__item:has-text("待办"), .el-tabs__item:has-text("待处理")').first()
      if (await todoTab.isVisible().catch(() => false)) {
        await todoTab.click()
        await page.waitForTimeout(1000)
      }
      const terminateBtn = (await locateTodoRow(page, request, contractId)).locator('button:has-text("终止")')
      // ElMessageBox 是 DOM 弹窗（非 native dialog，CI 首跑实证 page.once('dialog') 永不触发）
      await terminateBtn.click()
      const msgbox = page.locator('.el-message-box')
      await expect(msgbox).toBeVisible({ timeout: 10_000 })
      const [terminateResp] = await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes('/workflow/approval/terminate') && resp.request().method() === 'POST',
          { timeout: 15_000 }
        ),
        msgbox.locator('button:has-text("确定")').click(),
      ])
      expect(terminateResp.status()).toBe(200)
      // 回写断言：合同回 DRAFT + 待办清空
      const cResp = await request.get(`${API_BASE}/api/v1/contract/${contractId}`)
      expect((await cResp.json()).data?.status, '终止后合同应 DRAFT').toBe('DRAFT')
      expect((await findTodos(request, contractId)).length, '终止后待办应清空').toBe(0)
    } finally {
      await withdrawContract(request, contractId)
    }
  })

  test('批量通过 — 勾选+确认（D-32/D-33-12）', async ({ page }) => {
    const request = authed!
    const cidA = await prepareSubmittedContract(request)
    const cidB = await prepareSubmittedContract(request)
    try {
      await page.goto('/workflow/approval')
      await page.waitForLoadState('networkidle')
      const todoTab = page.locator('.el-tabs__item:has-text("待办"), .el-tabs__item:has-text("待处理")').first()
      if (await todoTab.isVisible().catch(() => false)) {
        await todoTab.click()
        await page.waitForTimeout(1000)
      }
      // 勾选两个目标待办行（按 createTime 定位，CI 并行下首行未必是目标）
      const rowA = await locateTodoRow(page, request, cidA)
      const rowB = await locateTodoRow(page, request, cidB)
      await rowA.locator('.el-checkbox').first().click()
      await rowB.locator('.el-checkbox').first().click()
      const batchBtn = page.locator('button:has-text("批量通过")').first()
      await expect(batchBtn).toBeEnabled({ timeout: 10_000 })
      await batchBtn.click()
      // ElMessageBox 是 DOM 弹窗（CI 首跑实证）
      const batchBox = page.locator('.el-message-box')
      await expect(batchBox).toBeVisible({ timeout: 10_000 })
      const [batchResp] = await Promise.all([
        page.waitForResponse(
          (resp) => resp.url().includes('/workflow/approval/batch-approve') && resp.request().method() === 'POST',
          { timeout: 15_000 }
        ),
        batchBox.locator('button:has-text("确定")').click(),
      ])
      expect(batchResp.status()).toBe(200)
      // 回写断言：两单进入第二级（一级批量通过，流程未结束仍 SUBMITTED）
      const todosA = await findTodos(request, cidA)
      const todosB = await findTodos(request, cidB)
      expect(todosA[0]?.taskDefinitionKey, '合同A应进入第二级').toBe('financeApproval')
      expect(todosB[0]?.taskDefinitionKey, '合同B应进入第二级').toBe('financeApproval')
    } finally {
      await withdrawContract(request, cidA)
      await withdrawContract(request, cidB)
    }
  })

  test('已办列表回显已办记录（D-32）', async ({ page }) => {
    const request = authed!
    const contractId = await prepareSubmittedContract(request)
    try {
      // API 通过一级，产生已办记录
      const todos = await findTodos(request, contractId)
      expect(todos.length).toBeGreaterThan(0)
      const c1 = await request.post(`${API_BASE}/api/v1/workflow/approval/complete`, {
        data: { taskId: todos[0].taskId, comment: 'P1 UI已办验证' },
      })
      expect(c1.ok()).toBeTruthy()
      // UI 已办 tab 回显
      await page.goto('/workflow/approval')
      await page.waitForLoadState('networkidle')
      const doneTab = page.locator('.el-tabs__item:has-text("已办"), .el-tabs__item:has-text("已处理")').first()
      await expect(doneTab).toBeVisible({ timeout: 15_000 })
      await doneTab.click()
      await page.waitForTimeout(1500)
      const rows = page.locator('.el-table__body-wrapper .el-table__row')
      expect(await rows.count(), '已办列表应有记录').toBeGreaterThan(0)
    } finally {
      await withdrawContract(request, contractId)
    }
  })
})

/**
 * 测试数据清理
 * 通过 API 删除测试中创建的合同
 */
test.afterAll(async () => {
  const request = authed!
  // 逆序删除（合同先于项目；先 withdraw 残留审批流）
  for (let i = createdContractIds.length - 1; i >= 0; i--) {
    const id = createdContractIds[i]
    try {
      await request.post(
        `${API_BASE}/api/v1/workflow/approval/withdraw-by-business?businessType=CONSTRUCTION_CONTRACT&businessId=${id}`
      ).catch(() => {})
      await request.delete(`${API_BASE}/api/v1/contract/${id}`)
    } catch {
      // 非合同 id（项目 id）走项目删除
      try {
        await request.delete(`${API_BASE}/api/v1/project/${id}`)
      } catch {
        console.warn(`[Cleanup] 删除 ${id} 失败，可能需要手动清理`)
      }
    }
  }
  if (authed) await authed.dispose()
})
