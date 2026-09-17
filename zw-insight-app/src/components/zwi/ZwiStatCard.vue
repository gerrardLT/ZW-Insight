<template>
  <!--
    ZwiStatCard 数值卡（Stage 0.3）
    - 契约 class：.stat-card（home 页既有）
    - calm 模式（默认，2026 Calm Design）：白卡 + 彩色大数字 + 灰标签——数字即焦点
    - solid 模式：浅彩底（success/warning/danger/info）——用于需要更强分区语义的看板
    - 大数值 mono tabular（等宽数字纪律）
  -->
  <view class="stat-card" :class="[mode === 'solid' ? `stat-solid-${tone}` : 'stat-calm', { 'stat-plain': plain }]">
    <text class="stat-value" :class="`stat-value-${tone}`">{{ value }}<text v-if="unit" class="stat-unit">{{ unit }}</text></text>
    <text class="stat-label">{{ label }}</text>
    <slot />
  </view>
</template>

<script setup lang="ts">
defineOptions({ name: 'ZwiStatCard' })

withDefaults(
  defineProps<{
    /** 大数值文本（已格式化，如 "5,150"） */
    value: string | number
    /** 单位（万/% 等，小号跟随） */
    unit?: string
    /** 标签 */
    label: string
    /** 视觉模式：calm=白卡彩数字（默认） / solid=浅彩底 */
    mode?: 'calm' | 'solid'
    /** 语义色调：数字色（calm）或底色（solid） */
    tone?: 'brand' | 'success' | 'warning' | 'danger' | 'info' | 'neutral'
    /** 无边框纯排布（嵌在卡内的小指标） */
    plain?: boolean
  }>(),
  { unit: '', mode: 'calm', tone: 'neutral', plain: false }
)
</script>

<style scoped>
.stat-card {
  flex: 1;
  min-width: 0;
  padding: 28rpx 24rpx;
  background: var(--zw-bg-card);
  border: 1rpx solid var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  box-sizing: border-box;
  text-align: center;
}
.stat-plain {
  border: none;
  padding: 8rpx 0;
  background: transparent;
}
/* solid 模式：soft 底（与 status-badge 同源纪律） */
.stat-solid-success { background: var(--zw-success-light); border-color: transparent; }
.stat-solid-warning { background: var(--zw-warning-light); border-color: transparent; }
.stat-solid-danger { background: var(--zw-danger-light); border-color: transparent; }
.stat-solid-info { background: var(--zw-info-light); border-color: transparent; }
.stat-solid-brand { background: var(--zw-brand-light); border-color: transparent; }
.stat-solid-neutral { background: var(--zw-bg-hover); border-color: transparent; }

.stat-value {
  display: block;
  font-size: 44rpx;
  font-weight: bold;
  font-family: var(--zw-font-mono);
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
  color: var(--zw-text-primary);
}
/* calm 模式数字色调（数字即焦点） */
.stat-value-brand { color: var(--zw-brand); }
.stat-value-success { color: var(--zw-success); }
.stat-value-warning { color: var(--zw-warning); }
.stat-value-danger { color: var(--zw-danger); }
.stat-value-info { color: var(--zw-info); }
.stat-value-neutral { color: var(--zw-text-primary); }
/* solid 模式数字恒主色（浅彩底上的深字纪律） */
.stat-solid-success .stat-value,
.stat-solid-warning .stat-value,
.stat-solid-danger .stat-value,
.stat-solid-info .stat-value,
.stat-solid-brand .stat-value,
.stat-solid-neutral .stat-value {
  color: var(--zw-text-primary);
}
.stat-unit {
  font-size: 24rpx;
  font-weight: normal;
  margin-left: 4rpx;
  color: var(--zw-text-tertiary);
}
.stat-label {
  display: block;
  font-size: 24rpx;
  color: var(--zw-text-tertiary);
  margin-top: 8rpx;
}
</style>
