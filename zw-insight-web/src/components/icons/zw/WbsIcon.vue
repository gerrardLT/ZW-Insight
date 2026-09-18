<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :class="['zw-icon', 'zw-icon-wbs', customClass]"
    aria-hidden="true"
  >
    <!-- WBS 工作分解结构树与直角支架 -->
    <rect x="3" y="3" width="6" height="5" stroke="currentColor" stroke-width="2" />
    <rect x="15" y="3" width="6" height="5" stroke="currentColor" stroke-width="2" />
    <rect x="15" y="16" width="6" height="5" class="zw-icon-accent" stroke-width="2" />
    <path d="M6 8V18.5H15" stroke="currentColor" stroke-width="2" stroke-linecap="square" />
    <line x1="9" y1="5.5" x2="15" y2="5.5" stroke="currentColor" stroke-width="2" />
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
