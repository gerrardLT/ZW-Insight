/**
 * 真实模式登录 Setup：真实 TAC popup + 真实后端天爱挑战/行为轨迹校验。
 * 测试只经 SSH 读取天爱存于 Redis 的挑战（captcha:{id} → JSON.percentage）；不 mock 浏览器或 API。
 */
import { test as setup, expect } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const SSH_KEY = process.env.E2E_SSH_KEY || resolve(__dirname, '../../../keys/zwinsight.pem')
const SSH_HOST = process.env.E2E_SSH_HOST || 'root@129.204.3.200'

/** 读取天爱挑战目标：缺口 X 占背景图宽度的比例（0~1）。 */
function readSliderPercentage(challengeId: string): number {
  const out = execFileSync('ssh', [
    '-i', SSH_KEY, '-o', 'StrictHostKeyChecking=no', '-o', 'ConnectTimeout=10',
    SSH_HOST, `docker exec zwi-redis redis-cli GET "captcha:${challengeId}"`,
  ], { encoding: 'utf-8', timeout: 20_000 })
  const percentage = Number(JSON.parse(out.trim())?.percentage)
  if (!Number.isFinite(percentage)) throw new Error(`[auth-real.setup] 无法读取 captcha:${challengeId}`)
  return percentage
}

setup('authenticate against real server', async ({ page }) => {
  await page.goto('/login')
  await page.waitForSelector('.login-box', { timeout: 15_000 })

  const challengeResponse = page.waitForResponse(
    resp => resp.url().includes('/captcha/slider') && !resp.url().includes('/verify') && resp.ok(),
    { timeout: 15_000 },
  )
  await page.getByRole('button', { name: '点击完成安全验证' }).click()
  const challenge = (await (await challengeResponse).json()).data
  const percentage = readSliderPercentage(challenge.id)

  // 天爱校验：(末点X − 首点X) / 背景图显示宽度 ≈ percentage
  const handle = page.locator('#tianai-captcha-slider-move-btn').first()
  const bg = page.locator('#tianai-captcha-slider-bg-img').first()
  await expect(handle).toBeVisible()
  const handleBox = await handle.boundingBox()
  const bgBox = await bg.boundingBox()
  if (!handleBox || !bgBox) throw new Error('[auth-real.setup] 无法读取 TAC 滑块尺寸')
  const startX = handleBox.x + handleBox.width / 2
  const y = handleBox.y + handleBox.height / 2
  const verifyResponse = page.waitForResponse(resp => resp.url().includes('/captcha/slider/verify'))
  await page.mouse.move(startX, y)
  await page.mouse.down()
  await page.mouse.move(startX + percentage * bgBox.width, y + 2, { steps: 25 })
  await page.mouse.up()
  const verifyBody = await (await verifyResponse).json()
  if (verifyBody.code !== 200) {
    throw new Error(`[auth-real.setup] 滑块校验失败: ${JSON.stringify({ percentage, bgWidth: bgBox.width, handleWidth: handleBox.width, response: verifyBody })}`)
  }
  await expect(page.getByRole('button', { name: '安全验证已完成' })).toBeVisible()

  await page.fill('input[placeholder="请输入用户名/手机号"]', 'admin')
  await page.fill('input[placeholder="请输入密码"]', '123456')
  await page.click('button:has-text("进入系统")')
  await page.waitForURL(url => !url.pathname.includes('/login'), { timeout: 15_000 })
  expect(page.url()).not.toContain('/login')
  await page.context().storageState({ path: './e2e/.auth/storage-state.json' })
})
