<template>
  <view class="zw-offline-indicator" @click="toggleDetailModal">
    <!-- 指示灯核心区 -->
    <view class="indicator-chip" :class="statusClass">
      <view class="led-light" :class="ledClass"></view>
      <text class="indicator-label">{{ labelText }}</text>
      <text v-if="network.queueCount > 0" class="queue-badge">{{ network.queueCount }}</text>
    </view>

    <!-- 离线排队详情抽屉弹窗 -->
    <view v-if="showModal" class="queue-modal-mask" @click.stop="toggleDetailModal">
      <view class="queue-modal-content" @click.stop>
        <view class="modal-header plate-header">
          <view class="page-eyebrow">HARDWARE TELEMETRY // SYNC</view>
          <text class="modal-title">现场离线操作队列</text>
          <text class="close-btn" @click="toggleDetailModal">×</text>
        </view>

        <view class="modal-body">
          <view class="telemetry-info">
            <view class="info-row">
              <text class="info-label">网络状态：</text>
              <text class="info-val" :class="network.isOffline ? 'text-amber' : 'text-green'">
                {{ network.isOffline ? 'OFFLINE (离线作业中)' : 'ONLINE (服务联通)' }}
              </text>
            </view>
            <view class="info-row">
              <text class="info-label">积压单据：</text>
              <text class="info-val mono-num">{{ queueList.length }} 笔</text>
            </view>
          </view>

          <!-- 队列列表 -->
          <scroll-view scroll-y class="queue-list" v-if="queueList.length > 0">
            <view v-for="op in queueList" :key="op.id" class="queue-item">
              <view class="queue-item-top">
                <text class="op-type-tag">{{ op.type }}</text>
                <text class="op-endpoint">{{ op.endpoint }}</text>
              </view>
              <view class="queue-item-bottom">
                <text class="op-time mono-num">{{ formatTime(op.timestamp) }}</text>
                <text class="op-status" :class="getStatusClass(op.status)">{{ op.status }}</text>
              </view>
            </view>
          </scroll-view>
          <view v-else class="queue-empty">
            <text class="empty-text">当前无离线积压操作，本地数据已完全对齐</text>
          </view>
        </view>

        <view class="modal-footer">
          <button
            class="sync-btn zw-btn-primary"
            :disabled="network.isOffline || isSyncing || queueList.length === 0"
            @click="triggerManualSync"
          >
            {{ isSyncing ? '正在同步数据...' : '手动立即同步' }}
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useNetworkStore } from '@/stores/network'
import { syncEngine, type OfflineOperation } from '@/utils/syncEngine'

const network = useNetworkStore()
const showModal = ref(false)
const queueList = ref<OfflineOperation[]>([])
const isSyncing = ref(false)

function refreshQueue() {
  try {
    queueList.value = syncEngine.getQueue()
    network.setQueueCount(queueList.value.length)
  } catch {
    queueList.value = []
  }
}

const statusClass = computed(() => {
  if (isSyncing.value || network.isSyncing) return 'chip-syncing'
  if (network.isOffline) return 'chip-offline'
  return 'chip-online'
})

const ledClass = computed(() => {
  if (isSyncing.value || network.isSyncing) return 'led-sweep'
  if (network.isOffline) return 'led-amber'
  return 'led-green'
})

const labelText = computed(() => {
  if (isSyncing.value || network.isSyncing) return 'SYNCING...'
  if (network.isOffline) return 'OFFLINE'
  return 'ONLINE'
})

function toggleDetailModal() {
  refreshQueue()
  showModal.value = !showModal.value
}

