<template>
  <div class="workbench-summary" data-testid="workbench-summary">
    <template v-if="loading">
      <div v-for="n in 4" :key="n" class="summary-card summary-card--skeleton">
        <el-skeleton animated>
          <template #template>
            <el-skeleton-item variant="text" style="width: 38%" />
            <el-skeleton-item variant="h1" style="width: 56%; margin-top: 14px" />
            <el-skeleton-item variant="text" style="width: 72%; margin-top: 10px" />
          </template>
        </el-skeleton>
      </div>
    </template>
    <button
      v-for="card in cards"
      v-else
      :key="card.key"
      type="button"
      class="summary-card"
      :class="{ 'summary-card--alert': card.alert }"
      @click="emit('select', card)"
    >
      <span class="summary-label">{{ card.label }}</span>
      <span class="summary-value">{{ card.value }}</span>
      <span class="summary-sub">{{ card.sub || ' ' }}</span>
    </button>
  </div>
</template>

<script setup lang="ts">
export interface WorkbenchSummaryCard {
  key: string
  label: string
  value: number | string
  sub?: string
  alert?: boolean
}

withDefaults(defineProps<{ cards: WorkbenchSummaryCard[]; loading?: boolean }>(), {
  loading: false
})

const emit = defineEmits<{ (e: 'select', card: WorkbenchSummaryCard): void }>()
</script>

<style scoped>
.workbench-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--zw-space-md);
  margin-bottom: var(--zw-space-md);
}

.summary-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--zw-space-xs);
  padding: var(--zw-space-md);
  border: 1px solid var(--zw-border);
  border-radius: var(--zw-radius-md);
  background: var(--zw-bg-card);
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.summary-card:hover {
  border-color: var(--zw-brand);
  box-shadow: var(--zw-shadow-sm);
}

.summary-card--alert {
  border-color: var(--zw-danger);
}

.summary-card--skeleton {
  cursor: default;
}

.summary-label {
  color: var(--zw-text-secondary);
  font-size: var(--zw-font-size-sm);
}

.summary-value {
  color: var(--zw-text-primary);
  font-family: var(--zw-font-display);
  font-size: var(--zw-font-size-2xl);
  font-weight: 700;
  line-height: 1.1;
}

.summary-card--alert .summary-value {
  color: var(--zw-danger);
}

.summary-sub {
  color: var(--zw-text-secondary);
  font-size: var(--zw-font-size-xs);
  min-height: 16px;
}

@media (max-width: 992px) {
  .workbench-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
