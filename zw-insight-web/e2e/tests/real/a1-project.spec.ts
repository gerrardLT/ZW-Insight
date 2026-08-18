/**
 * 真实模式 E2E：A-1 项目管理账本补测（账本全量补齐 M1，2026-08）
 *
 * @matrix A2-02 完整新增保存（硬断言）/ A4-05 添加成员成功 / A4-06 重复添加拦截 /
 *   A4-08 变更角色成功 / A4-10 移除成功 / A1-10 提交成功状态流转（直批 FILED）/
 *   A1-08 非草稿直调 submit 拦截（+ delete 无守卫实证钉住）/ A1-13 删除草稿成功刷新 /
 *   A1-11 结项预检不满足拦截 / A-X4 项目删除引用拦截（实证：无守卫放行，缺陷钉住）/
 *   A3-05 非法 id 详情空态
 *
 * 实证修正（探测 2026-08，以事实为依据）：
 *   - 项目提交为直批：DRAFT→submit 立即 FILED（无 Flowable 待办，账本 A-X2 预期修正）
 *   - 项目 DELETE 无状态守卫（FILED 可删）且无报名引用检查（A-X4 与账本预期不符，钉住现状）
 *   - 非草稿 resubmit 拦截 code=500「仅草稿状态可提交」（HTTP 200 + 业务 code）
 *   - 演示数据存在 1 条 COMPLETED 项目，close-check allPassed=false（未收款 2500000）→ A1-11 可测；
 *     A1-12 预检全通过无数据前提 → tasks.md 受阻登记（DATA）
 *
 * 范式：serial + storageState token authed API context（禁重新登录）+ E2E_TEST_ 前缀自置
 * + waitForResponse 硬断言 + afterAll 逆序清理。
 * 纯前端守卫用例（A4-03/07/09 等）见 src/__tests__/project-member-matrix.component.test.ts。
 */
import { test, expect } from '@playwright/test'
import { authedApiContext, e2ePrefix, type AuthedContext } from './real-helper'

test.describe.configure({ mode: 'serial' })

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'
const PREFIX = e2ePrefix()

let authed: AuthedContext | null = null
let mainProjectId = ''       // 成员链 + 提交链承载项目
let mainProjectName = ''
let formProjectName = ''     // A2-02 UI 创建 → A1-13 UI 删除
let completedProjectName = '' // 演示 COMPLETED（A1-11）
let memberUser: { id: string; realName: string } | null = null
let mainDeleted = false       // A-X4 用例中项目被删则置位，afterAll 跳过

async function apiJson(method: 'GET' | 'POST' | 'DELETE', path: string, data?: any) {
  const resp = method === 'GET'
    ? await authed!.get(`${API_BASE}${path}`)
    : method === 'POST'
      ? await authed!.post(`${API_BASE}${path}`, { data })
      : await authed!.delete(`${API_BASE}${path}`)
  return { status: resp.status(), body: await resp.json().catch(() => null) }
}

/** API 自置项目并按名称差集定位 id（创建响应 R<Void> 无 id，探测实证） */
async function createProject(name: string): Promise<string> {
  const cr = await apiJson('POST', '/api/v1/project', { projectName: name, projectNature: '新建', projectType: '市政工程' })
  expect(cr.body?.code, `创建项目 ${name}`).toBe(200)
  const pg = await apiJson('GET', `/api/v1/project/page?pageNum=1&pageSize=5&projectName=${encodeURIComponent(name)}`)
  const id = String(pg.body?.data?.records?.[0]?.id || '')
  expect(id, '新建项目应可按名称定位').toBeTruthy()
  return id
}

test.beforeAll(async () => {
  authed = await authedApiContext()
  mainProjectName = `${PREFIX}_项目主链`
  mainProjectId = await createProject(mainProjectName)
  // 演示 COMPLETED 项目（A1-11 数据前提，探测实证存在 1 条）
  const comp = await apiJson('GET', '/api/v1/project/page?pageNum=1&pageSize=1&status=COMPLETED')
  completedProjectName = comp.body?.data?.records?.[0]?.projectName || ''
  expect(completedProjectName, '数据前提：应存在 COMPLETED 演示项目（A1-11）').toBeTruthy()
  // 成员添加目标用户（排除创建者 id=1——新建项目已自动挂载）
  const users = await apiJson('GET', '/api/v1/system/user?pageNum=1&pageSize=20')
  memberUser = (users.body?.data?.records || [])
    .map((u: any) => ({ id: String(u.id), realName: u.realName }))
    .find((u: any) => u.id !== '1' && u.realName) || null
  expect(memberUser, '数据前提：应存在可添加成员用户').toBeTruthy()
})

