<template>
  <view class="home-page">
    <OfflineBanner />
    <!-- 顶部卡片（蓝图角标：品牌签名组件）；首屏骨架（S3.1）加载中显示概览卡骨架 -->
    <ZwSkeleton v-if="overviewLoading" type="card" />
    <view class="stat-cards" v-else>
      <view class="stat-card card-corner-marked">
        <text class="stat-value">{{ overview.projectTotal || 0 }}</text>
        <text class="stat-label">项目总数</text>
      </view>
      <view class="stat-card card-corner-marked">
        <text class="stat-value">{{ formatWan(overview.totalContractAmount) }}</text>
        <text class="stat-label">合同总额(万)</text>
      </view>
      <view class="stat-card card-corner-marked">
        <text class="stat-value">{{ formatWan(overview.totalIncome) }}</text>
        <text class="stat-label">已收款(万)</text>
      </view>
      <view class="stat-card card-corner-marked">
        <text class="stat-value">{{ formatWan(overview.advanceFund) }}</text>
        <text class="stat-label">垫资(万)</text>
      </view>
    </view>

    <!-- 快捷功能 -->
    <view class="section">
      <view class="section-header">
        <view class="section-heading">
          <text class="eyebrow-cap">Shortcuts</text>
          <text class="section-title">常用功能</text>
        </view>
        <view class="edit-entry" @click="goShortcutEdit">
          <text class="edit-icon">✎</text><text class="edit-text">编辑</text>
        </view>
      </view>
      <view class="shortcut-grid" v-if="shortcuts.length">
        <view
          class="shortcut-item"
          v-for="item in shortcuts"
          :key="item.shortcutId"
          @click="navigateTo(item.menuPath)"
        >
          <text class="shortcut-icon">{{ item.menuIcon || '📌' }}</text>
          <text>{{ item.menuName }}</text>
        </view>
      </view>
      <view class="empty" v-else><text>暂无快捷入口，点击右上角“编辑”添加</text></view>
    </view>

    <!-- 消息提醒 -->
    <view class="section">
      <view class="section-header">
        <view class="section-heading">
          <text class="eyebrow-cap">Inbox</text>
          <view class="section-title">消息提醒<text class="badge" v-if="unreadCount">{{ unreadCount }}</text></view>
        </view>
        <view class="edit-entry" @click="goMessageCenter">
          <text class="edit-text">信息中心</text><text class="more-arrow">›</text>
        </view>
      </view>
      <view class="msg-list" v-if="messages.length">
        <view class="msg-item" v-for="msg in messages" :key="msg.id">
          <text class="msg-title">{{ msg.title }}</text>
          <text class="msg-time">{{ msg.createdAt }}</text>
        </view>
      </view>
      <view class="empty" v-else><text>暂无消息</text></view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getCompanyOverview, getUnreadCount, getUnreadMessages } from '@/api/common'
import { getUserShortcuts, type UserShortcutConfig } from '@/api/shortcut'
import OfflineBanner from '@/components/OfflineBanner.vue'
import ZwSkeleton from '@/components/ZwSkeleton.vue'

const overview = ref<any>({})
const overviewLoading = ref(true)
const unreadCount = ref(0)
const messages = ref<any[]>([])
const shortcuts = ref<UserShortcutConfig[]>([])

function formatWan(val: number) {
  if (!val) return '0'
  return (val / 10000).toFixed(1)
}

function navigateTo(url: string) {
  uni.navigateTo({ url })
}

function goShortcutEdit() {
  uni.navigateTo({ url: '/pages/mine/shortcut-edit' })
}

function goMessageCenter() {
  uni.navigateTo({ url: '/pages/message-center/index' })
}

// 加载用户个性化快捷入口（未配置时后端返回系统默认项，≤4 项）
async function loadShortcuts() {
  try {
    const res: any = await getUserShortcuts()
    const list: UserShortcutConfig[] = res?.data || []
    shortcuts.value = [...list].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  } catch {
    uni.showToast({ title: '快捷入口加载失败', icon: 'none' })
  }
}

