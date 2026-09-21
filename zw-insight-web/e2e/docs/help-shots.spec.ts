/**
 * 使用文档配图截图流水线（真实服务器）。
 *
 * 复用 setup-real 的真实登录 storageState，逐目标导航→稳定→（可选开弹窗）→截图，
 * 产出直接写入 src/docs/help/images/<chapter>/<file>.png 供 Markdown 引用。
 *
 * 运行：npx playwright test --project=docs-shots
 * 说明：
 *   - 打开弹窗类目标若按钮文案不符→记 WARN 并跳过（不伪造、不失败整套）；
 *   - 导航/截图本身报错→记 FAIL，afterAll 汇总后以非零退出，暴露真实问题。
 */
import { test, expect, type Page } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { settle, annotate, tryOpenDialog, screenshotDialog, screenshotViewport, type Mark } from './annotate'

const __dirname = dirname(fileURLToPath(import.meta.url))
const IMAGES_ROOT = resolve(__dirname, '../../src/docs/help/images')

interface ShotTarget {
  chapter: string
  file: string
  route: string
  /** 打开弹窗的候选按钮文案（按顺序尝试）；提供时截取弹窗，否则截视口 */
  openDialogWith?: string[]
  /** 视口截图上的红框标注（仅在非弹窗截图时生效） */
  marks?: Mark[]
  /** 表格类页面等待选择器，默认 .el-table 或 .el-card */
  waitSelector?: string
}

