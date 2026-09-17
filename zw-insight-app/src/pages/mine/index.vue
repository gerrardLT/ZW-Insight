<template>
  <view class="mine-page">
    <OfflineBanner />
    <!-- 用户信息头部：石墨黑控制室 + hazard 顶线（品牌签名） -->
    <view class="user-header">
      <view class="hazard-divider header-hazard"></view>
      <view class="user-header-inner">
        <view class="avatar">
          <image class="avatar-img" :src="userStore.userInfo?.avatar || '/static/brand/default-avatar.png'" mode="aspectFill" />
          <text class="avatar-text">{{ avatarText }}</text>
        </view>
        <view class="user-info">
          <text class="user-name">{{ userStore.userInfo?.realName || userStore.userInfo?.username || '未登录' }}</text>
          <text class="user-role">{{ userStore.userInfo?.roleName || '-' }}</text>
        </view>
        <text class="user-eyebrow eyebrow-cap">Account</text>
      </view>
    </view>

    <!-- 菜单列表（契约钉住 .menu-item 结构与顺序：items[0]签到/1密码/2关于；视觉已达标保留原结构） -->
    <view class="menu-section zw-card">
      <view class="menu-item" @click="navigateTo('/pages/mine/sign')">
        <text class="menu-text">定位签到</text>
        <text class="menu-arrow">›</text>
      </view>
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
      <view class="menu-item">
        <view class="menu-label-group">
          <text class="menu-text">现场强光高对比模式</text>
          <text class="menu-sub-tip">烈日直射作业高反差增强</text>
        </view>
        <switch :checked="isOutdoorMode" color="#ff6b00" style="transform: scale(0.8);" @change="toggleOutdoorMode" />
      </view>
    </view>

    <!-- 退出登录（契约 class .logout-btn） -->
    <button class="logout-btn" @click="handleLogout">退出登录</button>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { logout as logoutApi } from '@/api/auth'
import OfflineBanner from '@/components/OfflineBanner.vue'

const userStore = useUserStore()

const isOutdoorMode = ref(false)

function applyOutdoorTheme(enable: boolean) {
  if (typeof document !== 'undefined') {
    if (enable) {
      document.documentElement.dataset.theme = 'outdoor'
      document.body.classList.add('theme-outdoor')
    } else {
      delete document.documentElement.dataset.theme
      document.body.classList.remove('theme-outdoor')
    }
  }
}

function toggleOutdoorMode(e: any) {
  isOutdoorMode.value = e.detail.value
  uni.setStorageSync('zw_outdoor_mode', isOutdoorMode.value ? '1' : '0')
  applyOutdoorTheme(isOutdoorMode.value)
  uni.showToast({
    title: isOutdoorMode.value ? '已开启现场强光高对比' : '已恢复常规视觉',
    icon: 'none'
  })
}

onMounted(() => {
  isOutdoorMode.value = uni.getStorageSync('zw_outdoor_mode') === '1'
  if (isOutdoorMode.value) {
    applyOutdoorTheme(true)
  }
})

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
.mine-page { padding: 24rpx; }

/* 头部：石墨黑控制室 + hazard 顶线（离线语义之外的唯一合法——账户安全警示区） */
.user-header {
  position: relative;
  background: var(--zw-bg-sidebar);
  border-radius: var(--zw-radius-sm);
  margin-bottom: 24rpx;
  overflow: hidden;
}
.header-hazard { height: 6rpx; }
.user-header-inner {
  display: flex;
  align-items: center;
  padding: 40rpx 30rpx;
}
.avatar {
  position: relative;
  width: 108rpx;
  height: 108rpx;
  border-radius: var(--zw-radius-xs);
  background: var(--zw-brand);
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3rpx solid var(--zw-brand);
  overflow: hidden;
  flex-shrink: 0;
}
.avatar-img { width: 100%; height: 100%; }
/* 头像后备末两字（契约 .avatar-text；真实头像加载后覆盖在下层） */
.avatar-text { position: absolute; color: var(--zw-on-primary); font-size: 32rpx; font-weight: bold; }
.user-info { margin-left: 24rpx; flex: 1; min-width: 0; }
.user-name { font-size: 34rpx; color: #fff; font-weight: bold; display: block; }
.user-role { font-size: 24rpx; color: rgba(255, 255, 255, 0.6); margin-top: 8rpx; display: block; }
.user-eyebrow { color: var(--zw-brand); }

/* 菜单区：契约结构 menu-item（原达标行样式） */
.menu-section { border-radius: var(--zw-radius-sm); margin-bottom: 24rpx; overflow: hidden; }
.menu-item { display: flex; justify-content: space-between; align-items: center; padding: 12rpx 24rpx; min-height: 44px; border-bottom: 1rpx solid var(--zw-border-light); box-sizing: border-box; }
.menu-section .menu-item:last-child, .menu-section .menu-item:nth-child(4) { border-bottom: none; }
.menu-arrow { font-size: 32rpx; color: var(--zw-text-quaternary); }
.menu-value { font-size: 26rpx; color: var(--zw-text-tertiary); font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.menu-label-group { display: flex; flex-direction: column; gap: 4rpx; }
.menu-text { font-size: 28rpx; color: var(--zw-text-primary); }
.menu-sub-tip { font-size: 20rpx; color: var(--zw-text-quaternary); }

.logout-btn { margin: 40rpx 0; height: 88rpx; line-height: 88rpx; background: var(--zw-bg-card); color: var(--zw-danger); font-size: 30rpx; border-radius: var(--zw-radius-xs); border: 1rpx solid var(--zw-danger-light); }
</style>