async function loadData() {
  try {
    const res1: any = await getCompanyOverview()
    overview.value = res1.data || {}
  } catch {} finally {
    overviewLoading.value = false
  }
  try {
    const res2: any = await getUnreadCount()
    unreadCount.value = res2.data || 0
  } catch {}
  try {
    const res3: any = await getUnreadMessages({ page: 1, size: 5 })
    messages.value = res3.data?.records || []
  } catch {}
  // 从编辑页返回时通过 onShow 重新加载，保证编辑结果即时生效
  await loadShortcuts()
}

onShow(() => { loadData() })
onMounted(() => { loadData() })
</script>

<style scoped>
.home-page { padding: 20rpx; }
.stat-cards { display: flex; flex-wrap: wrap; gap: 16rpx; margin-bottom: 24rpx; }
.stat-card { flex: 1; min-width: 45%; background: var(--zw-bg-card); border: 1rpx solid var(--zw-border); border-radius: var(--zw-radius-sm); padding: 24rpx; text-align: center; box-shadow: var(--zw-shadow-card); }
.stat-value { font-size: 40rpx; font-weight: bold; color: var(--zw-text-primary); display: block; font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; } /* 等宽数字 */
.stat-label { font-size: 24rpx; color: var(--zw-text-tertiary); margin-top: 8rpx; display: block; }
.section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border); border-radius: var(--zw-radius-sm); padding: 24rpx; margin-bottom: 24rpx; }
.section-heading { display: flex; flex-direction: column; }
.section-heading .eyebrow-cap { margin-bottom: 4rpx; }
.section-title { font-size: 30rpx; font-weight: bold; color: var(--zw-text-primary); margin-bottom: 20rpx; position: relative; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20rpx; }
.section-header .section-title { margin-bottom: 0; }
.edit-entry { display: flex; align-items: center; color: var(--zw-brand); font-size: 24rpx; padding: 6rpx 10rpx; min-height: 44px; box-sizing: border-box; border-radius: var(--zw-radius-xs); transition: opacity 80ms; } /* P0 触控达标：46rpx→min-height 44px */
.edit-entry:active { opacity: 0.7; }
.edit-icon { font-size: 26rpx; margin-right: 6rpx; }
.edit-text { font-size: 24rpx; }
.more-arrow { font-size: 30rpx; color: var(--zw-brand); margin-left: 4rpx; }
.badge { background: var(--zw-danger); color: var(--zw-text-inverse); font-size: 22rpx; padding: 2rpx 12rpx; border-radius: 9999px; margin-left: 12rpx; font-weight: normal; } /* 红底配 inverse 字：暗色 danger 提亮后自动翻深 */
.shortcut-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20rpx; }
.shortcut-item { display: flex; flex-direction: column; align-items: center; padding: 16rpx 0; font-size: 24rpx; color: var(--zw-text-secondary); transition: transform 80ms; }
.shortcut-item:active { transform: scale(0.96); opacity: 0.85; }
.shortcut-icon { width: 88rpx; height: 88rpx; display: flex; align-items: center; justify-content: center; background: var(--zw-brand-light); border-radius: var(--zw-radius-md); font-size: 44rpx; margin-bottom: 8rpx; } /* 品牌浅底方块 */
.msg-list { }
.msg-item { display: flex; justify-content: space-between; align-items: center; padding: 20rpx 0; border-bottom: 1rpx solid var(--zw-border-light); transition: background-color 80ms; }
.msg-item:active { background-color: var(--zw-bg-hover); }
.msg-title { font-size: 26rpx; color: var(--zw-text-primary); flex: 1; }
.msg-time { font-size: 22rpx; color: var(--zw-text-quaternary); margin-left: 16rpx; font-family: var(--zw-font-mono); }
.empty { text-align: center; padding: 40rpx; color: var(--zw-text-quaternary); font-size: 26rpx; }
</style>