function formatTime(ts: number): string {
  if (!ts) return ''
  const d = new Date(ts)
  const pad = (n: number) => (n < 10 ? '0' + n : '' + n)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function getStatusClass(status: string) {
  if (status === 'SYNCED') return 'status-success'
  if (status === 'CONFLICT' || status === 'FAILED') return 'status-danger'
  return 'status-warning'
}

async function triggerManualSync() {
  if (network.isOffline || isSyncing.value) return
  isSyncing.value = true
  network.setSyncing(true)
  try {
    await syncEngine.syncAll()
    await syncEngine.compareVersions()
    uni.showToast({ title: '队列同步完成', icon: 'success' })
  } catch (e: any) {
    uni.showToast({ title: e?.message || '同步失败', icon: 'none' })
  } finally {
    isSyncing.value = false
    network.setSyncing(false)
    refreshQueue()
  }
}

onMounted(() => {
  refreshQueue()
})
</script>

<style scoped>
.zw-offline-indicator {
  display: inline-flex;
  align-items: center;
}

.indicator-chip {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: var(--zw-radius-xs);
  border: 1rpx solid var(--zw-border);
  background: var(--zw-bg-card);
  gap: 6px;
  cursor: pointer;
  box-sizing: border-box;
}

.chip-online {
  border-color: var(--zw-success);
  background: var(--zw-success-light);
}

.chip-offline {
  border-color: var(--zw-warning);
  background: var(--zw-warning-light);
}

.chip-syncing {
  border-color: var(--zw-brand);
  background: var(--zw-brand-light);
}

/* 硬件指示灯物理效果 */
.led-light {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.led-green {
  background: var(--zw-success);
  box-shadow: 0 0 4px var(--zw-success);
  animation: led-pulse 2s infinite ease-in-out;
}

.led-amber {
  background: var(--zw-warning);
  box-shadow: 0 0 5px var(--zw-warning);
}

.led-sweep {
  background: var(--zw-brand);
  animation: led-blink 0.6s infinite alternate;
}

@keyframes led-pulse {
  0% { opacity: 0.5; }
  50% { opacity: 1; }
  100% { opacity: 0.5; }
}

@keyframes led-blink {
  from { opacity: 0.2; }
  to { opacity: 1; }
}

.indicator-label {
  font-family: var(--zw-font-mono);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: var(--zw-text-primary);
}

.queue-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 14px;
  min-width: 14px;
  padding: 0 4px;
  background: var(--zw-danger);
  color: #fff;
  font-size: 9px;
  font-family: var(--zw-font-mono);
  font-weight: bold;
  border-radius: var(--zw-radius-xs);
}

/* 弹窗抽屉 */
.queue-modal-mask {
  position: fixed;
  inset: 0;
  background: var(--zw-bg-mask);
  z-index: 9999;
  display: flex;
  align-items: flex-end;
}

.queue-modal-content {
  width: 100%;
  max-height: 80vh;
  background: var(--zw-bg-card);
  border-top: 2rpx solid var(--zw-border);
  border-radius: var(--zw-radius-sm) var(--zw-radius-sm) 0 0;
  padding: 24rpx;
  box-sizing: border-box;
}

.modal-header {
  position: relative;
  margin-bottom: 16rpx;
}

.modal-title {
  font-size: 30rpx;
  font-weight: bold;
  color: var(--zw-text-primary);
}

.close-btn {
  position: absolute;
  right: 0;
  top: 0;
  font-size: 36rpx;
  color: var(--zw-text-tertiary);
  padding: 8rpx;
  line-height: 1;
}

.telemetry-info {
  background: var(--zw-bg-hover);
  padding: 16rpx;
  border-radius: var(--zw-radius-xs);
  border: 1rpx solid var(--zw-border-light);
  margin-bottom: 20rpx;
}

.info-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8rpx;
  font-size: 24rpx;
}

.info-row:last-child {
  margin-bottom: 0;
}

.info-label {
  color: var(--zw-text-secondary);
}

.info-val {
  font-weight: 600;
}

.text-green { color: var(--zw-success); }
.text-amber { color: var(--zw-warning); }

.queue-list {
  max-height: 360rpx;
}

.queue-item {
  background: var(--zw-bg-card);
  border: 1rpx solid var(--zw-border);
  border-radius: var(--zw-radius-xs);
  padding: 14rpx 16rpx;
  margin-bottom: 12rpx;
}

.queue-item-top {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin-bottom: 6rpx;
}

.op-type-tag {
  background: var(--zw-brand-light);
  color: var(--zw-brand);
  font-family: var(--zw-font-mono);
  font-size: 18rpx;
  font-weight: 700;
  padding: 2rpx 6rpx;
  border-radius: var(--zw-radius-xs);
}

.op-endpoint {
  font-family: var(--zw-font-mono);
  font-size: 22rpx;
  color: var(--zw-text-primary);
  word-break: break-all;
}

.queue-item-bottom {
  display: flex;
  justify-content: space-between;
  font-size: 20rpx;
}

.op-time {
  color: var(--zw-text-tertiary);
}

.status-warning { color: var(--zw-warning); font-weight: bold; }
.status-success { color: var(--zw-success); font-weight: bold; }
.status-danger { color: var(--zw-danger); font-weight: bold; }

.queue-empty {
  padding: 40rpx 0;
  text-align: center;
}

.empty-text {
  font-size: 24rpx;
  color: var(--zw-text-tertiary);
}

.modal-footer {
  margin-top: 20rpx;
}

.sync-btn {
  height: 80rpx;
  line-height: 80rpx;
  font-size: 28rpx;
}
</style>
