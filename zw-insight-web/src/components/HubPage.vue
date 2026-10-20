<template>
  <div class="biz-hub">
    <div v-if="visibleTabs.length" class="hub-shell">
      <div class="hub-tabbar" role="tablist" aria-label="模块页签">
        <button
          v-for="t in primaryTabs"
          :key="t.key"
          type="button"
          role="tab"
          :aria-selected="activeTab === t.key"
          class="hub-tab"
          :class="{ 'hub-tab--active': activeTab === t.key }"
          @click="activate(t.key)"
        >
          <span class="hub-tab-label">{{ t.label }}</span>
          <span
            v-if="tabBadge(t)"
            class="hub-tab-badge"
            :class="tabBadgeClass(t)"
            :title="tabBadgeTitle(t)"
          >{{ tabBadgeText(t) }}</span>
        </button>

        <el-dropdown v-if="overflowTabs.length" trigger="click" @command="activate">
          <button type="button" class="hub-tab hub-tab--more" :class="{ 'hub-tab--active': overflowActive }">
            <span class="hub-tab-label">{{ overflowActive ? `更多 · ${activeLabel}` : '更多' }}</span>
            <el-icon class="hub-tab-caret"><ArrowDown /></el-icon>
          </button>
          <template #dropdown>
            <el-dropdown-menu>
              <template v-for="(group, gi) in overflowGroups" :key="gi">
                <el-dropdown-item v-if="group.group" disabled>{{ group.group }}</el-dropdown-item>
                <el-dropdown-item
                  v-for="t in group.items"
                  :key="t.key"
                  :command="t.key"
                  :class="{ 'hub-more-item--active': activeTab === t.key }"
                >
                  {{ t.label }}
                  <span
                    v-if="tabBadge(t)"
                    class="hub-tab-badge"
                    :class="tabBadgeClass(t)"
                    :title="tabBadgeTitle(t)"
                  >{{ tabBadgeText(t) }}</span>
                </el-dropdown-item>
              </template>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div v-if="activeComp" class="hub-panel">
        <keep-alive>
          <component :is="activeComp" :key="`${hubKey}::${activeTab}`" />
        </keep-alive>
      </div>
    </div>
    <el-empty v-else description="当前账号无该模块的功能权限，请联系管理员分配" />
  </div>
</template>

<script setup lang="ts">
/**
 * 通用聚合入口壳（V2026_120/121/122 二级菜单收敛）。
 *
 * 各域二级菜单收敛为一等入口后按 route.meta.hubTabs 渲染 Tab，默认只挂载当前 Tab
 * 的原整页组件（keep-alive 缓存）。原路由全部保留且支持 URL 直达（?projectId=
 * 等参数经 route.query 原样透传给子组件——子组件 onMounted 已按字符串承接）。
 *
 * 权限双层（V2026_122 补强，修复 Hub 化后细粒度权限失效）：
 * - 入口层：入口路由 meta.permission（域级 view 码，router 守卫任一匹配即过）；
 * - Tab 层：hubTabs[].permission 可选（原子路由原 meta.permission 原样回填），
 *   无该权限码的 Tab 不渲染——避免「只在原子路由上受守卫、在 Tab 内挂载时绕过」。
 *   超管（*:*:*）全量放行；无 permission 的 Tab 跟随入口授权。
 *
 * S2 首屏体验（本版本新增）：
 * - Tab 选择优先级：URL ?tab= > 上次访问记忆（localStorage，按路由 path）> 角色默认
 *   （hubTabs[].defaultForRoles）> 首个可见 Tab；三者都失效时回落首个可见，不静默进入无权限页。
 * - 页签待办徽标：hubTabs[].badge 声明数据源，徽标失败只隐藏不改口径。
 * - 长 Hub（超过 6 个页签）把溢出页签收进「更多」下拉，dropdown 内按 group 分组。
 *
 * 其他约束：
 * - 只读 route.query，切换 Tab 用 router.replace 保留其余 query（projectId/tab 之外不丢）；
 * - 不复制业务逻辑，Tab 即原页面组件本身，数据口径单一源不变。
 */
import { computed, ref, watch, markRaw, defineAsyncComponent, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ArrowDown } from '@/components/icons/registry'
import { loadHubBadges, type HubBadge, type HubBadgeSource } from '@/composables/useHubBadges'

