/**
 * L5-UI 忘记密码真实走查（2026-08-14 P0 补测 @matrix D-2）
 *
 * 打部署前端（:18081）真实三步流程：
 *   输入手机号 → 发送验证码（sms.enabled=false 模拟模式码写 Redis）→
 *   SSH 读 sms:{phone} 取码 → 校验 → 设置新密码 → 重置成功跳登录页
 *
 * 数据纪律：
 * - 目标账号 t9999user/13900009998（租户 9999 低权限用户；严禁对 admin 重置——
 *   reset 会使目标用户全部 token 失效）
 * - afterAll 经 SSH 直改 DB 恢复密码 123456（BCrypt 常量；避免跨租户 API 权限问题）
 * - 未认证上下文（空 storageState），先例：tests/real/login.spec.ts
 *
 * 频控注意：send-code 有 60s 频率限制（sms:freq:{phone}）与日发送上限
 * （sms:daily:{phone}，SMS_DAILY_LIMIT=10，TTL 到当天结束），与 30-security.spec.ts
 * 的 D-2 组需间隔执行（CI 中分属不同 step，天然隔离）。同日内五连实跑会累积
 * daily 计数打满上限 → beforeAll/afterAll 均清理该键（测试专用号 13900009998）。
 */
import { test, expect } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// ESM 兼容（package.json type=module，无 __dirname）
const __dirname = dirname(fileURLToPath(import.meta.url))

const SSH_KEY = process.env.E2E_SSH_KEY || resolve(__dirname, '../../../../keys/zwinsight.pem')
const SSH_HOST = process.env.E2E_SSH_HOST || 'root@129.204.3.200'
const PHONE = '13900009998'
const USER = 't9999user'
/** BCrypt("123456")，与 keys/init-test-tenant.sh 同一常量 */
const BCRYPT_123456 = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi'
const NEW_PASSWORD = 'E2eUiPwd2026x'

function runRemote(command: string): string {
  const b64 = Buffer.from(command, 'utf-8').toString('base64')
  return execFileSync('ssh', [
    '-i', SSH_KEY, '-o', 'StrictHostKeyChecking=no', '-o', 'ConnectTimeout=10',
    SSH_HOST, `echo ${b64} | base64 -d | bash`,
  ], { encoding: 'utf-8', timeout: 40_000 }).replace(/\r/g, '')
}

function getSmsCode(): string | null {
  const out = runRemote(`docker exec zwi-redis redis-cli GET 'sms:${PHONE}'`).trim()
  if (out === '' || out === '(nil)') return null
  return out.replace(/^"|"$/g, '')
}

// 未认证上下文（找回密码页属登录前流程）
test.use({ storageState: { cookies: [], origins: [] } })

test.describe('忘记密码 UI 真实走查（@matrix D-2）', () => {
  test.beforeAll(async () => {
    // 日发送上限键清理：同日内多轮实跑会累积 sms:daily 计数（上限 10），
    // 打满后 send-code 被拒、步骤 1 无法推进（2026-08-18 W6 五连实跑实证）
    try {
      runRemote(`docker exec zwi-redis redis-cli DEL 'sms:daily:${PHONE}' 'sms:freq:${PHONE}'`)
    } catch { /* 清理失败不阻塞：用例内发码失败会以断言形式暴露 */ }
  })

  test.afterAll(async () => {
    // 恢复密码为 123456（无论用例成败）
    try {
      runRemote(
        `echo "${Buffer.from(`UPDATE sys_user SET password='${BCRYPT_123456}' WHERE username='${USER}'`, 'utf-8').toString('base64')}" | base64 -d | docker exec -i zwi-mysql mysql -uroot -pzwinsight123 --default-character-set=utf8mb4 zw_insight`
      )
    } catch (e) {
      // 不静默：恢复失败将致 t9999user 残留测试密码，30-security D-2 组连锁失败
      console.warn(`[Cleanup] t9999user 密码恢复失败，需手动重置为 123456:`, e)
    }
    // 清理短信频控键（含日计数），避免影响后续批次
    try {
      runRemote(`docker exec zwi-redis redis-cli DEL 'sms:${PHONE}' 'sms:freq:${PHONE}' 'sms:daily:${PHONE}' 'pwd_reset:lock:${PHONE}' 'pwd_reset:verify_fail:${PHONE}'`)
    } catch { /* 卫生清理 */ }
  })

  test('三步重置密码：发码→SSH 取码→校验→重置→跳登录页', async ({ page }) => {
    await page.goto('/forgot-password')
    await expect(page.locator('.forgot-title')).toHaveText('找回密码')

    // 步骤 1：输入手机号并发送验证码
    await page.locator('input[placeholder="请输入手机号"]').fill(PHONE)
    await page.getByRole('button', { name: /发送验证码/ }).click()
    // 步骤推进到第 2 步（出现验证码输入框与脱敏手机号提示）
    await expect(page.locator('input[placeholder="请输入 6 位验证码"]')).toBeVisible({ timeout: 15_000 })
    await expect(page.locator('.tip-text').first()).toContainText('139****9998')

    // 取码：SSH 读 Redis（模拟短信模式码仍写 Redis）
    let code = ''
    for (let i = 0; i < 5 && !code; i++) {
      code = getSmsCode() || ''
      if (!code) await page.waitForTimeout(1000)
    }
    expect(code, 'Redis 应可读到短信验证码').toMatch(/^\d{6}$/)

    // 步骤 2：校验验证码
    await page.locator('input[placeholder="请输入 6 位验证码"]').fill(code)
    await page.getByRole('button', { name: '下一步' }).click()
    await expect(page.locator('input[placeholder="请输入新密码"]')).toBeVisible({ timeout: 15_000 })

    // 步骤 3：设置新密码并重置
    await page.locator('input[placeholder="请输入新密码"]').fill(NEW_PASSWORD)
    await page.locator('input[placeholder="请再次输入新密码"]').fill(NEW_PASSWORD)
    await page.getByRole('button', { name: '重置密码' }).click()

    // 成功跳转登录页
    await page.waitForURL(/\/login/, { timeout: 15_000 })
    expect(page.url()).toContain('/login')
  })
})
