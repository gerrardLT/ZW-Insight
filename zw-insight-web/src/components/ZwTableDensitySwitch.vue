<template>
  <div class="zw-table-density-switch" role="group" aria-label="表格密度切换">
    <el-tooltip content="紧凑视图 (超多列高密)" placement="top" :show-after="400">
      <button
        type="button"
        class="density-btn"
        :class="{ active: currentDensity === 'compact' }"
        @click="selectDensity('compact')"
      >
        <span class="density-icon compact-bars"></span>
        <span class="density-label">紧凑</span>
      </button>
    </el-tooltip>
    <el-tooltip content="标准视图" placement="top" :show-after="400">
      <button
        type="button"
        class="density-btn"
        :class="{ active: currentDensity === 'default' }"
        @click="selectDensity('default')"
      >
        <span class="density-icon default-bars"></span>
        <span class="density-label">标准</span>
      </button>
    </el-tooltip>
    <el-tooltip content="宽松视图 (大屏宽松)" placement="top" :show-after="400">
      <button
        type="button"
        class="density-btn"
        :class="{ active: currentDensity === 'loose' }"
        @click="selectDensity('loose')"
      >
        <span class="density-icon loose-bars"></span>
        <span class="density-label">宽松</span>
      </button>
    </el-tooltip>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAppStore, type TableDensity } from '@/stores/app'

const props = withDefaults(defineProps<{
  /** 是否与全局 appStore 同步，默认 true */
  global?: boolean
  /** 外部绑定的密度，未提供时跟随全局 */
  modelValue?: TableDensity
}>(), {
  global: true
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: TableDensity): void
  (e: 'change', value: TableDensity): void
}>()

const appStore = useAppStore()

const currentDensity = computed(() => {
  if (props.modelValue !== undefined) {
    return props.modelValue
  }
  return appStore.tableDensity
})

function selectDensity(density: TableDensity) {
  if (props.global) {
    appStore.setTableDensity(density)
  }
  emit('update:modelValue', density)
  emit('change', density)
}
</script>

<style scoped>
.zw-table-density-switch {
  display: inline-flex;
  align-items: center;
  border: 1px solid var(--zw-border);
  border-radius: var(--zw-radius-xs);
  background: var(--zw-bg-card);
  padding: 1px;
  gap: 1px;
}

.density-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 26px;
  padding: 0 8px;
  background: transparent;
  border: none;
  border-radius: var(--zw-radius-xs);
  color: var(--zw-text-secondary);
  font-size: 12px;
  font-weight: var(--zw-font-weight-medium);
  cursor: pointer;
  transition: all var(--zw-transition-fast);
}

.density-btn:hover {
  color: var(--zw-text-primary);
  background: var(--zw-bg-hover);
}

.density-btn.active {
  background: var(--zw-brand);
  color: var(--zw-on-primary); /* 橙底深字承重规则 */
  font-weight: var(--zw-font-weight-semibold);
}

/* 工业条状示意图标 */
.density-icon {
  display: inline-flex;
  flex-direction: column;
  justify-content: space-between;
  width: 12px;
  height: 10px;
}

.compact-bars {
  background: repeating-linear-gradient(
    to bottom,
    currentColor 0,
    currentColor 1.5px,
    transparent 1.5px,
    transparent 3px
  );
}

.default-bars {
  background: repeating-linear-gradient(
    to bottom,
    currentColor 0,
    currentColor 2px,
    transparent 2px,
    transparent 5px
  );
}

.loose-bars {
  background: repeating-linear-gradient(
    to bottom,
    currentColor 0,
    currentColor 2.5px,
    transparent 2.5px,
    transparent 7px
  );
}

.density-label {
  font-family: var(--zw-font-mono);
  font-size: 11px;
}
</style>
