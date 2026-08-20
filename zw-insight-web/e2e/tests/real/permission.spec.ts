/**
 * 真实模式 E2E 测试：非 admin 权限视角（前端补测四步收口 W5，2026-08）
 *
 * @matrix C-13-1 封账 canOperate 契约 / C-13-2 普通用户隐藏操作入口 /
 *   菜单权限生效（PERM-GAP 2026-08-21 修复后翻正向）/ 后端 @RequiresPermission 写接口边界
 *
 * 种子账号：lina=STAFF（仅授权首页+消息）、wangqiang=FINANCE_STAFF，密码均 123456
 * 产品现状实证（2026-08-21 修复后）：
 *   1. 侧边栏消费 GET /v1/system/menu/user（sys_role_menu 授权集）过滤静态路由表 →
 *      lina 仅见首页+消息管理（缺陷#1 PERM-GAP 已解除）
 *   2. 封账页 canOperate = permissions 含 "*:*:*" 或 roles ∈ ["FINANCE_ADMIN","ADMIN"]
 *      → wangqiang（FINANCE_STAFF）新增封账按钮同样隐藏（以代码契约为准）
 *   3. 后端写接口 @RequiresPermission（finance:financelock:create/unlock）= 真实权限边界
 *
 * 纯只读 + 一次被拒的写调用（前后差集校验无落库），无数据清理需求。
 */
import { test, expect, request as pwRequest } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'
const WEB_BASE = process.env.E2E_SERVER_URL || 'http://129.204.3.200:18081'
const SSH_KEY = process.env.E2E_SSH_KEY || resolve(__dirname, '../../../../keys/zwinsight.pem')
const SSH_HOST = process.env.E2E_SSH_HOST || 'root@129.204.3.200'

