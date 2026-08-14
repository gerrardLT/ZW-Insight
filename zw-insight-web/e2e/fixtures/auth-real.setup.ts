/**
 * 真实模式登录 Setup
 *
 * 通过真实登录流程获取 session 并保存 storageState，供后续 e2e-real 测试复用。
 * 流程：导航登录页 → 拦截页面真实验证码响应取 uuid → SSH 读服务器 Redis 取验证码答案
 *      →（备选）/api/v1/test/captcha-code 测试端点 → 填表提交 → 保存 storageState
 *
 * 验证码答案来自真实 Redis（与 keys/verify-base.sh 同一链路），全程无 mock。
 *
 * 环境变量：
 * - E2E_API_BASE:  后端 API 地址（默认 http://129.204.3.200:18080）
 * - E2E_SSH_KEY:   SSH 私钥路径（CI 显式指定 deploy_key；本地默认仓库内 keys/zwinsight.pem，2026-08-14 M6 修复）
 * - E2E_SSH_HOST:  SSH 目标（默认 root@129.204.3.200）
 */
import { test as setup, expect } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// ESM 兼容（package.json type=module 无 __dirname；2026-08-14 P0 修复既有隐患）
const __dirname = dirname(fileURLToPath(import.meta.url))

const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'
// 本地默认回退到仓库内密钥（相对解析，跨机器可用）；CI 通过 E2E_SSH_KEY 显式覆盖，行为不变
const SSH_KEY = process.env.E2E_SSH_KEY || resolve(__dirname, '../../../keys/zwinsight.pem')
const SSH_HOST = process.env.E2E_SSH_HOST || 'root@129.204.3.200'

/** 经 SSH 从服务器 Redis 读取验证码答案（真实组件，需求 5.1 同源链路） */
function readCaptchaFromRedis(uuid: string): string {
  const out = execFileSync(
    'ssh',
    [
      '-i', SSH_KEY,
      '-o', 'StrictHostKeyChecking=no',
      '-o', 'ConnectTimeout=10',
      SSH_HOST,
      `docker exec zwi-redis redis-cli GET "captcha:${uuid}"`,
    ],
    { encoding: 'utf-8', timeout: 20_000 }
  )
  return out.replace(/["\r\n]/g, '').trim()
}

setup('authenticate against real server', async ({ page }) => {
  // 1. 准备拦截页面自身的验证码响应（页面 onMounted 会调 /captcha/image，uuid 必须与页面一致）
  const captchaRespPromise = page.waitForResponse(
    (resp) => resp.url().includes('/captcha/image') && resp.ok(),
    { timeout: 15_000 }
  )

  // 2. 导航到登录页
  await page.goto('/login')
  await page.waitForSelector('.login-box', { timeout: 15_000 })

  const captchaResp = await captchaRespPromise
  const captchaJson = await captchaResp.json()
  const uuid: string = captchaJson.data?.uuid

  if (!uuid) {
    throw new Error(
      `[auth-real.setup] 页面验证码响应异常，未获取到 uuid。响应: ${JSON.stringify(captchaJson)}`
    )
  }

  // 3. 获取验证码答案：优先 SSH 读 Redis；备选 /api/v1/test/captcha-code（仅 test profile 可用）
  let captchaCode = ''
  try {
    captchaCode = readCaptchaFromRedis(uuid)
    if (captchaCode) console.log('[auth-real.setup] 验证码已从服务器 Redis 获取')
  } catch (e) {
    console.warn(`[auth-real.setup] SSH 读 Redis 失败: ${(e as Error).message}`)
  }

  if (!captchaCode) {
    const codeResp = await page.request.get(`${API_BASE}/api/v1/test/captcha-code/${uuid}`)
    if (codeResp.ok()) {
      const codeJson = await codeResp.json()
      captchaCode = String(codeJson.data || '')
      console.log('[auth-real.setup] 验证码已从测试端点获取')
    }
  }

  if (!captchaCode) {
    throw new Error(
      '[auth-real.setup] 无法获取验证码（SSH Redis 与测试端点均不可用），登录流程中止'
    )
  }

  // 4. 填写用户名、密码、验证码并提交
  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', '123456')
  await page.fill('input[placeholder="验证码"]', captchaCode)
  await page.click('button:has-text("登 录")')

  // 5. 等待登录成功跳转（离开 /login 页面）
  await page.waitForURL((url) => !url.pathname.includes('/login'), {
    timeout: 15_000,
  })

  // 验证已离开登录页
  expect(page.url()).not.toContain('/login')
  console.log(`[auth-real.setup] 登录成功，当前页面: ${page.url()}`)

  // 6. 保存 storageState
  await page.context().storageState({ path: './e2e/.auth/storage-state.json' })
})
