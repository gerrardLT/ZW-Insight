<template>
  <view class="archive-page">
    <view class="loading-mask" v-if="loading">
      <text>加载中...</text>
    </view>

    <view class="failed-state" v-else-if="loadFailed">
      <text class="failed-tip">项目档案加载失败</text>
      <text class="retry-btn" @click="retryLoad">重试</text>
    </view>

    <template v-else-if="archive">
      <!-- 项目基本信息 -->
      <view class="section">
        <view class="section-title">项目信息</view>
        <view class="info-row">
          <text class="info-label">项目名称</text>
          <text class="info-value">{{ archive.projectName }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">项目编号</text>
          <text class="info-value">{{ archive.projectCode }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">项目状态</text>
          <text class="info-value">{{ archive.statusText }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">业主单位</text>
          <text class="info-value">{{ archive.ownerName }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">合同金额</text>
          <text class="info-value">{{ archive.contractAmount ? (archive.contractAmount / 10000).toFixed(2) + '万' : '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">项目经理</text>
          <text class="info-value">{{ archive.managerName || '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">开工日期</text>
          <text class="info-value">{{ archive.startDate || '-' }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">计划竣工</text>
          <text class="info-value">{{ archive.endDate || '-' }}</text>
        </view>
      </view>

      <!-- 财务概况 -->
      <view class="section" v-if="archive.finance">
        <view class="section-title">财务概况</view>
        <view class="info-row">
          <text class="info-label">已收款</text>
          <text class="info-value">{{ formatWan(archive.finance.totalIncome) }}万</text>
        </view>
        <view class="info-row">
          <text class="info-label">已付款</text>
          <text class="info-value">{{ formatWan(archive.finance.totalExpense) }}万</text>
        </view>
        <view class="info-row">
          <text class="info-label">利润</text>
          <text class="info-value">{{ formatWan(archive.finance.profit) }}万</text>
        </view>
      </view>

      <!-- 进度概况 -->
      <view class="section" v-if="archive.progress !== undefined">
        <view class="section-title">进度概况</view>
        <view class="progress-bar-wrap">
          <view class="progress-bar">
            <view class="progress-inner" :style="{ width: (archive.progress || 0) + '%' }"></view>
          </view>
          <text class="progress-text">{{ archive.progress || 0 }}%</text>
        </view>
      </view>
    </template>

    <view class="empty" v-if="!loading && !archive">
      <text>暂无项目档案信息</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProjectArchive } from '@/api/common'

const archive = ref<any>(null)
const loading = ref(true)
const loadFailed = ref(false)
let currentProjectId = 0

function formatWan(val: number) {
  if (!val) return '0'
  return (val / 10000).toFixed(2)
}

onLoad((options: any) => {
  const projectId = Number(options.projectId)
  currentProjectId = projectId || 0
  if (projectId) {
    loadArchive(projectId)
  } else {
    loading.value = false
  }
})

async function loadArchive(projectId: number) {
  loading.value = true
  loadFailed.value = false
  try {
    const res: any = await getProjectArchive(projectId)
    archive.value = res.data
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

function retryLoad() {
  if (currentProjectId) {
    loadArchive(currentProjectId)
  }
}
</script>

<style scoped>
.archive-page { padding: 20rpx; }
.loading-mask, .failed-state { display: flex; justify-content: center; align-items: center; height: 400rpx; font-size: 28rpx; }
.loading-mask { color: var(--zw-text-tertiary); }
.failed-state { flex-direction: column; }
.failed-tip { color: var(--zw-danger); margin-bottom: 20rpx; }
.retry-btn { padding: 8rpx 28rpx; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 26rpx; border-radius: var(--zw-radius-xs); }
.section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border); border-radius: var(--zw-radius-xs); padding: 24rpx; margin-bottom: 20rpx; }
.section-title { font-size: 28rpx; font-weight: bold; color: var(--zw-text-primary); margin-bottom: 16rpx; padding-bottom: 12rpx; border-bottom: 1rpx solid var(--zw-border-light); }
.info-row { display: flex; justify-content: space-between; padding: 12rpx 0; }
.info-label { font-size: 26rpx; color: var(--zw-text-tertiary); }
.info-value { font-size: 26rpx; color: var(--zw-text-primary); font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.progress-bar-wrap { display: flex; align-items: center; gap: 16rpx; }
.progress-bar { flex: 1; height: 16rpx; background: var(--zw-bg-hover); border-radius: 2rpx; overflow: hidden; }
.progress-inner { height: 100%; width: 100%; background: var(--zw-brand); border-radius: 2rpx; transform-origin: left center; transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1); will-change: transform; }
.progress-text { font-size: 26rpx; color: var(--zw-brand); font-weight: bold; font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.empty { text-align: center; padding: 80rpx; color: var(--zw-text-quaternary); font-size: 26rpx; }
</style>
