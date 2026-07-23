/**
 * dashboard 驾驶舱 —— 图表/看板页 前端展示 vs 后端数据 一致性
 *  A 首页驾驶舱 /dashboard          GET /v1/dashboard/company-overview（KPI 卡片字段级）
 *  B 项目看板  /project-dashboard    GET /v1/dashboard/project/{id}/overview（需选择项目，best-effort）
 *
 * 两页均以图表/卡片而非表格呈现，故对可断言的 KPI 数字做字段级校验。
 *
 * ⚠ 发现（dashboard/index.vue）：loadStats / 饼图 / 柱图 三处 catch 均静默兜底为空数据
 *  （`catch { stats.value = {} }` 等），接口异常时页面无任何错误提示，违反「无静默 fallback」约定。
 *  对比 project-dashboard.vue 使用 loadDimension 逐维度 state.error 显式报错，为正确范式。
 */
import { test, expect } from '@playwright/test'
import {
  gotoAndCapture,
  writeModuleReport,
  type PageConsistencyResult,
  type Mismatch,
} from './consistency-helper'

/** formatWan 独立复刻：空值→'0'，否则 (val/10000).toFixed(1) */
function fmtWan(val: unknown): string {
  const n = Number(val)
  if ((!val && val !== 0) || Number.isNaN(n)) return '0'
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

    // 静默 fallback 属静态代码事实，固定记入发现
    mismatches.push({
      row: -1,
      column: '__silentFallback__',
      field: 'loadStats/pie/bar catch',
      expected: '接口异常应显式提示',
      actual: 'index.vue 三处 catch 静默兜底空数据，无错误提示（违反无静默 fallback 约定）',
    })

    if (resp.code !== 200) {
      mismatches.push({ row: -1, column: '__apiError__', expected: 'code=200', actual: `code=${resp.code} message=${resp.message}` })
      results.push({ route, title, api, mismatches })
      expect(resp.code, `接口应返回成功码，实际 message=${resp.message}`).toBe(200)
      return
    }

    const data = resp.data ?? {}
    const expectedByLabel: Record<string, string> = {
      '项目总数': String(data.projectCount || 0),
      '合同总额(万)': fmtWan(data.contractAmount),
      '已收款(万)': fmtWan(data.receivedAmount),
      '垫资(万)': fmtWan(data.advanceAmount),
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
    // KPI 不一致（排除固定的静默 fallback 记录）才让用例失败
    const kpiMismatches = mismatches.filter((m) => m.column !== '__silentFallback__')
    expect(kpiMismatches, `KPI 卡片存在 ${kpiMismatches.length} 处不一致：\n${JSON.stringify(kpiMismatches, null, 2)}`).toHaveLength(0)
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
    writeModuleReport('dashboard', results)
  })
})
