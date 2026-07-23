<template>
  <div class="page-container">
    <div v-if="title || $slots.actions" class="page-header">
      <div class="page-header-left">
        <h3 v-if="title">{{ title }}</h3>
        <p v-if="subtitle" class="page-subtitle">{{ subtitle }}</p>
      </div>
      <div v-if="$slots.actions" class="page-actions">
        <slot name="actions" />
      </div>
    </div>
    <div class="page-body">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  subtitle?: string
}>()
</script>

<style scoped>
.page-container {
  padding: var(--zw-content-padding);
  max-width: var(--zw-content-max-width);
  margin: 0 auto;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--zw-space-lg);
}

.page-header-left h3 {
  font-size: var(--zw-font-size-xl);
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
}

.page-subtitle {
  margin-top: 4px;
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-tertiary);
}

.page-actions {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  flex-shrink: 0;
}
</style>
