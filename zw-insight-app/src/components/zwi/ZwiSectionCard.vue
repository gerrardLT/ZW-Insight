<template>
  <!--
    ZwiSectionCard 分区卡（Stage 0.3）：plate-header 升级——3px 品牌竖标 + 标题 + 右侧 action
    - 契约 class：.section + .zw-card + .section-title（页面既有测试与样式依赖）
    - hairline 边框 + 直角 + 零阴影（工业纪律）+ 卡内 28rpx 呼吸留白
  -->
  <view class="section zw-card zwi-section-card">
    <view v-if="title || $slots.action || $slots.title" class="section-header">
      <view class="section-heading">
        <text v-if="eyebrow" class="eyebrow-cap">{{ eyebrow }}</text>
        <!-- 富标题插槽（角标等内联元素；未用时退回纯文本 title prop） -->
        <slot name="title">
          <text class="section-title">{{ title }}</text>
        </slot>
      </view>
      <slot name="action" />
    </view>
    <slot />
  </view>
</template>

<script setup lang="ts">
defineOptions({ name: 'ZwiSectionCard' })

withDefaults(
  defineProps<{
    /** 区块标题 */
    title?: string
    /** 大写英文眉题（可选，如 Shortcuts / Inbox） */
    eyebrow?: string
  }>(),
  { title: '', eyebrow: '' }
)
</script>

<style scoped>
.zwi-section-card {
  padding: 28rpx;
  margin-bottom: 24rpx;
}
/* 契约 class 样式源：页面原 .section-title 定义收敛于此（30rpx bold primary） */
.section-title {
  font-size: 30rpx;
  font-weight: bold;
  color: var(--zw-text-primary);
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
  min-height: 44px; /* 触控底线：action 区可点 */
  box-sizing: border-box;
}
.section-heading {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  /* 3px 品牌竖标（plate-header 签名元素移动到 heading，随标题走） */
  padding-left: 16rpx;
}
.section-heading::before {
  content: '';
  position: absolute;
  left: 0;
  top: 4rpx;
  bottom: 4rpx;
  width: 6rpx;
  background: var(--zw-brand);
}
.eyebrow-cap {
  font-family: var(--zw-font-display);
  font-size: 20rpx;
  font-weight: 700;
  letter-spacing: 1.5px;
  text-transform: uppercase;
  color: var(--zw-text-tertiary);
}
</style>
