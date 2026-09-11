<template>
  <span class="zw-money stat-number" :class="{ 'text-brand': highlight }">
    <span v-if="prefix" class="zw-money-prefix">{{ prefix }}</span>
    <span class="zw-money-value">{{ formattedValue }}</span>
    <span v-if="suffix" class="zw-money-suffix">{{ suffix }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * ZW-Insight 精密等宽金额组件
 * 强制应用 tabular-nums 与 JetBrains Mono，统一千分位
 */
const props = withDefaults(defineProps<{
  value?: number | string | null
  prefix?: string
  suffix?: string
  decimals?: number
  highlight?: boolean
}>(), {
  value: 0,
  prefix: '¥ ',
  suffix: '',
  decimals: 2,
  highlight: false
})

const formattedValue = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return '-'
  const num = Number(props.value)
  if (isNaN(num)) return String(props.value)

  const parts = num.toFixed(props.decimals).split('.')
  parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return parts.join('.')
})
</script>

<style scoped>
.zw-money {
  display: inline-flex;
  align-items: baseline;
  font-family: var(--zw-font-mono);
  font-variant-numeric: tabular-nums;
  font-weight: var(--zw-font-weight-medium);
  letter-spacing: -0.02em;
}

.zw-money-prefix {
  font-size: 0.85em;
  margin-right: 2px;
  opacity: 0.8;
}

.zw-money-suffix {
  font-size: 0.85em;
  margin-left: 2px;
  opacity: 0.8;
}
</style>
