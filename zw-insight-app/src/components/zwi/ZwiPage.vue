<template>
  <!--
    ZwiPage 页面骨架（Stage 0.3）：统一页面留白与安全区底部
    - padding 24rpx + 底部 tabbar 安全距离
    - 契约 class：.zw-page（页面根）
  -->
  <view class="zw-page">
    <slot />
  </view>
</template>

<script setup lang="ts">
defineOptions({ name: 'ZwiPage' })
</script>

<style scoped>
.zw-page {
  padding: 24rpx;
  /* 非 tabbar 页（详情/表单）也预留底部安全区，防止内容贴 home indicator */
  padding-bottom: calc(24rpx + var(--zw-safe-bottom));
  min-height: 100vh;
  box-sizing: border-box;
}
</style>
