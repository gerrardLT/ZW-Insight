<template>
  <div class="layout-container">
    <div v-if="isNarrow && mobileNavOpen" class="nav-backdrop" @click="mobileNavOpen = false" />
    <aside
      ref="asideRef"
      class="layout-aside"
      :class="{ collapsed: isCollapse, expanded: !isCollapse, 'mobile-open': mobileNavOpen }"
      aria-label="主导航"
      @transitionend="handleTransitionEnd"
    >
      <nav class="domain-rail" aria-label="业务域">
        <div class="rail-brand">
          <!-- logo 加载失败回落内置默认图（裂图纪律）：配置 URL 404 时不露破图 -->
          <img
            class="logo-icon"
            :src="logoFailed ? builtinLogo : brandStore.resolveLogo(appStore.isDark)"
            :alt="brandStore.systemName"
            @error="logoFailed = true"
          />
        </div>
        <button
          v-for="domain in navigation.domains"
          :key="domain.key"
          class="domain-button"
          :class="{ active: activeDomainKey === domain.key }"
          type="button"
          :aria-current="activeDomainKey === domain.key ? 'page' : undefined"
          @click="selectDomain(domain.key)"
        >
          <el-icon><component :is="resolveMenuIcon(domainIcon(domain.key))" /></el-icon>
          <span>{{ domain.title }}</span>
        </button>
      </nav>

      <section v-if="!isCollapse || isNarrow" class="nav-panel" aria-label="功能导航">
        <header class="nav-panel-header">
          <div>
            <strong>{{ activeDomain?.title || '功能导航' }}</strong>
            <span>{{ activeDomainItemCount }} 项已授权功能</span>
          </div>
          <button v-if="isNarrow" class="nav-close" type="button" aria-label="关闭导航" @click="mobileNavOpen = false">×</button>
        </header>

        <div v-if="menuStatus === 'loading'" class="nav-state">正在加载授权导航…</div>
        <div v-else-if="menuStatus === 'error'" class="nav-state nav-error">
          <span>导航加载失败</span>
          <button type="button" @click="loadUserMenus">重试</button>
        </div>
        <el-scrollbar v-else class="menu-scrollbar">
          <section v-if="favoriteItems.length" class="nav-section">
            <h2>常用</h2>
            <NavLink v-for="item in favoriteItems" :key="item.path" :item="item" :active="$route.path === item.path" :favorite="true" @navigate="handleMenuClick" @favorite="toggleFavorite" />
          </section>
          <!-- 最近访问已移至顶栏「历史」下拉（方案A）：跨域时间线与域结构目录是两种心智模型，
               全局最近入口混在域面板顶部会把本域功能组挤出首屏、且与首组重复；
               面板回归纯结构目录，常用（用户主动收藏）保留在顶部 -->
          <section v-for="section in activeDomain?.sections || []" :key="section.title" class="nav-section">
            <h2>{{ section.title }}</h2>
            <NavLink v-for="item in section.items" :key="item.path" :item="item" :active="$route.path === item.path" :favorite="favoritePaths.includes(item.path)" @navigate="handleMenuClick" @favorite="toggleFavorite" />
          </section>
          <div v-if="menuStatus === 'ready' && !navigation.items.length" class="nav-state">当前账号暂无可用菜单</div>
        </el-scrollbar>
      </section>
    </aside>

    <div class="layout-body">
      <!-- 顶部导航 -->
      <header class="layout-header">
        <div class="header-left">
          <button class="collapse-btn" type="button" :aria-label="isNarrow ? '打开导航' : (isCollapse ? '展开导航' : '收起导航')" @click="toggleCollapse">
            <el-icon><Expand v-if="isCollapse || isNarrow" /><Fold v-else /></el-icon>
          </button>
          <AppBreadcrumb />
        </div>
        <div class="header-right">
          <!-- 最近访问（方案A）：全局任务恢复入口，与域无关，收进顶栏 -->
          <el-dropdown
            trigger="click"
            popper-class="nav-history-popper"
            :teleported="true"
            @command="goRecent"
          >
            <el-tooltip content="最近访问" placement="bottom">
              <div class="header-action" role="button" aria-label="最近访问" data-testid="nav-history-btn">
                <el-icon><IconClock /></el-icon>
              </div>
            </el-tooltip>
            <template #dropdown>
              <el-dropdown-menu data-testid="nav-history-menu">
                <div v-if="!historyItems.length" class="nav-history-empty">暂无访问记录，去任意页面看看吧</div>
                <el-dropdown-item
                  v-for="item in historyItems"
                  :key="item.path"
                  :command="item.path"
                  data-testid="nav-history-item"
                >
                  <span class="nav-history-title">{{ item.title }}</span>
                  <span class="nav-history-domain">{{ domainTitle(item.domain) }}</span>
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="historyItems.length"
                  divided
                  command="__clear__"
                  class="nav-history-clear"
                  data-testid="nav-history-clear"
                >
                  清空访问记录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
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
              <ZwAvatar
                :name="userName"
                :src="userStore.userInfo?.avatar || ''"
                :alt="`${userName} 的头像`"
                data-testid="layout-avatar"
              />
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

    <!-- 核心链路速览抽屉：顶栏「?」与首页引导条经 guide store 唤起，全局单例 -->
    <GuideQuickDrawer />

    <!-- 首登三步引导（Phase 1.3：localStorage zw-tour-done 标记，仅首次进入展示；
         中途关闭或完成均写标记，下次不再打扰）；
         2026-09-16 修复：target 必须返回真实 DOM（.value）——原传 Ref 对象导致 EP 定位
         失败，气泡不渲染只剩全屏遮罩拦截点击，且遮住「跳过/完成」→ 标记永写不上 → 每次登录都弹 -->
    <el-tour v-model="tourVisible" :mask="false" data-testid="first-login-tour" @finish="markTourDone" @close="markTourDone">
      <el-tour-step
        :target="() => asideRef ?? undefined"
        title="模块导航"
        description="左侧菜单按你的授权展示全部业务模块，顶部面包屑显示当前位置。菜单可折叠，窄屏下自动收起。"
      />
      <el-tour-step
        :target="() => paletteBtnRef ?? undefined"
        title="命令面板"
        description="按 Ctrl+K（或非输入态按 /）随时唤起命令面板，输入名称即可快速跳转任意页面、执行常用操作。"
      />
      <el-tour-step
        :target="() => helpBtnRef ?? undefined"
        title="帮助中心"
        description="点这里随时打开「核心链路一册通」速览（项目全景图/资金双线/红线速查）；速览里可再进完整帮助文档。"
      />
    </el-tour>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { getUserMenus, type AuthorizedMenuDto } from '@/api/system'
