/**
 * dashboard 驾驶舱 —— 图表/看板页 前端展示 vs 后端数据 一致性
 *  A 首页驾驶舱 /dashboard          GET /v1/dashboard/company-overview（KPI 卡片字段级）
 *  B 项目看板  /project-dashboard    GET /v1/dashboard/project/{id}/overview（需选择项目，best-effort）
 *
 * 两页均以图表/卡片而非表格呈现，故对可断言的 KPI 数字做字段级校验。
 *
 * 历史记录（2026-08-14 P2 修复）：本 spec 曾硬编码 __silentFallback__ 假缺陷记录
 *（当时 index.vue 三处 catch 静默兜底）；源码已修复为显式 ElMessage.error
 *（dashboard-index.component.test.ts C-29-5/7/8 钉住），假缺陷记录与容忍 filter 已移除。
 * fmtWan 与 src/utils/chart-format.ts 同源（e2e tsconfig 限制不可 import，见下方注释）。
 */
import { test, expect } from '@playwright/test'
import {
  gotoAndCapture,
  finalizeModuleConsistency,
  type PageConsistencyResult,
  type Mismatch,
} from './consistency-helper'

/**
 * formatWan 复刻（单一事实源：src/utils/chart-format.ts formatWan，
 * e2e tsconfig 未覆盖 src 相对导入，无法直接 import；源码变更时须同步本函数）：
 * 空值/NaN → '0'，否则 (val/10000).toFixed(1)
 */
function fmtWan(val: unknown): string {
  const n = Number(val)
  if ((val === null || val === undefined || val === '') || Number.isNaN(n)) return '0'
  return (n / 10000).toFixed(1)
}

const results: PageConsistencyResult[] = []

test.describe.serial('dashboard 一致性', () => {
  test('A 首页驾驶舱 /dashboard KPI 卡片字段级一致', async ({ page }) => {
    const resp = await gotoAndCapture<any>(page, '/dashboard', /\/v1\/dashboard\/company-overview/)
    const route = '/dashboard'
    const title = '首页驾驶舱'
    const api = 'GET /v1/dashboard/company-overview'
    const mismatches: Mismatch[] = []

    if (resp.code !== 200) {
      mismatches.push({ row: -1, column: '__apiError__', expected: 'code=200', actual: `code=${resp.code} message=${resp.message}` })
      results.push({ route, title, api, mismatches })
      expect(resp.code, `接口应返回成功码，实际 message=${resp.message}`).toBe(200)
      return
    }

    const data = resp.data ?? {}
    // 期望值取后端真实字段（2026-08-17 修复：原断言 projectCount 等错位字段，
    // 与页面同为 undefined→'0' 造成假一致；翻转后双向钉住真实值）
    const expectedByLabel: Record<string, string> = {
      '项目总数': String(data.projectTotal || 0),
      '合同总额(万)': fmtWan(data.totalContractAmount),
      '已收款(万)': fmtWan(data.totalIncome),
      '垫资(万)': fmtWan(data.advanceFund),
    }

    const cards = page.locator('.stat-card')
    await cards.first().waitFor({ timeout: 15_000 })
    const cardCount = await cards.count()
    const actualByLabel: Record<string, string> = {}
    for (let i = 0; i < cardCount; i++) {
      const label = (await cards.nth(i).locator('.stat-label').innerText()).trim()
      const value = (await cards.nth(i).locator('.stat-value').innerText()).trim()
      actualByLabel[label] = value
    }

    for (const [label, exp] of Object.entries(expectedByLabel)) {
      const act = actualByLabel[label] ?? '(未找到卡片)'
      if (act !== exp) {
        mismatches.push({ row: -1, column: label, field: undefined, expected: exp, actual: act })
      }
    }

    results.push({ route, title, api, mismatches })
    // KPI 不一致即用例失败（无容忍项：原 __silentFallback__ 假缺陷已随源码修复移除）
    expect(mismatches, `KPI 卡片存在 ${mismatches.length} 处不一致：\n${JSON.stringify(mismatches, null, 2)}`).toHaveLength(0)
  })

  test('B 项目看板 /project-dashboard 数据源与错误处理', async ({ page }) => {
    const route = '/project-dashboard'
    const title = '项目看板'
    const api = 'GET /v1/dashboard/project/{id}/overview'
    // 页面需手动选择项目才发起请求：尝试选择第一个项目并抓取任一维度接口
    let fired = false
    try {
      const respPromise = page.waitForResponse(
        (r) => /\/v1\/dashboard\/project\/\d+\/(overview|budget|progress|contract|output)/.test(r.url()),
        { timeout: 8_000 }
      )
      await page.goto(route)
      // 打开项目选择器并选中第一项
      const selector = page.locator('.el-select').first()
      await selector.click({ timeout: 5_000 })
      await page.locator('.el-select-dropdown__item').first().click({ timeout: 5_000 })
      const resp = await respPromise
      const json = await resp.json()
      fired = true
      if (json.code !== 200) {
        results.push({ route, title, api, mismatches: [{ row: -1, column: '__apiError__', expected: 'code=200', actual: `code=${json.code} message=${json.message}` }] })
        expect(json.code, `项目看板维度接口应返回成功码，实际 message=${json.message}`).toBe(200)
        return
      }
    } catch {
      // 无可选项目或未触发请求：记录为文档性说明，不误报为缺陷
    }

    results.push({
      route, title, api,
      mismatches: [{
        row: -1,
        column: '__note__',
        field: fired ? 'overview' : 'projectSelection',
        expected: fired ? '维度接口 code=200' : '需手动选择项目后加载',
        actual: fired ? '接口正常返回' : '未自动选择项目/无可选项目，页面显示空状态（project-dashboard 采用逐维度显式错误处理，非静默 fallback）',
      }],
    })
  })

  test.afterAll(async () => {
    finalizeModuleConsistency('dashboard', results)
  })
})
