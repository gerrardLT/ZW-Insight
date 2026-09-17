<template>
  <!--
    ZwiEmptyState 四变体空态（Stage 0.3，PC ZwEmptyState 移植 + 2026 渐进披露趋势）
    - 契约 class：.empty（31 页面既有纯文字空态的升级位）
    - data: 无数据（默认插画 + 可选 CTA——空态只教一个动作）
    - error: 加载失败（重试 CTA）
    - offline: 离线缓存过期（提示联网）
    - permission: 无权限（联系管理员提示）
  -->
  <view class="empty" :class="`empty-${type}`">
    <!-- 双插画随主题显示（与项目纯 CSS 暗色机制同构，零 JS；微信基础库 2.11+ 同支持 darkmode） -->
    <image class="empty-illustration empty-light" src="/static/brand/empty-blueprint.png" mode="aspectFit" />
    <image class="empty-illustration empty-dark" src="/static/brand/empty-blueprint-dark.png" mode="aspectFit" />
    <text class="empty-text">{{ text }}</text>
    <view v-if="$slots.action" class="empty-action">
      <slot name="action" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

defineOptions({ name: 'ZwiEmptyState' })

const props = withDefaults(
  defineProps<{
    /** 空态类型 */
    type?: 'data' | 'error' | 'offline' | 'permission'
    /** 描述文案（未传时按类型给默认） */
    description?: string
  }>(),
  { type: 'data', description: '' }
)

const DEFAULT_TEXT: Record<string, string> = {
  data: '暂无数据',
  error: '加载失败，请稍后重试',
  offline: '离线状态下无缓存数据，联网后自动同步',
  permission: '暂无访问权限，请联系管理员',
}

const text = computed(() => props.description || DEFAULT_TEXT[props.type])
</script>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48rpx 40rpx;
}
.empty-illustration {
  width: 240rpx;
  height: 240rpx;
  opacity: 0.9;
}
.empty-dark {
  display: none;
}
@media (prefers-color-scheme: dark) {
  .empty-light {
    display: none;
  }
  .empty-dark {
    display: block;
  }
}
.empty-text {
  font-size: 26rpx;
  color: var(--zw-text-tertiary);
  text-align: center;
  line-height: 1.6;
  margin-top: 8rpx;
}
.empty-action {
  margin-top: 24rpx;
  min-height: 44px;
  display: flex;
  align-items: center;
}
</style>
