/**
 * 真实模式 E2E：A-2 投标管理账本补测（账本全量补齐 M1，2026-08）
 *
 * @matrix A5-04 完整新增报名 / A5-09 提交成功状态流转 / A5-10 非法状态提交拦截 /
 *   A5-13 删除仅报名态（直调拦截）/ A5-14 编辑任意状态可入（现状放行钉住）/
 *   A5-11 开标早于报名前后端均不拦截（现状钉住）/ A5-12 page/size 口径抓包 /
 *   A-X6 报名提交→项目 TENDERING 联动 / A-X7 开标 isWon=1→报名/项目双 WON 联动 /
 *   A-X9 中标后项目可挂合同（项目 WON 前提钉住）/
 *   A6-01~10 证件模块 API-GAP 现状钉住（前后端字段/分页参数/状态枚举全脱节）
 *
 * 实证（探测 2026-08）：
 *   - register：REGISTERED→submit→SUBMITTED；resubmit code=500「仅报名状态可提交」；
 *     DELETE code=500「仅报名状态可删除」（SUBMITTED/WON/LOST 不可删 → 每次实跑残留 1 条
 *     E2E_TEST_ SUBMITTED 报名，ownerCompany 前缀可识别，巡检兜底）；PUT 无状态守卫放行
 *   - 开标联动：POST open-bid isWon=1 → register WON + 项目 WON（联动存在，账本"无联动断言"补齐）
 *   - 证件：前端 certName/certNo/holderName/expiryDate vs 后端 certificateType/certificateNo/
 *     personName/expireDate 字段脱节；前端 pageNum/pageSize vs 后端 page/size；
 *     前端 status 枚举 VALID/EXPIRING/EXPIRED vs 后端 Integer 1/0 且无到期计算 → API-GAP 受阻
 *
 * 纯前端守卫用例（A5-01/02/03/05/07/08、A6-02/03/08/09/10）见
 * src/__tests__/tender-matrix.component.test.ts。
 */
import { test, expect } from '@playwright/test'
import { authedApiContext, e2ePrefix, todayStr, type AuthedContext } from './real-helper'

test.describe.configure({ mode: 'serial' })

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'
const PREFIX = e2ePrefix()
const TODAY = todayStr()

let authed: AuthedContext | null = null
let carrierProjectId = ''
const carrierProjectName = `${PREFIX}_投标项目`
const regOwnerA = `${PREFIX}_业主A`   // 主链报名（REGISTERED→SUBMITTED，不可删残留）
const regOwnerB = `${PREFIX}_业主B`   // 倒挂日期报名（REGISTERED，可清理）
let regIdA = ''

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

/** 按 ownerCompany 定位报名记录（创建响应 R<Void> 无 id，探测实证） */
async function findRegister(ownerCompany: string) {
  const pg = await apiJson('GET', `/api/v1/tender/register/page?page=1&size=50&projectId=${carrierProjectId}`)
  return (pg.body?.data?.records || []).find((r: any) => r.ownerCompany === ownerCompany)
}

test.beforeAll(async () => {
  authed = await authedApiContext()
  const cr = await apiJson('POST', '/api/v1/project', { projectName: carrierProjectName, projectNature: '新建', projectType: '市政工程' })
  expect(cr.body?.code, '创建报名承载项目').toBe(200)
  const pg = await apiJson('GET', `/api/v1/project/page?pageNum=1&pageSize=5&projectName=${encodeURIComponent(carrierProjectName)}`)
  carrierProjectId = String(pg.body?.data?.records?.[0]?.id || '')
  expect(carrierProjectId, '承载项目应可定位').toBeTruthy()
})

