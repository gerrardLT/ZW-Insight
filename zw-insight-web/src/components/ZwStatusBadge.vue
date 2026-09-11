<template>
  <span
    class="zw-status-badge"
    :class="[
      `zw-status-badge--${type}`,
      { 'zw-status-badge--hazard': isHazard, 'zw-status-badge--pulse': pulse }
    ]"
  >
    <span v-if="isHazard" class="zw-hazard-bar" />
    <slot>{{ text }}</slot>
  </span>
</template>

<script setup lang="ts">
/**
 * ZW-Insight 工程化状态徽章组件
 * 支持标准业务态（default, info, success, warning, danger）与现场警示态（hazard）
 */
withDefaults(defineProps<{
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
  text?: string
  isHazard?: boolean
  pulse?: boolean
}>(), {
  type: 'info',
  text: '',
  isHazard: false,
  pulse: false
})
</script>

<style scoped>
.zw-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 10px;
  border-radius: var(--zw-radius-full);
  font-size: var(--zw-font-size-xs);
  font-weight: var(--zw-font-weight-medium);
  line-height: 1.5;
  white-space: nowrap;
}

.zw-status-badge--info {
  background-color: var(--zw-bg-tag);
  color: var(--zw-text-secondary);
}

.zw-status-badge--primary {
  background-color: var(--zw-brand-light);
  color: var(--zw-brand-active);
}

.zw-status-badge--success {
  background-color: var(--zw-success-light);
  color: var(--zw-success);
}

.zw-status-badge--warning {
  background-color: var(--zw-warning-light);
  color: var(--zw-text-primary);
}

.zw-status-badge--danger {
  background-color: var(--zw-danger-light);
  color: var(--zw-danger);
}

/* 工程警示态：自带双色斜纹小标 */
.zw-hazard-bar {
  display: inline-block;
  width: 6px;
  height: 10px;
  background: repeating-linear-gradient(
    45deg,
    var(--zw-hazard-black) 0 3px,
    var(--zw-hazard-yellow) 3px 6px
  );
  border-radius: var(--zw-radius-xs);
}

.zw-status-badge--pulse {
  animation: badge-pulse var(--zw-duration-shimmer) var(--zw-ease-in-out) infinite;
}

@keyframes badge-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.6; }
}
</style>
