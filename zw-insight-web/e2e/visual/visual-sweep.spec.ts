/**
 * 真实模式视觉回归：关键页面截图基线比对（前端补测四步收口 W4，2026-08）
 *
 * 设计要点（实事求是，规避真实服务器动态数据导致的 flaky）：
 *   1. 页面清单为「结构稳定」精选：登录/忘记密码（纯静态）、系统管理配置页
 *      （近乎静态）、工作台与项目看板（掩码图表）、核心列表页（数据为
 *      31_V2026_26 持久种子，跨 run 稳定）
 *   2. 时间类动态文本用 maskLocator 全局掩码（yyyy-MM-dd / HH:mm(:ss) 模式）；
 *      ECharts 渲染存在抗锯齿抖动，图表容器整体掩码
 *   3. maxDiffPixelRatio=0.02 在 config 统一声明：兜底微量渲染差异，
 *      结构性回归（布局塌陷/组件丢失/整块错位）远超阈值必 FAIL，不做空洞断言
 *   4. 基线生成：npm run test:e2e:visual:update；日常验证：npm run test:e2e:visual
 */
import { test, expect, type Page } from '@playwright/test'

/** 时间类动态文本：日期与时刻模式（真实服务器页面实证存在「操作时间/创建时间」列） */
const TIME_TEXT = [
  /\d{4}-\d{2}-\d{2}( \d{2}:\d{2}(:\d{2})?)?/,
  /\d{2}:\d{2}(:\d{2})?/,
]

/** 图表容器（ECharts canvas 抗锯齿抖动，整体掩码） */
const CHARTS = ['canvas', '[class*="echarts"]', '.echarts']

/** 通用等待：网络空闲 + 骨架/加载态消失 */
async function settle(page: Page) {
  await page.waitForLoadState('networkidle', { timeout: 30_000 }).catch(() => {})
  await page.waitForSelector('.el-loading-mask', { state: 'hidden', timeout: 15_000 }).catch(() => {})
  await page.waitForTimeout(500)
}

/** 全页截图（视口内），统一掩码策略：时间文本 + 图表容器 */
async function snap(page: Page, name: string) {
  await settle(page)
  await expect(page).toHaveScreenshot(`${name}.png`, {
    mask: [
      ...TIME_TEXT.map((re) => page.getByText(re)),
      ...CHARTS.map((sel) => page.locator(sel)),
    ],
    maskColor: '#999999',
  })
}

test.describe.configure({ mode: 'serial' })

test.describe('视觉回归 — 真实服务器关键页面（W4）', () => {
  test('登录页 — 纯静态结构', async ({ browser }) => {
    // 登录页必须未登录态：绕过项目级 storageState，独立空白 context
    const ctx = await browser.newContext({ storageState: { cookies: [], origins: [] } })
    const page = await ctx.newPage()
    try {
      await page.goto('/login')
      await page.waitForSelector('.el-form, form', { timeout: 30_000 })
      await settle(page)
      await expect(page).toHaveScreenshot('login.png', {
        // 验证码图片每次不同 → 掩码
        mask: [page.locator('img[src*="captcha"], .captcha-img, img[alt*="验证码"]').first()],
        maskColor: '#999999',
      })
    } finally {
      await ctx.close()
    }
  })

  test('忘记密码页 — 纯静态结构', async ({ browser }) => {
    const ctx = await browser.newContext({ storageState: { cookies: [], origins: [] } })
    const page = await ctx.newPage()
    try {
      await page.goto('/forgot-password')
      await page.waitForSelector('.el-form, form', { timeout: 30_000 })
      await settle(page)
      await expect(page).toHaveScreenshot('forgot-password.png', {
        mask: [page.locator('img[src*="captcha"], .captcha-img').first()],
        maskColor: '#999999',
      })
    } finally {
      await ctx.close()
    }
  })

  test('工作台 dashboard — 布局+卡片结构（图表掩码）', async ({ page }) => {
    await page.goto('/dashboard')
    await page.waitForSelector('.el-card, .dashboard', { timeout: 30_000 })
    await snap(page, 'dashboard')
  })

  test('项目看板 project-dashboard — 布局结构（图表掩码）', async ({ page }) => {
    await page.goto('/project-dashboard')
    await page.waitForSelector('.el-card, .dashboard', { timeout: 30_000 })
    await snap(page, 'project-dashboard')
  })

  test('项目报备列表 — 筛选栏+表格骨架', async ({ page }) => {
    await page.goto('/project/list')
    await page.waitForSelector('.el-table', { timeout: 30_000 })
    await snap(page, 'project-list')
  })

  test('施工合同列表 — 筛选栏+表格骨架', async ({ page }) => {
    await page.goto('/contract/list')
    await page.waitForSelector('.el-table', { timeout: 30_000 })
    await snap(page, 'contract-list')
  })

  test('系统管理-字典 — 近静态双栏结构', async ({ page }) => {
    await page.goto('/system/dict')
    await page.waitForSelector('.el-table, .el-card', { timeout: 30_000 })
    await snap(page, 'system-dict')
  })
})

// CHARTS 掩码在 dashboard snap 中未使用（snap 仅掩时间文本）——
// dashboard 的图表掩码诉求由 maxDiffPixelRatio 兜底；此处导出防 tree-shake 警告无意义，保留常量供扩展
