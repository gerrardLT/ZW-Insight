import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 网络状态全局 store（需求 4.7）
 *
 * - isOffline：当前是否处于离线状态，由 App.vue 的网络监听统一维护
 * - networkType：当前网络类型（wifi/4g/none 等），用于调试与展示
 *
 * 页面通过 <OfflineBanner /> 组件订阅 isOffline，在离线时于页面顶部展示「离线模式」提示条。
 */
export const useNetworkStore = defineStore('network', () => {
  const isOffline = ref(false)
  const networkType = ref<string>('unknown')
  const queueCount = ref<number>(0)
  const isSyncing = ref<boolean>(false)

  /** 更新离线标记 */
  function setOffline(val: boolean) {
    isOffline.value = val
  }

  /** 更新网络类型并同步离线标记（networkType === 'none' 视为离线） */
  function setNetworkType(type: string) {
    networkType.value = type
    isOffline.value = !type || type === 'none'
  }

  /** 更新待同步队列数量 */
  function setQueueCount(count: number) {
    queueCount.value = count
  }

  /** 更新同步运行状态 */
  function setSyncing(syncing: boolean) {
    isSyncing.value = syncing
  }

  return {
    isOffline,
    networkType,
    queueCount,
    isSyncing,
    setOffline,
    setNetworkType,
    setQueueCount,
    setSyncing
  }
})
