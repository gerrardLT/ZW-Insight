<template>
  <div class="nav-link-row" :class="{ active }">
    <button class="nav-link" type="button" :aria-current="active ? 'page' : undefined" @click="$emit('navigate', item.path)">
      <el-icon><component :is="resolveMenuIcon(item.icon)" /></el-icon>
      <span>{{ item.title }}</span>
    </button>
    <button class="favorite-button" type="button" :aria-label="favorite ? `取消固定${item.title}` : `固定${item.title}`" @click="$emit('favorite', item.path)">
      {{ favorite ? '★' : '☆' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import type { NavItem } from '@/utils/navigation'
import { resolveMenuIcon } from '@/components/icons/registry'

defineProps<{ item: NavItem; active: boolean; favorite: boolean }>()
defineEmits<{ navigate: [path: string]; favorite: [path: string] }>()
</script>

<style scoped>
.nav-link-row { display: flex; align-items: center; min-height: 38px; }
.nav-link { flex: 1; min-width: 0; display: flex; align-items: center; gap: 10px; padding: 9px 10px; border: 0; background: transparent; color: var(--zw-text-secondary); font: inherit; text-align: left; cursor: pointer; }
.nav-link span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.favorite-button { width: 32px; border: 0; background: transparent; color: var(--zw-text-quaternary); cursor: pointer; }
.nav-link-row:hover { background: var(--zw-bg-hover); }
.nav-link-row.active { background: var(--zw-bg-active); }
.nav-link-row.active .nav-link { color: var(--zw-brand); font-weight: var(--zw-font-weight-semibold); }
.nav-link:focus-visible, .favorite-button:focus-visible { outline: 2px solid var(--zw-brand); outline-offset: -2px; }
</style>