import { buildNavigation, NAV_DOMAINS, type NavItem, type NavDomain } from '@/utils/navigation'
import AppBreadcrumb from '@/components/AppBreadcrumb.vue'
import TagsView from '@/components/TagsView.vue'
import CommandPalette from '@/components/CommandPalette.vue'
import GuideQuickDrawer from '@/components/GuideQuickDrawer.vue'
import ZwAvatar from '@/components/ZwAvatar.vue'
import NavLink from '@/components/NavLink.vue'
import { useGuideStore } from '@/stores/guide'
import { useShortcuts, ZW_SHORTCUT_EVENTS } from '@/composables/useShortcuts'
import { useCommandPalette, type PaletteCommand } from '@/composables/useCommandPalette'
import { Expand, Fold, Moon, Sunny, ArrowDown, Bell, Search, SwitchButton } from '@/components/icons/registry'
import { resolveMenuIcon } from '@/components/icons/registry'
import { IconHelp, IconClock } from '@tabler/icons-vue'
import defaultLogoLight from '@/assets/logo-light.png'
// 使用文档元数据：仅类型导入（编译擦除）；正文 chunk 由命令面板首次挂载时动态 import，
// 避免 19 章 Markdown 正文进入主包（CodeReview Major-2）
import type { GuideChapterMeta } from '@/docs/help/registry'
import { useBrandStore } from '@/stores/brand'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()
const guideStore = useGuideStore()
// 品牌 Store：Logo 双态（亮底黑字/暗底反白）、系统名称与副标题按部署环境动态取值，
// 详见 stores/brand.ts；App.vue 已在应用启动时拉取配置
const brandStore = useBrandStore()

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

const mobileNavOpen = ref(false)
const isCollapse = computed(() => !isNarrow.value && appStore.sidebarCollapsed)

