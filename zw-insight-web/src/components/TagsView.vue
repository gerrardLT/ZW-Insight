<template>
  <div class="tags-view">
    <el-scrollbar class="tags-scrollbar">
      <div class="tags-list">
        <div
          v-for="tag in tagsStore.visitedViews"
          :key="tag.path"
          class="tag-item"
          :class="{ active: isActive(tag) }"
          @click="handleClick(tag)"
          @contextmenu.prevent="openMenu(tag, $event)"
        >
          <span class="tag-dot" v-if="isActive(tag)"></span>
          <span class="tag-title">{{ tag.title }}</span>
          <el-icon
            v-if="tag.path !== '/dashboard'"
            class="tag-close"
            @click.stop="handleClose(tag)"
          >
            <Close />
          </el-icon>
        </div>
      </div>
    </el-scrollbar>

    <!-- 右键菜单 -->
    <ul
      v-show="menuVisible"
      class="context-menu"
      :style="{ left: menuLeft + 'px', top: menuTop + 'px' }"
    >
      <li @click="handleCloseOthers">关闭其他</li>
      <li @click="handleCloseAll">关闭所有</li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useTagsViewStore, type TagView } from '@/stores/tagsView'

const route = useRoute()
const router = useRouter()
const tagsStore = useTagsViewStore()

const menuVisible = ref(false)
const menuLeft = ref(0)
const menuTop = ref(0)
const selectedTag = ref<TagView | null>(null)

function isActive(tag: TagView) {
  return tag.path === route.path
}

function handleClick(tag: TagView) {
  router.push(tag.fullPath)
}

function handleClose(tag: TagView) {
  const target = tagsStore.closeView(tag)
  if (isActive(tag) && target) {
    router.push(target.fullPath)
  }
}

function openMenu(tag: TagView, e: MouseEvent) {
  menuLeft.value = e.clientX
  menuTop.value = e.clientY
  selectedTag.value = tag
  menuVisible.value = true
}

function closeMenu() {
  menuVisible.value = false
}

function handleCloseOthers() {
  if (selectedTag.value) {
    tagsStore.closeOthers(selectedTag.value)
    router.push(selectedTag.value.fullPath)
  }
  closeMenu()
}

function handleCloseAll() {
  tagsStore.closeAll()
  router.push('/dashboard')
  closeMenu()
}

// 路由变化时记录标签
watch(
  () => route.path,
  () => {
    tagsStore.addView(route)
  },
  { immediate: true }
)

onMounted(() => {
  document.addEventListener('click', closeMenu)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', closeMenu)
})
</script>

<style scoped>
.tags-view {
  height: var(--zw-tags-height);
  background-color: var(--zw-bg-card);
  border-bottom: 1px solid var(--zw-border-light);
  padding: 0 var(--zw-space-md);
  display: flex;
  align-items: center;
}

.tags-scrollbar {
  width: 100%;
}

.tags-list {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  padding: 6px 0;
  white-space: nowrap;
}

.tag-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 10px;
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-secondary);
  background-color: var(--zw-bg-page);
  border: 1px solid var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  cursor: pointer;
  transition: all var(--zw-transition-fast);
  user-select: none;
}

.tag-item:hover {
  color: var(--zw-brand);
  border-color: var(--zw-brand);
}

.tag-item.active {
  color: #fff;
  background: var(--zw-brand-gradient);
  border-color: transparent;
}

.tag-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: #fff;
}

.tag-close {
  font-size: 12px;
  border-radius: 50%;
  transition: background-color var(--zw-transition-fast);
}

.tag-close:hover {
  background-color: rgba(255, 255, 255, 0.3);
}

.tag-item:not(.active) .tag-close:hover {
  background-color: var(--zw-bg-hover);
}

.context-menu {
  position: fixed;
  z-index: var(--zw-z-dropdown);
  background-color: var(--zw-bg-card);
  border-radius: var(--zw-radius-sm);
  box-shadow: var(--zw-shadow-dropdown);
  padding: 4px;
  list-style: none;
  min-width: 120px;
}

.context-menu li {
  padding: 8px 12px;
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-secondary);
  border-radius: var(--zw-radius-xs);
  cursor: pointer;
  transition: all var(--zw-transition-fast);
}

.context-menu li:hover {
  background-color: var(--zw-bg-hover);
  color: var(--zw-brand);
}
</style>
