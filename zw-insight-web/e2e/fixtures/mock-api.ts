import { Page } from '@playwright/test'

/** 拦截指定 API 路径并返回 mock 数据 */
export async function mockApi(page: Page, urlPattern: string, responseData: any) {
  await page.route(`**${urlPattern}`, async (route) => {
    // 静态资源请求不拦截
    if (route.request().resourceType() === 'script' || route.request().resourceType() === 'stylesheet') {
      await route.continue()
      return
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, data: responseData, message: 'ok' }),
    })
  })
}

/** 拦截分页 API，返回标准分页结构 */
export async function mockListApi(
  page: Page,
  urlPattern: string,
  records: any[],
  total?: number,
) {
  await mockApi(page, urlPattern, {
    records,
    total: total ?? records.length,
    page: 1,
    size: 10,
  })
}

/** 一键 mock 模块 CRUD 全套 API */
export async function mockCrudApis(page: Page, baseUrl: string, mockRecord?: any) {
  const record = mockRecord || { id: 1, name: '测试记录', status: 'DRAFT' }

  // GET 列表
  await mockListApi(page, `${baseUrl}/page`, [record])
  await mockListApi(page, `${baseUrl}/list`, [record])
  await mockListApi(page, baseUrl, [record])

  // GET 详情
  await mockApi(page, `${baseUrl}/*`, record)

  // POST 新增
  await page.route(`**${baseUrl}`, async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: { id: 99 }, message: '新增成功' }),
      })
    } else {
      await route.continue()
    }
  })

  // PUT 更新 / 提交
  await page.route(`**${baseUrl}/*`, async (route) => {
    if (route.request().method() === 'PUT' || route.request().method() === 'DELETE') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: '操作成功' }),
      })
    } else if (route.request().method() === 'GET') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: record, message: 'ok' }),
      })
    } else {
      await route.continue()
    }
  })
}

/** 拦截所有 /api/ 请求返回空成功（兜底，防止未 mock 的请求挂起） */
export async function mockAllApisFallback(page: Page) {
  await page.route('**/api/**', async (route) => {
    if (route.request().resourceType() === 'script' || route.request().resourceType() === 'stylesheet') {
      await route.continue()
      return
    }
    // 已被其他 route 处理的不会到这里
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        data: { records: [], total: 0, page: 1, size: 10 },
        message: 'ok',
      }),
    })
  })
}

/** 等待 Element Plus 表格渲染完成 */
export async function waitForTable(page: Page) {
  await page.waitForSelector('.el-table', { timeout: 10_000 })
  // 等待 loading 消失
  await page.waitForSelector('.el-loading-mask', { state: 'hidden', timeout: 10_000 }).catch(() => {})
}

/** 等待 Element Plus 表单渲染完成 */
export async function waitForForm(page: Page) {
  await page.waitForSelector('.el-form', { timeout: 10_000 })
}

/** 等待页面导航完成（无 loading 遮罩） */
export async function waitForPageReady(page: Page) {
  await page.waitForLoadState('domcontentloaded')
  // 等待 Vue 应用挂载
  await page.waitForSelector('#app', { timeout: 10_000 }).catch(() => {})
  // 等待 loading 消失
  await page.waitForSelector('.el-loading-mask', { state: 'hidden', timeout: 8_000 }).catch(() => {})
  // 短暂等待 Vue 渲染周期
  await page.waitForTimeout(500)
}
