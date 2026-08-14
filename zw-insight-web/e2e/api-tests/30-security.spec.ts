/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * 30 - 安全机制端到端（P0 补测，副作用零外溢设计）
 *
 * @matrix P0-安全机制 | tests/frontend-test-case-matrix.md 附录二
 *   D-1-5 → 验证码 uuid 一次性消费（开关探测自适应；联调 captcha-enabled=false 受阻登记）
 *   D-1-6 → IP 失败锁定（5次/5分钟→锁15分钟，CaptchaService L226-230 实证）
 *   D-2   → 忘记密码全流程（发码→peek校验→reset消费→全token失效；sms.enabled=false 模拟模式码仍写 Redis）
 *   D-3-9 → 多设备上限踢出（max-devices=5，DeviceManagerService L164-203 实证）
 *
 * 副作用隔离纪律（2026-08-13 IP 锁定连锁事故教训）：
 * - 全部负向登录用 DB 自建一次性专用账号（E2E_SEC_*），绝不触碰 admin/t9999admin 会话
 * - IP 锁定注入 X-Forwarded-For TEST-NET 假 IP（AuthController.getClientIp 信任该头实证），
 *   真实来源 IP 永不入锁
 * - afterAll 清扫全部 Redis 锁定键 + 删除专用账号/设备记录 + 恢复 t9999user 密码
 */
import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import { ApiClient } from './api-client'
import { createAuthedClient } from './setup'
import { PREFIX } from './test-data'
import {
  getRedisKey, getSmsCode, delRedisKeys, queryMysql, execMysql,
} from './helpers/redis-probe'

const TS = Date.now()
/** BCrypt("123456")，与 keys/init-test-tenant.sh 同一常量（实证） */
const BCRYPT_123456 = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi'

// 专用账号（租户 1）：IP 锁定 / 账号锁定 / 设备踢出 各一，互不干扰
const IP_USER = `E2E_SEC_IP_${TS}`
const ACC_USER = `E2E_SEC_ACC_${TS}`
const DEV_USER = `E2E_SEC_DEV_${TS}`
const IP_USER_ID = 9880000000 + (TS % 1000000)
const ACC_USER_ID = IP_USER_ID + 1
const DEV_USER_ID = IP_USER_ID + 2
// TEST-NET-3（RFC 5737 文档保留段，永不为真实来源 IP）
const FAKE_IP_1 = '203.0.113.51'
const FAKE_IP_2 = '203.0.113.52'

// 忘记密码目标：t9999user（租户 9999 低权限用户，重置会使该用户全部 token 失效，
// 故严禁对 admin/t9999admin 执行；phone 13900009998 为 init-test-tenant.sh 实证）
const RESET_PHONE = '13900009998'
const RESET_USER = 't9999user'
const NEW_PASSWORD = `E2ePwd${TS % 100000}x` // 满足 8-20 位字母+数字（PASSWORD_PATTERN 实证）

/** 独立登录（不共享全局 token；extraHeaders 注入 XFF/设备头） */
async function rawLogin(
  username: string,
  password: string,
  extraHeaders?: Record<string, string>
): Promise<{ code: number; message: string; token?: string }> {
  const c = new ApiClient()
  try {
    const data = await c.login(username, password, extraHeaders)
    return { code: 200, message: 'ok', token: data.token }
  } catch (e: any) {
    return { code: -1, message: String(e?.message || e) }
  }
}

/** 直接 POST 登录端点（需要拿到业务 code/message 而非异常） */
async function loginAttempt(
  username: string,
  password: string,
  extraHeaders?: Record<string, string>
): Promise<any> {
  const c = new ApiClient()
  return c.post('/api/v1/auth/login', {
    loginType: 'PASSWORD',
    username,
    password,
  }, extraHeaders)
}