export interface HubTabConfig {
  key: string
  label: string
  loader: () => Promise<any>
  /** Tab 级权限码（可选）：对应原子路由原 meta.permission，无权限不渲染该 Tab */
  permission?: string
  /** 角色默认首屏（可选）：URL 与访问记忆都未命中时生效，取第一个命中的 Tab */
  defaultForRoles?: string[]
  /** 「更多」下拉里的分组名（长 Hub 收敛用） */
  group?: string
  /** 待办徽标数据源（可选）；无数据源或计数为 0 时不渲染徽标 */
  badge?: HubBadgeSource
}

/** 超过该数量启用「更多」收敛，避免长 Hub 页签溢出换行 */
const PRIMARY_LIMIT = 6

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const tabs = computed<HubTabConfig[]>(() => (route.meta.hubTabs as HubTabConfig[]) || [])
const roles = computed<string[]>(() => (userStore?.roles as string[] | undefined) || [])
const isSuperAdmin = computed(() => (userStore?.permissions || []).includes('*:*:*'))

const visibleTabs = computed<HubTabConfig[]>(() => {
  if (isSuperAdmin.value) return tabs.value
  const perms = userStore?.permissions || []
  return tabs.value.filter((t) => !t.permission || perms.includes(t.permission))
})

const primaryTabs = computed<HubTabConfig[]>(() =>
  visibleTabs.value.length > PRIMARY_LIMIT ? visibleTabs.value.slice(0, PRIMARY_LIMIT) : visibleTabs.value
)
const overflowTabs = computed<HubTabConfig[]>(() =>
  visibleTabs.value.length > PRIMARY_LIMIT ? visibleTabs.value.slice(PRIMARY_LIMIT) : []
)

const activeTab = ref<string>('')

const overflowActive = computed(() => overflowTabs.value.some((t) => t.key === activeTab.value))
const activeLabel = computed(() => visibleTabs.value.find((t) => t.key === activeTab.value)?.label || '')

/** 「更多」下拉按 group 连续分组，保持 router 中的书写顺序 */
const overflowGroups = computed<Array<{ group: string; items: HubTabConfig[] }>>(() => {
  const groups: Array<{ group: string; items: HubTabConfig[] }> = []
  for (const tab of overflowTabs.value) {
    const name = tab.group || ''
    const last = groups[groups.length - 1]
    if (last && last.group === name) last.items.push(tab)
    else groups.push({ group: name, items: [tab] })
  }
  return groups
})

// 访问记忆：按入口路由 path 存，避免不同 Hub 互相覆盖；测试环境无 path 时关闭记忆
const memoryKey = computed(() => (route.path ? `zw-hub-tab:${route.path}` : ''))

function readMemory(): string {
  if (!memoryKey.value) return ''
  try {
    return localStorage.getItem(memoryKey.value) || ''
  } catch {
    return ''
  }
}

function writeMemory(key: string) {
  if (!memoryKey.value) return
  try {
    localStorage.setItem(memoryKey.value, key)
  } catch {
    // 隐私模式/配额满时静默放弃记忆，不影响本次导航
  }
}

/** Tab 选择优先级：URL > 访问记忆 > 角色默认 > 首个可见 */
function pickTab(list: HubTabConfig[]): string {
  const queryTab = String(route.query.tab || '')
  if (queryTab && list.some((t) => t.key === queryTab)) return queryTab
  const remembered = readMemory()
  if (remembered && list.some((t) => t.key === remembered)) return remembered
  const roleDefault = list.find((t) => t.defaultForRoles?.some((role) => roles.value.includes(role)))
  return roleDefault?.key || list[0]?.key || ''
}

watch(
  visibleTabs,
  (list) => {
    if (!list.length) {
      activeTab.value = ''
      return
    }
    // 当前 Tab 仍可见时不打断用户（权限异步加载、徽标刷新等场景）
    if (!list.some((t) => t.key === activeTab.value)) {
      activeTab.value = pickTab(list)
    }
  },
  { immediate: true }
)

const compCache = new Map<string, Component>()
/**
 * 缓存键必须带 Hub 身份：同一 HubPage 实例会被多个 hub 路由复用（router-view 不换组件），
 * 而 contract/settlement/output 等页签 key 在劳务、机械、专业分包多个 Hub 中重名，
 * 只按 key 缓存会把上一个 Hub 的页面挂到当前 Hub 下。
 */
const hubKey = computed(() => route.path || '')

function resolveComp(t: HubTabConfig): Component {
  const cacheKey = `${hubKey.value}::${t.key}`
  let c = compCache.get(cacheKey)
  if (!c) {
    c = markRaw(defineAsyncComponent(t.loader))
    compCache.set(cacheKey, c)
  }
  return c
}

