<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="layout-aside">
      <div class="logo">
        <span v-if="!isCollapse">中维智营</span>
        <span v-else>ZW</span>
      </div>
      <el-scrollbar>
        <el-menu
          :default-active="$route.path"
          :collapse="isCollapse"
          router
          background-color="#001529"
          text-color="#ffffffa6"
          active-text-color="#ffffff"
        >
          <template v-for="route in menuRoutes" :key="route.path">
            <!-- 单层菜单（无可见子项或只有自身） -->
            <el-menu-item
              v-if="route.singleChild"
              :index="route.singleChild.fullPath"
            >
              <el-icon v-if="route.singleChild.icon"><component :is="route.singleChild.icon" /></el-icon>
              <span>{{ route.singleChild.title }}</span>
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
                <span>{{ child.title }}</span>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container>
      <!-- 顶部导航 -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="isCollapse = !isCollapse">
            <Expand v-if="isCollapse" />
            <Fold v-else />
          </el-icon>
        </div>
        <div class="header-right">
          <el-dropdown>
            <span class="user-info">
              {{ userStore.userInfo?.realName || userStore.userInfo?.name || '管理员' }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)

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
    // 仅处理挂载了子路由的布局型路由；跳过登录/错误页/通配等
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

    // 根路由 '/'（首页/看板等)：没有自身 title，直接铺平为单层菜单项
    const groupTitle = r.meta?.title as string | undefined
    if (!groupTitle) {
      for (const c of visibleChildren) {
        groups.push({ path: c.fullPath, singleChild: c })
      }
      continue
    }

    // 普通模块目录
    if (visibleChildren.length === 1) {
      // 只有一个可见子项时，折叠为单层菜单，显示模块名
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

function handleLogout() {
  userStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}
.layout-aside {
  background-color: #001529;
  transition: width 0.3s;
  overflow: hidden;
}
.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
}
.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f0f0f0;
  padding: 0 20px;
}
.collapse-btn {
  font-size: 20px;
  cursor: pointer;
}
.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
}
.layout-main {
  background-color: #f5f5f5;
  overflow-y: auto;
}
</style>
