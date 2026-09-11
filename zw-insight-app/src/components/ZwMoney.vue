<template>
  <text class="zw-money tabular-num" :class="[colorClass]">
    <text class="zw-money-symbol" v-if="showSymbol">{{ symbol }}</text>
    <text class="zw-money-integer">{{ integerPart }}</text>
    <text class="zw-money-decimal" v-if="hasDecimal">.{{ decimalPart }}</text>
    <text class="zw-money-unit" v-if="unit">{{ unit }}</text>
  </text>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    value?: number | string | null
    symbol?: string
    showSymbol?: boolean
    precision?: number
    unit?: string
    type?: 'primary' | 'success' | 'warning' | 'danger' | 'default'
  }>(),
  {
    value: 0,
    symbol: '¥',
    showSymbol: true,
    precision: 2,
    unit: '',
    type: 'default'
  }
)

const colorClass = computed(() => {
  if (props.type === 'primary') return 'money-primary'
  if (props.type === 'success') return 'money-success'
  if (props.type === 'warning') return 'money-warning'
  if (props.type === 'danger') return 'money-danger'
  return 'money-default'
})

const formatted = computed(() => {
  const num = Number(props.value)
  if (isNaN(num)) return { integer: '0', decimal: '00' }
  const fixed = num.toFixed(props.precision)
  const parts = fixed.split('.')
  const integer = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  const decimal = parts[1] || ''
  return { integer, decimal }
})

const integerPart = computed(() => formatted.value.integer)
const decimalPart = computed(() => formatted.value.decimal)
const hasDecimal = computed(() => props.precision > 0 && decimalPart.value !== '')
</script>

<style scoped>
.zw-money {
  font-family: var(--zw-font-mono);
  font-variant-numeric: tabular-nums;
  display: inline-flex;
  align-items: baseline;
}
.zw-money-symbol {
  font-size: 0.8em;
  margin-right: 2px;
  opacity: 0.85;
}
.zw-money-integer {
  font-weight: 600;
}
.zw-money-decimal {
  font-size: 0.85em;
}
.zw-money-unit {
  font-size: 0.8em;
  margin-left: 2px;
  color: var(--zw-text-secondary);
}

.money-default {
  color: var(--zw-text-primary);
}
.money-primary {
  color: var(--zw-brand);
}
.money-success {
  color: var(--zw-success);
}
.money-warning {
  color: var(--zw-warning);
}
.money-danger {
  color: var(--zw-danger);
}
</style>
