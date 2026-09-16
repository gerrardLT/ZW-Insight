<template>
  <div class="layout-container">
    <!-- 侧边栏 -->
    <aside 
      class="layout-aside" 
      :class="{ collapsed: isCollapse, expanded: !isCollapse }"
      ref="asideRef"
      @transitionend="handleTransitionEnd"
    >
      <div class="logo">
        <div class="logo-icon">ZW</div>
        <transition name="fade">
          <div v-if="!isCollapse" class="logo-text-group">
            <span class="logo-text">中维智营</span>
            <span class="logo-sub">INSIGHT OS</span>
          </div>
        </transition>
      </div>
      <el-scrollbar class="menu-scrollbar">
        <el-menu
          :default-active="$route.path"
          :collapse="isCollapse"
          class="side-menu"
        >
          <template v-for="route in menuRoutes" :key="route.path">
            <!-- 单层菜单 -->
            <el-menu-item
              v-if="route.singleChild"
              :index="route.singleChild.fullPath"
              @click="handleMenuClick(route.singleChild.fullPath)"
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
                @click="handleMenuClick(child.fullPath)"
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
          <!-- 命令面板触发（⌘K / Ctrl+K） -->
          <el-tooltip content="命令面板 (Ctrl+K)" placement="bottom">
            <div ref="paletteBtnRef" class="header-action" role="button" aria-label="打开命令面板" @click="togglePalette">
              <el-icon><Search /></el-icon>
            </div>
          </el-tooltip>
          <!-- 帮助中心（Phase 1.3） -->
          <el-tooltip content="帮助中心" placement="bottom">
            <div ref="helpBtnRef" class="header-action" role="button" aria-label="打开帮助中心" @click="goHelp">
              <el-icon><IconHelp /></el-icon>
            </div>
          </el-tooltip>
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
              <div class="user-avatar">
                <img :src="userAvatarImg" alt="" class="user-avatar-img" />
                <span class="user-avatar-fallback">{{ avatarText }}</span>
              </div>
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

    <!-- 全局命令面板（Phase 1.1，模块级单例控制开合） -->
    <CommandPalette :commands="paletteCommands" />

    <!-- 首登三步引导（Phase 1.3：localStorage zw-tour-done 标记，仅首次进入展示；
         中途关闭或完成均写标记，下次不再打扰）；
         2026-09-16 修复：target 必须返回真实 DOM（.value）——原传 Ref 对象导致 EP 定位
         失败，气泡不渲染只剩全屏遮罩拦截点击，且遮住「跳过/完成」→ 标记永写不上 → 每次登录都弹 -->
    <el-tour v-model="tourVisible" data-testid="first-login-tour" @finish="markTourDone" @close="markTourDone">
      <el-tour-step
        :target="() => asideRef.value ?? undefined"
        title="模块导航"
        description="左侧菜单按你的授权展示全部业务模块，顶部面包屑显示当前位置。菜单可折叠，窄屏下自动收起。"
      />
      <el-tour-step
        :target="() => paletteBtnRef.value ?? undefined"
        title="命令面板"
        description="按 Ctrl+K（或非输入态按 /）随时唤起命令面板，输入名称即可快速跳转任意页面、执行常用操作。"
      />
      <el-tour-step
        :target="() => helpBtnRef.value ?? undefined"
        title="帮助中心"
        description="业务术语、键盘快捷键与错误码速查都在这里。点击此按钮，或按 Ctrl+K 搜索「帮助」。"
      />
    </el-tour>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { getUserMenus } from '@/api/system'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import TagsView from '@/components/TagsView.vue'
import CommandPalette from '@/components/CommandPalette.vue'
import { useShortcuts, ZW_SHORTCUT_EVENTS } from '@/composables/useShortcuts'
import { useCommandPalette, type PaletteCommand } from '@/composables/useCommandPalette'
import { Expand, Fold, Moon, Sunny, ArrowDown, Bell, Search, SwitchButton } from '@/components/icons/registry'
import { resolveMenuIcon } from '@/components/icons/registry'
import { IconHelp } from '@tabler/icons-vue'

