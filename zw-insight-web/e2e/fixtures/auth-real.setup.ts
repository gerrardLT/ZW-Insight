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
const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'

setup('authenticate against real server', async ({ page }) => {
  const challengeResponse = page.waitForResponse(
    (resp) => resp.url().includes('/captcha/slider') && !resp.url().includes('/verify') && resp.ok(),
    { timeout: 15_000 }
  )
  await page.goto('/login')
  await page.waitForSelector('.login-box', { timeout: 15_000 })
  const challenge = await (await challengeResponse).json()
  const gapPct = Number(challenge.data?.gapPct)
  if (!Number.isFinite(gapPct)) throw new Error(`[auth-real.setup] 滑块挑战异常: ${JSON.stringify(challenge)}`)

  const track = page.locator('.slider-captcha .track')
  const handle = page.locator('.slider-captcha .handle')
  const box = await track.boundingBox()
  if (!box) throw new Error('[auth-real.setup] 无法读取滑块轨道尺寸')
  const startX = box.x + 23
  const y = box.y + box.height / 2
  await handle.hover()
  await page.mouse.move(startX, y)
  await page.mouse.down()
  await page.mouse.move(box.x + gapPct * box.width, y, { steps: 12 })
  await page.mouse.up()
  await expect(page.locator('.slider-captcha')).toHaveClass(/ok/)

  await page.fill('input[placeholder="请输入用户名"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', '123456')
  await page.click('button:has-text("进入系统")')

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
