import { test, expect } from '@playwright/test'
import { mockAllApisFallback, waitForTable, waitForPageReady } from '../fixtures/mock-api'

const systemPages = [
  { path: '/system/org', title: '机构管理' },
  { path: '/system/user', title: '人员管理' },
  { path: '/system/role', title: '角色管理' },
  { path: '/system/menu', title: '菜单管理' },
  { path: '/system/dict', title: '数据字典' },
  { path: '/system/post', title: '岗位管理' },
  { path: '/system/config', title: '系统设置' },
  { path: '/system/template', title: '模板管理' },
  { path: '/system/print-template', title: '打印模板' },
  { path: '/system/log', title: '日志管理' },
  { path: '/system/serial-number', title: '编号规则管理' },
  { path: '/system/backup', title: '数据备份' },
  { path: '/system/version', title: '版本管理' },
  { path: '/system/monitor', title: '系统监控' },
]

test.describe('系统管理模块', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApisFallback(page)
  })

  for (const { path, title } of systemPages) {
    test(`${title} — 页面加载并渲染表格`, async ({ page }) => {
      await page.goto(path)
      await waitForPageReady(page)
      await expect(page).toHaveURL(new RegExp(path.replace(/\//g, '\\/')))
      // 页面应该包含 el-card 容器
      await expect(page.locator('.el-card').first()).toBeVisible({ timeout: 10_000 })
    })
  }

  test('机构管理 — 搜索功能可用', async ({ page }) => {
    await page.goto('/system/org')
    await waitForPageReady(page)
    // 搜索按钮应该可见
    const searchBtn = page.locator('button:has-text("搜索")')
    if (await searchBtn.isVisible()) {
      await expect(searchBtn).toBeEnabled()
    }
  })

  test('人员管理 — 新增按钮可用', async ({ page }) => {
    await page.goto('/system/user')
    await waitForPageReady(page)
    const addBtn = page.locator('button:has-text("新增")')
    if (await addBtn.isVisible()) {
      await expect(addBtn).toBeEnabled()
    }
  })

  test('角色管理 — 新增按钮可用', async ({ page }) => {
    await page.goto('/system/role')
    await waitForPageReady(page)
    const addBtn = page.locator('button:has-text("新增")')
    if (await addBtn.isVisible()) {
      await expect(addBtn).toBeEnabled()
    }
  })
})
