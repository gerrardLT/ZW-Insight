<template>
  <div class="zw-list-toolbar">
    <div class="zw-list-toolbar__left">
      <slot />
    </div>
    <div class="zw-list-toolbar__right">
      <slot name="actions" />
      <el-tooltip content="刷新" placement="top">
        <el-button :icon="Refresh" circle @click="$emit('refresh')" />
      </el-tooltip>
      <slot name="density" />
      <slot name="export" />
      <ColumnSettingPopover
        v-if="columns.length"
        :columns="columns"
        :visible="visible"
        @update:visible="$emit('update:visible', $event)"
        @reset="$emit('reset')"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import ColumnSettingPopover from '@/components/ColumnSettingPopover.vue'

/**
 * 列表页标准工具栏（阶段三 3b 契约件之一）。
 * 左区放业务按钮（新增/批量…，页面 slot 注入），右区固定五件套：刷新/密度(页面插)/导出(页面插)/列设置。
 * 导出与密度因依赖页面的 AsyncExportDialog 与 appStore，仍以 slot 由页面挂载——保持组件无副作用。
 */
defineProps<{
  columns: Array<{ key: string; label: string }>
  visible: boolean[]
}>()

defineEmits<{
  (e: 'update:visible', val: boolean[]): void
  (e: 'reset'): void
  (e: 'refresh'): void
}>()
</script>

<style scoped>
.zw-list-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--zw-space-md);
}
.zw-list-toolbar__right {
  display: flex;
  align-items: center;
  gap: var(--zw-space-xs);
}
</style>