test.afterAll(async () => {
  if (!authed) return
  // 报名残留：SUBMITTED/WON 不可删（实证）；REGISTERED 尝试清理
  const regB = await findRegister(regOwnerB).catch(() => null)
  if (regB) await apiJson('DELETE', `/api/v1/tender/register/${regB.id}`).catch(() => {})
  // 开标记录按 registerId 可查可删
  for (const owner of [regOwnerA, regOwnerB]) {
    const reg = await findRegister(owner).catch(() => null)
    if (reg) {
      const ob = await apiJson('GET', `/api/v1/tender/open-bid/${reg.id}`).catch(() => null)
      if (ob?.body?.data?.id) await apiJson('DELETE', `/api/v1/tender/open-bid/${ob.body.data.id}`).catch(() => {})
    }
  }
  await authed.delete(`${API_BASE}/api/v1/project/${carrierProjectId}`).catch(() => {})
  await authed.dispose()
})

async function confirmMessageBox(page: any) {
  const box = page.locator('.el-message-box:visible')
  await expect(box).toBeVisible({ timeout: 10_000 })
  await box.locator('button:has-text("确定")').click()
}

test.describe('A-2 投标报名主链', () => {
  test('@matrix A5-04/A5-12 完整新增报名（UI，page/size 口径抓包）', async ({ page }) => {
    test.setTimeout(180_000)
    await page.goto('/tender/register')
    await page.waitForSelector('button:has-text("新增投标报名")', { timeout: 30_000 })
    // A5-12：列表请求口径 page/size（其余模块 pageNum/pageSize）抓包钉住
    const [pageResp] = await Promise.all([
      page.waitForResponse((r) => r.url().includes('/v1/tender/register/page'), { timeout: 20_000 }),
      page.locator('button:has-text("搜索")').click(),
    ])
    expect(pageResp.url(), '列表请求应带 page/size 参数').toMatch(/[?&]page=\d+/)
    expect(pageResp.url()).toMatch(/[?&]size=\d+/)

    await page.locator('button:has-text("新增投标报名")').click()
    const dialog = page.locator('.el-dialog:has-text("新增投标报名")')
    await expect(dialog).toBeVisible({ timeout: 10_000 })
    // 项目（ProjectSelector remote：输入触发 GET /v1/project/list）
    const prjSelect = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("项目")) .el-select')
    await prjSelect.click()
    await prjSelect.locator('input').first().fill(carrierProjectName)
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 20_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item').filter({ hasText: carrierProjectName }).first().click()
    await page.waitForTimeout(500)
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("业主单位")) input').fill(regOwnerA)
    // 招标方式
    const bidSelect = dialog.locator('.el-form-item:has(.el-form-item__label:text-is("招标方式")) .el-select')
    await bidSelect.click()
    await page.waitForSelector('.el-select-dropdown:visible .el-select-dropdown__item', { timeout: 10_000 })
    await page.locator('.el-select-dropdown:visible .el-select-dropdown__item:has-text("公开招标")').first().click()
    // 日期（el-date-picker 输入直填）
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("报名日期")) input').fill(TODAY)
    await page.keyboard.press('Enter')
    await dialog.locator('.el-form-item:has(.el-form-item__label:text-is("开标日期")) input').fill(TODAY)
    await page.keyboard.press('Enter')
    // 保证金
    await dialog.locator('.el-form-item:has(.el-form-item__label:has-text("保证金")) input').first().fill('50000')

    const [createResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes('/v1/tender/register') && !r.url().includes('/page') && r.request().method() === 'POST' && !r.url().includes('/submit'),
        { timeout: 20_000 }
      ),
      dialog.locator('button:has-text("确定")').click(),
    ])
    expect(createResp.status()).toBe(200)
    expect((await createResp.json()).code, 'POST /v1/tender/register').toBe(200)
    // 列表出现 REGISTERED 行（标签「报名中」）
    await page.locator('button:has-text("搜索")').click()
    const row = page.locator('.el-table__row', { hasText: regOwnerA })
    await expect(row).toBeVisible({ timeout: 15_000 })
    await expect(row.locator('.el-tag')).toContainText('报名中')
    const reg = await findRegister(regOwnerA)
    expect(reg, '报名记录应可按业主单位定位').toBeTruthy()
    expect(reg.status).toBe('REGISTERED')
    regIdA = String(reg.id)
  })

  test('@matrix A5-09/A-X6 提交成功 SUBMITTED + 项目联动 TENDERING', async ({ page }) => {
    test.setTimeout(120_000)
    await page.goto('/tender/register')
    await page.waitForSelector('.el-table', { timeout: 30_000 })
    const row = page.locator('.el-table__row', { hasText: regOwnerA })
    await expect(row).toBeVisible({ timeout: 20_000 })
    const [submitResp] = await Promise.all([
      page.waitForResponse(
        (r) => r.url().includes(`/v1/tender/register/${regIdA}/submit`) && r.request().method() === 'POST',
        { timeout: 20_000 }
      ),
      (async () => {
        await row.locator('button:has-text("提交")').click()
        await confirmMessageBox(page)
      })(),
    ])
    expect(submitResp.status()).toBe(200)
    expect((await submitResp.json()).code, 'POST submit').toBe(200)
    await expect(row.locator('.el-tag')).toContainText('已投标', { timeout: 15_000 })
    await expect(row.locator('button:has-text("提交")')).toHaveCount(0)
    await expect(row.locator('button:has-text("删除")')).toHaveCount(0)
    // A-X6 联动：承载项目状态 → TENDERING（探测实证）
    const proj = await apiJson('GET', `/api/v1/project/${carrierProjectId}`)
    expect(proj.body?.data?.status, '报名提交后项目应联动 TENDERING').toBe('TENDERING')
  })

  test('@matrix A5-10/A5-13/A5-14 SUBMITTED 态直调拦截与放行现状钉住', async () => {
    expect(regIdA).toBeTruthy()
    // A5-10 resubmit 拦截
    const resub = await apiJson('POST', `/api/v1/tender/register/${regIdA}/submit`)
    expect(resub.body?.code, 'SUBMITTED resubmit 应拦截').not.toBe(200)
    expect(resub.body?.message).toContain('仅报名状态可提交')
    // A5-13 DELETE 拦截
    const del = await apiJson('DELETE', `/api/v1/tender/register/${regIdA}`)
    expect(del.body?.code, 'SUBMITTED 删除应拦截').not.toBe(200)
    expect(del.body?.message).toContain('仅报名状态可删除')
    // A5-14 PUT 现状放行（账本预期"验证后端状态校验"→实证无校验，钉住）
    const put = await apiJson('PUT', `/api/v1/tender/register/${regIdA}`, {
      projectId: carrierProjectId, ownerCompany: regOwnerA, bidMethod: 'INVITE',
      registerDate: TODAY, openDate: TODAY, depositAmount: 60000,
    })
    expect(put.body?.code, '现状钉住：SUBMITTED 编辑放行（无状态守卫）').toBe(200)
  })

  test('@matrix A5-11 开标日期早于报名日期前后端均不拦截（现状钉住）', async () => {
    // 前端无校验（vitest tender-matrix 源码钉住）；后端实证接受
    const cr = await apiJson('POST', '/api/v1/tender/register', {
      projectId: carrierProjectId, ownerCompany: regOwnerB, bidMethod: 'PUBLIC',
      registerDate: TODAY, openDate: '2026-01-01', depositAmount: 100,
    })
    expect(cr.body?.code, '现状钉住：日期倒挂后端接受').toBe(200)
    const reg = await findRegister(regOwnerB)
    expect(reg, '倒挂报名应创建成功').toBeTruthy()
    expect(reg.status).toBe('REGISTERED')
    // REGISTERED 可删 → 当场清理
    const del = await apiJson('DELETE', `/api/v1/tender/register/${reg.id}`)
    expect(del.body?.code, 'REGISTERED 删除').toBe(200)
  })

  test('@matrix A-X7/A-X9 开标 isWon=1 → 报名 WON + 项目 WON（合同承接前提）', async () => {
    expect(regIdA).toBeTruthy()
    const ob = await apiJson('POST', '/api/v1/tender/open-bid', {
      registerId: regIdA, projectId: carrierProjectId, isWon: 1, winInfo: `${PREFIX}_中标`, status: 'WON',
    })
    expect(ob.body?.code, 'POST open-bid').toBe(200)
    const reg = await apiJson('GET', `/api/v1/tender/register/${regIdA}`)
    expect(reg.body?.data?.status, '开标中标后报名应联动 WON').toBe('WON')
    const proj = await apiJson('GET', `/api/v1/project/${carrierProjectId}`)
    expect(proj.body?.data?.status, '开标中标后项目应联动 WON').toBe('WON')
    // A-X9：WON 项目进入合同承接链路的前提钉住（合同创建属 A-3 里程碑覆盖）
    expect(['WON']).toContain(proj.body?.data?.status)
  })
})