const TARGETS: ShotTarget[] = [
  // ===== 审批与待办 =====
  { chapter: 'workflow-approval', file: 'todo-list', route: '/workflow/approval', waitSelector: '.el-tabs',
    marks: [{ selector: '.el-table__fixed-right', label: 1 }] },
  { chapter: 'workflow-approval', file: 'approve-dialog', route: '/workflow/approval', openDialogWith: ['通过'] },
  { chapter: 'workflow-approval', file: 'reject-dialog', route: '/workflow/approval', openDialogWith: ['退回'] },
  { chapter: 'workflow-approval', file: 'message-center', route: '/message/center', waitSelector: '.el-card' },

  // ===== 业务主线（关键衔接点列表） =====
  { chapter: 'main-flow', file: 'project-list', route: '/project/list', waitSelector: '.el-table' },
  { chapter: 'main-flow', file: 'contract-list', route: '/contract/list', waitSelector: '.el-table' },

  // ===== 预算管理 =====
  { chapter: 'budget', file: 'budget-list', route: '/budget/list', waitSelector: '.el-table' },
  { chapter: 'budget', file: 'budget-form', route: '/budget/list', openDialogWith: ['新增', '新建', '编制'] },
  { chapter: 'budget', file: 'control-config', route: '/budget/control-config', waitSelector: '.el-table' },
  { chapter: 'budget', file: 'cost-account', route: '/budget/cost-account', waitSelector: '.el-card' },

  // ===== 财务管理（收入/支出/资金 P0-P2） =====
  { chapter: 'finance', file: 'payment-apply', route: '/finance/payment-apply', waitSelector: '.el-table' },
  { chapter: 'finance', file: 'payment-form', route: '/finance/payment-apply', openDialogWith: ['新增', '付款申请', '申请付款', '新建'] },
  { chapter: 'finance', file: 'invoice-apply', route: '/finance/invoice-apply', waitSelector: '.el-table' },
  { chapter: 'finance', file: 'payment-received', route: '/finance/payment-received', waitSelector: '.el-table' },
  { chapter: 'finance', file: 'retention', route: '/finance/retention', waitSelector: '.el-card' },
  { chapter: 'finance', file: 'settlement', route: '/finance/settlement', waitSelector: '.el-card' },
  { chapter: 'finance', file: 'fund-dashboard', route: '/finance/fund-dashboard', waitSelector: '.el-card' },
  { chapter: 'finance', file: 'security-bond', route: '/finance/security-bond', waitSelector: '.el-card' },
  { chapter: 'finance', file: 'wage-account', route: '/finance/wage-account', waitSelector: '.el-card' },
  { chapter: 'finance', file: 'fund-plan', route: '/finance/fund-plan', waitSelector: '.el-card' },
  { chapter: 'finance', file: 'bank-flow', route: '/finance/bank-flow', waitSelector: '.el-card' },
  { chapter: 'finance', file: 'bill', route: '/finance/bill', waitSelector: '.el-card' },
  { chapter: 'finance', file: 'financing', route: '/finance/financing', waitSelector: '.el-card' },

  // ===== 快速上手 =====
  { chapter: 'quickstart', file: 'home', route: '/dashboard', waitSelector: '.el-card' },

  // ===== 看板 =====
  { chapter: 'dashboard', file: 'project-dashboard', route: '/project-dashboard', waitSelector: '.el-card' },
  { chapter: 'dashboard', file: 'cost-control', route: '/project-cost-control', waitSelector: '.el-card' },

  // ===== 项目 =====
  { chapter: 'project', file: 'wbs', route: '/project/wbs', waitSelector: '.el-card' },

  // ===== 投标 =====
  { chapter: 'tender', file: 'register', route: '/tender/register', waitSelector: '.el-table' },
  { chapter: 'tender', file: 'certificate', route: '/tender/certificate', waitSelector: '.el-table' },

  // ===== 合同 =====
  { chapter: 'contract', file: 'output-report', route: '/contract/output-report', waitSelector: '.el-table' },
  { chapter: 'contract', file: 'change-event', route: '/contract/change-event', waitSelector: '.el-table' },

  // ===== 采购 =====
  { chapter: 'purchase', file: 'contract', route: '/purchase/contract', waitSelector: '.el-table' },
  { chapter: 'purchase', file: 'settlement', route: '/purchase/settlement', waitSelector: '.el-table' },
  { chapter: 'purchase', file: 'inquiry', route: '/purchase/inquiry', waitSelector: '.el-table' },

  // ===== 劳务 =====
  { chapter: 'labor', file: 'contract', route: '/labor/contract', waitSelector: '.el-table' },
  { chapter: 'labor', file: 'roster', route: '/labor/roster', waitSelector: '.el-table' },
  { chapter: 'labor', file: 'payroll', route: '/labor/payroll', waitSelector: '.el-table' },

  // ===== 材料 =====
  { chapter: 'material', file: 'inbound', route: '/material/inbound', waitSelector: '.el-table' },
  { chapter: 'material', file: 'outbound', route: '/material/outbound', waitSelector: '.el-table' },
  { chapter: 'material', file: 'stock', route: '/material/stock', waitSelector: '.el-table' },

  // ===== 机械 =====
  { chapter: 'machine', file: 'contract', route: '/machine/contract', waitSelector: '.el-table' },
  { chapter: 'machine', file: 'ledger', route: '/machine/ledger', waitSelector: '.el-table' },
  { chapter: 'machine', file: 'work-log', route: '/machine/work-log', waitSelector: '.el-table' },
  { chapter: 'machine', file: 'settlement', route: '/machine/settlement', waitSelector: '.el-table' },

  // ===== 分包 =====
  { chapter: 'subcontract', file: 'contract', route: '/subcontract/contract', waitSelector: '.el-table' },
  { chapter: 'subcontract', file: 'settlement', route: '/subcontract/settlement', waitSelector: '.el-table' },

  // ===== 现场 =====
  { chapter: 'site', file: 'schedule', route: '/site/schedule', waitSelector: '.el-card' },
  { chapter: 'site', file: 'construction-log', route: '/site/construction-log', waitSelector: '.el-table' },
  { chapter: 'site', file: 'inspection', route: '/site/inspection', waitSelector: '.el-card' },

  // ===== 行政人事 =====
  { chapter: 'hr', file: 'entry', route: '/hr/entry', waitSelector: '.el-table' },
  { chapter: 'hr', file: 'office-supply', route: '/hr/office-supply', waitSelector: '.el-table' },
  { chapter: 'hr', file: 'vehicle', route: '/hr/vehicle', waitSelector: '.el-table' },
  { chapter: 'hr', file: 'statistics', route: '/hr/statistics', waitSelector: '.el-card' },

  // ===== 档案 =====
  { chapter: 'archive', file: 'index', route: '/archive/index', waitSelector: '.el-card' },

  // ===== 系统管理 =====
  { chapter: 'system', file: 'org', route: '/system/org', waitSelector: '.el-card' },
  { chapter: 'system', file: 'user', route: '/system/user', waitSelector: '.el-table' },
  { chapter: 'system', file: 'role', route: '/system/role', waitSelector: '.el-card' },
  { chapter: 'system', file: 'dict', route: '/system/dict', waitSelector: '.el-card' },
  { chapter: 'system', file: 'amount-tier', route: '/system/amount-tier', waitSelector: '.el-card' },
  { chapter: 'system', file: 'serial-number', route: '/system/serial-number', waitSelector: '.el-card' },
  { chapter: 'system', file: 'monitor', route: '/system/monitor', waitSelector: '.el-card' },

  // ===== 基础数据（新增章） =====
  { chapter: 'basedata', file: 'material', route: '/basedata/material', waitSelector: '.el-table' },
  { chapter: 'basedata', file: 'supplier', route: '/basedata/supplier', waitSelector: '.el-table' },
  { chapter: 'basedata', file: 'supplier-evaluation', route: '/basedata/supplier-evaluation', waitSelector: '.el-card' },
]