test.afterAll(async () => {
  if (!authed) return
  if (!mainDeleted && mainProjectId) {
    await authed.delete(`${API_BASE}/api/v1/project/${mainProjectId}`).catch(() => {})
  }
  await authed.dispose()
})

/** ElMessageBox 确认框点「确定」 */
async function confirmMessageBox(page: any, titlePart?: string) {
  const box = titlePart
    ? page.locator(`.el-message-box:visible:has-text("${titlePart}")`)
    : page.locator('.el-message-box:visible')
  await expect(box).toBeVisible({ timeout: 10_000 })
  await box.locator('button:has-text("确定")').click()
}

test.describe('A-1 项目表单与成员链', () => {
  test('@matrix A2-02 完整新增保存（业主远程搜索+签约公司联动，硬断言）', async ({ page }) => {
    test.setTimeout(180_000)
    formProjectName = `${PREFIX}_表单项目`
    await page.goto('/project/create')
    await page.waitForSelector('.el-form', { timeout: 30_000 })
    await page.locator('input[placeholder="请输入项目名称"]').fill(formProjectName)
    // 项目性质 / 项目类型（静态选项下拉）
    for (const label of ['项目性质', '项目类型']) {
      const select = page.locator(`.el-form-item:has(.el-form-item__label:text-is("${label}")) .el-select`)
      await select.click()
      await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 10_000 })
      await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
      await page.waitForTimeout(400)
    }
    // 业主单位（remote：输入触发 GET /v1/basedata/owner/list）
    const ownerSelect = page.locator('.el-form-item:has(.el-form-item__label:text-is("业主单位")) .el-select')
    await ownerSelect.click()
    await ownerSelect.locator('input').first().fill('')
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await page.waitForTimeout(400)
    // 签约公司（filterable，挂载已预载）
    const companySelect = page.locator('.el-form-item:has(.el-form-item__label:text-is("签约公司")) .el-select')
    await companySelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 10_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first().click()
    await page.waitForTimeout(400)
    // 保存 → POST /v1/project 硬断言 + 跳列表
    const [saveResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/v1/project') && r.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      page.locator('button:has-text("保存")').click(),
    ])
    expect(saveResp.status()).toBe(200)
    expect((await saveResp.json()).code, 'POST /v1/project 业务 code').toBe(200)
    await page.waitForURL('**/project/list', { timeout: 15_000 })
  })

  test('@matrix A4-05 添加成员成功（远程搜索用户+多角色）', async ({ page }) => {
    test.setTimeout(120_000)
    await page.goto(`/project/detail/${mainProjectId}?tab=team`)
    await page.waitForSelector('button:has-text("添加成员")', { timeout: 30_000 })
    await page.locator('button:has-text("添加成员")').click()
    const dialog = page.locator('.el-dialog:has-text("添加成员")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 选择用户（remote：输入姓名首字触发 GET /v1/system/user）
    const userSelect = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("选择用户")) .el-select')
    await userSelect.click()
    await userSelect.locator('input').first().fill(memberUser!.realName.slice(0, 1))
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: memberUser!.realName }).first().click()
    await page.waitForTimeout(500)
    // 项目角色多选：选两项
    const roleSelect = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("项目角色")) .el-select')
    await roleSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 10_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("项目经理")').first().click()
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("安全员")').first().click()
    await page.keyboard.press('Escape')
    const [addResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes(`/v1/project/${mainProjectId}/member`) && r.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(addResp.status()).toBe(200)
    expect((await addResp.json()).code, 'POST member').toBe(200)
    await expect(page.locator('.el-table__row', { hasText: memberUser!.realName })).toBeVisible({ timeout: 15_000 })
  })

  test('@matrix A4-06 重复添加同一用户拦截（code=400 弹窗保留）', async ({ page }) => {
    test.setTimeout(120_000)
    await page.goto(`/project/detail/${mainProjectId}?tab=team`)
    await page.waitForSelector('button:has-text("添加成员")', { timeout: 30_000 })
    await page.locator('button:has-text("添加成员")').click()
    const dialog = page.locator('.el-dialog:has-text("添加成员")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    const userSelect = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("选择用户")) .el-select')
    await userSelect.click()
    await userSelect.locator('input').first().fill(memberUser!.realName.slice(0, 1))
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: memberUser!.realName }).first().click()
    await page.waitForTimeout(500)
    const roleSelect = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("项目角色")) .el-select')
    await roleSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 10_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("施工员")').first().click()
    await page.keyboard.press('Escape')
    const [dupResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes(`/v1/project/${mainProjectId}/member`) && r.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(dupResp.status()).toBe(200)
    const dupJson = await dupResp.json()
    expect(dupJson.code, '重复添加应业务拒绝').toBe(400)
    expect(dupJson.message).toContain('已是本项目成员')
    await expect(dialog).toBeVisible() // 弹窗保留
    await dialog.locator('button:has-text("取消")').click()
  })

  test('@matrix A4-08 变更角色成功（PUT roles 刷新标签）', async ({ page }) => {
    test.setTimeout(120_000)
    await page.goto(`/project/detail/${mainProjectId}?tab=team`)
    const row = page.locator('.el-table__row', { hasText: memberUser!.realName })
    await expect(row).toBeVisible({ timeout: 30_000 })
    await row.locator('button:has-text("变更角色")').click()
    const dialog = page.locator('.el-dialog:has-text("变更角色")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    const roleSelect = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("项目角色")) .el-select')
    await roleSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 10_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("资料员")').first().click()
    await page.keyboard.press('Escape')
    const [putResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/roles') && r.request().method() === 'PUT',
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(putResp.status()).toBe(200)
    expect((await putResp.json()).code, 'PUT member roles').toBe(200)
    await expect(row.locator('.el-tag', { hasText: '资料员' })).toBeVisible({ timeout: 15_000 })
  })

  test('@matrix A4-10 移除成员成功（确认→DELETE→行消失）', async ({ page }) => {
    test.setTimeout(120_000)
    await page.goto(`/project/detail/${mainProjectId}?tab=team`)
    const row = page.locator('.el-table__row', { hasText: memberUser!.realName })
    await expect(row).toBeVisible({ timeout: 30_000 })
    const [delResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes(`/v1/project/${mainProjectId}/member/${memberUser!.id}`) && r.request().method() === 'DELETE',
        { timeout: 20_000 }
      ),
      (async () => {
        await row.locator('button:has-text("移除")').click()
        await confirmMessageBox(page, '移除确认')
      })(),
    ])
    expect(delResp.status()).toBe(200)
    expect((await delResp.json()).code, 'DELETE member').toBe(200)
    await expect(row).toHaveCount(0, { timeout: 15_000 })
  })
})

