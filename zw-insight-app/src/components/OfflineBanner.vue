<template>
  <!-- 离线模式提示条（需求 4.7）：仅在离线时展示于页面顶部 -->
  <view v-if="network.isOffline" class="offline-banner">
    <text class="offline-icon">⚠️</text>
    <text class="offline-text">离线模式 · 当前展示本地缓存数据，联网后将自动同步</text>
  </view>
</template>

<script setup lang="ts">
import { useNetworkStore } from '@/stores/network'

const network = useNetworkStore()
</script>

<style scoped>
.offline-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fdf6ec;
  color: #e6a23c;
  font-size: 24rpx;
  padding: 16rpx 20rpx;
  border-bottom: 1rpx solid #faecd8;
}
.offline-icon { margin-right: 8rpx; }
.offline-text { line-height: 1.2; }
</style>
