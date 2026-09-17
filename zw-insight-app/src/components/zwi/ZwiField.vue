<template>
  <!--
    ZwiField 表单行（Stage 0.3）——critique P2「placeholder 承载唯一指引」的解
    - 契约 class：.form-item（行容器）+ .form-label（常驻 label）+ .form-input（输入框）
    - label 上置常驻（不随聚焦消失）；辅助文案插槽（格式提示）；校验错误位
    - 输入热区 88rpx（44pt 触控底线）
  -->
  <view class="form-item" :class="{ 'form-item-error': !!error }">
    <text v-if="label" class="form-label">
      {{ label }}<text v-if="required" class="form-required">*</text>
    </text>
    <view class="form-control">
      <slot>
        <input
          class="form-input"
          :value="modelValue"
          :type="inputType"
          :placeholder="placeholder"
          :disabled="disabled"
          :maxlength="maxlength"
          :cursor-spacing="24"
          @input="$emit('update:modelValue', ($event as any).detail?.value ?? ($event as any).target?.value)"
        />
      </slot>
      <slot name="suffix" />
    </view>
    <text v-if="hint && !error" class="form-hint">{{ hint }}</text>
    <text v-if="error" class="form-error">{{ error }}</text>
  </view>
</template>

<script setup lang="ts">
defineOptions({ name: 'ZwiField' })

withDefaults(
  defineProps<{
    /** 双向绑定值（默认插槽被覆盖时由插槽方自行绑定） */
    modelValue?: string | number
    /** 常驻字段标签（承载语义，替代 placeholder 唯一指引） */
    label?: string
    /** 必填标记（红色 *） */
    required?: boolean
    /** placeholder（仅作格式示例提示，不承载语义） */
    placeholder?: string
    /** 输入类型 */
    inputType?: 'text' | 'number' | 'digit' | 'password'
    /** 辅助文案（如「格式：YYYY-MM-DD」） */
    hint?: string
    /** 校验错误（置空 hint 显示） */
    error?: string
    /** 禁用 */
    disabled?: boolean
    maxlength?: number
  }>(),
  {
    modelValue: '',
    label: '',
    required: false,
    placeholder: '',
    inputType: 'text',
    hint: '',
    error: '',
    disabled: false,
    maxlength: undefined,
  }
)

defineEmits<{ (e: 'update:modelValue', v: string): void }>()
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
.form-control {
  display: flex;
  align-items: center;
  gap: 12rpx;
}
.form-input {
  flex: 1;
  min-height: 88rpx; /* 44pt 触控底线 */
  padding: 0 20rpx;
  background: var(--zw-bg-card);
  border: 1rpx solid var(--zw-border);
  border-radius: var(--zw-radius-xs);
  font-size: 28rpx;
  color: var(--zw-text-primary);
  box-sizing: border-box;
  transition: border-color var(--zw-duration-fast) var(--zw-ease-out);
}
.form-item-error .form-input {
  border-color: var(--zw-danger);
}
.form-hint {
  display: block;
  font-size: 22rpx;
  color: var(--zw-text-tertiary);
  margin-top: 8rpx;
}
.form-error {
  display: block;
  font-size: 22rpx;
  color: var(--zw-danger);
  margin-top: 8rpx;
}
</style>
