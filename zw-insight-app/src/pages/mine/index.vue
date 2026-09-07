<template>
  <view class="mine-page">
    <OfflineBanner />
    <!-- 用户信息头部 -->
    <view class="user-header">
      <view class="avatar">
        <text class="avatar-text">{{ avatarText }}</text>
      </view>
      <view class="user-info">
        <text class="user-name">{{ userStore.userInfo?.realName || userStore.userInfo?.username || '未登录' }}</text>
        <text class="user-role">{{ userStore.userInfo?.roleName || '-' }}</text>
      </view>
    </view>

    <!-- 菜单列表 -->
    <view class="menu-section">
      <view class="menu-item" @click="navigateTo('/pages/mine/password')">
        <text class="menu-text">修改密码</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="handleAbout">
        <text class="menu-text">关于我们</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item">
        <text class="menu-text">当前版本</text>
        <text class="menu-value">v1.0.0</text>
      </view>
    </view>

    <!-- 退出登录 -->
    <button class="logout-btn" @click="handleLogout">退出登录</button>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '@/stores/user'
import { logout as logoutApi } from '@/api/auth'
import OfflineBanner from '@/components/OfflineBanner.vue'

const userStore = useUserStore()

const avatarText = computed(() => {
  const name = userStore.userInfo?.realName || userStore.userInfo?.username || ''
  return name.slice(-2) || '用户'
})

function navigateTo(url: string) {
  uni.navigateTo({ url })
}

function handleAbout() {
  uni.showModal({
    title: '关于中维智营',
    content: '中维智营工程项目管理平台 v1.0.0\n专注于工程项目全生命周期管理',
    showCancel: false
  })
}

function handleLogout() {
  uni.showModal({
    title: '提示',
    content: '确定退出登录？',
    success: async (res) => {
      if (res.confirm) {
        try {
          await logoutApi()
        } catch {}
        userStore.logout()
      }
    }
  })
}
</script>

<style scoped>
.mine-page { padding: 20rpx; }
/* 头部：石墨黑控制室纯色块（去渐变纪律）+ 橙色头像强调 */
.user-header { display: flex; align-items: center; background: var(--zw-bg-sidebar); border-radius: var(--zw-radius-lg); padding: 40rpx 30rpx; margin-bottom: 24rpx; }
.avatar { width: 100rpx; height: 100rpx; border-radius: var(--zw-radius-xs); background: var(--zw-brand); display: flex; align-items: center; justify-content: center; }
.avatar-text { color: var(--zw-on-primary); font-size: 32rpx; font-weight: bold; } /* 橙底深字承重规则 */
.user-info { margin-left: 24rpx; }
.user-name { font-size: 32rpx; color: #fff; font-weight: bold; display: block; }
.user-role { font-size: 24rpx; color: rgba(255,255,255,0.6); margin-top: 8rpx; display: block; }
.menu-section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light); border-radius: var(--zw-radius-lg); margin-bottom: 24rpx; }
.menu-item { display: flex; justify-content: space-between; align-items: center; padding: 28rpx 24rpx; border-bottom: 1rpx solid var(--zw-border-light); }
.menu-item:last-child { border-bottom: none; }
.menu-text { font-size: 28rpx; color: var(--zw-text-primary); }
.menu-arrow { font-size: 32rpx; color: var(--zw-text-quaternary); }
.menu-value { font-size: 26rpx; color: var(--zw-text-tertiary); }
.logout-btn { margin: 40rpx 0; height: 88rpx; line-height: 88rpx; background: var(--zw-bg-card); color: var(--zw-danger); font-size: 30rpx; border-radius: var(--zw-radius-lg); border: 1rpx solid var(--zw-danger-light); }
</style>
