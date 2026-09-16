<template>
  <!-- 工业精密骨架屏（S3.1）：2px 直角灰块 + 品牌橙高亮条 + shimmer 扫描
       list: 列表卡片骨架; form: 表单行骨架; card: 概览卡骨架 -->
  <view class="zw-skeleton" :class="`type-${type}`">
    <!-- 列表骨架：N 张卡片，每张 = 标题条 + 2 行内容 + 右侧状态块 -->
    <template v-if="type === 'list'">
      <view v-for="i in rows" :key="i" class="sk-card">
        <view class="sk-row">
          <view class="sk-bar w-40" />
          <view class="sk-chip" :class="{ accent: i === 1 }" />
        </view>
        <view class="sk-bar w-90" />
        <view class="sk-bar w-60" />
      </view>
    </template>

    <!-- 表单骨架：N 行字段（label 短条 + input 长条） -->
    <template v-else-if="type === 'form'">
      <view v-for="i in rows" :key="i" class="sk-field">
        <view class="sk-bar w-24" />
        <view class="sk-bar w-fill" :class="{ accent: i === 1 }" />
      </view>
      <view class="sk-submit" />
    </template>

    <!-- 概览卡骨架：2×2 数字卡 -->
    <template v-else>
      <view class="sk-grid">
        <view v-for="i in 4" :key="i" class="sk-stat">
          <view class="sk-bar w-30" />
          <view class="sk-bar w-50 big" :class="{ accent: i <= 2 }" />
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
/** 骨架屏（感知性能优化 S3.1）：加载中替代「加载中...」文字 */
withDefaults(defineProps<{
  /** 骨架形态 */
  type?: 'list' | 'form' | 'card'
  /** list/form 行数（默认 3） */
  rows?: number
}>(), {
  type: 'list',
  rows: 3,
})
</script>

<style scoped>
.zw-skeleton {
  width: 100%;
}

/* 列表卡片骨架 */
.sk-card {
  background: var(--zw-bg-card);
  border: 1rpx solid var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  padding: 24rpx;
  margin-bottom: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}
.sk-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4rpx;
}

/* 表单字段骨架 */
.sk-field {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 28rpx;
}
.sk-submit {
  height: 88rpx; /* 44pt 触控合规 */
  background: var(--zw-bg-hover);
  border-radius: var(--zw-radius-sm);
  margin-top: 24rpx;
}

/* 概览卡网格 */
.sk-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20rpx;
}
.sk-stat {
  background: var(--zw-bg-card);
  border: 1rpx solid var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  padding: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

/* 基础灰条（shimmer 扫描） */
.sk-bar {
  height: 28rpx;
  border-radius: var(--zw-radius-xs);
  background: var(--zw-bg-hover);
  background-image: linear-gradient(
    90deg,
    var(--zw-bg-hover) 0%,
    var(--zw-bg-active) 50%,
    var(--zw-bg-hover) 100%
  );
  background-size: 200% 100%;
  animation: sk-shimmer 1.4s ease-in-out infinite;
}
.sk-bar.big { height: 44rpx; }
.sk-bar.w-24 { width: 24%; }
.sk-bar.w-40 { width: 40%; }
.sk-bar.w-50 { width: 50%; }
.sk-bar.w-60 { width: 60%; }
.sk-bar.w-90 { width: 90%; }
.sk-bar.w-fill { width: 100%; height: 72rpx; }

/* 品牌橙高亮条（模拟关键数据位） */
.sk-bar.accent {
  background-image: linear-gradient(
    90deg,
    var(--zw-bg-active) 0%,
    var(--zw-brand-light) 50%,
    var(--zw-bg-active) 100%
  );
}

/* 状态徽章块 */
.sk-chip {
  width: 96rpx;
  height: 32rpx;
  border-radius: var(--zw-radius-xs);
  background: var(--zw-bg-hover);
}
.sk-chip.accent { background: var(--zw-bg-active); }

@keyframes sk-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