function toggleCollapse() {
  if (isNarrow.value) {
    mobileNavOpen.value = !mobileNavOpen.value
    return
  }
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

// 头像：ZwAvatar 按姓名派生（首字+稳定配色），有自定义头像 URL 时展示图片（@error 自动回落）
const logoFailed = ref(false)
const builtinLogo = defaultLogoLight

type MenuStatus = 'loading' | 'ready' | 'error'
const menuStatus = ref<MenuStatus>('loading')
const authorizedMenus = ref<AuthorizedMenuDto[]>([])
const navigation = computed(() => buildNavigation(router.options.routes, authorizedMenus.value))
const activeDomainKey = ref('')
const activeDomain = computed(() => navigation.value.domains.find((domain) => domain.key === activeDomainKey.value) || navigation.value.domains[0])
const activeDomainItemCount = computed(() => activeDomain.value?.sections.reduce((sum, section) => sum + section.items.length, 0) || 0)

const storageUserKey = computed(() => String(userStore.userInfo?.id || userStore.userInfo?.username || userName.value))
const favoritePaths = ref<string[]>([])
const recentPaths = ref<string[]>([])
const favoriteStorageKey = computed(() => `zw-nav-favorites:${storageUserKey.value}`)
const recentStorageKey = computed(() => `zw-nav-recent:${storageUserKey.value}`)
const itemByPath = computed(() => new Map(navigation.value.items.map((item) => [item.path, item])))
const favoriteItems = computed(() => favoritePaths.value.map((path) => itemByPath.value.get(path)).filter((item): item is NavItem => Boolean(item)))
/* ================= 最近访问（顶栏历史下拉，方案A） ================= */
/** 首页属常驻入口（品牌位/面包屑均可达），进最近列表只有噪音 */
const TRIVIAL_RECENT_PATHS = new Set(['/dashboard'])

/** 顶栏历史下拉数据：全局最近（跨域），排除常驻入口，最多 10 条 */
const historyItems = computed(() =>
  recentPaths.value
    .map((path) => itemByPath.value.get(path))
    .filter((item): item is NavItem => Boolean(item) && !TRIVIAL_RECENT_PATHS.has(item.path))
    .slice(0, 10)
)

const DOMAIN_TITLE_BY_KEY = new Map<string, string>(NAV_DOMAINS.map((d: NavDomain) => [d.key, d.title]))
function domainTitle(key: string): string {
  return DOMAIN_TITLE_BY_KEY.get(key) || ''
}

function goRecent(path: string) {
  if (path === '__clear__') {
    // 清空访问记录（保留常用收藏）：本地隐私数据，用户应可主动清除
    recentPaths.value = []
    localStorage.setItem(recentStorageKey.value, '[]')
    return
  }
  router.push(path)
}

function loadPersonalNavigation() {
  try {
    favoritePaths.value = JSON.parse(localStorage.getItem(favoriteStorageKey.value) || '[]').slice(0, 8)
    recentPaths.value = JSON.parse(localStorage.getItem(recentStorageKey.value) || '[]').slice(0, 10)
  } catch {
    favoritePaths.value = []
    recentPaths.value = []
  }
}

async function loadUserMenus() {
  menuStatus.value = 'loading'
  try {
    const res = await getUserMenus()
    authorizedMenus.value = Array.isArray(res?.data) ? res.data : []
    menuStatus.value = 'ready'
  } catch {
    authorizedMenus.value = []
    menuStatus.value = 'error'
  }
}

function selectDomain(key: string) {
  activeDomainKey.value = key
}

function domainIcon(key: string) {
  return ({ overview: 'Odometer', business: 'Briefcase', cost: 'Coin', site: 'Place', finance: 'Money', resources: 'FolderOpened', collaboration: 'Bell', administration: 'Setting' } as Record<string, string>)[key] || 'Grid'
}

function toggleFavorite(path: string) {
  favoritePaths.value = favoritePaths.value.includes(path)
    ? favoritePaths.value.filter((item) => item !== path)
    : [path, ...favoritePaths.value].slice(0, 8)
  localStorage.setItem(favoriteStorageKey.value, JSON.stringify(favoritePaths.value))
}

function recordRecent(path: string) {
  if (!itemByPath.value.has(path) || TRIVIAL_RECENT_PATHS.has(path)) return
  recentPaths.value = [path, ...recentPaths.value.filter((item) => item !== path)].slice(0, 10)
  localStorage.setItem(recentStorageKey.value, JSON.stringify(recentPaths.value))
}

onMounted(() => {
  loadPersonalNavigation()
  loadUserMenus()
})

watch(() => route.path, (path) => {
  const item = itemByPath.value.get(path)
  if (item) activeDomainKey.value = item.domain
  recordRecent(path)
  mobileNavOpen.value = false
}, { immediate: true })

watch(navigation, (model) => {
  const item = itemByPath.value.get(route.path)
  activeDomainKey.value = item?.domain || model.domains[0]?.key || ''
  recordRecent(route.path)
})

function goMessage() {
  router.push('/message/center')
}

function goDevices() {
  router.push('/user/devices')
}

function goHelp() {
  // 顶栏「?」直达核心链路速览抽屉（原为跳 /help 落地页；抽屉内可再进完整文档）
  guideStore.openQuick()
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
  mobileNavOpen.value = false
}

/* ================= 命令面板（Phase 1.1） ================= */
const { toggle: togglePalette, close: closePalette } = useCommandPalette()

/**
 * 命令表：导航项直接复用 menuRoutes（已按 getUserMenus 真实授权过滤）
 * → 天然权限安全；操作项（主题/消息/退出）注册为命令。
 */
const paletteCommands = computed<PaletteCommand[]>(() => {
  const cmds: PaletteCommand[] = []
  for (const item of navigation.value.items) {
    const domain = navigation.value.domains.find((entry) => entry.key === item.domain)
    cmds.push({
      id: 'nav-' + item.path,
      title: item.title,
      group: domain?.title || '导航',
      keywords: `${domain?.title || ''} ${item.section} ${item.title} ${item.path}`,
      hint: `${domain?.title || '导航'} / ${item.section}`,
      icon: item.icon ? resolveMenuIcon(item.icon) : undefined,
      run: () => router.push(item.path),
    })
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
  // 使用文档：每章一条命令，keywords 供命令面板检索，run 跳转锚点章节
  for (const ch of guideChapters.value) {
    cmds.push({
      id: 'doc-' + ch.id,
      title: ch.title,
      group: '使用文档',
      keywords: ch.keywords,
      hint: '/help/guide#' + ch.id,
      icon: IconHelp,
      run: () => router.push('/help/guide#' + ch.id),
    })
  }
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

/** 使用文档章节元数据：挂载后异步加载 registry chunk（不进主包）；加载完成前命令面板暂无文档命令 */
const guideChapters = ref<GuideChapterMeta[]>([])

// 命令面板开合接入快捷键事件总线（与 useShortcuts 共用 `/` 与 ⌘K）
function onPaletteToggleEvent() { togglePalette() }
function onPaletteEscapeEvent() {
  closePalette()
  mobileNavOpen.value = false
}
onMounted(() => {
  window.addEventListener(ZW_SHORTCUT_EVENTS.togglePalette, onPaletteToggleEvent)
  window.addEventListener(ZW_SHORTCUT_EVENTS.escape, onPaletteEscapeEvent)
  import('@/docs/help/registry')
    .then((m) => { guideChapters.value = m.USER_GUIDE })
    .catch(() => { /* 文档 chunk 加载失败：仅影响命令面板「使用文档」组，不阻断布局 */ })
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

/* ===== 业务域导航 ===== */
.layout-aside { flex-shrink: 0; display: flex; overflow: hidden; background: var(--zw-bg-sidebar); transition: transform var(--zw-transition-slow); z-index: var(--zw-z-drawer); }
.layout-aside.expanded { width: 320px; }
.layout-aside.collapsed { width: var(--zw-sidebar-collapsed-width); }
.layout-aside.transition-end { will-change: auto; }
.domain-rail { width: var(--zw-sidebar-collapsed-width); flex-shrink: 0; display: flex; flex-direction: column; border-right: 1px solid var(--zw-steel-line); }
.rail-brand { height: var(--zw-header-height); display: grid; place-items: center; border-bottom: 1px solid var(--zw-steel-line); }
.domain-button { min-height: 58px; padding: 7px 3px; border: 0; border-bottom: 1px solid transparent; background: transparent; color: var(--zw-steel-text-muted); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; font: inherit; font-size: 11px; cursor: pointer; }
.domain-button :deep(.el-icon) { font-size: 18px; }
.domain-button:hover, .domain-button.active { color: var(--zw-brand); background: var(--zw-bg-sidebar-hover); }
.domain-button:focus-visible, .nav-close:focus-visible, .collapse-btn:focus-visible { outline: 2px solid var(--zw-brand); outline-offset: -2px; }
.nav-panel { width: 256px; min-width: 0; display: flex; flex-direction: column; background: var(--zw-bg-card); color: var(--zw-text-primary); }
.nav-panel-header { min-height: var(--zw-header-height); padding: 9px 14px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--zw-border); }
.nav-panel-header div { display: flex; flex-direction: column; gap: 2px; }
.nav-panel-header strong { font-size: var(--zw-font-size-md); }
.nav-panel-header span { color: var(--zw-text-tertiary); font-size: var(--zw-font-size-xs); }
.nav-close { border: 0; background: transparent; color: var(--zw-text-secondary); font-size: 24px; cursor: pointer; }
.nav-section { padding: 10px 8px; border-bottom: 1px solid var(--zw-border-light); }
.nav-section h2 { margin: 0 10px 6px; color: var(--zw-text-tertiary); font-size: var(--zw-font-size-xs); font-weight: var(--zw-font-weight-semibold); }
.nav-state { padding: var(--zw-space-lg); color: var(--zw-text-tertiary); font-size: var(--zw-font-size-sm); }
.nav-error { display: flex; align-items: center; justify-content: space-between; color: var(--zw-danger); }
.nav-error button { border: 1px solid var(--zw-border); background: transparent; color: inherit; padding: 5px 9px; cursor: pointer; }
.nav-backdrop { display: none; }

.logo { height: var(--zw-header-height); display: flex; align-items: center; gap: 10px; padding: 0 18px; flex-shrink: 0; border-bottom: 1px solid var(--zw-steel-line); }

.logo-icon {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  object-fit: contain;
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
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 0;
  background: transparent;
  font-size: 18px;
  color: var(--zw-text-secondary);
  cursor: pointer;
  transition: color var(--zw-transition-fast), background var(--zw-transition-fast);
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

/* ===== 顶栏最近访问下拉（popper 为 teleport 元素，样式走全局块） ===== */

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

/* 头像由 ZwAvatar 组件自绘（首字+稳定配色），此处仅微调与用户名的间距 */
.user-info .zw-avatar {
  width: 30px;
  height: 30px;
  font-size: var(--zw-font-size-sm);
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

@media (max-width: 992px) {
  .layout-aside,
  .layout-aside.expanded,
  .layout-aside.collapsed {
    position: fixed;
    inset: 0 auto 0 0;
    width: min(340px, 92vw);
    transform: translateX(-100%);
    box-shadow: var(--zw-shadow-overlay);
  }

  .layout-aside.mobile-open { transform: translateX(0); }
  .nav-panel { flex: 1; width: auto; }
  .nav-backdrop { display: block; position: fixed; inset: 0; z-index: calc(var(--zw-z-drawer) - 1); background: var(--zw-bg-mask); }
  .layout-header { padding: 0 var(--zw-space-sm); }
  .header-left { min-width: 0; gap: var(--zw-space-xs); }
  .user-name, .user-arrow { display: none; }
}

@media (max-width: 640px) {
  /* 窄屏只保留高频动作（最近访问/命令面板/帮助），主题与消息收纳进用户菜单场景 */
  .layout-header .header-action[aria-label="切换主题"],
  .layout-header .header-action[aria-label="消息通知"] { display: none; }
}
</style>

<style>
/* 顶栏最近访问下拉：popper 挂 body（teleported），须走全局样式 */
.nav-history-popper .nav-history-title {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-history-popper .nav-history-domain {
  flex-shrink: 0;
  margin-left: auto;
  padding-left: 16px;
  font-size: var(--zw-font-size-xs);
  color: var(--zw-text-quaternary);
}

.nav-history-popper .nav-history-domain::before {
  content: '';
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  margin-right: 5px;
  vertical-align: middle;
  background: var(--zw-brand);
  opacity: 0.55;
}

.nav-history-popper .nav-history-empty {
  padding: var(--zw-space-md) var(--zw-space-lg);
  color: var(--zw-text-quaternary);
  font-size: var(--zw-font-size-sm);
  text-align: center;
}

.nav-history-popper .el-dropdown-menu__item {
  display: flex;
  align-items: center;
  min-width: 260px;
}
</style>
