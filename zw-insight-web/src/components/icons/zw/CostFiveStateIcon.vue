<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :class="['zw-icon', 'zw-icon-cost-five-state', customClass]"
    aria-hidden="true"
  >
    <!-- 成本五态轨道：基准(1) -> 当前(2) -> 承诺(3) -> 实际(4) -> 预测(5) -->
    <path d="M3 18V6" stroke="currentColor" stroke-width="2" stroke-linecap="square" />
    <path d="M7 18V10" stroke="currentColor" stroke-width="2" stroke-linecap="square" />
    <path d="M11 18V8" stroke="currentColor" stroke-width="2" stroke-linecap="square" />
    <path d="M15 18V12" stroke="currentColor" stroke-width="2" stroke-linecap="square" />
    <path d="M19 18V5" class="zw-icon-accent" stroke-width="2" stroke-linecap="square" />
    <line x1="2" y1="20" x2="22" y2="20" stroke="currentColor" stroke-width="1.5" stroke-linecap="square" />
  </svg>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    size?: number | string
    customClass?: string
  }>(),
  {
    size: 24,
    customClass: ''
  }
)
</script>