const activeComp = computed<Component | null>(() => {
  const tab = visibleTabs.value.find((t) => t.key === activeTab.value)
  return tab ? resolveComp(tab) : null
})

// 切 Hub 时强制重选首 Tab：同名 key 沿用旧 Tab 会让用户看到上一个 Hub 的内容
watch(hubKey, () => {
  if (visibleTabs.value.length) activeTab.value = pickTab(visibleTabs.value)
})

function activate(key: string) {
  if (key) activeTab.value = key
}

watch(activeTab, (key) => {
  if (!key) return
  writeMemory(key)
  if (key !== route.query.tab) {
    router.replace({ query: { ...route.query, tab: key } })
  }
})

// 外部直达（?tab=xxx）或路由参数变化时同步激活 Tab（无权限的 Tab 会被回落到首个可见 Tab）
watch(
  () => route.query.tab,
  (t) => {
    const key = String(t || '')
    if (key && visibleTabs.value.some((x) => x.key === key) && key !== activeTab.value) {
      activeTab.value = key
    }
  }
)

const badges = ref<Record<string, HubBadge>>({})
let badgeSeq = 0

async function refreshBadges() {
  const sources = visibleTabs.value.map((t) => t.badge).filter(Boolean) as HubBadgeSource[]
  if (!sources.length) {
    badges.value = {}
    return
  }
  const seq = ++badgeSeq
  const result = await loadHubBadges(sources)
  // 期间页签已变化（切换路由/权限刷新）时丢弃过期结果
  if (seq === badgeSeq) badges.value = result
}

watch(visibleTabs, refreshBadges, { immediate: true })

function tabBadge(t: HubTabConfig): HubBadge | null {
  if (!t.badge) return null
  const badge = badges.value[t.badge]
  return badge && badge.count > 0 ? badge : null
}

function tabBadgeText(t: HubTabConfig): string {
  const badge = tabBadge(t)
  return badge ? (badge.count > 99 ? '99+' : String(badge.count)) : ''
}

function tabBadgeClass(t: HubTabConfig): string {
  const badge = tabBadge(t)
  return badge ? `hub-tab-badge--${badge.tone}` : ''
}

function tabBadgeTitle(t: HubTabConfig): string {
  const badge = tabBadge(t)
  return badge ? `${badge.label} ${badge.count} 笔` : ''
}
</script>

<style scoped>
.biz-hub {
  padding: var(--zw-space-md);
}
.hub-shell {
  border: 1px solid var(--zw-border);
  border-radius: var(--zw-radius-sm);
  background: var(--zw-bg-card);
  overflow: hidden;
}
.hub-tabbar {
  display: flex;
  align-items: stretch;
  flex-wrap: wrap;
  border-bottom: 1px solid var(--zw-border);
  background: var(--zw-bg-tag);
}
.hub-tab {
  display: inline-flex;
  align-items: center;
  gap: var(--zw-space-xs);
  height: 42px;
  padding: 0 var(--zw-space-md);
  border: none;
  border-right: 1px solid var(--zw-border);
  background: transparent;
  color: var(--zw-text-secondary);
  font-size: var(--zw-font-size-sm);
  font-weight: 500;
  cursor: pointer;
}
.hub-tab:hover {
  color: var(--zw-brand);
}
.hub-tab--active {
  color: var(--zw-brand);
  background: var(--zw-bg-card);
  box-shadow: inset 0 -2px 0 var(--zw-brand);
}
.hub-tab--more {
  border-right: none;
}
.hub-tab-caret {
  font-size: var(--zw-font-size-xs);
}
.hub-panel {
  padding: var(--zw-space-md);
  min-height: 320px;
}
.hub-tab-label {
  font-size: var(--zw-font-size-sm);
  font-weight: 500;
}
.hub-tab-badge {
  min-width: 18px;
  padding: 0 5px;
  border-radius: var(--zw-radius-full);
  background: var(--zw-bg-tag);
  color: var(--zw-text-secondary);
  font-size: var(--zw-font-size-xs);
  line-height: 18px;
  text-align: center;
}
.hub-tab-badge--danger {
  background: var(--zw-danger-light);
  color: var(--zw-danger);
}
.hub-tab-badge--warning {
  background: var(--zw-warning-light);
  color: var(--zw-warning);
}
.hub-more-item--active {
  color: var(--zw-brand);
  font-weight: 600;
}
</style>
