<template>
  <div class="biz-hub">
    <el-tabs v-if="visibleTabs.length" v-model="activeTab" type="border-card" class="hub-tabs">
      <el-tab-pane v-for="t in visibleTabs" :key="t.key" :name="t.key" lazy>
        <template #label>
          <span class="hub-tab-label">{{ t.label }}</span>
        </template>
        <keep-alive>
          <component :is="resolveComp(t)" v-if="activeTab === t.key" />
        </keep-alive>
      </el-tab-pane>
    </el-tabs>
    <el-empty v-else description="当前账号无该模块的功能权限，请联系管理员分配" />
  </div>
</template>

<script setup lang="ts">
/**
 * 通用聚合入口壳（V2026_120/121/122 二级菜单收敛）。
 *
 * 各域二级菜单收敛为一等入口后按 route.meta.hubTabs 渲染 Tab，Tab 内容懒挂载
 * 原整页组件（keep-alive 缓存）。原路由全部保留且支持 URL 直达（?projectId=
 * 等参数经 route.query 原样透传给子组件——子组件 onMounted 已按字符串承接）。
 *
 * 权限双层（V2026_122 补强，修复 Hub 化后细粒度权限失效）：
 * - 入口层：入口路由 meta.permission（域级 view 码，router 守卫任一匹配即过）；
 * - Tab 层：hubTabs[].permission 可选（原子路由原 meta.permission 原样回填），
 *   无该权限码的 Tab 不渲染——避免「只在原子路由上受守卫、在 Tab 内挂载时绕过」。
 *   超管（*:*:*）全量放行；无 permission 的 Tab 跟随入口授权。
 *
 * 其他约束：
 * - 只读 route.query，切换 Tab 用 router.replace 保留其余 query（projectId/tab 之外不丢）；
 * - 不复制业务逻辑，Tab 即原页面组件本身，数据口径单一源不变。
 */
import { ref, computed, watch, markRaw, defineAsyncComponent, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

export interface HubTabConfig {
  key: string
  label: string
  loader: () => Promise<any>
  /** Tab 级权限码（可选）：对应原子路由原 meta.permission，无权限不渲染该 Tab */
  permission?: string
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const tabs = computed<HubTabConfig[]>(() => (route.meta.hubTabs as HubTabConfig[]) || [])

const isSuperAdmin = computed(() => (userStore?.permissions || []).includes('*:*:*'))

const visibleTabs = computed<HubTabConfig[]>(() => {
  if (isSuperAdmin.value) return tabs.value
  const perms = userStore?.permissions || []
  return tabs.value.filter((t) => !t.permission || perms.includes(t.permission))
})

const activeTab = ref<string>('')

watch(
  visibleTabs,
  (list) => {
    const qTab = String(route.query.tab || '')
    const match = list.find((t) => t.key === qTab)
    if (match) {
      activeTab.value = match.key
    } else if (!list.some((t) => t.key === activeTab.value)) {
      activeTab.value = list[0]?.key || ''
    }
  },
  { immediate: true }
)

const compCache = new Map<string, Component>()
function resolveComp(t: HubTabConfig): Component {
  let c = compCache.get(t.key)
  if (!c) {
    c = markRaw(defineAsyncComponent(t.loader))
    compCache.set(t.key, c)
  }
  return c
}

watch(activeTab, (key) => {
  if (key && key !== route.query.tab) {
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
</script>

<style scoped>
.biz-hub {
  padding: var(--zw-space-md);
}
.hub-tabs {
  min-height: 320px;
}
.hub-tab-label {
  font-size: var(--zw-font-size-sm);
  font-weight: 500;
}
</style>
