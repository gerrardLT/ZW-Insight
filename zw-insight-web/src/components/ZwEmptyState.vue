/**
 * 统一空态组件 (ZwEmptyState)
 * 覆盖 4 种类型：数据/错误/离线/权限；支持 action slot
 */
<template>
  <div class="zw-empty-state" :class="[`type-${type}`, containerClass]">
    <!-- 图标区 -->
    <div class="empty-icon">
      <component :is="activeIcon" style="font-size: 48px; opacity: 0.6" />
    </div>
    
    <!-- 描述文本 -->
    <div class="empty-description">
      <slot>{{ description }}</slot>
    </div>
    
    <!-- 操作区域 -->
    <div v-if="$slots.action" class="empty-action">
      <slot name="action"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  IconApps,
  IconAlertCircle,
  IconWifiOff,
  IconShieldLock
} from '@element-plus/icons-vue'

const props = defineProps<{
  type?: 'data' | 'error' | 'offline' | 'permission'
  description?: string
  containerClass?: string
}>()

// 图标映射
const iconMap = {
  data: 'IconApps',
  error: 'IconAlertCircle',
  offline: 'IconWifiOff',
  permission: 'IconShieldLock',
}

const activeIcon = computed(() => 
  iconMap[props.type || 'data']
)

// 默认文案
const defaultTextMap: Record<string, string> = {
  data: '暂无数据',
  error: '加载失败，请刷新重试',
  offline: '网络连接异常',
  permission: '无访问权限',
}

defineEmits<{
  retry: []
}>()
</script>

<style scoped lang="scss">
.zw-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--zw-space-xl);
  text-align: center;
  
  &.type-data { color: var(--zw-text-secondary); }
  &.type-error { color: var(--zw-danger); }
  &.type-offline { color: var(--zw-warning); }
  &.type-permission { color: var(--zw-text-tertiary); }
  
  .empty-icon {
    font-size: 48px;
    margin-bottom: var(--zw-space-md);
    opacity: 0.6;
  }
  
  .empty-description {
    font-size: var(--zw-font-size-sm);
    line-height: 1.5;
    margin-bottom: var(--zw-space-lg);
  }
  
  .empty-action {
    display: flex;
    gap: var(--zw-space-sm);
  }
}
</style>
