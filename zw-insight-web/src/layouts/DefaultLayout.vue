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
          <el-menu-item index="/dashboard">
            <el-icon><HomeFilled /></el-icon>
            <span>首页</span>
          </el-menu-item>

          <!-- 项目管理 -->
          <el-sub-menu index="/project">
            <template #title>
              <el-icon><Briefcase /></el-icon>
              <span>项目管理</span>
            </template>
            <el-menu-item index="/project/list">项目报备</el-menu-item>
          </el-sub-menu>

          <!-- 合同管理 -->
          <el-sub-menu index="/contract">
            <template #title>
              <el-icon><Notebook /></el-icon>
              <span>合同管理</span>
            </template>
            <el-menu-item index="/contract/list">施工合同</el-menu-item>
          </el-sub-menu>

          <!-- 财务管理 -->
          <el-sub-menu index="/finance">
            <template #title>
              <el-icon><Money /></el-icon>
              <span>财务管理</span>
            </template>
            <el-menu-item index="/finance/invoice-apply">开票申请</el-menu-item>
            <el-menu-item index="/finance/payment-received">回款登记</el-menu-item>
            <el-menu-item index="/finance/payment-apply">付款申请</el-menu-item>
          </el-sub-menu>

          <!-- 系统管理 -->
          <el-sub-menu index="/system">
            <template #title>
              <el-icon><Setting /></el-icon>
              <span>系统管理</span>
            </template>
            <el-menu-item index="/system/org">机构管理</el-menu-item>
            <el-menu-item index="/system/user">人员管理</el-menu-item>
            <el-menu-item index="/system/role">角色管理</el-menu-item>
            <el-menu-item index="/system/menu">菜单管理</el-menu-item>
            <el-menu-item index="/system/dict">数据字典</el-menu-item>
          </el-sub-menu>
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
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const isCollapse = ref(false)

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
