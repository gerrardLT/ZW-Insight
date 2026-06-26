/**
 * SyncEngine — 移动端离线操作同步引擎
 *
 * 负责：
 *  - enqueue()：离线状态下将写操作（新增/编辑）记录到本地操作队列（需求 5.1）
 *  - syncAll()：恢复在线后，按操作时间顺序逐条提交队列中的请求（需求 5.2）
 *      - 服务端 2xx → 标记 SYNCED 并从队列移除（需求 5.4）
 *      - 版本冲突（HTTP 409 / 业务码 409）→ handleConflict() 标记 CONFLICT 并保留队列（需求 5.3）
 *      - 网络再次中断 → 暂停同步并保留未提交队列，按重试策略稍后继续（需求 5.5、4.9）
 *  - handleConflict()：将冲突操作标记 CONFLICT，通知用户手动处理（需求 5.3）
 *  - compareVersions()：联网后比对本地缓存版本号与服务端版本号，
 *      对不一致数据执行全量覆盖（以服务端为准，复用 offlineCache.sync()）（需求 4.3）
 *  - 重试逻辑：失败后 5 分钟自动重试，最多 3 次，3 次均失败后停止并通知用户（需求 4.9）
 *
 * 真实链路：操作通过 uni.request 直接提交至 op.endpoint，
 * 区分「成功 / 版本冲突 / 网络中断 / 服务端错误」四种结果，无 fallback、无静默处理。
 *
 * 对应需求：5.1, 5.2, 5.3, 5.4, 5.5, 4.3, 4.9
 */

import { offlineCache, STORAGE_KEYS } from './offlineCache'

// ---------------------------------------------------------------------------
// 常量
// ---------------------------------------------------------------------------

/** 与 utils/request.ts 保持一致的接口前缀 */
const BASE_URL = '/api'

// ---------------------------------------------------------------------------
// 类型定义
// ---------------------------------------------------------------------------

/** 离线操作类型 */
export type OfflineOperationType = 'CREATE' | 'UPDATE'

/** 离线操作状态 */
export type OfflineOperationStatus = 'PENDING' | 'SYNCED' | 'CONFLICT' | 'FAILED'

/** 单条离线操作记录 */
export interface OfflineOperation {
  /** 操作唯一标识 */
  id: string
  /** 操作类型：新增 / 编辑 */
  type: OfflineOperationType
  /** 目标接口路径（不含 BASE_URL 前缀，如 '/v1/material/inbound'） */
  endpoint: string
  /** 请求负载 */
  payload: any
  /** 操作发生时间戳（ms），同步按此升序提交 */
  timestamp: number
  /** 当前状态 */
  status: OfflineOperationStatus
}

/** 单条操作提交结果分类 */
type SubmitResultKind = 'success' | 'conflict' | 'network' | 'error'

interface SubmitResult {
  kind: SubmitResultKind
  /** 错误/冲突描述信息 */
  message?: string
}

// ---------------------------------------------------------------------------
// SyncEngine
// ---------------------------------------------------------------------------

export class SyncEngine {
  /** 最大自动重试次数（需求 4.9） */
  private readonly MAX_RETRY = 3
  /** 重试间隔：5 分钟（需求 4.9） */
  private readonly RETRY_INTERVAL_MS = 5 * 60 * 1000

  /** 当前已发生的连续重试次数 */
  private retryCount = 0
  /** 重试定时器句柄，便于取消 */
  private retryTimer: ReturnType<typeof setTimeout> | null = null
  /** 防止 syncAll 并发执行 */
  private syncing = false

  // -------------------------------------------------------------------------
  // 队列读写
  // -------------------------------------------------------------------------

  /** 读取本地操作队列 */
  private readQueue(): OfflineOperation[] {
    try {
      const raw = uni.getStorageSync(STORAGE_KEYS.OP_QUEUE)
      if (Array.isArray(raw)) {
        return raw as OfflineOperation[]
      }
    } catch {
      // 读取失败按空队列处理
    }
    return []
  }

  /** 写入本地操作队列 */
  private writeQueue(queue: OfflineOperation[]): void {
    uni.setStorageSync(STORAGE_KEYS.OP_QUEUE, queue)
  }

