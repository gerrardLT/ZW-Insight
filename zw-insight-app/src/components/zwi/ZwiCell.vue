<template>
  <!--
    ZwiCell 通用列表行（Stage 0.3）
    - 契约 class：.cell-row（新范式）
    - 布局：左主区（title/desc）+ 右侧值区（value 徽章插槽）+ 可选箭头
    - min-height 88rpx（44pt 触控底线）、hairline 分隔（last 无）、active-press 反馈
  -->
  <view
    class="cell-row"
    :class="{ 'cell-clickable': clickable, 'cell-last': last }"
    :hover-class="clickable ? 'cell-hover' : ''"
    :hover-stay-time="80"
    @click="clickable && $emit('click')"
  >
    <view class="cell-main">
      <text class="cell-title" :class="{ 'cell-ellipsis': ellipsis }">{{ title }}</text>
      <text v-if="desc" class="cell-desc">{{ desc }}</text>
    </view>
    <view class="cell-right">
      <slot name="value" />
      <text v-if="value" class="cell-value">{{ value }}</text>
      <text v-if="arrow" class="cell-arrow">›</text>
    </view>
  </view>
</template>

<script setup lang="ts">
defineOptions({ name: 'ZwiCell' })

withDefaults(
  defineProps<{
    /** 主标题 */
    title: string
    /** 副行描述（发起人/摘要等） */
    desc?: string
    /** 右侧值文本（金额/时间）；复杂内容用 #value 插槽 */
    value?: string
    /** 是否可点击（触发 click + 按压反馈） */
    clickable?: boolean
    /** 右侧箭头 */
    arrow?: boolean
    /** 最后一行（去掉底部分隔线） */
    last?: boolean
    /** 标题超长省略（默认两行截断，false 则完整换行） */
    ellipsis?: boolean
  }>(),
  { desc: '', value: '', clickable: false, arrow: false, last: false, ellipsis: true }
)

defineEmits<{ (e: 'click'): void }>()
</script>

<style scoped>
.cell-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  min-height: 88rpx; /* 44pt 触控底线 */
  padding: 20rpx 0;
  border-bottom: 1rpx solid var(--zw-border-light);
  box-sizing: border-box;
}
.cell-last {
  border-bottom: none;
}
.cell-clickable {
  cursor: pointer;
}
.cell-hover {
  background: var(--zw-bg-hover);
}
.cell-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.cell-title {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--zw-text-primary);
  line-height: 1.4;
}
.cell-ellipsis {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.cell-desc {
  font-size: 24rpx;
  color: var(--zw-text-tertiary);
  line-height: 1.4;
}
.cell-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
  flex-shrink: 0;
}
.cell-value {
  font-size: 26rpx;
  color: var(--zw-text-secondary);
  font-family: var(--zw-font-mono);
  font-variant-numeric: tabular-nums;
}
.cell-arrow {
  font-size: 32rpx;
  color: var(--zw-text-quaternary);
  line-height: 1;
}
</style>
