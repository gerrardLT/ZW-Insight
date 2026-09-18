<!--
  统一空态组件 (ZwEmptyState) — 品牌化蓝图语言（Batch 1.1/1.2，2026-09-17）
  覆盖 4 种类型：数据/错误/离线/权限；插图委托 ZwStateIllustration（形状区分，非颜色区分）。
  纪律（DESIGN Iconography L667/L675）：插图线条走中性 --zw-text-quaternary，
  单一 --zw-brand 橙点缀（橙稀缺，一图一处）；不用图标颜色传递状态（状态由文案承载）。
  #image slot 可覆盖为 bespoke 插图（如 StatChartPanel 的 AI 空白图纸 PNG）。
-->
<template>
  <div class="zw-empty-state" :class="[`type-${type}`, containerClass]">
    <!-- 图标区（契约 class .empty-icon 保留：既有单测依赖）；#image slot 可覆盖为 bespoke 插图 -->
    <div class="empty-icon">
      <slot name="image">
        <ZwStateIllustration :type="type" :aria-label="ariaLabel" size="64px" />
      </slot>
    </div>

    <!-- 标题（可选，方向性叙事） -->
    <div v-if="resolvedTitle" class="empty-title">{{ resolvedTitle }}</div>

    <!-- 描述文本 -->
    <div class="empty-description">
      <slot>{{ resolvedDescription }}</slot>
    </div>

    <!-- 操作区域 -->
    <div v-if="$slots.action" class="empty-action">
      <slot name="action"></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ZwStateIllustration from '@/components/visual/ZwStateIllustration.vue'

const props = withDefaults(
  defineProps<{
    type?: 'data' | 'error' | 'offline' | 'permission'
    /** 描述文案（未传时按类型给方向性默认，"给方向不道歉"） */
    description?: string
    /** 可选标题（强叙事场景，如错误码/无权限原因） */
    title?: string
    containerClass?: string
  }>(),
  { type: 'data', description: '', title: '', containerClass: '' }
)

defineEmits<{ retry: [] }>()

const DEFAULT_TITLE: Record<string, string> = {
  data: '',
  error: '',
  offline: '',
  permission: ''
}

const DEFAULT_TEXT: Record<string, string> = {
  data: '暂无数据',
  error: '加载失败，请刷新重试',
  offline: '网络连接异常，请检查网络后重试',
  permission: '无访问权限，请联系管理员'
}

const resolvedTitle = computed(() => props.title || DEFAULT_TITLE[props.type])
const resolvedDescription = computed(() => props.description || DEFAULT_TEXT[props.type])
const ariaLabel = computed(
  () => `${resolvedTitle.value} ${resolvedDescription.value}`.trim() || '空状态插图'
)
</script>

<style scoped lang="scss">
.zw-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: var(--zw-space-xl);
  text-align: center;

  /* 状态语义仅作用于文案（图标恒中性，遵 DESIGN L675 图标不传状态色） */
  &.type-error .empty-description { color: var(--zw-danger); }
  &.type-offline .empty-description { color: var(--zw-warning); }
  &.type-data .empty-description,
  &.type-permission .empty-description { color: var(--zw-text-tertiary); }

  .empty-icon {
    margin-bottom: var(--zw-space-md);
    line-height: 0;
  }

  .empty-title {
    font-size: var(--zw-font-size-base);
    font-weight: var(--zw-font-weight-semibold);
    color: var(--zw-text-secondary);
    margin-bottom: 4px;
  }

  .empty-description {
    font-size: var(--zw-font-size-sm);
    line-height: 1.5;
    color: var(--zw-text-tertiary);
    margin-bottom: var(--zw-space-lg);
  }

  .empty-action {
    display: flex;
    gap: var(--zw-space-sm);
    margin-top: calc(-1 * var(--zw-space-sm));
  }
}
</style>
