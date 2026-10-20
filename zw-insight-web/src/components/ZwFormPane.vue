<template>
  <div class="zw-form-pane" :class="{ 'is-loading': loading }">
    <header v-if="title || description" class="zw-form-pane__header">
      <h3 v-if="title" class="zw-form-pane__title">{{ title }}</h3>
      <p v-if="description" class="zw-form-pane__description">{{ description }}</p>
    </header>
    <div class="zw-form-pane__layout">
      <div class="zw-form-pane__body">
        <slot />
      </div>
      <aside v-if="anchor && sections.length" class="zw-form-pane__anchor">
        <ZwFormAffixNav :sections="sections" />
      </aside>
    </div>
    <footer v-if="$slots.actions" class="zw-form-pane__actions">
      <slot name="actions" />
    </footer>
    <section v-if="$slots.approval" class="zw-form-pane__approval">
      <slot name="approval" />
    </section>
  </div>
</template>

<script setup lang="ts">
/**
 * ZwFormPane — 表单契约容器（阶段三 R5）。
 * 提供：标题/描述、分区样式类、可选锚点导航（页面级长表单）、底部操作区、审批区、
 * 派生只读样式。弹窗内表单（本期三个样板）不启用锚点，只统一分区标题与只读样式；
 * 锚点导航复用 ZwFormAffixNav（依赖 window 滚动，仅页面级长表单启用）。
 *
 * 分区约定：调用方在默认 slot 内用
 * `<div class="zw-form-pane__section-title">分区名</div>` 标记分区，
 * 需要锚点时把分区 id 一并传入 sections。
 */
import type { NavSection } from '@/components/ZwFormAffixNav.vue'
import ZwFormAffixNav from '@/components/ZwFormAffixNav.vue'

withDefaults(defineProps<{
  title?: string
  description?: string
  sections?: NavSection[]
  anchor?: boolean
  loading?: boolean
}>(), {
  title: '',
  description: '',
  sections: () => [],
  anchor: false,
  loading: false
})
</script>

<style scoped>
.zw-form-pane {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-md);
}
.zw-form-pane__header {
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding-bottom: var(--zw-space-sm);
}
.zw-form-pane__title {
  margin: 0;
  font-size: var(--zw-font-size-lg);
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.zw-form-pane__description {
  margin: var(--zw-space-xs) 0 0;
  font-size: var(--zw-font-size-sm);
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
.zw-form-pane__layout {
  display: flex;
  gap: var(--zw-space-lg);
  align-items: flex-start;
}
.zw-form-pane__body {
  flex: 1;
  min-width: 0;
}
.zw-form-pane__anchor {
  flex: 0 0 220px;
  position: sticky;
  top: var(--zw-space-md);
}
.zw-form-pane__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--zw-space-sm);
  padding-top: var(--zw-space-sm);
  border-top: 1px solid var(--el-border-color-lighter);
}
.zw-form-pane__approval {
  padding: var(--zw-space-sm);
  background: var(--el-fill-color-lighter);
  border-radius: var(--zw-radius-sm);
}
/* 分区标题（调用方在默认 slot 内使用） */
:deep(.zw-form-pane__section-title) {
  margin: var(--zw-space-md) 0 var(--zw-space-sm);
  padding-left: var(--zw-space-sm);
  border-left: 3px solid var(--el-color-primary);
  font-size: var(--zw-font-size-sm);
  font-weight: 600;
  color: var(--el-text-color-primary);
}
:deep(.zw-form-pane__section-title:first-child) {
  margin-top: 0;
}
/* 派生只读样式：仅展示、不可编辑的派生字段 */
:deep(.zw-form-pane__derived) {
  padding: var(--zw-space-xs) var(--zw-space-sm);
  background: var(--el-fill-color-light);
  border-radius: var(--zw-radius-sm);
  color: var(--el-text-color-secondary);
}
</style>
