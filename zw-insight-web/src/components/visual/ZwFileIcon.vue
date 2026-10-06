<!--
  ZwFileIcon — 文件类型图标（P1 资产，2026-10-06）
  6 类：pdf / word / excel / image / zip / unknown。蓝图线稿风（与 ZwStateIllustration 同语言）：
  中性描边 + 类型语义色标签角标（角标文字硬编码语义色属类型信息，非状态装饰，豁免"零 hex"纪律，
  但仅限标签小面积；纸面与折角仍走 --zw-text-quaternary 变量）。
-->
<template>
  <svg
    class="zw-file-icon"
    :style="{ width: size }"
    viewBox="0 0 48 48"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    role="img"
    :aria-label="`${label}文件图标`"
  >
    <!-- 纸面 + 折角（共用） -->
    <path class="fi-paper" d="M10 6 h20 l8 8 v28 H10 z" stroke-width="1.8" stroke-linejoin="round" />
    <path class="fi-paper" d="M30 6 v8 h8" stroke-width="1.8" stroke-linejoin="round" />
    <!-- 内容线（word/excel/text 类） -->
    <g v-if="kind === 'word' || kind === 'excel' || kind === 'unknown'" class="fi-line" stroke-width="1.5" opacity="0.4">
      <line x1="15" y1="22" x2="27" y2="22" v-if="kind !== 'excel'" />
      <line x1="15" y1="28" x2="33" y2="28" />
      <line x1="15" y1="34" x2="24" y2="34" />
    </g>
    <!-- image：山与太阳 -->
    <g v-else-if="kind === 'image'">
      <rect class="fi-line" x="14" y="20" width="20" height="16" rx="1.5" stroke-width="1.5" />
      <path class="fi-line" d="M16 33 l6 -7 l4 4 l4 -5 l4 8" stroke-width="1.5" stroke-linejoin="round" />
      <circle class="fi-sun" cx="30" cy="25" r="2" />
    </g>
    <!-- pdf：横线 + 链环 -->
    <g v-else-if="kind === 'pdf'">
      <line class="fi-line" x1="15" y1="24" x2="33" y2="24" stroke-width="1.5" opacity="0.4" />
      <path class="fi-line" d="M17 31 h6 M25 31 h6" stroke-width="1.5" opacity="0.4" />
      <circle class="fi-line" cx="24" cy="31" r="3" stroke-width="1.5" />
    </g>
    <!-- zip：拉链齿 -->
    <g v-else-if="kind === 'zip'">
      <path class="fi-line" d="M24 18 v4 M21 20 h6 M24 26 v4 M21 28 h6 M24 34 v2" stroke-width="1.5" opacity="0.5" />
      <path class="fi-line" d="M21 20 l3 2 l3 -2 M21 28 l3 2 l3 -2" stroke-width="1.5" opacity="0.3" />
    </g>
    <!-- 类型角标（语义色，小面积） -->
    <rect class="fi-badge" x="8" y="34" width="20" height="11" rx="2" :fill="badgeColor" />
    <text x="18" y="42.5" text-anchor="middle" font-size="7" font-weight="bold" fill="#ffffff" font-family="var(--zw-font-mono, monospace)">{{ label }}</text>
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

type Kind = 'pdf' | 'word' | 'excel' | 'image' | 'zip' | 'unknown'

const props = withDefaults(
  defineProps<{
    /** 文件名或扩展名（自动识别类型；识别不出为 unknown） */
    file: string
    /** 渲染宽度 */
    size?: string
  }>(),
  { file: '', size: '40px' }
)

const EXT_MAP: Record<string, Kind> = {
  pdf: 'pdf',
  doc: 'word', docx: 'word',
  xls: 'excel', xlsx: 'excel', csv: 'excel',
  png: 'image', jpg: 'image', jpeg: 'image', gif: 'image', webp: 'image', bmp: 'image',
  zip: 'zip', rar: 'zip', '7z': 'zip', gz: 'zip',
}

const kind = computed<Kind>(() => {
  const ext = (props.file.split('.').pop() || '').toLowerCase()
  return (props.file && EXT_MAP[ext]) || 'unknown'
})

const LABEL: Record<Kind, string> = {
  pdf: 'PDF', word: 'DOC', excel: 'XLS', image: 'IMG', zip: 'ZIP', unknown: 'FILE',
}
const label = computed(() => LABEL[kind.value])

// 语义色角标：类型识别信息（PDF 红/Word 蓝/Excel 绿/图片 紫/压缩 橙褐/未知 灰）
const BADGE_COLOR: Record<Kind, string> = {
  pdf: '#d9534f', word: '#2b6cb0', excel: '#2f855a', image: '#6b46c1', zip: '#b7791f', unknown: '#5a6b7b',
}
const badgeColor = computed(() => BADGE_COLOR[kind.value])
</script>

<style scoped>
.zw-file-icon {
  display: block;
  height: auto;
}

.fi-paper {
  stroke: var(--zw-text-quaternary);
}

.fi-line {
  stroke: var(--zw-text-quaternary);
}

.fi-sun {
  fill: var(--zw-brand);
}
</style>
