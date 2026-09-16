<template>
  <span class="field-help-label" :data-testid="'field-help-' + label">
    {{ label }}
    <el-tooltip
      v-if="tooltip"
      :content="tooltip"
      placement="top"
      :show-after="200"
    >
      <el-icon class="field-help-icon"><IconHelpSmall /></el-icon>
    </el-tooltip>
  </span>
</template>

<script setup lang="ts">
/**
 * 带业务定义提示的表单字段标签（Phase 1.3）。
 *
 * 用于 el-form-item / el-descriptions-item 的 #label 插槽，为复杂字段内嵌
 * 业务定义；完整说明统一收录在帮助中心（/help）业务术语词典，此处只放一句话速查。
 *
 * @example
 * <el-form-item prop="paymentAmount">
 *   <template #label>
 *     <FieldHelpLabel label="付款金额" tooltip="可付上限 = 累计结算 + 净奖惩 − 累计已付" />
 *   </template>
 *   ...
 * </el-form-item>
 */
import { IconHelpSmall } from '@tabler/icons-vue'

defineProps<{
  /** 字段名 */
  label: string
  /** 业务定义速查（不传则不渲染图标） */
  tooltip?: string
}>()
</script>

<style scoped>
.field-help-label {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.field-help-icon {
  font-size: 13px;
  color: var(--zw-text-tertiary);
  cursor: help;
  transition: color var(--zw-transition-fast);
}

.field-help-icon:hover {
  color: var(--zw-brand);
}
</style>
