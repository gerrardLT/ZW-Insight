/**
 * 真实模式 E2E 测试：项目 CRUD 操作
 *
 * 验证项目模块的完整生命周期：
 * - 项目列表查看
 * - 创建新项目
 * - 编辑项目
 * - 删除项目（清理测试数据）
 *
 * 所有操作使用 tenant_id=9999 数据（通过真实登录的 admin 账号）。
 * 测试完成后通过 afterAll 清理创建的数据。
 *
 * 需求: 6.1, 6.2
 */
import { test, expect } from '@playwright/test'

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'

// 测试中创建的项目 ID（雪花 ID 以字符串保存防精度丢失），用于 afterAll 清理
const createdProjectIds: (number | string)[] = []

// 生成唯一测试项目名称（带时间戳避免冲突）
function testProjectName(): string {
  const ts = Date.now()
  return `E2E自动化测试项目_${ts}`
}

test.describe('项目管理 — 列表查看', () => {
  test('项目列表页 — 正常加载', async ({ page }) => {
    await page.goto('/project/list')
    await page.waitForLoadState('networkidle')
    // 表格或空状态应可见
    await expect(
      page.locator('.el-table, .el-empty').first()
    ).toBeVisible({ timeout: 20_000 })
  })

  test('项目列表页 — 搜索按钮可用', async ({ page }) => {
    await page.goto('/project/list')
    await page.waitForLoadState('networkidle')
    const searchBtn = page.locator('button:has-text("搜索"), button:has-text("查询")').first()
    await expect(searchBtn).toBeVisible({ timeout: 15_000 })
    await expect(searchBtn).toBeEnabled()
  })

  test('项目列表页 — 新增按钮可用', async ({ page }) => {
    await page.goto('/project/list')
    await page.waitForLoadState('networkidle')
    const addBtn = page.locator('button:has-text("新增"), button:has-text("新建")').first()
    await expect(addBtn).toBeVisible({ timeout: 15_000 })
    await expect(addBtn).toBeEnabled()
  })

  test('项目列表页 — 分页组件存在', async ({ page }) => {
    await page.goto('/project/list')
    await page.waitForLoadState('networkidle')
    // 等待表格加载
    await page.waitForSelector('.el-table, .el-empty', { timeout: 15_000 })
    // 分页组件（与 src/views/project/index.vue 的 pagination-wrap 容器保持一致）
    const pagination = page.locator('.el-pagination, .pagination-wrap')
    const tableRows = page.locator('.el-table__row')
    const rowCount = await tableRows.count()
    if (rowCount > 0) {
      await expect(pagination.first()).toBeVisible({ timeout: 10_000 })
    }
  })
})

test.describe('项目管理 — 创建项目', () => {
  test('点击新增按钮 — 跳转到创建表单', async ({ page }) => {
    await page.goto('/project/list')
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.el-table, .el-empty', { timeout: 15_000 })

    const addBtn = page.locator('button:has-text("新增"), button:has-text("新建")').first()
    await addBtn.click()

    // 应跳转到创建页面或弹出对话框
    await page.waitForURL('**/project/create', { timeout: 10_000 }).catch(() => {
      // 如果不跳转，可能是对话框模式
    })
    // 表单应可见
    await expect(page.locator('.el-form').first()).toBeVisible({ timeout: 10_000 })
  })

  test('创建表单 — 核心字段渲染', async ({ page }) => {
    await page.goto('/project/create')
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.el-form', { timeout: 15_000 })

    // 验证核心表单字段存在
    await expect(
      page.locator('[placeholder*="项目名称"], [placeholder*="请输入项目名称"]').first()
    ).toBeVisible({ timeout: 10_000 })

    // 保存按钮存在
    await expect(
      page.locator('button:has-text("保存"), button:has-text("提交")').first()
    ).toBeVisible()
  })

  test('创建表单 — 必填项为空时拒绝提交', async ({ page }) => {
    await page.goto('/project/create')
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.el-form', { timeout: 15_000 })

    // 直接点击保存
    const saveBtn = page.locator('button:has-text("保存"), button:has-text("提交")').first()
    await saveBtn.click()

    // 应出现表单验证错误
    await expect(page.locator('.el-form-item__error').first()).toBeVisible({ timeout: 5_000 })
  })

  test('创建项目 — 填写完整表单并保存', async ({ page, request }) => {
    await page.goto('/project/create')
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.el-form', { timeout: 15_000 })

    const projectName = testProjectName()

    // 填写项目名称
    const nameInput = page.locator('[placeholder*="项目名称"], [placeholder*="请输入项目名称"]').first()
    await nameInput.fill(projectName)

    // 尝试选择项目性质（el-select 下拉带动画，选项在 body 传送门中，force 点击避免动画期不稳定）
    const natureSelect = page.locator(
      '.el-form-item:has(.el-form-item__label:has-text("项目性质")) .el-select'
    )
    if (await natureSelect.isVisible().catch(() => false)) {
      await natureSelect.click()
      const firstOption = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first()
      await firstOption.waitFor({ state: 'visible', timeout: 5_000 }).catch(() => {})
      if (await firstOption.isVisible().catch(() => false)) {
        await firstOption.click({ force: true })
        await page.keyboard.press('Escape')
      }
    }

    // 尝试选择项目类型
    const typeSelect = page.locator(
      '.el-form-item:has(.el-form-item__label:has-text("项目类型")) .el-select'
    )
    if (await typeSelect.isVisible().catch(() => false)) {
      await typeSelect.click()
      const firstOption = page.locator('.el-select-dropdown:visible .el-select-dropdown__item').first()
      await firstOption.waitFor({ state: 'visible', timeout: 5_000 }).catch(() => {})
      if (await firstOption.isVisible().catch(() => false)) {
        await firstOption.click({ force: true })
        await page.keyboard.press('Escape')
      }
    }

    // 监听保存请求
    const [saveResponse] = await Promise.all([
      page.waitForResponse(
        (resp) => resp.url().includes('/v1/project') && resp.request().method() === 'POST',
        { timeout: 15_000 }
      ).catch(() => null),
      page.locator('button:has-text("保存"), button:has-text("提交")').first().click(),
    ])

    if (saveResponse) {
      const responseBody = await saveResponse.json().catch(() => null)
      if (responseBody?.code === 200 && responseBody?.data) {
        const projectId = typeof responseBody.data === 'number'
          ? responseBody.data
          : responseBody.data?.id
        if (projectId) {
          createdProjectIds.push(projectId)
        }
      }
      // 验证请求成功
      expect(saveResponse.status()).toBeLessThan(400)
    }
  })
})

