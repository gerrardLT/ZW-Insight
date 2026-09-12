<script setup lang="ts">
import { onLaunch, onShow } from '@dcloudio/uni-app'
import { offlineCache } from '@/utils/offlineCache'
import { syncEngine } from '@/utils/syncEngine'
import { useNetworkStore } from '@/stores/network'

onLaunch(() => {
  console.log('App Launch')

  const network = useNetworkStore()

  // 0. 恢复强光现场模式（户外施工高对比模式）
  try {
    if (uni.getStorageSync('zw_outdoor_mode') === '1' && typeof document !== 'undefined') {
      document.documentElement.dataset.theme = 'outdoor'
      document.body.classList.add('theme-outdoor')
    }
  } catch {}

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

  // P0: Touch Target Verification — 所有交互元素 ≥44pt
  // #ifdef H5
  setTimeout(() => {
    const interactiveSelectors = [
      'button', '.btn', '[class*="-btn"]', 
      '.menu-item', '.tab-item', '.checkbox', '.radio-item',
      '.picker-item', '.task-item', '.fab-btn', '.submit-btn'
    ]
    const elements = document.querySelectorAll(interactiveSelectors.join(','))
    let violations = 0
    elements.forEach(el => {
      const rect = el.getBoundingClientRect()
      const height = rect.height || 0
      const width = rect.width || 0
      const effectiveHeight = Math.max(height, width)
      const effectiveWidth = Math.max(width, height)
      const isViolated = effectiveHeight < 44 || effectiveWidth < 44
      if (isViolated) {
        violations++
        console.warn(`[a11y] Touch target violation: ${el.className || 'unnamed'}, size: ${width.toFixed(1)}x${height.toFixed(1)}, min: 44x44`)
      }
    })
    if (violations > 0) {
      console.warn(`[a11y] Total touch target violations: ${violations}`)
    } else {
      console.log('[a11y] ✓ All touch targets ≥ 44pt (WCAG 2.5.5 AA)')
    }
  }, 1000)
  // #endif
})

onShow(() => {
  // 页面显示时更新队列计数
  const network = useNetworkStore()
  const queueCount = syncEngine.getQueue().length
  network.setQueueCount(queueCount)
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

/* P0: Native TabBar Safe Area — H5 platform requires explicit safe-area padding */
/* #ifdef H5 */
.uni-tabbar {
  padding-bottom: env(safe-area-inset-bottom, 0px);
  height: calc(50px + env(safe-area-inset-bottom, 0px));
}
/* #endif */
</style>
