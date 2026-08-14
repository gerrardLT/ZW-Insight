/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * SSH → docker exec 服务器探测/清理通道（2026-08-14 P0 补测基建）
 *
 * 复刻 e2e/fixtures/auth-real.setup.ts 的 execFileSync('ssh') 模式：
 * - Redis 读写（验证码/短信码/锁定键）经 zwi-redis 容器
 * - MySQL 查询（ACT_RU_TASK 兜底/测试账号管理）经 zwi-mysql 容器
 *
 * 环境变量（与 auth-real.setup.ts 同口径）：
 * - E2E_SSH_KEY  SSH 私钥路径（本地默认仓库内 keys/zwinsight.pem；CI 显式指定 deploy_key）
 * - E2E_SSH_HOST SSH 目标（默认 root@129.204.3.200）
 *
 * 纪律：SSH 失败直接抛错由调用方登记受阻台账，禁止静默兜底。
 */
import { execFileSync } from 'node:child_process'
import { resolve } from 'node:path'

// helpers/ → api-tests/ → e2e/ → zw-insight-web/ → 仓库根
const SSH_KEY = process.env.E2E_SSH_KEY || resolve(__dirname, '../../../../keys/zwinsight.pem')
const SSH_HOST = process.env.E2E_SSH_HOST || 'root@129.204.3.200'

/**
 * 在联调服务器上执行远程命令并返回 stdout（失败抛错）
 *
 * Windows 兼容：本机 ssh 将远程命令整体作为单个 argv 传给远端 shell，
 * 含空格命令会被拆词（实测 SQL 语句失败），故用 base64 包装传输，
 * 远端 `base64 -d | bash` 还原执行，无需服务器侧任何变更。
 */
export function runRemote(command: string, timeoutMs = 30_000): string {
  const b64 = Buffer.from(command, 'utf-8').toString('base64')
  const out = execFileSync(
    'ssh',
    [
      '-i', SSH_KEY,
      '-o', 'StrictHostKeyChecking=no',
      '-o', 'ConnectTimeout=10',
      SSH_HOST,
      `echo ${b64} | base64 -d | bash`,
    ],
    { encoding: 'utf-8', timeout: timeoutMs }
  )
  return out.replace(/\r/g, '')
}

/** 读取 Redis 键值（不存在返回 null） */
export function getRedisKey(key: string): string | null {
  const out = runRemote(`docker exec zwi-redis redis-cli GET '${key}'`).trim()
  if (out === '' || out === '(nil)') return null
  return out.replace(/^"|"$/g, '')
}

/** 读取图形验证码答案 captcha:{uuid} */
export function getCaptchaCode(uuid: string): string | null {
  return getRedisKey(`captcha:${uuid}`)
}

/** 读取短信验证码 sms:{phone}（sms.enabled=false 模拟模式下仍写 Redis） */
export function getSmsCode(phone: string): string | null {
  return getRedisKey(`sms:${phone}`)
}

/** 删除指定 Redis 键（返回删除数量；失败抛错） */
export function delRedisKeys(...keys: string[]): number {
  if (keys.length === 0) return 0
  const quoted = keys.map((k) => `'${k}'`).join(' ')
  const out = runRemote(`docker exec zwi-redis redis-cli DEL ${quoted}`).trim()
  const n = parseInt(out, 10)
  return Number.isNaN(n) ? 0 : n
}

/** 按 pattern 扫描并删除 Redis 键（用于锁定键兜底清扫） */
export function delRedisPattern(pattern: string): number {
  const out = runRemote(
    `docker exec zwi-redis sh -c "redis-cli --scan --pattern '${pattern}' | xargs -r redis-cli DEL"`
  ).trim()
  const n = parseInt(out, 10)
  return Number.isNaN(n) ? 0 : n
}

/** 执行 MySQL 查询（zw_insight 库），返回原始 stdout（制表符分隔） */
export function queryMysql(sql: string, timeoutMs = 30_000): string {
  // 双引号包裹 -e 参数，SQL 内只允许单引号
  if (sql.includes('"')) {
    throw new Error('[redis-probe] queryMysql: SQL 内不得含双引号，请改用单引号')
  }
  return runRemote(
    `docker exec zwi-mysql mysql -uroot -pzwinsight123 zw_insight -N -e "${sql}" 2>/dev/null`,
    timeoutMs
  )
}

/**
 * 执行 MySQL 写操作（INSERT/UPDATE/DELETE），失败抛错。
 *
 * 特殊字符安全：经 base64 管道 stdin 传 SQL（mysql --default-character-set=utf8mb4），
 * 避免 SSH 命令行多层引号嵌套吞掉 $ 等字符（实证：BCrypt 哈希 $2a 前缀曾因此损坏）。
 */
export function execMysql(sql: string, timeoutMs = 30_000): void {
  const b64 = Buffer.from(sql, 'utf-8').toString('base64')
  runRemote(
    `echo '${b64}' | base64 -d | docker exec -i zwi-mysql mysql -uroot -pzwinsight123 --default-character-set=utf8mb4 zw_insight`,
    timeoutMs
  )
}
