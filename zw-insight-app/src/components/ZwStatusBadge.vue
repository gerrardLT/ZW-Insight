<template>
  <text class="status-badge" :class="[statusClass]">
    <slot>{{ text }}</slot>
  </text>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    status?: string
    text?: string
  }>(),
  {
    status: 'info',
    text: ''
  }
)

const statusClass = computed(() => {
  const s = props.status?.toLowerCase() || 'info'
  if (['success', 'approved', 'completed', 'normal', '1'].includes(s)) {
    return 'status-badge-success'
  }
  if (['warning', 'pending', 'approving', 'processing', '0'].includes(s)) {
    return 'status-badge-warning'
  }
  if (['danger', 'error', 'rejected', 'failed', 'overdue', '2'].includes(s)) {
    return 'status-badge-danger'
  }
  return 'status-badge-info'
})
</script>
