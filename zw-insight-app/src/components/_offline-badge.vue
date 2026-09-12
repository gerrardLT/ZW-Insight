<template>
  <!-- P1: Offline Queue Badge — Sync status indicator for list items -->
  <view class="offline-badge" :class="statusClass">
    <view v-if="status === 'pending'" class="badge-dot"></view>
    <view v-else-if="status === 'syncing'" class="badge-syncing">
      <text class="sync-spinner">⟳</text>
    </view>
    <view v-else-if="status === 'conflict'" class="badge-warning">⚠️</view>
    <view v-else-if="status === 'failed'" class="badge-error">✕</view>
    <view v-else-if="status === 'synced'" class="badge-success">✓</view>
    <view v-else class="badge-offline" title="离线未提交">○</view>
    
    <text v-if="showLabel && status !== 'synced'" class="badge-label">{{ label }}</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface BadgeProps {
  /** Operation status: pending | syncing | conflict | failed | synced */
  status?: 'pending' | 'syncing' | 'conflict' | 'failed' | 'synced' | null
  showLabel?: boolean
}

const props = withDefaults(defineProps<BadgeProps>(), {
  status: null,
  showLabel: false
})

const emit = defineEmits(['click'])

const statusClass = computed(() => {
  if (!props.status) return ''
  return `status-${props.status}`
})

const label = computed(() => {
  switch (props.status) {
    case 'pending': return '待同步'
    case 'syncing': return '同步中...'
    case 'conflict': return '冲突'
    case 'failed': return '失败'
    case 'synced': return '已同步'
    default: return ''
  }
})

function handleClick() {
  emit('click', props.status)
}
</script>

<style scoped>
.offline-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 20rpx;
  padding: 2rpx 6rpx;
  border-radius: var(--zw-radius-xs);
  font-family: var(--zw-font-mono);
}

/* Pending - amber dot */
.status-pending {
  background: var(--zw-warning-light);
  color: var(--zw-warning);
}
.badge-dot {
  width: 8px;
  height: 8px;
  background: var(--zw-warning);
  border-radius: 50%;
  flex-shrink: 0;
}
.badge-label {
  font-size: 12px;
}

/* Syncing - blinking sync icon */
.status-syncing {
  background: var(--zw-brand-light);
  color: var(--zw-brand);
}
.badge-syncing {
  animation: sync-blink 1s infinite;
}
.sync-spinner {
  font-size: 14px;
  font-weight: bold;
}

/* Conflict/Failed - warning/error colors */
.status-conflict, .status-failed {
  background: var(--zw-danger-light);
  color: var(--zw-danger);
}
.badge-warning, .badge-error {
  font-size: 14px;
  line-height: 1;
}

/* Success */
.status-synced {
  background: var(--zw-success-light);
  color: var(--zw-success);
}
.badge-success {
  font-size: 14px;
  line-height: 1;
  font-weight: bold;
}

/* Offline - neutral */
.status-offline {
  background: var(--zw-bg-card);
  color: var(--zw-text-secondary);
}
.badge-offline {
  font-size: 14px;
  line-height: 1;
}

@keyframes sync-blink {
  0%, 100% { opacity: 0.5; }
  50% { opacity: 1; }
}
</style>
