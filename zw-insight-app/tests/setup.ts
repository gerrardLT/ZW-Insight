/**
 * 测试环境 uni-* 全局桩
 *
 * uni-app 的 `uni.*` API 在 Node 环境不存在，offlineCache / syncEngine 的类方法
 * 通过 `uni.setStorageSync / getStorageSync / removeStorageSync` 读写本地存储。
 * 这里用一个内存 Map 真实地后备这些方法，使被测类的存储记账逻辑可以在 Node 下
 * 端到端运行（非 mock 业务逻辑，仅替换平台存储底座）。
 *
 * uni.request 默认不实现——需要它的测试（同步引擎）自行覆盖 globalThis.uni.request，
 * 以便记录真实的提交顺序。
 */

interface UniStorageStub {
  __store: Map<string, any>
  getStorageSync: (key: string) => any
  setStorageSync: (key: string, value: any) => void
  removeStorageSync: (key: string) => void
  request?: (options: any) => void
  showToast?: (options: any) => void
}

function createUniStub(): UniStorageStub {
  const store = new Map<string, any>()
  return {
    __store: store,
    getStorageSync(key: string) {
      // uni 在 key 不存在时返回 ''，与真实行为保持一致
      return store.has(key) ? store.get(key) : ''
    },
    setStorageSync(key: string, value: any) {
      // 深拷贝，模拟序列化存储语义，避免引用串改
      store.set(key, JSON.parse(JSON.stringify(value)))
    },
    removeStorageSync(key: string) {
      store.delete(key)
    },
    showToast() {
      /* no-op in tests */
    },
  }
}

// 每个测试文件加载时安装一个干净的桩
;(globalThis as any).uni = createUniStub()

/** 供测试在用例间重置存储状态 */
export function resetUniStorage(): void {
  ;(globalThis as any).uni = createUniStub()
}

/** 直接读取/写入底层存储，便于在用例中预置过期时间戳等 */
export function getUni(): UniStorageStub {
  return (globalThis as any).uni as UniStorageStub
}