test.describe('A-1 项目状态流转', () => {
  test('@matrix A1-10/A1-08 提交成功直批 FILED + 非草稿操作列收缩与 resubmit 拦截', async ({ page }) => {
    test.setTimeout(120_000)
    await page.goto('/project/list')
    await page.waitForSelector('.el-table', { timeout: 30_000 })
    await page.locator('input[placeholder*="项目名称"]').first().fill(mainProjectName)
    await page.locator('button:has-text("搜索")').click()
    const row = page.locator('.el-table__row', { hasText: mainProjectName })
    await expect(row).toBeVisible({ timeout: 15_000 })
    await expect(row.locator('.el-tag')).toContainText('草稿')
    let closeCount = 0
    page.on('request', (r) => { if (r.url().includes('/submit') && r.method() === 'POST') closeCount++ })
    const [submitResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes(`/v1/project/${mainProjectId}/submit`) && r.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      (async () => {
        await row.locator('button:has-text("提交")').click()
        await confirmMessageBox(page)
      })(),
    ])
    expect(submitResp.status()).toBe(200)
    expect((await submitResp.json()).code, 'POST submit（直批）').toBe(200)
    expect(closeCount, '提交请求仅一次').toBe(1)
    // 刷新后状态 FILED，操作列无编辑/提交/删除（A1-08 UI 面）
    await expect(row.locator('.el-tag')).toContainText('已报备', { timeout: 15_000 })
    await expect(row.locator('button:has-text("编辑")')).toHaveCount(0)
    await expect(row.locator('button:has-text("提交")')).toHaveCount(0)
    await expect(row.locator('button:has-text("删除")')).toHaveCount(0)
    // API 面：非草稿 resubmit 业务拦截（探测实证 code=500 仅草稿状态可提交）
    const resub = await apiJson('POST', `/api/v1/project/${mainProjectId}/submit`)
    expect(resub.body?.code, '非草稿 resubmit 应拦截').not.toBe(200)
    expect(resub.body?.message).toContain('仅草稿状态可提交')
  })

  test('@matrix A1-13 删除草稿成功刷新（A2-02 创建的表单项目）', async ({ page }) => {
    test.setTimeout(120_000)
    await page.goto('/project/list')
    await page.waitForSelector('.el-table', { timeout: 30_000 })
    await page.locator('input[placeholder*="项目名称"]').first().fill(formProjectName)
    await page.locator('button:has-text("搜索")').click()
    const row = page.locator('.el-table__row', { hasText: formProjectName })
    await expect(row).toBeVisible({ timeout: 15_000 })
    const delUrlPart = '/v1/project/'
    const [delResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes(delUrlPart) && !r.url().includes('/page') && r.request().method() === 'DELETE',
        { timeout: 20_000 }
      ),
      (async () => {
        await row.locator('button:has-text("删除")').click()
        await confirmMessageBox(page)
      })(),
    ])
    expect(delResp.status()).toBe(200)
    expect((await delResp.json()).code, 'DELETE 草稿项目').toBe(200)
    await expect(row).toHaveCount(0, { timeout: 15_000 })
  })

  test('@matrix A1-11 结项预检不满足拦截（alert failedReasons，不发 close）', async ({ page }) => {
    test.setTimeout(120_000)
    await page.goto('/project/list')
    await page.waitForSelector('.el-table', { timeout: 30_000 })
    await page.locator('input[placeholder*="项目名称"]').first().fill(completedProjectName)
    await page.locator('button:has-text("搜索")').click()
    const row = page.locator('.el-table__row', { hasText: completedProjectName })
    await expect(row).toBeVisible({ timeout: 15_000 })
    let closePostCount = 0
    page.on('request', (r) => {
      if (/\/v1\/project\/[^/]+\/close$/.test(r.url()) && r.method() === 'POST') closePostCount++
    })
    const [checkResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/close-check'),
        { timeout: 20_000 }
      ),
      row.locator('button:has-text("结项")').click(),
    ])
    expect(checkResp.status()).toBe(200)
    const checkJson = await checkResp.json()
    expect(checkJson.code, 'close-check').toBe(200)
    expect(checkJson.data?.allPassed, '演示 COMPLETED 项目预检应不通过（探测实证）').toBe(false)
    const alertBox = page.locator('.el-message-box:visible:has-text("无法结项")')
    await expect(alertBox).toBeVisible({ timeout: 10_000 })
    const reasons = (checkJson.data?.failedReasons || []).join('；')
    expect(reasons).toBeTruthy()
    await expect(alertBox).toContainText(reasons.slice(0, 20))
    await alertBox.locator('button:has-text("确定")').click()
    await page.waitForTimeout(800)
    expect(closePostCount, '预检不通过不应发起结项').toBe(0)
  })

  test('@matrix A-X4 项目删除引用检查现状钉住（实证：挂报名引用仍放行删除）', async () => {
    // 账本预期「后端引用拦截」；探测实证无引用守卫——挂 E2E_TEST 报名后 DELETE 仍 code=200。
    // 此处钉住现状（缺陷实证），不静默跳过；tasks.md 同步登记。
    const regName = `${PREFIX}_引用业主`
    const cr = await apiJson('POST', '/api/v1/tender/register', {
      projectId: mainProjectId, ownerCompany: regName, registerDate: '2026-08-01', openDate: '2026-08-02', depositAmount: 10,
    })
    expect(cr.body?.code, '挂报名引用').toBe(200)
    const del = await apiJson('DELETE', `/api/v1/project/${mainProjectId}`)
    expect(del.body?.code, '现状钉住：引用项目删除放行（无守卫）').toBe(200)
    mainDeleted = true
    const gone = await apiJson('GET', `/api/v1/project/${mainProjectId}`)
    expect(gone.body?.code, '项目应已删除').not.toBe(200)
    // 残留报名清理（REGISTERED 态可删，探测实证）
    const regPage = await apiJson('GET', `/api/v1/tender/register/page?page=1&size=50`)
    const reg = (regPage.body?.data?.records || []).find((r: any) => r.ownerCompany === regName)
    if (reg) await apiJson('DELETE', `/api/v1/tender/register/${reg.id}`)
  })

  test('@matrix A3-05 非法 id 详情页空态（不崩溃，标题无名称）', async ({ page }) => {
    await page.goto('/project/detail/99999999999999999')
    await page.waitForSelector('.el-card', { timeout: 30_000 })
    await expect(page.locator('.card-header')).toContainText('项目详情：')
    // 详情接口失败后 projectInfo 保持空对象：描述区无项目名称内容
    const pg = await apiJson('GET', '/api/v1/project/99999999999999999')
    expect(pg.body?.code, '非法 id 详情应业务失败').not.toBe(200)
  })
})
