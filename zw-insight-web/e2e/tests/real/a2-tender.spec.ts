/**
 * 真实模式 E2E：A-2 投标管理账本补测（账本全量补齐 M1，2026-08）
 *
 * @matrix A5-04 完整新增报名 / A5-09 提交成功状态流转 / A5-10 非法状态提交拦截 /
 *   A5-13 删除仅报名态（直调拦截）/ A5-14 非报名态编辑拦截（2026-08-21 缺陷#3 修复后翻负向）/
 *   A5-11 开标早于报名后端拦截（2026-08-21 缺陷#4 修复后翻负向）/ A5-12 page/size 口径抓包 /
 *   A-X6 报名提交→项目 TENDERING 联动 / A-X7 开标 isWon=1→报名/项目双 WON 联动 /
 *   A-X9 中标后项目可挂合同（项目 WON 前提钉住）/
 *   A6-01/02/04/05/07/10 证件模块真实契约闭环（2026-08-21 缺口#1 解除后翻正向）
 *
 * 实证（探测 2026-08）：
 *   - register：REGISTERED→submit→SUBMITTED；resubmit code=500「仅报名状态可提交」；
 *     DELETE code=500「仅报名状态可删除」（SUBMITTED/WON/LOST 不可删 → 每次实跑残留 1 条
 *     E2E_TEST_ SUBMITTED 报名，ownerCompany 前缀可识别，巡检兜底）；
 *     PUT 非报名态 2026-08-21 起拦截「仅报名状态可编辑」（TenderRegisterService 状态守卫）
 *   - 开标联动：POST open-bid isWon=1 → register WON + 项目 WON（联动存在，账本"无联动断言"补齐）
 *   - 证件：2026-08-21 契约对齐后前端 personName/certificateType/certificateNo/issueDate/
 *     expireDate + page/size 与后端一致；展示三态由前端基于 expireDate 派生
 *     （vitest tender-matrix A6-07），后端 Integer status 仅启用标记
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

  test('@matrix A5-10/A5-13/A5-14 SUBMITTED 态提交/删除/编辑直调全拦截', async () => {
    expect(regIdA).toBeTruthy()
    // A5-10 resubmit 拦截
    const resub = await apiJson('POST', `/api/v1/tender/register/${regIdA}/submit`)
    expect(resub.body?.code, 'SUBMITTED resubmit 应拦截').not.toBe(200)
    expect(resub.body?.message).toContain('仅报名状态可提交')
    // A5-13 DELETE 拦截
    const del = await apiJson('DELETE', `/api/v1/tender/register/${regIdA}`)
    expect(del.body?.code, 'SUBMITTED 删除应拦截').not.toBe(200)
    expect(del.body?.message).toContain('仅报名状态可删除')
    // A5-14 PUT 状态守卫拦截（2026-08-21 缺陷#3 修复后翻负向）
    const put = await apiJson('PUT', `/api/v1/tender/register/${regIdA}`, {
      projectId: carrierProjectId, ownerCompany: regOwnerA, bidMethod: 'INVITE',
      registerDate: TODAY, openDate: TODAY, depositAmount: 60000,
    })
    expect(put.body?.code, 'SUBMITTED 编辑应拦截').not.toBe(200)
    expect(put.body?.message).toContain('仅报名状态可编辑')
  })

  test('@matrix A5-11 开标日期早于报名日期后端拦截（2026-08-21 缺陷#4 修复后翻负向）', async () => {
    const cr = await apiJson('POST', '/api/v1/tender/register', {
      projectId: carrierProjectId, ownerCompany: regOwnerB, bidMethod: 'PUBLIC',
      registerDate: TODAY, openDate: '2026-01-01', depositAmount: 100,
    })
    expect(cr.body?.code, '日期倒挂应拦截').not.toBe(200)
    expect(cr.body?.message).toContain('开标日期不能早于报名日期')
    // 拦截即不落库
    const reg = await findRegister(regOwnerB)
    expect(reg, '倒挂报名不应创建').toBeUndefined()
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

test.describe('A-2 证件模块真实契约闭环（缺口#1 解除后翻正向）', () => {
  let certId = ''
  const certPerson = `${PREFIX}_持证人`
  const certType = `${PREFIX}_一级建造师`
  const certNo = `${PREFIX}_CERT_${Date.now()}`

  test('@matrix A6-01/02 真实契约创建→personName 过滤列表回显字段', async () => {
    const cr = await apiJson('POST', '/api/v1/tender/certificate/person', {
      personName: certPerson, certificateType: certType, certificateNo: certNo,
      issueDate: '2026-01-01', expireDate: '2027-01-01', status: 1,
    })
    expect(cr.body?.code, 'POST person（后端实体字段直写）').toBe(200)
    const pg = await apiJson('GET', `/api/v1/tender/certificate/person?page=1&size=10&personName=${encodeURIComponent(certPerson)}`)
    expect(pg.body?.code).toBe(200)
    const rec = (pg.body?.data?.records || []).find((r: any) => r.certificateNo === certNo)
    expect(rec, '创建后按 personName 过滤应回显').toBeTruthy()
    expect(rec.personName).toBe(certPerson)
    expect(rec.certificateType).toBe(certType)
    expect(String(rec.expireDate)).toContain('2027-01-01')
    certId = String(rec.id)
  })

  test('@matrix A6-05 编辑回写生效（PUT /person/:id → expireDate 回显）', async () => {
    expect(certId).toBeTruthy()
    const up = await apiJson('PUT', `/api/v1/tender/certificate/person/${certId}`, {
      personName: certPerson, certificateType: certType, certificateNo: certNo,
      issueDate: '2026-01-01', expireDate: '2028-01-01', status: 1,
    })
    expect(up.body?.code, 'PUT person/:id').toBe(200)
    const pg = await apiJson('GET', `/api/v1/tender/certificate/person?page=1&size=10&personName=${encodeURIComponent(certPerson)}`)
    const rec = (pg.body?.data?.records || []).find((r: any) => String(r.id) === certId)
    expect(String(rec?.expireDate), '编辑后到期日应回写').toContain('2028-01-01')
  })

  test('@matrix A6-10 page/size 分页口径生效（size=1 单页单条且 total≥1）', async () => {
    const p1 = await apiJson('GET', '/api/v1/tender/certificate/person?page=1&size=1')
    expect(p1.body?.code).toBe(200)
    expect(p1.body?.data?.records.length).toBeLessThanOrEqual(1)
    expect(p1.body?.data?.total).toBeGreaterThanOrEqual(1)
  })

  test('@matrix A6-07 派生源数据契约：status 为 Integer 启用标记，三态由前端基于 expireDate 派生', async () => {
    expect(certId).toBeTruthy()
    const pg = await apiJson('GET', `/api/v1/tender/certificate/person?page=1&size=10&personName=${encodeURIComponent(certPerson)}`)
    const rec = (pg.body?.data?.records || []).find((r: any) => String(r.id) === certId)
    expect(typeof rec.status, 'status 为数字启用标记非展示枚举').toBe('number')
    expect(rec.expireDate, '前端派生三态的数据源应存在').toBeTruthy()
  })

  test('@matrix A6-04 删除闭环（DELETE /person/:id → 列表不再回显）', async () => {
    expect(certId).toBeTruthy()
    const del = await apiJson('DELETE', `/api/v1/tender/certificate/person/${certId}`)
    expect(del.body?.code, 'DELETE person/:id').toBe(200)
    const pg = await apiJson('GET', `/api/v1/tender/certificate/person?page=1&size=50&personName=${encodeURIComponent(certPerson)}`)
    expect((pg.body?.data?.records || []).find((r: any) => String(r.id) === certId), '删除后不再回显').toBeUndefined()
    certId = ''
  })
})
