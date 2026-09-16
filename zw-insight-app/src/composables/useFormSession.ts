/**
 * useFormSession — 表单会话持久化（S3.3）
 * 长表单中途退出（来电/切后台/杀进程）不丢已填内容：
 * - onHide 自动快照（由调用方传入 getter）
 * - onShow 自动还原（仅空表单时，防覆盖已填）
 * - 提交成功后 clear；快照 24h 过期丢弃
 */
import { onShow, onHide } from '@dcloudio/uni-app'

const PREFIX = 'form-session:'
const MAX_AGE_MS = 24 * 60 * 60 * 1000 // 24h

interface StoredSession<T> {
  savedAt: number
  data: T
}

/**
 * @param pageKey 页面级唯一键（如 'material-inbound'）
 * @param getSnapshot 返回当前表单数据（onHide 时调用）
 * @param restoreTo 还原入口：仅在表单为空时写入（实现方自行判定空态）
 */
export function useFormSession<T extends Record<string, any>>(
  pageKey: string,
  options: {
    getSnapshot: () => T
    restoreTo: (data: T) => void
    /** 判定表单是否为空（空才还原，防覆盖用户已输入） */
    isEmpty: (data: T) => boolean
  }
) {
  const key = PREFIX + pageKey

  function save() {
    try {
      const data = options.getSnapshot()
      if (!options.isEmpty(data)) {
        const payload: StoredSession<T> = { savedAt: Date.now(), data }
        uni.setStorageSync(key, JSON.stringify(payload))
      } else {
        // 空表单不存快照，同时清掉旧快照
        uni.removeStorageSync(key)
      }
    } catch {
      // 存储失败不阻断页面生命周期
    }
  }

  function restore() {
    try {
      const raw = uni.getStorageSync(key)
      if (!raw) return
      const session = JSON.parse(raw) as StoredSession<T>
      // 过期快照直接丢弃
      if (!session?.savedAt || Date.now() - session.savedAt > MAX_AGE_MS) {
        uni.removeStorageSync(key)
        return
      }
      const current = options.getSnapshot()
      // 仅空表单时还原（防覆盖已填）
      if (options.isEmpty(current)) {
        options.restoreTo(session.data)
      }
    } catch {
      // 损坏数据静默丢弃（恢复不了但不能阻断页面）
    }
  }

  function clear() {
    try {
      uni.removeStorageSync(key)
    } catch {
      // ignore
    }
  }

  onHide(() => { save() })
  onShow(() => { restore() })

  return { save, restore, clear }
}
