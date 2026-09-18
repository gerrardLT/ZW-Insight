<template>
  <!--
    ZwiFormPage 表单页骨架（Stage 0.3）——解决 critique P0「主操作在滚动底部拇指够不到」
    - 契约 class：.form-page（页面根）
    - sticky 底部操作条：fixed + safe-area；主按钮 .submit-btn（21 页面契约 class）
      + 可选次要操作插槽（#secondary）
    - 内容区底部 200rpx 预留防遮挡；disable-submit 透传 loading/disabled
  -->
  <view class="form-page">
    <view class="form-content">
      <slot />
    </view>
    <view v-if="$slots.footer || submitText" class="form-footer">
      <slot name="footer" />
      <view v-if="submitText" class="form-footer-bar">
        <slot name="secondary" />
        <wd-button
          class="submit-btn"
          size="large"
          :loading="loading"
          :disabled="disabled"
          custom-class="zwi-submit"
          @click="onSubmit"
        >
          {{ submitText }}
        </wd-button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { hapticTap } from '@/utils/haptic'

defineOptions({ name: 'ZwiFormPage' })

withDefaults(
  defineProps<{
    /** 主按钮文案（空则不渲染底部条，纯内容页用） */
    submitText?: string
    /** 主按钮 loading */
    loading?: boolean
    /** 主按钮 disabled */
    disabled?: boolean
  }>(),
  { submitText: '', loading: false, disabled: false }
)

const emit = defineEmits<{ (e: 'submit'): void }>()

/** 提交：先一次轻触觉确认「已触发」（现场手套/强光下视觉反馈易被忽略），再派发 submit */
function onSubmit() {
  hapticTap('light')
  emit('submit')
}
</script>

<style scoped>
.form-page {
  min-height: 100vh;
}
.form-content {
  padding: 24rpx;
  /* 预留底部操作条高度 + 安全区，防最后字段被遮挡 */
  padding-bottom: calc(200rpx + var(--zw-safe-bottom));
}
.form-footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 50;
  background: var(--zw-bg-card);
  border-top: 1rpx solid var(--zw-border-light);
  padding: 16rpx 24rpx calc(16rpx + var(--zw-safe-bottom));
}
.form-footer-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
}
.form-footer-bar .submit-btn {
  flex: 1;
}
</style>

<style>
/* wd-button 主按钮工业皮肤（全局作用域穿透组件样式隔离）：
   橙底深字 + 直角 + 44px 高，替代原 21 页面各自手写的 .submit-btn */
.zwi-submit {
  --wot-button-large-height: 44px;
  --wot-button-large-radius: var(--zw-radius-sm);
  background: var(--zw-brand) !important;
  color: var(--zw-on-primary) !important;
  font-weight: 600;
}
.zwi-submit.is-plain {
  background: transparent !important;
  color: var(--zw-brand) !important;
  border: 1rpx solid var(--zw-brand);
}
</style>
