<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    :class="['zw-icon', 'zw-icon-evidence', customClass]"
    aria-hidden="true"
  >
    <!-- 工程存证盾牌与凭证核验 -->
    <path
      d="M12 2L4 5V11C4 16.5 7.5 21.5 12 22C16.5 21.5 20 16.5 20 11V5L12 2Z"
      stroke="currentColor"
      stroke-width="2"
      stroke-linejoin="round"
    />
    <path
      d="M9 11.5L11 13.5L15 9.5"
      class="zw-icon-accent"
      stroke-width="2"
      stroke-linecap="square"
      stroke-linejoin="miter"
    />
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