interface Result { target: string; status: 'OK' | 'WARN' | 'FAIL'; note: string }
const results: Result[] = []

/** 预置首登引导完成标记（等价老用户），避免 el-tour 遮罩入镜 */
async function dismissTour(page: Page) {
  await page.addInitScript(() => localStorage.setItem('zw-tour-done', '1'))
}

test.describe.configure({ mode: 'serial' })

test.describe('使用文档配图', () => {
  for (const t of TARGETS) {
    const id = `${t.chapter}/${t.file}`
    test(id, async ({ page }) => {
      const outPath = resolve(IMAGES_ROOT, t.chapter, `${t.file}.png`)
      mkdirSync(dirname(outPath), { recursive: true })
      await dismissTour(page)
      try {
        await page.goto(t.route)
        await page.waitForSelector(t.waitSelector || '.el-card', { timeout: 30_000 })
        await settle(page)

        if (t.openDialogWith) {
          const hit = await tryOpenDialog(page, t.openDialogWith)
          if (!hit) {
            results.push({ target: id, status: 'WARN', note: '未找到打开弹窗的按钮文案，跳过（列表页本身已可另截）' })
            return
          }
          await settle(page)
          await screenshotDialog(page, outPath)
          results.push({ target: id, status: 'OK', note: `弹窗（按钮「${hit}」）` })
        } else {
          if (t.marks?.length) await annotate(page, t.marks)
          await screenshotViewport(page, outPath)
          results.push({ target: id, status: 'OK', note: '视口' })
        }
      } catch (e) {
        results.push({ target: id, status: 'FAIL', note: (e as Error).message.split('\n')[0] })
      }
    })
  }

  // ===== 特殊截图：未登录登录页（独立空白 context，不用登录态） =====
  test('quickstart/login', async ({ browser }) => {
    const outPath = resolve(IMAGES_ROOT, 'quickstart', 'login.png')
    mkdirSync(dirname(outPath), { recursive: true })
    const ctx = await browser.newContext({ storageState: { cookies: [], origins: [] }, viewport: { width: 1440, height: 900 } })
    const page = await ctx.newPage()
    try {
      await page.goto('/login')
      await page.waitForSelector('.captcha-img, .el-form, form', { timeout: 30_000 })
      await settle(page)
      // 验证码每次不同→不掩码会 flaky，但文档只需看布局，直接截
      await screenshotViewport(page, outPath)
      results.push({ target: 'quickstart/login', status: 'OK', note: '未登录态' })
    } catch (e) {
      results.push({ target: 'quickstart/login', status: 'FAIL', note: (e as Error).message.split('\n')[0] })
    } finally {
      await ctx.close()
    }
  })

  // ===== 特殊截图：命令面板（Ctrl+K 唤起） =====
  test('quickstart/command-palette', async ({ page }) => {
    const outPath = resolve(IMAGES_ROOT, 'quickstart', 'command-palette.png')
    mkdirSync(dirname(outPath), { recursive: true })
    try {
      await dismissTour(page)
      await page.goto('/dashboard')
      await page.waitForSelector('.el-card', { timeout: 30_000 })
      await settle(page)
      await page.keyboard.press('Control+k')
      await page.waitForTimeout(600)
      if ((await page.locator('.palette-overlay').count()) === 0) {
        // 快捷键未命中则点顶栏按钮
        await page.click('[aria-label="打开命令面板"]', { timeout: 5000 }).catch(() => {})
        await page.waitForTimeout(600)
      }
      if ((await page.locator('.palette-overlay').count()) === 0) {
        results.push({ target: 'quickstart/command-palette', status: 'WARN', note: '命令面板未唤起，跳过' })
        return
      }
      await screenshotViewport(page, outPath)
      results.push({ target: 'quickstart/command-palette', status: 'OK', note: 'Ctrl+K/顶栏按钮' })
    } catch (e) {
      results.push({ target: 'quickstart/command-palette', status: 'FAIL', note: (e as Error).message.split('\n')[0] })
    }
  })

  test.afterAll(() => {
    const ok = results.filter((r) => r.status === 'OK').length
    const warn = results.filter((r) => r.status === 'WARN')
    const fail = results.filter((r) => r.status === 'FAIL')
    console.log('\n===== 使用文档截图结果 =====')
    console.log(`OK=${ok}  WARN=${warn.length}  FAIL=${fail.length}`)
    warn.forEach((r) => console.log(`  [WARN] ${r.target}: ${r.note}`))
    fail.forEach((r) => console.log(`  [FAIL] ${r.target}: ${r.note}`))
    // 导航/截图类硬失败必须显式暴露（不静默）
    expect(fail.length, `存在 ${fail.length} 个截图硬失败，见上方明细`).toBe(0)
  })
})
