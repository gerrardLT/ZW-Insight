<template>
  <div class="zw-skeleton" :class="`zw-skeleton--${variant}`" :data-testid="`zw-skeleton-${variant}`" role="status" aria-label="加载中">
    <!-- KPI 卡片骨架：图标块 + 标签 + 数值 -->
    <template v-if="variant === 'cards'">
      <div v-for="i in count" :key="i" class="zw-skeleton__card">
        <el-skeleton-item variant="circle" class="zw-skeleton__card-icon" />
        <div class="zw-skeleton__card-lines">
          <el-skeleton-item variant="text" class="zw-skeleton__card-label" />
          <el-skeleton-item variant="h1" class="zw-skeleton__card-value" />
        </div>
      </div>
    </template>

    <!-- 表格骨架：工具条 + 表头 + 数据行 -->
    <template v-else-if="variant === 'table'">
      <div class="zw-skeleton__toolbar">
        <el-skeleton-item variant="text" class="zw-skeleton__tool" />
        <el-skeleton-item variant="button" class="zw-skeleton__tool" />
        <el-skeleton-item variant="button" class="zw-skeleton__tool" />
      </div>
      <div class="zw-skeleton__thead">
        <el-skeleton-item v-for="c in cols" :key="'h' + c" variant="text" class="zw-skeleton__th" />
      </div>
      <div v-for="r in rows" :key="r" class="zw-skeleton__tr">
        <el-skeleton-item v-for="c in cols" :key="c" variant="text" class="zw-skeleton__td" :style="{ width: cellWidth(c) }" />
      </div>
    </template>

    <!-- 图表骨架：标题 + 大块画布 + 图例 -->
    <template v-else-if="variant === 'chart'">
      <el-skeleton-item variant="text" class="zw-skeleton__chart-title" />
      <el-skeleton-item variant="image" class="zw-skeleton__chart-canvas" />
      <div class="zw-skeleton__legend">
        <el-skeleton-item v-for="i in 3" :key="i" variant="text" class="zw-skeleton__legend-item" />
      </div>
    </template>

    <!-- 详情骨架：标题 + 描述行 + 表单块（默认） -->
    <template v-else>
      <el-skeleton-item variant="h3" class="zw-skeleton__detail-title" />
      <el-skeleton-item v-for="i in 2" :key="'d' + i" variant="text" class="zw-skeleton__detail-line" />
      <div class="zw-skeleton__detail-grid">
        <el-skeleton-item v-for="i in rows" :key="'g' + i" variant="text" class="zw-skeleton__detail-field" />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
/**
 * 骨架屏资产：统一加载态占位，替代裸 v-loading 白块。
 *
 * 四种形态对齐系统高频页面结构：
 *   cards  —— 首页/驾驶舱 KPI 卡行
 *   table  —— 列表页（工具条 + 表头 + 行）
 *   chart  —— 图表卡（标题 + 画布 + 图例）
 *   detail —— 详情/表单页
 * 主题走 --zw-* 变量（el-skeleton 底色变量在本组件内重映射），明暗自适应。
 */
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    variant?: 'cards' | 'table' | 'chart' | 'detail'
    /** cards 卡片数 / table 行数 / detail 字段数 */
    count?: number
    rows?: number
    cols?: number
  }>(),
  { variant: 'detail', count: 4, rows: 5, cols: 5 }
)

/** CSS v-bind 需引用响应式量（卡片栅格列数） */
const cardCount = computed(() => props.count)

/** 表格单元格宽度做轻微起伏，避免机械等宽的“假骨架”感 */
const PATTERN = ['38%', '62%', '45%', '70%', '30%', '55%', '48%']
function cellWidth(c: number): string {
  return PATTERN[(c - 1) % PATTERN.length]
}
</script>

<style scoped>
.zw-skeleton {
  width: 100%;
  --el-skeleton-color: color-mix(in srgb, var(--zw-text-quaternary) 12%, transparent);
  --el-skeleton-to-color: color-mix(in srgb, var(--zw-text-quaternary) 4%, transparent);
}

/* cards */
.zw-skeleton__card {
  display: flex;
  align-items: center;
  gap: var(--zw-space-md);
  padding: var(--zw-space-lg);
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  background: var(--zw-bg-card, var(--zw-bg-page));
}

.zw-skeleton--cards {
  display: grid;
  grid-template-columns: repeat(v-bind(cardCount), minmax(0, 1fr));
  gap: var(--zw-space-md);
}

.zw-skeleton__card-icon {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
}

.zw-skeleton__card-lines {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-sm);
  flex: 1;
}

.zw-skeleton__card-label {
  width: 56%;
  height: 12px;
}

.zw-skeleton__card-value {
  width: 80%;
  height: 26px;
}

/* table */
.zw-skeleton__toolbar {
  display: flex;
  gap: var(--zw-space-sm);
  margin-bottom: var(--zw-space-md);
}

.zw-skeleton__tool {
  width: 160px;
  height: 28px;
}

.zw-skeleton__thead,
.zw-skeleton__tr {
  display: flex;
  gap: var(--zw-space-md);
  align-items: center;
}

.zw-skeleton__thead {
  padding: var(--zw-space-sm) 0;
  border-bottom: 1px solid var(--zw-border-light);
}

.zw-skeleton__tr {
  padding: var(--zw-space-md) 0;
}

.zw-skeleton__th {
  flex: 1;
  height: 12px;
}

.zw-skeleton__td {
  flex: 1;
  height: 14px;
}

/* chart */
.zw-skeleton__chart-title {
  width: 120px;
  height: 14px;
  margin-bottom: var(--zw-space-md);
}

.zw-skeleton__chart-canvas {
  width: 100%;
  height: 240px;
  border-radius: var(--zw-radius-sm);
}

.zw-skeleton__legend {
  display: flex;
  gap: var(--zw-space-lg);
  margin-top: var(--zw-space-md);
}

.zw-skeleton__legend-item {
  width: 64px;
  height: 10px;
}

/* detail */
.zw-skeleton__detail-title {
  width: 200px;
  height: 20px;
  margin-bottom: var(--zw-space-md);
}

.zw-skeleton__detail-line {
  width: 100%;
  height: 13px;
  margin-bottom: var(--zw-space-xs);
}

.zw-skeleton__detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--zw-space-md);
  margin-top: var(--zw-space-lg);
}

.zw-skeleton__detail-field {
  width: 100%;
  height: 14px;
}

@media (max-width: 767px) {
  .zw-skeleton--cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .zw-skeleton__detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
