<template>
  <el-popover placement="bottom-end" :width="220" trigger="click">
    <template #reference>
      <el-button link title="列设置">
        <el-icon><Setting /></el-icon>
      </el-button>
    </template>
    <div class="column-setting">
      <div class="header">
        <span>列显示</span>
        <el-button link size="small" @click="reset">重置</el-button>
      </div>
      <el-checkbox-group v-model="checked">
        <el-checkbox v-for="(col, i) in columns" :key="col.key" :value="i" :label="col.label" />
      </el-checkbox-group>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Setting } from '@element-plus/icons-vue'
import type { ColumnDef, } from '@/composables/useColumnSetting'

const props = defineProps<{
  columns: ColumnDef[]
  visible: boolean[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean[]): void
  (e: 'reset'): void
}>()

// checkbox 绑定的可见列索引数组
const checked = computed({
  get: () => props.visible.map((v, i) => (v ? i : -1)).filter(i => i >= 0),
  set: (val: number[]) => {
    const next = props.columns.map((_, i) => val.includes(i))
    emit('update:visible', next)
  },
})

function reset() {
  emit('reset')
}
</script>

<style scoped lang="scss">
.column-setting {
  .header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: var(--zw-font-size-sm);
    font-weight: 600;
    margin-bottom: var(--zw-space-sm);
    padding-bottom: var(--zw-space-xs);
    border-bottom: 1px solid var(--zw-border-light);
  }
  :deep(.el-checkbox) {
    display: flex;
    margin-right: 0;
    width: 100%;
  }
}
</style>
