<!--
  ZwStateIllustration — 品牌化状态插图（蓝图语言，Batch 1.2，2026-09-17）
  4 态专属 SVG：data(空账本) / error(警示三角) / offline(断连云) / permission(挂锁)。
  纪律（DESIGN Iconography L667/L675）：线条恒中性 --zw-text-quaternary，单一 --zw-brand 橙点缀；
  形状区分状态（非颜色区分，色盲安全）；内联 SVG 零硬编码 hex（颜色经 CSS 类注入，
  presentation attribute 不支持 var()）。可复用于空态 / 错误页 / 帮助中心 / 全局错误边界。
-->
<template>
  <svg
    class="zw-illust"
    :class="`zw-illust--${type}`"
    :style="{ width: size, height: size }"
    viewBox="0 0 64 64"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    role="img"
    :aria-label="ariaLabel"
  >
    <!-- 蓝图底板（4 态共用：虚线框 = 图纸未完成隐喻） -->
    <rect class="ill-frame" x="10" y="10" width="44" height="44" rx="2" stroke-width="1.5" stroke-dasharray="4 3" />
    <!-- data：空账本行 + 品牌橙游标点 -->
    <g v-if="type === 'data'">
      <line class="ill-line" x1="19" y1="24" x2="45" y2="24" stroke-width="1.5" opacity="0.65" />
      <line class="ill-line" x1="19" y1="33" x2="45" y2="33" stroke-width="1.5" opacity="0.4" />
      <line class="ill-line" x1="19" y1="42" x2="37" y2="42" stroke-width="1.5" opacity="0.25" />
      <circle class="ill-accent-f" cx="44" cy="42" r="2.5" />
    </g>
    <!-- error：警示三角 + 橙感叹号 -->
    <g v-else-if="type === 'error'">
      <path class="ill-line" d="M32 21 L45 44 L19 44 Z" stroke-width="1.5" stroke-linejoin="round" />
      <line class="ill-accent-s" x1="32" y1="30" x2="32" y2="37" stroke-width="2" stroke-linecap="round" />
      <circle class="ill-accent-f" cx="32" cy="41" r="1.4" />
    </g>
    <!-- offline：断连云 + 橙斜杠 -->
    <g v-else-if="type === 'offline'">
      <path
        class="ill-line"
        d="M23 41 h17 a5.2 5.2 0 0 0 0.6 -10.4 a8.4 8.4 0 0 0 -16 -1.3 a5.6 5.6 0 0 0 -1.6 11.7 z"
        stroke-width="1.5" stroke-linejoin="round"
      />
      <line class="ill-accent-s" x1="20" y1="20" x2="44" y2="44" stroke-width="2" stroke-linecap="round" />
    </g>
    <!-- permission：挂锁 -->
    <g v-else>
      <rect class="ill-line" x="24" y="30" width="16" height="14" rx="2" stroke-width="1.5" />
      <path class="ill-line" d="M27.5 30 v-4 a4.5 4.5 0 0 1 9 0 v4" stroke-width="1.5" />
      <circle class="ill-accent-f" cx="32" cy="37" r="1.8" />
    </g>
  </svg>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    /** 状态类型（决定插图形状） */
    type?: 'data' | 'error' | 'offline' | 'permission'
    /** 渲染尺寸（宽高同值） */
    size?: string
    /** 无障碍标签 */
    ariaLabel?: string
  }>(),
  { type: 'data', size: '64px', ariaLabel: '状态插图' }
)
</script>

<style scoped>
.zw-illust {
  display: block;
}
/* 颜色经 CSS 类注入（presentation attribute 不支持 var()）：线条恒中性，单一橙点缀 */
.ill-frame,
.ill-line {
  stroke: var(--zw-text-quaternary);
}
.ill-accent-s {
  stroke: var(--zw-brand);
}
.ill-accent-f {
  fill: var(--zw-brand);
}
</style>
