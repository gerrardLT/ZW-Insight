<template>
  <!-- 离线模式提示条（需求 4.7）：仅在离线时展示于页面顶部
       Mobile Shell 章：warning-soft 底 + ink 字，不做静默重试欺骗；左侧 hazard 竖条为「不静默」的视觉表达 -->
  <view v-if="network.isOffline" class="offline-banner">
    <view class="offline-hazard"></view>
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
  background: var(--zw-warning-light);
  color: var(--zw-text-primary); /* 警示黄禁做前景字，正文用 ink */
  font-size: 24rpx;
  padding: 16rpx 20rpx;
  border-bottom: 1rpx solid var(--zw-border);
  position: relative;
}
.offline-hazard {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 8rpx;
  background: repeating-linear-gradient(
    45deg,
    var(--zw-hazard-black) 0 8rpx,
    var(--zw-hazard-yellow) 8rpx 16rpx
  );
}
.offline-icon { margin-right: 8rpx; }
.offline-text { line-height: 1.2; }
</style>
