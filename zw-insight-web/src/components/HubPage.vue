<template>
  <div class="biz-hub">
    <el-tabs v-model="activeTab" type="border-card" class="hub-tabs">
      <el-tab-pane v-for="t in tabs" :key="t.key" :name="t.key" lazy>
        <template #label>
          <span class="hub-tab-label">{{ t.label }}</span>
        </template>
        <keep-alive>
          <component :is="resolveComp(t)" v-if="activeTab === t.key" />
        </keep-alive>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
/**
 * 通用聚合入口壳（V2026_120/121 二级菜单收敛）。
 *
 * 财务 29→8、经营总览 3→1、项目管理 7→3、成本管理 31→5：本壳按
 * route.meta.hubTabs 渲染 Tab，Tab 内容懒挂载原整页组件（keep-alive 缓存）。
 * 原路由全部保留且支持 URL 直达（?projectId= 等参数经 route.query 原样
 * 透传给子组件——子组件 onMounted 已按字符串承接 projectId）。
 *
 * 约束：
 * - 只读 route.query，切换 Tab 用 router.replace 保留其余 query（projectId/tab 之外不丢）；
 * - 不复制业务逻辑，Tab 即原页面组件本身，权限码/数据口径单一源不变。
 */
import { ref, computed, watch, markRaw, defineAsyncComponent, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'

export interface HubTabConfig {
  key: string
  label: string
  loader: () => Promise<any>
}

const route = useRoute()
const router = useRouter()

const tabs = computed<HubTabConfig[]>(() => (route.meta.hubTabs as HubTabConfig[]) || [])

const activeTab = ref<string>(String(route.query.tab || tabs.value[0]?.key || ''))

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

// 外部直达（?tab=xxx）或路由参数变化时同步激活 Tab
watch(
  () => route.query.tab,
  (t) => {
    const key = String(t || '')
    if (key && tabs.value.some((x) => x.key === key) && key !== activeTab.value) {
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