const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

// 全局键盘快捷键（⌘K / `/` / Ctrl+S / Ctrl+Enter / Esc），注册在布局根组件，全局生效
useShortcuts()

/** 侧边栏 DOM 引用，用于监听动画结束 */
const asideRef = ref<HTMLElement | null>(null)

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
  // 使用类切换而非样式变化，配合 CSS transform 优化性能
  nextTick(() => {
    if (asideRef.value) {
      // 移除旧的 transition-end 标记，准备新一轮动画
      asideRef.value.classList.remove('transition-end')
    }
  })
}

// 监听过渡结束，清理 will-change 避免持续占用 GPU
function handleTransitionEnd() {
  if (asideRef.value) {
    asideRef.value.classList.add('transition-end')
  }
}

const userName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.name || '管理员'
)

const avatarText = computed(() => userName.value.charAt(0).toUpperCase())

// 头像：用户设置的真实头像 → 默认品牌插画（2026-09-16 安全帽工人剪影，AI 生成）
import defaultAvatar from '@/assets/default-avatar.png'
const userAvatarImg = computed(() => userStore.userInfo?.avatar || defaultAvatar)

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

function goHelp() {
  router.push('/help')
}

/* ================= 首登引导（Phase 1.3） ================= */

/** 引导完成标记的存储键；写入后不再自动弹出 */
const TOUR_STORAGE_KEY = 'zw-tour-done'

const tourVisible = ref(false)
const paletteBtnRef = ref<HTMLElement | null>(null)
const helpBtnRef = ref<HTMLElement | null>(null)

function markTourDone() {
  localStorage.setItem(TOUR_STORAGE_KEY, '1')
}

onMounted(() => {
  // 首次进入（无完成标记）延迟一帧开启三步引导，避开首屏渲染与菜单加载争抢
  if (!localStorage.getItem(TOUR_STORAGE_KEY)) {
    nextTick(() => {
      tourVisible.value = true
    })
  }
})

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

/**
 * 手动菜单点击路由跳转（移除 el-menu 的 router 属性后必须显式调用）
 * 修复首次点击菜单无法跳转的问题：通过 @click 显式触发路由切换
 */
function handleMenuClick(path: string) {
  router.push(path)
}

/* ================= 命令面板（Phase 1.1） ================= */
const { toggle: togglePalette, close: closePalette } = useCommandPalette()

/**
 * 命令表：导航项直接复用 menuRoutes（已按 getUserMenus 真实授权过滤）
 * → 天然权限安全；操作项（主题/消息/退出）注册为命令。
 */
const paletteCommands = computed<PaletteCommand[]>(() => {
  const cmds: PaletteCommand[] = []
  for (const group of menuRoutes.value) {
    if (group.singleChild) {
      const c = group.singleChild
      cmds.push({
        id: 'nav-' + c.fullPath,
        title: c.title,
        group: '导航',
        keywords: c.fullPath,
        hint: c.fullPath,
        icon: c.icon ? resolveMenuIcon(c.icon) : undefined,
        run: () => router.push(c.fullPath),
      })
    } else {
      for (const child of group.children || []) {
        cmds.push({
          id: 'nav-' + child.fullPath,
          title: child.title,
          group: '导航',
          keywords: `${group.title} ${child.title} ${child.fullPath}`,
          hint: child.fullPath,
          icon: child.icon ? resolveMenuIcon(child.icon) : (group.icon ? resolveMenuIcon(group.icon) : undefined),
          run: () => router.push(child.fullPath),
        })
      }
    }
  }
  cmds.push({
    id: 'act-theme',
    title: appStore.isDark ? '切换到浅色主题' : '切换到深色主题',
    group: '操作',
    keywords: 'theme dark light 主题 深色 浅色 切换',
    icon: appStore.isDark ? Sunny : Moon,
    run: () => appStore.toggleTheme(),
  })
  cmds.push({
    id: 'act-message',
    title: '前往消息中心',
    group: '操作',
    keywords: 'message 消息 通知 催办',
    icon: Bell,
    run: () => goMessage(),
  })
  cmds.push({
    id: 'act-help',
    title: '打开帮助中心',
    group: '操作',
    keywords: 'help 帮助 术语 快捷键 错误码 指引',
    icon: IconHelp,
    run: () => goHelp(),
  })
  cmds.push({
    id: 'act-logout',
    title: '退出登录',
    group: '操作',
    keywords: 'logout 退出 登出',
    icon: SwitchButton,
    run: () => handleLogout(),
  })
  return cmds
})