test.describe('A-2 证件模块 API-GAP 现状钉住', () => {
  test('@matrix A6-01/02/04/07 前后端契约脱节实证（字段/分页参数/状态枚举）', async () => {
    // ① 前端字段名创建 → 后端全部忽略（certName/certNo/holderName 非实体字段，Jackson 静默丢弃）
    // 创建前快照 id 集合，供清理差集定位（后端丢弃全部前端字段，记录不可按内容辨识）
    const before = await apiJson('GET', '/api/v1/tender/certificate/person?page=1&size=200')
    const beforeIds = new Set(((before.body?.data?.records) || []).map((r: any) => String(r.id)))
    const certName = `${PREFIX}_证件`
    const cr = await apiJson('POST', '/api/v1/tender/certificate/person', {
      certName, certNo: `GAP${Date.now()}`, holderName: '前端字段', issueDate: '2026-01-01', expiryDate: '2027-01-01', issueOrgan: 'API-GAP 探测',
    })
    expect(cr.body?.code, 'POST 仍 200（字段静默丢弃）').toBe(200)
    // ② 前端 certName 筛选参数不被识别（后端仅认 personName/certificateType）
    const filtered = await apiJson('GET', `/api/v1/tender/certificate/person?page=1&size=10&certName=${encodeURIComponent(certName)}`)
    expect(filtered.body?.code).toBe(200)
    const recs = filtered.body?.data?.records || []
    const mine = recs.find((r: any) => r.certName === certName)
    expect(mine, '现状钉住：certName 非后端字段，记录无名称').toBeUndefined()
    // ③ 分页参数口径：前端 pageNum/pageSize 与后端 page/size 命名不一致（契约脱节，
    //    CertificateController 源码实证 @RequestParam page/size）；后端实体无 certName
    //    字段 → 前端筛选/列表字段全失效（数据量波动不宣作断言依据）
    const p1 = await apiJson('GET', '/api/v1/tender/certificate/person?pageNum=1&pageSize=1')
    expect(p1.body?.code, 'pageNum/pageSize 口径请求仍 200').toBe(200)
    // ④ status 为 Integer 1/0，前端 VALID/EXPIRING/EXPIRED 枚举永不匹配 → 列表恒显示「已过期」
    const p1recs = p1.body?.data?.records || []
    const all = await apiJson('GET', '/api/v1/tender/certificate/person?page=1&size=50')
    const allRecs = all.body?.data?.records || []
    expect(allRecs.length, '证件表应有记录（含演示数据）').toBeGreaterThan(0)
    for (const r of allRecs) {
      expect(typeof r.status, '现状钉住：status 为数字非枚举串').toBe('number')
      expect(r.certName, '现状钉住：后端无 certName 字段').toBeUndefined()
    }
    // 清理本轮创建的空记录：差集定位（before 快照之外的新 id），绝不触碰演示数据
    const after = await apiJson('GET', '/api/v1/tender/certificate/person?page=1&size=200')
    for (const r of ((after.body?.data?.records) || [])) {
      if (!beforeIds.has(String(r.id))) {
        await apiJson('DELETE', `/api/v1/tender/certificate/person/${r.id}`)
      }
    }
    expect(p1recs.length).toBeLessThanOrEqual(allRecs.length)
  })
})
