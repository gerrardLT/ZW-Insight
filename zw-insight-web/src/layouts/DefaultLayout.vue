<template>
  <div class="layout-container">
    <!-- 侧边栏 -->
    <aside class="layout-aside" :class="{ collapsed: isCollapse }">
      <div class="logo">
        <div class="logo-icon">ZW</div>
        <transition name="fade">
          <span v-if="!isCollapse" class="logo-text">中维智营</span>
        </transition>
      </div>
      <el-scrollbar class="menu-scrollbar">
        <el-menu
          :default-active="$route.path"
          :collapse="isCollapse"
          router
          class="side-menu"
        >
          <template v-for="route in menuRoutes" :key="route.path">
            <!-- 单层菜单 -->
            <el-menu-item
              v-if="route.singleChild"
              :index="route.singleChild.fullPath"
            >
              <el-icon v-if="route.singleChild.icon"><component :is="route.singleChild.icon" /></el-icon>
              <template #title>{{ route.singleChild.title }}</template>
            </el-menu-item>

            <!-- 多层目录 -->
            <el-sub-menu v-else :index="route.path">
              <template #title>
                <el-icon v-if="route.icon"><component :is="route.icon" /></el-icon>
                <span>{{ route.title }}</span>
              </template>
              <el-menu-item
                v-for="child in route.children"
                :key="child.fullPath"
                :index="child.fullPath"
              >
                <el-icon v-if="child.icon"><component :is="child.icon" /></el-icon>
                <template #title>{{ child.title }}</template>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </el-scrollbar>
    </aside>

    <div class="layout-body">
      <!-- 顶部导航 -->
      <header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="toggleCollapse">
            <Expand v-if="isCollapse" />
            <Fold v-else />
          </el-icon>
          <AppBreadcrumb />
        </div>
        <div class="header-right">
          <!-- 主题切换 -->
          <el-tooltip :content="appStore.isDark ? '切换到浅色' : '切换到深色'" placement="bottom">
            <div class="header-action" role="button" aria-label="切换主题" @click="appStore.toggleTheme()">
              <el-icon><Moon v-if="!appStore.isDark" /><Sunny v-else /></el-icon>
            </div>
          </el-tooltip>
          <!-- 消息 -->
          <el-tooltip content="消息通知" placement="bottom">
            <div class="header-action" role="button" aria-label="消息通知" @click="goMessage">
              <el-icon><Bell /></el-icon>
            </div>
          </el-tooltip>
          <!-- 用户 -->
          <el-dropdown>
            <div class="user-info" role="button" aria-label="用户菜单">
              <div class="user-avatar">{{ avatarText }}</div>
              <span class="user-name">{{ userName }}</span>
              <el-icon class="user-arrow"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="goDevices">
                  <el-icon><Monitor /></el-icon>登录设备
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <!-- 标签页 -->
      <TagsView v-if="appStore.showTagsView" />

      <!-- 主内容区 -->
      <main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import TagsView from '@/components/TagsView.vue'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

/** 窄屏响应式：≤992px 自动折叠侧栏（独立于用户持久化偏好，不污染 store） */
const narrowMql = window.matchMedia('(max-width: 992px)')
const isNarrow = ref(narrowMql.matches)
function onMediaChange(e: MediaQueryListEvent) {
  isNarrow.value = e.matches
}
onMounted(() => narrowMql.addEventListener('change', onMediaChange))
onBeforeUnmount(() => narrowMql.removeEventListener('change', onMediaChange))

const isCollapse = computed(() => appStore.sidebarCollapsed || isNarrow.value)

function toggleCollapse() {
  appStore.toggleSidebar()
}

const userName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.name || '管理员'
)

const avatarText = computed(() => userName.value.charAt(0).toUpperCase())

/** 规范化拼接父子路径 */
function joinPath(parent: string, child: string): string {
  if (child.startsWith('/')) return child
  return (parent.endsWith('/') ? parent.slice(0, -1) : parent) + '/' + child
}

/**
 * 侧边栏菜单：基于路由表(constantRoutes)动态生成，
 * 自动列出所有挂在布局下的模块及其可见子菜单（尊重 meta.hidden / title / icon）。
 */
