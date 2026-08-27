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
              <el-icon class="menu-item-icon"><component :is="resolveMenuIcon(route.singleChild.icon)" /></el-icon>
              <template #title>{{ route.singleChild.title }}</template>
            </el-menu-item>

            <!-- 多层目录 -->
            <el-sub-menu v-else :index="route.path">
              <template #title>
                <el-icon class="sub-menu-icon"><component :is="resolveMenuIcon(route.icon)" /></el-icon>
                <span>{{ route.title }}</span>
              </template>
              <el-menu-item
                v-for="child in route.children"
                :key="child.fullPath"
                :index="child.fullPath"
              >
                <el-icon class="child-menu-icon"><component :is="resolveMenuIcon(child.icon)" /></el-icon>
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
import { getUserMenus } from '@/api/system'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import TagsView from '@/components/TagsView.vue'
import { Expand, Fold, Moon, Sunny, ArrowDown, Bell } from '@/components/icons/registry'
import { resolveMenuIcon } from '@/components/icons/registry'

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
 * 菜单权限状态（2026-08-21 台账 PERM-GAP 修复）：数据源为 GET /v1/system/menu/user
 * （后端经 sys_role_menu JOIN 的真实授权）。
 * - authorizedPaths：授权菜单的完整路由路径（一级绝对路径 / 二级 parent.path 拼接）
 * - authorizedDirs：授权的一级 DIR 路径 —— DIR 已授权但其子菜单均未单独授权时整组显示
 */
const authorizedPaths = ref<Set<string>>(new Set())
const authorizedDirs = ref<Set<string>>(new Set())

interface UserMenu {
  id: number | string
  menuType?: string
  parentId?: number | string | null
  path?: string
}

async function loadUserMenus() {
  try {
    const res: any = await getUserMenus()
    const menus: UserMenu[] = Array.isArray(res?.data) ? res.data : []
    const byId = new Map<string, UserMenu>(menus.map((m) => [String(m.id), m]))
    const paths = new Set<string>()
    const dirs = new Set<string>()
    for (const m of menus) {
      if (!m.path) continue
      const parent = m.parentId != null && m.parentId !== 0 ? byId.get(String(m.parentId)) : undefined
      if (parent && parent.path) {
        // 二级菜单：用父菜单 path 拼接完整路径
        paths.add(joinPath(parent.path, m.path))
      } else if (m.path.startsWith('/')) {
        // 一级 MENU / DIR（绝对路径）
        if (m.menuType === 'DIR') dirs.add(m.path)
        paths.add(m.path)
      }
    }
    authorizedPaths.value = paths
    authorizedDirs.value = dirs
  } catch {
    // 全局响应拦截器已提示错误；失败时菜单置空（真实行为，不静默回落静态路由）
    authorizedPaths.value = new Set()
    authorizedDirs.value = new Set()
  }
}

onMounted(loadUserMenus)

/**
 * 侧边栏菜单：基于路由表(constantRoutes)动态生成，
 * 自动列出所有挂在布局下的模块及其可见子菜单（尊重 meta.hidden / title / icon），
 * 并按用户授权菜单过滤（fullPath 命中授权路径；DIR 整组授权显示全组）。
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

    // 权限过滤：子项 fullPath 授权命中；DIR 授权但子项均未单独授权 → 显示整组
    const allowedChildren = visibleChildren.filter((c) => authorizedPaths.value.has(c.fullPath))
    const dirAuthorized = authorizedDirs.value.has(r.path)

    if (!groupTitle) {
      for (const c of allowedChildren) {
        groups.push({ path: c.fullPath, singleChild: c })
      }
      continue
    }

    if (allowedChildren.length === 0 && !dirAuthorized) continue
    const shownChildren = allowedChildren.length > 0 ? allowedChildren : visibleChildren

    if (shownChildren.length === 1) {
      groups.push({
        path: r.path,
        singleChild: { ...shownChildren[0], title: groupTitle, icon: r.meta?.icon }
      })
    } else {
      groups.push({
        path: r.path,
        title: groupTitle,
        icon: r.meta?.icon as string | undefined,
        children: shownChildren
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
  border-radius: var(--zw-radius-xs);
  background: var(--zw-brand);
  color: var(--zw-on-primary);
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

/* 激活态：深一档底 + 橙字 + 左侧 2px 橙竖条（工程定位标） */
.side-menu :deep(.el-menu-item.is-active) {
  color: var(--zw-brand);
  background-color: var(--zw-bg-sidebar-active);
  border-left: 2px solid var(--zw-brand);
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
  border-bottom: 1px solid var(--zw-border);
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
  border-radius: var(--zw-radius-sm);
  cursor: pointer;
  transition: background-color var(--zw-transition-fast);
}

.user-info:hover {
  background-color: var(--zw-bg-hover);
}

/* 方形首字母头像（直角纪律，不用渐变圆） */
.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: var(--zw-radius-xs);
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--zw-bg-surface-3);
  color: var(--zw-text-primary);
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

/* ===== 菜单图标 ===== */
.menu-item-icon,
.sub-menu-icon,
.child-menu-icon {
  font-size: 18px;
  color: var(--zw-text-secondary);
}
.icon-fallback-marked {
  color: var(--zw-warning) !important;
}

/* ===== 主内容区 ===== */
.layout-main {
  flex: 1;
  overflow-y: auto;
  background-color: var(--zw-bg-page);
}
</style>
