<template>
  <span
    class="zw-avatar"
    :class="[`zw-avatar--${size}`, { 'zw-avatar--img': !!resolvedSrc }]"
    :style="containerStyle"
    data-testid="zw-avatar"
  >
    <img
      v-if="resolvedSrc && !imgFailed"
      :src="resolvedSrc"
      :alt="alt"
      class="zw-avatar__img"
      data-testid="zw-avatar-img"
      @error="imgFailed = true"
    />
    <span v-else class="zw-avatar__text" data-testid="zw-avatar-text" :style="textStyle">{{ displayText }}</span>
  </span>
</template>

<script setup lang="ts">
/**
 * 用户头像（P0 资产，2026-10-06）。
 *
 * 设计：无自定义头像时按姓名渲染「首字 + 稳定哈希取色底」——同名恒同色、
 * 异名大概率异色（协同场景辨识度）；姓名缺失回落蓝图风人形剪影 SVG。
 * 颜色从固定调色板取（经哈希索引），不用随机：测试可断言、刷新不变。
 * 图片 @error 自动回落首字（裂图纪律：任何远程头像 404 都不露破图）。
 */
import { computed, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    /** 用户姓名（取首字展示；空则回落剪影） */
    name?: string
    /** 自定义头像 URL（空/加载失败均回落首字） */
    src?: string
    /** 尺寸档位 */
    size?: 'xs' | 'sm' | 'md' | 'lg'
    /** 无障碍文本 */
    alt?: string
  }>(),
  { name: '', src: '', size: 'md', alt: '用户头像' }
)

/** 稳定调色板：底色取品牌中性的低饱和系（与 --zw 色板同族），文字恒白 */
const PALETTE = [
  { bg: '#2b6cb0', fg: '#ffffff' }, // 蓝
  { bg: '#2f855a', fg: '#ffffff' }, // 绿
  { bg: '#b7791f', fg: '#ffffff' }, // 琥珀
  { bg: '#6b46c1', fg: '#ffffff' }, // 紫
  { bg: '#c05621', fg: '#ffffff' }, // 橙褐
  { bg: '#3178a8', fg: '#ffffff' }, // 青蓝
  { bg: '#86459a', fg: '#ffffff' }, // 品紫
  { bg: '#5a6b7b', fg: '#ffffff' }, // 蓝灰
] as const

/** djb2 字符串哈希（稳定、分布均匀、无碰撞处理需求） */
function hashName(s: string): number {
  let h = 5381
  for (let i = 0; i < s.length; i++) {
    h = ((h << 5) + h + s.charCodeAt(i)) | 0
  }
  return Math.abs(h)
}

const imgFailed = ref(false)
watch(
  () => props.src,
  () => {
    imgFailed.value = false
  }
)

const resolvedSrc = computed(() => (imgFailed.value ? '' : props.src || ''))

const displayText = computed(() => {
  const n = (props.name || '').trim()
  return n ? n.slice(0, 1) : ''
})

/** 姓名 → 稳定配色；无名时着色退化为中性剪影 */
const paletteEntry = computed(() => {
  const n = (props.name || '').trim()
  return n ? PALETTE[hashName(n) % PALETTE.length] : null
})

const containerStyle = computed(() => {
  if (resolvedSrc.value) return {}
  const p = paletteEntry.value
  return p ? { background: p.bg } : { background: 'var(--zw-border-light)' }
})

const textStyle = computed(() => {
  const p = paletteEntry.value
  return p ? { color: p.fg } : { color: 'var(--zw-text-quaternary)' }
})
</script>

<style scoped>
.zw-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  user-select: none;
  background: var(--zw-border-light);
}

.zw-avatar--xs {
  width: 24px;
  height: 24px;
  font-size: 12px;
}

.zw-avatar--sm {
  width: 32px;
  height: 32px;
  font-size: 14px;
}

.zw-avatar--md {
  width: 40px;
  height: 40px;
  font-size: 16px;
}

.zw-avatar--lg {
  width: 56px;
  height: 56px;
  font-size: 22px;
}

.zw-avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.zw-avatar__text {
  font-weight: var(--zw-font-weight-semibold);
  line-height: 1;
  letter-spacing: 0;
}
</style>