// 命令面板开合接入快捷键事件总线（与 useShortcuts 共用 `/` 与 ⌘K）
function onPaletteToggleEvent() { togglePalette() }
function onPaletteEscapeEvent() { closePalette() }
onMounted(() => {
  window.addEventListener(ZW_SHORTCUT_EVENTS.togglePalette, onPaletteToggleEvent)
  window.addEventListener(ZW_SHORTCUT_EVENTS.escape, onPaletteEscapeEvent)
})
onBeforeUnmount(() => {
  window.removeEventListener(ZW_SHORTCUT_EVENTS.togglePalette, onPaletteToggleEvent)
  window.removeEventListener(ZW_SHORTCUT_EVENTS.escape, onPaletteEscapeEvent)
})
</script>

<style scoped>
.layout-container {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ===== 侧边栏 ===== */
.layout-aside {
  /* 使用 transform 代替 width 过渡避免重排（reflow）导致 jank */
  flex-shrink: 0;
  background-color: var(--zw-bg-sidebar);
  contain: layout paint;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  /* 初始宽度通过 class 切换 */
}

/* 展开状态：translateX(0)，保持 GPU 合成 */
.layout-aside.expanded {
  width: var(--zw-sidebar-width);
  transform: translateX(0);
}

/* 折叠状态：translateX(-n px)，直接移动图层避免重排 */
.layout-aside.collapsed {
  width: var(--zw-sidebar-collapsed-width);
  transform: translateX(calc(var(--zw-sidebar-collapsed-width) - var(--zw-sidebar-width)));
}

/* 平滑动画：transform + opacity，完全 GPU 加速 */
.layout-aside {
  transition: 
    transform var(--zw-transition-slow) cubic-bezier(0.4, 0, 0.2, 1),
    width var(--zw-transition-slow) ease-out;
  will-change: transform, width;
}

/* 动画结束时移除 will-change，避免持续占用 GPU 资源 */
.layout-aside.transition-end {
  will-change: auto;
}

.logo {
  height: var(--zw-header-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  flex-shrink: 0;
  border-bottom: 1px solid var(--zw-steel-line);
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

.logo-text-group {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.logo-text {
  color: var(--zw-steel-text);
  font-size: var(--zw-font-size-md);
  font-weight: var(--zw-font-weight-semibold);
  white-space: nowrap;
  line-height: 1.2;
}

.logo-sub {
  color: var(--zw-steel-text-faint);
  font-family: var(--zw-font-display);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 1px;
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
  color: var(--zw-steel-text);
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

/* 头像：默认品牌插画（安全帽工人剪影），备用首字叠加在图片下方防加载失败 */
.user-avatar {
  position: relative;
  width: 30px;
  height: 30px;
  border-radius: var(--zw-radius-xs);
  overflow: hidden;
  background: var(--zw-bg-surface-3);
}
.user-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.user-avatar-fallback {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--zw-text-primary);
  font-size: var(--zw-font-size-sm);
  font-weight: var(--zw-font-weight-semibold);
}/* 图片加载成功后隐藏备用首字 */
.user-avatar img[src]:not([src='']) ~ .user-avatar-fallback { display: none; }

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
