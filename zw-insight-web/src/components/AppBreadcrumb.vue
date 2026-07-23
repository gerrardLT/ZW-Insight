<template>
  <el-breadcrumb class="app-breadcrumb" separator="/">
    <transition-group name="breadcrumb">
      <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
        <span
          v-if="item.redirect === 'noRedirect' || item.isLast"
          class="breadcrumb-text"
        >{{ item.title }}</span>
        <a v-else class="breadcrumb-link" @click.prevent="handleLink(item)">{{ item.title }}</a>
      </el-breadcrumb-item>
    </transition-group>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { RouteLocationMatched } from 'vue-router'

interface BreadcrumbItem {
  path: string
  title: string
  redirect?: string
  isLast?: boolean
}

const route = useRoute()
const router = useRouter()
const breadcrumbs = ref<BreadcrumbItem[]>([])

function getBreadcrumbs() {
  const matched = route.matched.filter(
    (item) => item.meta && item.meta.title && !item.meta.hidden
  )

  const list: BreadcrumbItem[] = matched.map((item: RouteLocationMatched, index) => ({
    path: item.path,
    title: item.meta.title as string,
    redirect: item.redirect as string | undefined,
    isLast: index === matched.length - 1
  }))

  // 首页始终作为第一项
  const first = list[0]
  if (!first || first.title !== '首页') {
    list.unshift({ path: '/dashboard', title: '首页', isLast: list.length === 0 })
  }

  breadcrumbs.value = list
}

function handleLink(item: BreadcrumbItem) {
  if (item.redirect && item.redirect !== 'noRedirect') {
    router.push(item.redirect)
    return
  }
  router.push(item.path)
}

watch(() => route.path, getBreadcrumbs, { immediate: true })
</script>

<style scoped>
.app-breadcrumb {
  display: inline-flex;
  align-items: center;
  font-size: var(--zw-font-size-sm);
}

.breadcrumb-link {
  color: var(--zw-text-tertiary);
  cursor: pointer;
  transition: color var(--zw-transition-fast);
}

.breadcrumb-link:hover {
  color: var(--zw-brand);
}

.breadcrumb-text {
  color: var(--zw-text-primary);
  font-weight: var(--zw-font-weight-medium);
}

/* 面包屑切换动画 */
.breadcrumb-enter-active,
.breadcrumb-leave-active {
  transition: all 0.3s ease;
}

.breadcrumb-enter-from {
  opacity: 0;
  transform: translateX(8px);
}

.breadcrumb-leave-active {
  position: absolute;
}
</style>