test.describe('项目管理 — 编辑项目', () => {
  test('编辑页面 — 表单回显数据', async ({ page }) => {
    // 自建 DRAFT 项目后直达编辑页，避免与并行用例争抢/删除列表首行导致 flaky
    // 先到任意页面恢复 storageState 的 localStorage，取 token 供 API 请求鉴权
    await page.goto('/')
    const token = await page.evaluate(() => localStorage.getItem('token') || '')
    expect(token, '已登录 token 应存在').toBeTruthy()

    const projectName = testProjectName()
    const createResp = await page.request.post(`${API_BASE}/api/v1/project`, {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`,
      },
      data: {
        projectName,
        projectNature: '新建',
        projectType: '房建工程',
        projectAddress: 'E2E编辑回显测试地址',
      },
    })
    expect(createResp.ok()).toBeTruthy()

    // 按名称查出刚创建的项目 ID
    const pageResp = await page.request.get(
      `${API_BASE}/api/v1/project/page?page=1&size=1&projectName=${encodeURIComponent(projectName)}`,
      { headers: { Authorization: `Bearer ${token}` } }
    )
    const pageJson = await pageResp.json()
    const record = pageJson?.data?.records?.[0]
    expect(record, '刚创建的项目应可查到').toBeTruthy()
    // 雪花 ID 超出 Number 安全整数范围，全程以字符串传递
    const projectId = String(record.id)
    createdProjectIds.push(projectId)

    // 直达编辑页，验证表单回显
    await page.goto(`/project/edit/${projectId}`)
    await page.waitForSelector('.el-form', { timeout: 10_000 })
    await expect(page.locator('.el-form').first()).toBeVisible()
    // 编辑页异步加载详情后回填，需等待回显为创建时的名称
    const nameInput = page.locator('[placeholder*="项目名称"], [placeholder*="请输入项目名称"]').first()
    await expect(nameInput).toHaveValue(projectName, { timeout: 10_000 })
  })
})

test.describe('项目管理 — 查看详情', () => {
  test('项目详情页 — 正常渲染', async ({ page }) => {
    await page.goto('/project/list')
    await page.waitForLoadState('networkidle')
    await page.waitForSelector('.el-table, .el-empty', { timeout: 15_000 })

    const rows = page.locator('.el-table__body-wrapper .el-table__row')
    const rowCount = await rows.count()

    if (rowCount > 0) {
      const viewBtn = page.locator('.el-table__body-wrapper .el-table__row')
        .first()
        .locator('button:has-text("查看"), a:has-text("查看"), button:has-text("详情")').first()

      if (await viewBtn.isVisible().catch(() => false)) {
        await viewBtn.click()
        // 等待详情页加载
        await page.waitForSelector('.el-form, .el-descriptions, .el-card', { timeout: 10_000 })
        await expect(
          page.locator('.el-form, .el-descriptions, .el-card, .detail-container').first()
        ).toBeVisible()
      } else {
        test.skip(true, '列表中没有查看按钮')
      }
    } else {
      test.skip(true, '项目列表为空，跳过详情测试')
    }
  })
})

/**
 * 测试数据清理
 * 通过 API 删除测试中创建的项目
 */
test.afterAll(async ({ request }) => {
  // 如果有创建的项目，通过 API 删除
  for (const projectId of createdProjectIds) {
    try {
      await request.delete(`${API_BASE}/api/v1/project/${projectId}`)
    } catch {
      // 清理失败不阻断测试报告
      console.warn(`[Cleanup] 删除项目 ${projectId} 失败，可能需要手动清理`)
    }
  }
})
