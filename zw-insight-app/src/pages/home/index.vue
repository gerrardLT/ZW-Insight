<template>
  <view class="home-page">
    <!-- 顶部卡片 -->
    <view class="stat-cards">
      <view class="stat-card">
        <text class="stat-value">{{ overview.projectTotal || 0 }}</text>
        <text class="stat-label">项目总数</text>
      </view>
      <view class="stat-card">
        <text class="stat-value">{{ formatWan(overview.totalContractAmount) }}</text>
        <text class="stat-label">合同总额(万)</text>
      </view>
      <view class="stat-card">
        <text class="stat-value">{{ formatWan(overview.totalIncome) }}</text>
        <text class="stat-label">已收款(万)</text>
      </view>
      <view class="stat-card">
        <text class="stat-value">{{ formatWan(overview.advanceFund) }}</text>
        <text class="stat-label">垫资(万)</text>
      </view>
    </view>

    <!-- 快捷功能 -->
    <view class="section">
      <view class="section-title">常用功能</view>
      <view class="shortcut-grid">
        <view class="shortcut-item" @click="navigateTo('/pages/material/inbound')">
          <text class="shortcut-icon">📦</text><text>材料入库</text>
        </view>
        <view class="shortcut-item" @click="navigateTo('/pages/material/outbound')">
          <text class="shortcut-icon">📤</text><text>材料出库</text>
        </view>
        <view class="shortcut-item" @click="navigateTo('/pages/site/construction-log')">
          <text class="shortcut-icon">📝</text><text>施工日志</text>
        </view>
        <view class="shortcut-item" @click="navigateTo('/pages/site/progress-feedback')">
          <text class="shortcut-icon">📊</text><text>进度反馈</text>
        </view>
        <view class="shortcut-item" @click="navigateTo('/pages/site/quality-check')">
          <text class="shortcut-icon">✅</text><text>质量检查</text>
        </view>
        <view class="shortcut-item" @click="navigateTo('/pages/site/safety-check')">
          <text class="shortcut-icon">🛡️</text><text>安全检查</text>
        </view>
        <view class="shortcut-item" @click="navigateTo('/pages/finance/invoice-apply')">
          <text class="shortcut-icon">🧾</text><text>开票申请</text>
        </view>
        <view class="shortcut-item" @click="navigateTo('/pages/finance/reimbursement')">
          <text class="shortcut-icon">💰</text><text>项目报销</text>
        </view>
      </view>
    </view>

    <!-- 消息提醒 -->
    <view class="section">
      <view class="section-title">消息提醒<text class="badge" v-if="unreadCount">{{ unreadCount }}</text></view>
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

const overview = ref<any>({})
const unreadCount = ref(0)
const messages = ref<any[]>([])

function formatWan(val: number) {
  if (!val) return '0'
  return (val / 10000).toFixed(1)
}

function navigateTo(url: string) {
  uni.navigateTo({ url })
}

async function loadData() {
  try {
    const res1: any = await getCompanyOverview()
    overview.value = res1.data || {}
  } catch {}
  try {
    const res2: any = await getUnreadCount()
    unreadCount.value = res2.data || 0
  } catch {}
  try {
    const res3: any = await getUnreadMessages({ page: 1, size: 5 })
    messages.value = res3.data?.records || []
  } catch {}
}

onShow(() => { loadData() })
onMounted(() => { loadData() })
</script>

<style scoped>
.home-page { padding: 20rpx; }
.stat-cards { display: flex; flex-wrap: wrap; gap: 16rpx; margin-bottom: 24rpx; }
.stat-card { flex: 1; min-width: 45%; background: #fff; border-radius: 12rpx; padding: 24rpx; text-align: center; box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.05); }
.stat-value { font-size: 40rpx; font-weight: bold; color: #303133; display: block; }
.stat-label { font-size: 24rpx; color: #909399; margin-top: 8rpx; display: block; }
.section { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 24rpx; }
.section-title { font-size: 30rpx; font-weight: bold; color: #303133; margin-bottom: 20rpx; position: relative; }
.badge { background: #f56c6c; color: #fff; font-size: 22rpx; padding: 2rpx 12rpx; border-radius: 20rpx; margin-left: 12rpx; font-weight: normal; }
.shortcut-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20rpx; }
.shortcut-item { display: flex; flex-direction: column; align-items: center; padding: 16rpx 0; font-size: 24rpx; color: #606266; }
.shortcut-icon { font-size: 48rpx; margin-bottom: 8rpx; }
.msg-list { }
.msg-item { display: flex; justify-content: space-between; align-items: center; padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.msg-title { font-size: 26rpx; color: #303133; flex: 1; }
.msg-time { font-size: 22rpx; color: #c0c4cc; margin-left: 16rpx; }
.empty { text-align: center; padding: 40rpx; color: #c0c4cc; font-size: 26rpx; }
</style>
