/**
 * 真实模式 E2E 共享工具（账本全量补齐 M0，2026-08）
 *
 * 从 expense-write-2.spec.ts / finance-write.spec.ts 已实证范式抽取，
 * 供 a1-project / b2-machine 等按账本分组的补测 spec 复用。
 *
 * 纪律（与既有 spec 一致）：
 * - authed API context 从 storageState 读 token（禁止重新 API 登录——
 *   admin max-devices=5，新会话会踢出 UI storageState 会话，P2 实跑事故先例）
 * - 测试数据自置一律 E2E_TEST_ 前缀（后端 E2eTestGuard 删除守卫放行；
 *   引用完整性守卫不放行 → CreatedTracker 须按子→父逆序清理）
 * - 全程真实接口，无 mock 无静默降级
 */
import { expect, request as pwRequest } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

export const API_BASE = process.env.E2E_API_BASE || 'http://129.204.3.200:18080'
export const SSH_KEY = process.env.E2E_SSH_KEY || resolve(__dirname, '../../../../keys/zwinsight.pem')
export const SSH_HOST = process.env.E2E_SSH_HOST || 'root@129.204.3.200'

export type AuthedContext = Awaited<ReturnType<typeof pwRequest.newContext>>

/**
 * 从 storageState 读登录 token 并创建带 Authorization 头的 API context。
 * 在 test.beforeAll 中调用；调用方负责在 afterAll 调 ctx.dispose()。
 */
export async function authedApiContext(): Promise<AuthedContext> {
  const st = JSON.parse(readFileSync('./e2e/.auth/storage-state.json', 'utf-8'))
  const token = (st.origins || []).flatMap((o: any) => o.localStorage || [])
    .find((kv: any) => kv.name === 'token')?.value
  expect(token, 'storageState 应含登录 token').toBeTruthy()
  return pwRequest.newContext({
    extraHTTPHeaders: { Authorization: `Bearer ${token}` },
  })
}

/** 测试数据命名前缀（与后端 E2eTestGuard.E2E_TEST_PREFIX 契约一致） */
export function e2ePrefix(ts: number = Date.now()): string {
  return `E2E_TEST_${ts}`
}

/**
 * 当天日期 yyyy-MM-dd（表单日期字段自置用）。
 * 时区纪律（2026-08-21 实证）：GitHub runner 为 UTC，后端 JVM 为 Asia/Shanghai；
 * 当 CI 落在 UTC 16:00~24:00（北京已跨天）时 runner 本地日期比服务端晚一天，
 * b2-machine B-10-2 `reportDate 强制今天` 断言 Expected/Received 跨日失败
 * （run 32390426243，Expected 2026-08-20 / Received 2026-08-21）。
 * 故"今天"一律以服务端时区 Asia/Shanghai 为口径，与后端 LocalDate.now() 对齐。
 */
export function todayStr(): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date())
}

/**
 * 创建资源登记器：按登记顺序收集 (kind, id)，cleanup 时逆序删除。
 * 逆序保证子表先于父表删除（引用完整性守卫不放行 E2E_TEST_ 前缀）。
 */
export class CreatedTracker {
  private items: { kind: string; id: string | number }[] = []

  track(kind: string, id: string | number): string | number {
    this.items.push({ kind, id })
    return id
  }

  /**
   * 逆序清理；deleteFn 返回的 Promise 失败仅告警不抛出
   * （清理失败会在服务器上累积 E2E_TEST_ 残留，由 verify-l4-clean 类巡检兜底）
   */
  async cleanup(
    deleteFn: (kind: string, id: string | number) => Promise<unknown>
  ): Promise<void> {
    for (const { kind, id } of [...this.items].reverse()) {
      try {
        await deleteFn(kind, id)
      } catch (e) {
        console.warn(`[CreatedTracker] 清理失败 kind=${kind} id=${id}:`, (e as Error).message)
      }
    }
  }
}

/**
 * SSH 远程执行（base64 编码传输，避免引号转义问题）。
 * 用于 Redis 频控键清理等测试卫生操作（forgot-password 实证模式）。
 */
export function runRemote(command: string): string {
  const b64 = Buffer.from(command, 'utf-8').toString('base64')
  return execFileSync('ssh', [
    '-i', SSH_KEY, '-o', 'StrictHostKeyChecking=no', '-o', 'ConnectTimeout=10',
    SSH_HOST, `echo ${b64} | base64 -d | bash`,
  ], { encoding: 'utf-8', timeout: 40_000 }).replace(/\r/g, '')
}

/** 断言响应 HTTP 200（列表/详情类用例锚定真实请求的通用助手；业务 code 由调用方按需断言） */
export function expectOkJson(resp: { status(): number }, what: string) {
  expect(resp.status(), `${what} HTTP 状态`).toBe(200)
}
