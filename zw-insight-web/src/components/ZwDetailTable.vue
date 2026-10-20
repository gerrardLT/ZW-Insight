<template>
  <div class="zw-detail-table">
    <div class="zw-detail-table__bar">
      <el-button v-if="addable" size="small" @click="emit('add')">{{ addText }}</el-button>
      <el-button v-if="importable" size="small" @click="emit('import')">{{ importText }}</el-button>
      <el-button v-if="resettable" size="small" @click="handleReset">{{ resetText }}</el-button>
      <span v-if="summaryText" class="zw-detail-table__summary">
        {{ summaryLabel }}：{{ summaryText }}
      </span>
      <slot name="actions" />
    </div>
    <el-table
      :data="modelValue"
      :border="border"
      :size="size"
      :empty-text="emptyText"
      class="zw-detail-table__table"
    >
      <slot />
    </el-table>
  </div>
</template>

<script setup lang="ts">
/**
 * 可编辑明细表容器（3.4：自 budget/index 上提）。
 *
 * 只统一「添加行 / 模板导入 / 重置 / 行合计」四件套，不接管列定义：
 * 业务页通过默认插槽注入 el-table-column，行数据仍由页面 v-model 持有，
 * 因此不会把成本类别下拉、单价联动等业务差异硬塞进组件。
 *
 * 约定：
 * - `add` 由页面处理（组件不构造行，避免默认行与业务必填字段不一致）；
 * - `import` 由页面打开批量导入弹窗（组件不感知导入实现）；
 * - `reset` 清空 v-model（同时 emit reset 便于页面做二次清理）；
 * - `summaryText` 由页面格式化后传入（金额口径以页面为准，组件只展示）。
 */
withDefaults(
  defineProps<{
    modelValue: any[]
    /** 是否显示「添加行」 */
    addable?: boolean
    addText?: string
    /** 是否显示「模板导入」（通常仅编辑态可用） */
    importable?: boolean
    importText?: string
    /** 是否显示「重置」 */
    resettable?: boolean
    resetText?: string
    /** 行合计文案（已格式化）；为空则不显示合计 */
    summaryLabel?: string
    summaryText?: string
    border?: boolean
    size?: 'large' | 'default' | 'small'
    emptyText?: string
  }>(),
  {
    modelValue: () => [],
    addable: true,
    addText: '添加明细行',
    importable: false,
    importText: '模板导入',
    resettable: true,
    resetText: '重置',
    summaryLabel: '明细合计',
    summaryText: '',
    border: true,
    size: 'small',
    emptyText: '暂无明细'
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', rows: any[]): void
  (e: 'add'): void
  (e: 'import'): void
  (e: 'reset'): void
}>()

function handleReset() {
  emit('update:modelValue', [])
  emit('reset')
}
</script>

<style scoped>
.zw-detail-table__bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zw-space-sm);
  margin-bottom: var(--zw-space-sm);
}

.zw-detail-table__summary {
  color: var(--zw-text-tertiary);
  font-size: var(--zw-font-size-xs);
}

.zw-detail-table__table {
  width: 100%;
}
</style>