/** 验证码答案在 Redis（captcha:{uuid}），经 SSH 读容器内 redis-cli（auth-real.setup.ts 同款链路） */
function readCaptchaFromRedis(uuid: string): string {
  const out = execFileSync('ssh',
    ['-i', SSH_KEY, '-o', 'StrictHostKeyChecking=no', '-o', 'ConnectTimeout=10',
      SSH_HOST, `docker exec zwi-redis redis-cli GET "captcha:${uuid}"`],
    { encoding: 'utf-8', timeout: 20_000 })
  return out.replace(/["\r\n]/g, "").trim()
}

/** 真实登录链路：captcha/image → SSH Redis 取答案 → /v1/auth/login */
async function apiLogin(username: string) {
  const ctx = await pwRequest.newContext()
  try {
    const cap = await ctx.get(`${API_BASE}/api/v1/captcha/image`)
    const uuid = (await cap.json()).data?.uuid
    expect(uuid, `${username} 验证码 uuid`).toBeTruthy()
    const code = readCaptchaFromRedis(uuid)
    expect(code, `${username} 验证码答案（SSH Redis）`).toBeTruthy()
    const resp = await ctx.post(`${API_BASE}/api/v1/auth/login`, {
      data: { username, password: "123456", captchaCode: code, captchaUuid: uuid },
    })
    const body = await resp.json()
    expect(body.code, `${username} 登录应成功`).toBe(200)
    return body.data
  } finally {
    await ctx.dispose()
  }
}

/** 按 admin storage-state 实证的 pinia 结构构造临时 storageState（不污染 admin 文件） */
function buildStorageState(login: any) {
  return {
    cookies: [],
    origins: [{
      origin: WEB_BASE,
      localStorage: [
        { name: "token", value: login.token },
        {
          name: "user", value: JSON.stringify({
            token: login.token,
            userInfo: {
              userId: login.userId, username: login.username, realName: login.realName,
              tenantId: login.tenantId, tenantName: login.tenantName, roles: login.roles,
            },
            menus: [],
            permissions: login.permissions || [],
          }),
        },
      ],
    }],
  } as any
}

let linaLogin: any = null
let wangqiangLogin: any = null

test.beforeAll(async () => {
  linaLogin = await apiLogin('lina')
  wangqiangLogin = await apiLogin('wangqiang')
})

test.describe.configure({ mode: 'serial' })

test.describe('权限视角 — 非 admin 真实登录（@matrix C-13/PERM-GAP）', () => {
  test('lina 登录响应契约 — roles=STAFF 且无封账操作权限', async () => {
    expect(linaLogin.roles, 'lina 角色').toContain('STAFF')
    expect(linaLogin.roles, 'lina 不应是超管').not.toContain('SUPER_ADMIN')
    expect(linaLogin.permissions || [], 'lina 无封账创建权限')
      .not.toContain('finance:financelock:create')
    expect(wangqiangLogin.roles, 'wangqiang 角色').toContain('FINANCE_STAFF')
  })

  test('lina 侧边栏菜单权限生效 — 仅首页/消息管理，未授权模块全部隐藏（PERM-GAP 2026-08-21 修复后翻正向）', async ({ browser }) => {
    const ctx = await browser.newContext({ storageState: buildStorageState(linaLogin) })
    const page = await ctx.newPage()
    try {
      await page.goto('/dashboard')
      await page.waitForSelector('.side-menu', { timeout: 30_000 })
      // 菜单经 GET /v1/system/menu/user 异步过滤渲染，先等授权项出现再断排他
      await expect(page.locator('.side-menu')).toContainText('消息管理', { timeout: 30_000 })
      const menuText = await page.locator('.side-menu').innerText()
      expect(menuText, 'lina 侧边栏含首页').toContain('首页')
      expect(menuText, 'lina 侧边栏含消息管理').toContain('消息管理')
      expect(menuText, 'lina 侧边栏不应含项目管理').not.toContain('项目管理')
      expect(menuText, 'lina 侧边栏不应含财务管理').not.toContain('财务管理')
      expect(menuText, 'lina 侧边栏不应含系统管理').not.toContain('系统管理')
    } finally {
      await ctx.close()
    }
  })

  test('C-13-2 lina（STAFF）— 新增封账按钮与操作列均隐藏', async ({ browser }) => {
    const ctx = await browser.newContext({ storageState: buildStorageState(linaLogin) })
    const page = await ctx.newPage()
    try {
      await page.goto('/finance/finance-lock')
      await page.waitForSelector('.el-table__row, .el-table__empty-block', { timeout: 30_000 })
      await expect(page.locator('button:has-text("新增封账")')).toHaveCount(0)
      // :text-is 精确匹配，避免误伤「操作人」「操作时间」两个包含“操作”的表头列
      await expect(page.locator('.el-table th:text-is("操作")')).toHaveCount(0)
    } finally {
      await ctx.close()
    }
  })

  test('C-13-1 wangqiang（FINANCE_STAFF）— 新增封账按钮同样隐藏（契约仅 FINANCE_ADMIN/ADMIN）', async ({ browser }) => {
    // 代码契约实证（finance-lock/index.vue canOperate）：OPERATE_ROLES=['FINANCE_ADMIN','ADMIN']，
    // FINANCE_STAFF 不在列 → 按钮隐藏。矩阵 C-13-1 与代码契约双实证优先于计划文字表述。
    const ctx = await browser.newContext({ storageState: buildStorageState(wangqiangLogin) })
    const page = await ctx.newPage()
    try {
      await page.goto('/finance/finance-lock')
      await page.waitForSelector('.el-table__row, .el-table__empty-block', { timeout: 30_000 })
      await expect(page.locator('button:has-text("新增封账")')).toHaveCount(0)
    } finally {
      await ctx.close()
    }
  })

  test('lina token 直调封账写接口被拒 — 且无落库（前后差集校验）', async () => {
    const ctx = await pwRequest.newContext({
      extraHTTPHeaders: { Authorization: `Bearer ${linaLogin.token}` },
    })
    try {
      const beforeResp = await ctx.get(`${API_BASE}/api/v1/finance/lock/page`, { params: { pageNum: 1, pageSize: 50 } })
      expect(beforeResp.status(), 'lina 读封账列表（GET 无注解，开放）').toBe(200)
      const beforeIds = new Set((((await beforeResp.json()).data?.records) || []).map((r: any) => r.id))

      const resp = await ctx.post(`${API_BASE}/api/v1/finance/lock`, {
        data: { period: '2099-12', lockType: 'MONTHLY' },
      })
      const blocked = resp.status() === 403 || ((await resp.json()).code !== 200)
      expect(blocked, 'lina 无 finance:financelock:create → 写被拒（403 或业务错误码）').toBeTruthy()

      const afterResp = await ctx.get(`${API_BASE}/api/v1/finance/lock/page`, { params: { pageNum: 1, pageSize: 50 } })
      const after = (((await afterResp.json()).data?.records) || []).map((r: any) => r.id)
      const diff = after.filter((id: any) => !beforeIds.has(id))
      expect(diff, '被拒的写请求不应落库').toHaveLength(0)
    } finally {
      await ctx.dispose()
    }
  })
})