describe('30 - 安全机制端到端', () => {
  let admin: ApiClient

  beforeAll(async () => {
    admin = createAuthedClient()
    // DB 自建一次性专用账号（租户 1；INSERT IGNORE 幂等）
    for (const [id, name, phone] of [
      [IP_USER_ID, IP_USER, `138${String(TS % 100000000).padStart(8, '0')}`],
      [ACC_USER_ID, ACC_USER, `137${String(TS % 100000000).padStart(8, '0')}`],
      [DEV_USER_ID, DEV_USER, `136${String(TS % 100000000).padStart(8, '0')}`],
    ] as Array<[number, string, string]>) {
      execMysql(
        `INSERT IGNORE INTO sys_user (id, username, password, real_name, phone, email, avatar, status, ` +
        `org_id, post_id, tenant_id, created_by, created_at, updated_at, deleted, version) ` +
        `VALUES (${id}, '${name}', '${BCRYPT_123456}', '${name}', '${phone}', NULL, NULL, 1, ` +
        `NULL, NULL, 1, 1, NOW(), NOW(), 0, 0)`
      )
    }
    // 前置卫生：清扫可能残留的锁定键
    delRedisKeys(
      `login:ip:fail:${FAKE_IP_1}`, `login:ip:lock:${FAKE_IP_1}`,
      `login:ip:fail:${FAKE_IP_2}`, `login:ip:lock:${FAKE_IP_2}`,
      `login_fail:${IP_USER}`, `login_fail:${ACC_USER}`,
      `pwd_reset:lock:${RESET_PHONE}`, `pwd_reset:verify_fail:${RESET_PHONE}`
    )
  }, 120_000)

  afterAll(async () => {
    // Redis 清扫（锁定键/验证码键）
    try {
      delRedisKeys(
        `login:ip:fail:${FAKE_IP_1}`, `login:ip:lock:${FAKE_IP_1}`,
        `login:ip:fail:${FAKE_IP_2}`, `login:ip:lock:${FAKE_IP_2}`,
        `login_fail:${IP_USER}`, `login_fail:${ACC_USER}`,
        `pwd_reset:lock:${RESET_PHONE}`, `pwd_reset:verify_fail:${RESET_PHONE}`,
        `sms:${RESET_PHONE}`, `sms:freq:${RESET_PHONE}`
      )
    } catch { /* 卫生清理失败不遮蔽 */ }
    // 恢复 t9999user 密码为 123456（DB 直改，BCrypt 常量；避免跨租户 API 权限问题）
    try {
      execMysql(`UPDATE sys_user SET password='${BCRYPT_123456}' WHERE username='${RESET_USER}'`)
    } catch { /* 恢复失败登记台账 */ }
    // 删除专用账号与设备记录
    try {
      execMysql(`DELETE FROM sys_login_device WHERE user_id IN (${IP_USER_ID}, ${ACC_USER_ID}, ${DEV_USER_ID})`)
      execMysql(`DELETE FROM sys_user WHERE username LIKE 'E2E_SEC_%'`)
    } catch { /* 清理失败不遮蔽 */ }
  }, 120_000)

  // ============ D-1-6 IP 失败锁定 ============
  describe('D-1-6 IP 失败锁定（5次/5分钟→锁15分钟）', () => {
    // @matrix D-1-6
    it('连续 5 次错误密码后第 6 次正确密码仍被拒（IP 锁定生效）', async () => {
      const xff = { 'X-Forwarded-For': FAKE_IP_1 }
      for (let i = 1; i <= 5; i++) {
        const resp = await loginAttempt(IP_USER, 'wrong-password', xff)
        expect(resp.code, `第 ${i} 次错误密码应被拒`).not.toBe(200)
      }
      // 第 6 次正确密码：IP 已锁，仍被拒
      const locked = await loginAttempt(IP_USER, '123456', xff)
      expect(locked.code, '锁定后正确密码仍被拒').not.toBe(200)
    })

    // @matrix D-1-6
    it('Redis 锁定键 login:ip:lock 存在（SSH 实证）', async () => {
      const lockVal = getRedisKey(`login:ip:lock:${FAKE_IP_1}`)
      expect(lockVal, '锁定键应存在').not.toBeNull()
    })

    // @matrix D-1-6
    it('解锁（DEL 锁定键）后登录恢复', async () => {
      delRedisKeys(`login:ip:lock:${FAKE_IP_1}`, `login:ip:fail:${FAKE_IP_1}`,
        `login_fail:${IP_USER}`)
      const resp = await loginAttempt(IP_USER, '123456', { 'X-Forwarded-For': FAKE_IP_1 })
      expect(resp.code, '解锁后正确密码应成功').toBe(200)
      expect(resp.data?.token).toBeTruthy()
    })

    // @matrix D-1-6（负向边界：未达阈值不锁定）
    it('4 次失败未达阈值：第 5 次正确密码可登录', async () => {
      const xff = { 'X-Forwarded-For': FAKE_IP_2 }
      for (let i = 1; i <= 4; i++) {
        await loginAttempt(IP_USER, 'wrong-password', xff)
      }
      const resp = await loginAttempt(IP_USER, '123456', xff)
      expect(resp.code, '未达 5 次阈值不应锁定').toBe(200)
      // 卫生：清计数
      delRedisKeys(`login:ip:fail:${FAKE_IP_2}`, `login_fail:${IP_USER}`)
    })
  })

  // ============ 账号锁定（lock-enabled=true，5次→锁30分钟） ============
  describe('账号失败锁定（同一账号跨 IP 累计）', () => {
    // @matrix D-1-6（账号维度）
    it('账号连续 5 次错误密码后被锁定（正确密码亦拒）', async () => {
      // 每次用不同假 IP，确保触发的是账号锁定而非 IP 锁定
      for (let i = 1; i <= 5; i++) {
        const resp = await loginAttempt(ACC_USER, 'wrong-password', {
          'X-Forwarded-For': `203.0.113.${60 + i}`,
        })
        expect(resp.code).not.toBe(200)
      }
      const locked = await loginAttempt(ACC_USER, '123456', {
        'X-Forwarded-For': '203.0.113.66',
      })
      expect(locked.code, '账号锁定后正确密码仍被拒').not.toBe(200)
    })

    // @matrix D-1-6（账号维度恢复）
    it('清除账号锁定计数后登录恢复', async () => {
      delRedisKeys(`login_fail:${ACC_USER}`)
      const resp = await loginAttempt(ACC_USER, '123456', {
        'X-Forwarded-For': '203.0.113.67',
      })
      expect(resp.code).toBe(200)
    })
  })

  // ============ D-3-9 多设备上限踢出 ============
  describe('D-3-9 多设备上限踢出（max-devices=5）', () => {
    const tokens: string[] = []

    // @matrix D-3-9
    it('同一账号 6 台设备登录：最早设备 token 被淘汰失效', async () => {
      for (let i = 1; i <= 6; i++) {
        const resp = await loginAttempt(DEV_USER, '123456', {
          'X-Device-Id': `E2E-DEV-${TS}-${i}`,
          'X-Device-Name': `E2E-Device-${i}`, // HTTP 头仅 ASCII（实证：中文触发 ByteString 异常）
          'X-Device-Os': 'E2E-OS',
        })
        expect(resp.code, `第 ${i} 台设备登录`).toBe(200)
        tokens.push(resp.data.token)
        // 淘汰按 loginTime 升序（秒级精度）：同秒内多次登录排序不确定，
        // 可能误汰新 token——每次登录间隔 >1s 保证时序确定性（后端语义正确，测试适配）
        if (i < 6) await new Promise((r) => setTimeout(r, 1100))
      }
      // 最早（第 1 台）token 已被淘汰：调受保护接口（设备列表）应 401
      const evicted = new ApiClient()
      evicted.setToken(tokens[0])
      const evictedResp = await evicted.get('/api/v1/user/devices/list')
      expect(evictedResp.code, '被淘汰设备 token 应失效').not.toBe(200)
      // 最新（第 6 台）token 有效
      const latest = new ApiClient()
      latest.setToken(tokens[5])
      const latestResp = await latest.get('/api/v1/user/devices/list')
      expect(latestResp.code, '最新设备 token 应有效').toBe(200)
    }, 60_000)

    // @matrix D-3-9
    it('设备列表活跃设备数 ≤ 5', async () => {
      const latest = new ApiClient()
      latest.setToken(tokens[5])
      const resp = await latest.get('/api/v1/user/devices/list')
      expect(resp.code).toBe(200)
      const devices: any[] = Array.isArray(resp.data) ? resp.data : (resp.data?.records || [])
      const active = devices.filter((d) => d.status === 1 || d.status === '1')
      expect(active.length, '活跃设备数不得超过 max-devices=5').toBeLessThanOrEqual(5)
    })

    // @matrix D-3-9
    it('注销当前设备被拒（DeviceManagerService 禁止实证）', async () => {
      const latest = new ApiClient()
      latest.setToken(tokens[5])
      const listResp = await latest.get('/api/v1/user/devices/list')
      const devices: any[] = Array.isArray(listResp.data) ? listResp.data : (listResp.data?.records || [])
      // 当前设备 = 本次登录设备标识（X-Device-Id 第 6 台）
      const current = devices.find((d) =>
        d.deviceId === `E2E-DEV-${TS}-6` || d.isCurrent === true || d.isCurrent === 1) || devices[0]
      expect(current, '设备列表应有当前设备').toBeDefined()
      const revokeResp = await latest.delete(`/api/v1/user/devices/${current.id}`)
      expect(revokeResp.code, '注销当前设备应被拒').not.toBe(200)
    })
  })

  // ============ D-2 忘记密码全流程 ============
  describe('D-2 忘记密码（t9999user，严禁对 admin 执行）', () => {
    let smsCode = ''

    beforeAll(() => {
      // 清理短信日限/频控键：同一天多遍全量实跑会打满 sms:daily 上限（10 次/日实证），
      // 测试账号配额属测试基建，清理不影响真实业务
      delRedisKeys(`sms:daily:${RESET_PHONE}`, `sms:freq:${RESET_PHONE}`)
    })

    // @matrix D-2
    it('发送重置验证码成功（sms 模拟模式码仍写 Redis）', async () => {
      const resp = await admin.post('/api/v1/auth/password-reset/send-code', {
        phone: RESET_PHONE,
      })
      expect(resp.code, `发送验证码：${resp.message}`).toBe(200)
      smsCode = getSmsCode(RESET_PHONE) || ''
      expect(smsCode, 'Redis sms:{phone} 应可读到验证码').toMatch(/^\d{6}$/)
    })

    // @matrix D-2（负向：错误码 5 次→锁定 30 分钟）
    it('负向：连续 5 次错误验证码后被锁定', async () => {
      for (let i = 1; i <= 5; i++) {
        const resp = await admin.post('/api/v1/auth/password-reset/verify-code', {
          phone: RESET_PHONE,
          code: '000000',
        })
        expect(resp.code, `第 ${i} 次错误验证码应被拒`).not.toBe(200)
      }
      // 锁定后即使正确码也被拒
      const locked = await admin.post('/api/v1/auth/password-reset/verify-code', {
        phone: RESET_PHONE,
        code: smsCode,
      })
      expect(locked.code, '锁定后正确验证码仍被拒').not.toBe(200)
      expect(getRedisKey(`pwd_reset:lock:${RESET_PHONE}`), '锁定键应存在').not.toBeNull()
      // 解锁进入主流程
      delRedisKeys(`pwd_reset:lock:${RESET_PHONE}`, `pwd_reset:verify_fail:${RESET_PHONE}`)
    })

    // @matrix D-2（verify 为非消费式 peek）
    it('verify-code 校验正确验证码通过（peek 不消费）', async () => {
      const resp = await admin.post('/api/v1/auth/password-reset/verify-code', {
        phone: RESET_PHONE,
        code: smsCode,
      })
      expect(resp.code, `verify-code：${resp.message}`).toBe(200)
    })

    // @matrix D-2（reset 消费验证码并改密）
    it('reset 重置密码成功', async () => {
      const resp = await admin.post('/api/v1/auth/password-reset/reset', {
        phone: RESET_PHONE,
        code: smsCode,
        newPassword: NEW_PASSWORD,
      })
      expect(resp.code, `reset：${resp.message}`).toBe(200)
    })

    // @matrix D-2（重置后旧密码失效/新密码生效/全部 token 失效）
    it('重置后旧密码登录失败、新密码登录成功', async () => {
      const oldResp = await loginAttempt(RESET_USER, '123456')
      expect(oldResp.code, '旧密码应失效').not.toBe(200)
      const newResp = await loginAttempt(RESET_USER, NEW_PASSWORD)
      expect(newResp.code, `新密码应生效：${newResp.message}`).toBe(200)
    })

    // @matrix D-2（验证码一次性：同码二次 reset 被拒）
    it('同验证码二次 reset 被拒（一次性消费）', async () => {
      const resp = await admin.post('/api/v1/auth/password-reset/reset', {
        phone: RESET_PHONE,
        code: smsCode,
        newPassword: `E2ePwd${(TS % 100000) + 1}x`,
      })
      expect(resp.code, '验证码已消费，二次 reset 应被拒').not.toBe(200)
    })

    // @matrix D-2（负向：未注册手机号发码被拒）
    it('负向：未注册手机号发送验证码被拒', async () => {
      const resp = await admin.post('/api/v1/auth/password-reset/send-code', {
        phone: '13900000000',
      })
      expect(resp.code).not.toBe(200)
    })
  })

  // ============ D-1-5 验证码一次性消费（开关探测自适应） ============
  describe('D-1-5 图形验证码一次性消费', () => {
    // @matrix D-1-5
    // 联调环境 captcha-enabled=false（application-dev.yml L70-71 实证，注释"上线前须改回 true"）：
    // 登录链不消费 uuid，运行时无法验证一次性语义 → 探测到关闭态即动态 skip，
    // 受阻事实登记 .kiro/specs/test-maturity-upgrade/tasks.md 台账（ENV 类），不伪造通过。
    it('探测开关并验证一次性消费（开关关闭则 skip 登记台账）', async (ctx) => {
      // 探测：无验证码直接登录成功 = 开关关闭（api-client.login 同逻辑）
      const probe = await loginAttempt(IP_USER, '123456')
      if (probe.code === 200) {
        console.warn(
          '[D-1-5 受阻登记] 联调 captcha-enabled=false：登录链不消费验证码 uuid，' +
          '一次性消费用例无法真实验证。待上线开关恢复 true 后补测。（ENV 类，台账同步登记）'
        )
        ctx.skip()
        return
      }
      // 开关开启：取码→首次登录成功→同 uuid 二次登录被拒（key 已删）
      const captchaResp = await admin.get<{ uuid: string; imageBase64: string }>('/api/v1/captcha/image')
      const uuid = captchaResp.data?.uuid
      expect(uuid).toBeTruthy()
      const code = getRedisKey(`captcha:${uuid}`)
      expect(code, 'SSH 应能读到验证码答案').toBeTruthy()

      const c = new ApiClient()
      const firstReal = await c.post('/api/v1/auth/login', {
        loginType: 'PASSWORD', username: IP_USER, password: '123456',
        captchaUuid: uuid, captchaCode: code,
      })
      expect(firstReal.code, '首次使用验证码应成功').toBe(200)
      const second = await new ApiClient().post('/api/v1/auth/login', {
        loginType: 'PASSWORD', username: IP_USER, password: '123456',
        captchaUuid: uuid, captchaCode: code,
      })
      expect(second.code, '同 uuid 二次使用应被拒（一次性消费）').not.toBe(200)
    })
  })
})
