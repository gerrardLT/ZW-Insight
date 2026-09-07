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

  // 4. H5 兜底：浏览器存在 navigator.connection 时 uni 仅监听其 change 事件，
  // 实测不捕获 window offline/online（2026-08-29 H5 走查实证），补原生监听，
  // 否则 H5 断网不切离线模式、联网不重放离线队列（需求 4.7 / 5.2）
  // #ifdef H5
  window.addEventListener('offline', () => {
    network.setNetworkType('none')
  })
  window.addEventListener('online', () => {
    const wasOffline = network.isOffline
    // 恢复瞬间无法精确判定网络类型，非 none 即视为在线（真实类型由后续请求自然验证）
    network.setNetworkType('unknown')
    if (wasOffline) {
      const t = uni.getStorageSync('token')
      if (!t) return
      syncEngine
        .syncAll()
        .then(() => syncEngine.compareVersions())
        .catch((e) => {
          console.warn('[offline] 联网同步失败', e)
        })
    }
  })
  // #endif
})
</script>

<style>
/* Industrial Precision 主题桥接（2026-08-28 迁移 Phase 1）：
   tokens = 与 PC 同名变量；signature = 签名工具类（按钮/角标/条纹/眉题/徽章/卡片） */
@import './styles/tokens.css';
@import './styles/signature.css';

page {
  background-color: var(--zw-bg-page);
  font-family: var(--zw-font-family);
  color: var(--zw-text-primary);
}
</style>
