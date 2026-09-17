<template>
  <!--
    ZwiPickerField 选择行（Stage 0.3）
    - 契约 class：.form-item + .form-label（与 ZwiField 同族）
    - 触发器展示已选值/占位灰（placeholder 不承载语义），右箭头；点击触发 #picker 插槽
      （wd-picker / ZwBottomSheetPicker 由调用方决定，壳只管行布局与契约）
    - 触控 88rpx 热区
  -->
  <view class="form-item" @click="$emit('open')">
    <text v-if="label" class="form-label">
      {{ label }}<text v-if="required" class="form-required">*</text>
    </text>
    <view class="picker-trigger" :class="{ 'picker-placeholder': !displayValue }">
      <slot name="display">
        <text class="picker-value">{{ displayValue || placeholder }}</text>
      </slot>
      <text class="picker-arrow">›</text>
    </view>
    <text v-if="hint" class="form-hint">{{ hint }}</text>
    <!-- 弹层插槽：wd-picker / ZwBottomSheetPicker 等 -->
    <slot name="picker" />
  </view>
</template>

<script setup lang="ts">
defineOptions({ name: 'ZwiPickerField' })

withDefaults(
  defineProps<{
    /** 常驻字段标签 */
    label?: string
    /** 已选值展示文本（空则显示 placeholder） */
    displayValue?: string
    /** 占位提示（仅视觉灰，语义在 label） */
    placeholder?: string
    required?: boolean
    /** 辅助文案 */
    hint?: string
  }>(),
  { label: '', displayValue: '', placeholder: '请选择', required: false, hint: '' }
)

defineEmits<{ (e: 'open'): void }>()
</script>

<style scoped>
.form-item {
  margin-bottom: 24rpx;
}
.form-label {
  display: block;
  font-size: 26rpx;
  font-weight: 500;
  color: var(--zw-text-secondary);
  margin-bottom: 10rpx;
}
.form-required {
  color: var(--zw-danger);
  margin-left: 4rpx;
}
.picker-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  min-height: 88rpx; /* 44pt 触控底线 */
  padding: 0 20rpx;
  background: var(--zw-bg-card);
  border: 1rpx solid var(--zw-border);
  border-radius: var(--zw-radius-xs);
  box-sizing: border-box;
}
.picker-trigger:active {
  background: var(--zw-bg-hover);
}
.picker-placeholder .picker-value {
  color: var(--zw-text-quaternary);
}
.picker-value {
  font-size: 28rpx;
  color: var(--zw-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.picker-arrow {
  font-size: 32rpx;
  color: var(--zw-text-quaternary);
  line-height: 1;
  flex-shrink: 0;
}
.form-hint {
  display: block;
  font-size: 22rpx;
  color: var(--zw-text-tertiary);
  margin-top: 8rpx;
}
</style>
