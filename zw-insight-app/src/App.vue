<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { offlineCache } from '@/utils/offlineCache'
import { syncEngine } from '@/utils/syncEngine'
import { useNetworkStore } from '@/stores/network'

onLaunch(() => {
  console.log('App Launch')

  const network = useNetworkStore()

  // 1. 初始化当前网络状态（需求 4.7：离线模式提示条依赖该状态）
  uni.getNetworkType({
    success: (res) => {
      network.setNetworkType(res.networkType)
    }
  })

  // 2. 首次登录后触发离线缓存初始同步（仅在已登录、有网络时执行）—— 需求 4.1
  const token = uni.getStorageSync('token')
  if (token && !network.isOffline) {
    offlineCache.sync().catch((e) => {
      console.warn('[offline] 初始同步失败', e)
    })
  }

  // 3. 监听网络状态变化（需求 4.3、4.7）
  uni.onNetworkStatusChange((res) => {
    const wasOffline = network.isOffline
    network.setNetworkType(res.networkType)

    // 网络由离线恢复为在线：执行离线操作同步 + 版本号比对全量覆盖
    if (wasOffline && res.isConnected) {
      const t = uni.getStorageSync('token')
      if (!t) return
      // 先提交离线操作队列（需求 5.2），再比对版本号刷新缓存（需求 4.3）
      syncEngine
        .syncAll()
        .then(() => syncEngine.compareVersions())
        .catch((e) => {
          console.warn('[offline] 联网同步失败', e)
        })
    }
  })
})
</script>

<style>
page {
  background-color: #f5f5f5;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}
</style>