const menuRoutes = computed(() => {
  const roots = router.options.routes as RouteRecordRaw[]
  const groups: any[] = []

  for (const r of roots) {
    if (!r.children || r.children.length === 0) continue
    if (r.meta?.hidden) continue

    const visibleChildren = r.children
      .filter((c) => !c.meta?.hidden && c.meta?.title)
      .map((c) => ({
        fullPath: joinPath(r.path, c.path),
        title: c.meta?.title as string,
        icon: c.meta?.icon as string | undefined
      }))

    if (visibleChildren.length === 0) continue

    const groupTitle = r.meta?.title as string | undefined
    if (!groupTitle) {
      for (const c of visibleChildren) {
        groups.push({ path: c.fullPath, singleChild: c })
      }
      continue
    }

    if (visibleChildren.length === 1) {
      groups.push({
        path: r.path,
        singleChild: { ...visibleChildren[0], title: groupTitle, icon: r.meta?.icon }
      })
    } else {
      groups.push({
        path: r.path,
        title: groupTitle,
        icon: r.meta?.icon as string | undefined,
        children: visibleChildren
      })
    }
  }
  return groups
})

function goMessage() {
  router.push('/message/center')
}

function goDevices() {
  router.push('/user/devices')
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ===== 侧边栏 ===== */
.layout-aside {
  width: var(--zw-sidebar-width);
  flex-shrink: 0;
  background-color: var(--zw-bg-sidebar);
  transition: width var(--zw-transition-slow);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.layout-aside.collapsed {
  width: var(--zw-sidebar-collapsed-width);
}

.logo {
  height: var(--zw-header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  flex-shrink: 0;
}

.logo-icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--zw-radius-sm);
  background: var(--zw-brand-gradient);
  color: #fff;
  font-weight: var(--zw-font-weight-bold);
  font-size: 13px;
  letter-spacing: 0.5px;
}

.logo-text {
  color: #fff;
  font-size: var(--zw-font-size-md);
  font-weight: var(--zw-font-weight-semibold);
  white-space: nowrap;
}

.menu-scrollbar {
  flex: 1;
  overflow: hidden;
}

.side-menu {
  background-color: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: var(--zw-text-sidebar);
  --el-menu-active-color: var(--zw-text-sidebar-active);
  --el-menu-hover-bg-color: var(--zw-bg-sidebar-hover);
}

.side-menu :deep(.el-menu-item),
.side-menu :deep(.el-sub-menu__title) {
  color: var(--zw-text-sidebar);
}

.side-menu :deep(.el-menu-item:hover),
.side-menu :deep(.el-sub-menu__title:hover) {
  color: #fff;
  background-color: var(--zw-bg-sidebar-hover);
}

.side-menu :deep(.el-menu-item.is-active) {
  color: #fff;
  background: var(--zw-brand-gradient);
}

.side-menu :deep(.el-sub-menu .el-menu-item) {
  background-color: transparent;
}

/* ===== 主体 ===== */
.layout-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 顶栏 ===== */
.layout-header {
  height: var(--zw-header-height);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--zw-space-lg);
  background-color: var(--zw-bg-card);
  box-shadow: var(--zw-shadow-header);
  z-index: var(--zw-z-sticky);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--zw-space-md);
}

.collapse-btn {
  font-size: 18px;
  color: var(--zw-text-secondary);
  cursor: pointer;
  transition: color var(--zw-transition-fast);
}

.collapse-btn:hover {
  color: var(--zw-brand);
}

.header-right {
  display: flex;
  align-items: center;
  gap: var(--zw-space-xs);
}

.header-action {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--zw-radius-sm);
  color: var(--zw-text-secondary);
  cursor: pointer;
  font-size: 18px;
  transition: all var(--zw-transition-fast);
}

.header-action:hover {
  background-color: var(--zw-bg-hover);
  color: var(--zw-brand);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border-radius: var(--zw-radius-full);
  cursor: pointer;
  transition: background-color var(--zw-transition-fast);
}

.user-info:hover {
  background-color: var(--zw-bg-hover);
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--zw-brand-gradient);
  color: #fff;
  font-size: var(--zw-font-size-sm);
  font-weight: var(--zw-font-weight-semibold);
}

.user-name {
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-primary);
  font-weight: var(--zw-font-weight-medium);
}

.user-arrow {
  font-size: 12px;
  color: var(--zw-text-tertiary);
}

/* ===== 主内容区 ===== */
.layout-main {
  flex: 1;
  overflow-y: auto;
  background-color: var(--zw-bg-page);
}
</style>
