<template>
  <span class="zw-count-up" data-testid="zw-count-up">{{ display }}</span>
</template>

<script setup lang="ts">
/**
 * 数字翻牌（count-up）展示组件。
 *
 * - rAF + easeOutCubic 缓动，从当前显示值滚动到目标值；
 * - 尊重系统「减少动态效果」偏好（prefers-reduced-motion）与 duration=0：直接落定终值，
 *   保证测试/无障碍场景下的确定性渲染（挂载即终值，不依赖帧时钟）；
 * - 值变化时从当前显示值续动到新值，避免重置跳变。
 */
import { ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 目标数值 */
    value: number
    /** 动画时长 ms；0 表示直接落定 */
    duration?: number
    /** 展示格式化（千分位/单位等），入参为当前数值 */
    format?: (n: number) => string
  }>(),
  { duration: 800, format: undefined }
)

const display = ref(formatValue(props.value))
let rafId = 0
let fromValue = props.value

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && !!window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

function formatValue(n: number): string {
  return props.format ? props.format(n) : String(n)
}

function render(target: number) {
  display.value = formatValue(target)
}

function animate(to: number) {
  if (props.duration <= 0 || prefersReducedMotion()) {
    fromValue = to
    render(to)
    return
  }
  const from = fromValue
  const delta = to - from
  if (Math.abs(delta) < Number.EPSILON) {
    render(to)
    return
  }
  const start = performance.now()
  const step = (now: number) => {
    const t = Math.min(1, (now - start) / props.duration)
    const eased = 1 - Math.pow(1 - t, 3)
    const current = from + delta * eased
    render(current)
    if (t < 1) {
      rafId = requestAnimationFrame(step)
    } else {
      fromValue = to
      render(to)
    }
  }
  cancelAnimationFrame(rafId)
  rafId = requestAnimationFrame(step)
}

watch(
  () => props.value,
  (v) => animate(v),
  { immediate: false }
)

/** 挂载即显示终值（确定性渲染），随后从 0 起滚（首屏动画由父级入场动效触发亦可） */
if (props.duration > 0 && !prefersReducedMotion() && props.value !== 0) {
  fromValue = 0
  render(0)
  requestAnimationFrame(() => animate(props.value))
}
</script>

<style scoped>
.zw-count-up {
  font-variant-numeric: tabular-nums;
}
</style>