  /** 生成操作唯一 id */
  private genId(): string {
    return `op_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
  }

  // -------------------------------------------------------------------------
  // 公共 API
  // -------------------------------------------------------------------------

  /** 当前操作队列快照（只读副本） */
  getQueue(): OfflineOperation[] {
    return this.readQueue()
  }

  /**
   * 入队一条离线操作（需求 5.1）
   * 缺失 id / timestamp / status 时自动补全，状态默认 PENDING。
   * @param operation 离线操作（id、timestamp、status 可省略）
   * @returns 实际入队的完整操作记录
   */
  enqueue(
    operation: Omit<OfflineOperation, 'id' | 'timestamp' | 'status'> &
      Partial<Pick<OfflineOperation, 'id' | 'timestamp' | 'status'>>
  ): OfflineOperation {
    const op: OfflineOperation = {
      id: operation.id ?? this.genId(),
      type: operation.type,
      endpoint: operation.endpoint,
      payload: operation.payload,
      timestamp: operation.timestamp ?? Date.now(),
      status: operation.status ?? 'PENDING'
    }
    const queue = this.readQueue()
    queue.push(op)
    this.writeQueue(queue)
    return op
  }

  /**
   * 按时间顺序逐条提交操作队列（需求 5.2、5.3、5.4、5.5）
   *
   * 处理规则：
   *  - 成功（2xx）：标记 SYNCED 并从队列移除（需求 5.4）
   *  - 版本冲突（409）：handleConflict() 标记 CONFLICT，保留在队列，继续处理后续操作（需求 5.3）
   *  - 网络中断：暂停同步、保留剩余队列、安排重试（需求 5.5、4.9）
   *  - 服务端其他错误：标记 FAILED、暂停同步、安排重试（需求 4.9）
   */
  async syncAll(): Promise<void> {
    if (this.syncing) return
    this.syncing = true

    try {
      // 取出待处理操作（PENDING / 之前失败的 FAILED），按时间升序
      const queue = this.readQueue()
      const pending = queue
        .filter((op) => op.status === 'PENDING' || op.status === 'FAILED')
        .sort((a, b) => a.timestamp - b.timestamp)

      let interrupted = false

      for (const op of pending) {
        const result = await this.submitOperation(op)

        if (result.kind === 'success') {
          // 成功 → 从队列移除（需求 5.4）
          this.removeFromQueue(op.id)
          continue
        }

        if (result.kind === 'conflict') {
          // 版本冲突 → 标记 CONFLICT 并保留（需求 5.3），继续处理后续操作
          this.handleConflict(op)
          continue
        }

        // 网络中断（需求 5.5）或服务端错误 → 暂停并保留队列
        if (result.kind === 'error') {
          this.updateStatus(op.id, 'FAILED')
        }
        interrupted = true
        break
      }

      if (interrupted) {
        // 网络/服务端失败 → 安排自动重试（需求 4.9）
        this.scheduleRetry()
      } else {
        // 整批处理完成（无中断）→ 重置重试计数
        this.resetRetry()
      }
    } finally {
      this.syncing = false
    }
  }

  /**
   * 处理同步冲突（需求 5.3）
   * 将操作标记为 CONFLICT，保留在队列中等待用户手动处理，并通知用户。
   */
  handleConflict(op: OfflineOperation): void {
    this.updateStatus(op.id, 'CONFLICT')
    this.notify('数据冲突，请手动处理')
  }

  /**
   * 版本号比对 + 全量覆盖（需求 4.3）
   * 联网恢复后，比对本地缓存版本号与服务端最新版本号，
   * 对版本不一致的数据执行全量覆盖更新（以服务端数据为准）。
   *
   * 复用 offlineCache.sync()：重新拉取材料字典、项目列表、用户信息并以新版本号全量覆盖，
   * 服务端数据始终为准（server wins）。
   */
  async compareVersions(): Promise<void> {
    // 记录覆盖前的本地版本号（用于判断是否发生变更）
    const before = this.localVersions()

    // 全量覆盖：服务端为准（offlineCache.sync 内部会先 markExpired 再拉取覆盖）
    await offlineCache.sync()

    const after = this.localVersions()

    // 版本号发生变化即视为完成全量覆盖更新
    const changed =
      before.material !== after.material ||
      before.project !== after.project ||
      before.user !== after.user
    if (changed) {
      this.notify('离线数据已更新至最新版本')
    }
  }

  // -------------------------------------------------------------------------
  // 内部实现
  // -------------------------------------------------------------------------

  /** 读取本地各缓存条目的版本号 */
  private localVersions(): { material: number; project: number; user: number } {
    return {
      material: offlineCache.get(STORAGE_KEYS.MATERIAL_DICT)?.version ?? 0,
      project: offlineCache.get(STORAGE_KEYS.PROJECT_LIST)?.version ?? 0,
      user: offlineCache.get(STORAGE_KEYS.USER_INFO)?.version ?? 0
    }
  }

  /** 从队列移除指定操作 */
  private removeFromQueue(id: string): void {
    const queue = this.readQueue().filter((op) => op.id !== id)
    this.writeQueue(queue)
  }

  /** 更新指定操作状态 */
  private updateStatus(id: string, status: OfflineOperationStatus): void {
    const queue = this.readQueue()
    const target = queue.find((op) => op.id === id)
    if (target) {
      target.status = status
      this.writeQueue(queue)
    }
  }

  /**
   * 提交单条操作到服务端，区分四种结果：
   *  - success：HTTP 2xx 且业务码成功
   *  - conflict：HTTP 409 或业务码 409（版本不匹配）
   *  - network：网络中断（uni.request fail）
   *  - error：其他服务端错误（5xx、业务错误码等）
   */
  private submitOperation(op: OfflineOperation): Promise<SubmitResult> {
    const token = uni.getStorageSync('token')
    const header: Record<string, string> = { 'Content-Type': 'application/json' }
    if (token) {
      header['Authorization'] = `Bearer ${token}`
    }

    // CREATE → POST，UPDATE → PUT
    const method = op.type === 'CREATE' ? 'POST' : 'PUT'

    return new Promise<SubmitResult>((resolve) => {
      uni.request({
        url: BASE_URL + op.endpoint,
        method: method as any,
        data: op.payload,
        header,
        success: (res: any) => {
          const statusCode = res?.statusCode
          const body = res?.data
          const bizCode = body?.code

          // 版本冲突：HTTP 409 或业务码 409（需求 5.3）
          if (statusCode === 409 || bizCode === 409) {
            resolve({ kind: 'conflict', message: body?.message || '数据版本冲突' })
            return
          }

          // 成功：HTTP 2xx 且业务码成功（200 或未返回 code）
          if (statusCode >= 200 && statusCode < 300 && (bizCode === 200 || bizCode === undefined)) {
            resolve({ kind: 'success' })
            return
          }

          // 其他情况视为服务端错误（需求 4.9 重试）
          resolve({ kind: 'error', message: body?.message || `同步失败(${statusCode})` })
        },
        fail: (err: any) => {
          // 网络中断（需求 5.5、4.9）
          resolve({ kind: 'network', message: err?.errMsg || '网络中断' })
        }
      })
    })
  }

  /**
   * 安排自动重试（需求 4.9）
   * 失败后 5 分钟重试，最多 3 次；3 次均失败后停止并通知用户。
   */
  private scheduleRetry(): void {
    if (this.retryTimer) {
      // 已有待执行的重试，避免重复排程
      return
    }

    if (this.retryCount >= this.MAX_RETRY) {
      // 达到最大重试次数 → 停止并通知（需求 4.9）
      this.resetRetry()
      this.notify('同步失败，请检查网络后手动重试')
      return
    }

    this.retryTimer = setTimeout(() => {
      this.retryTimer = null
      this.retryCount++
      void this.syncAll()
    }, this.RETRY_INTERVAL_MS)
  }

  /** 重置重试计数与定时器 */
  private resetRetry(): void {
    if (this.retryTimer) {
      clearTimeout(this.retryTimer)
      this.retryTimer = null
    }
    this.retryCount = 0
  }

  /** 统一通知（Toast） */
  private notify(message: string): void {
    try {
      uni.showToast({ title: message, icon: 'none' })
    } catch {
      // 非 UI 环境忽略
    }
  }
}

/** 单例实例 */
export const syncEngine = new SyncEngine()

export default syncEngine
