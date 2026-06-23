<template>
  <view class="workbench-page">
    <!-- 项目概览 -->
    <view class="section">
      <view class="section-title">项目看板</view>
      <view class="kanban-cards">
        <view class="kanban-card blue">
          <text class="kanban-value">{{ overview.inProgressCount || 0 }}</text>
          <text class="kanban-label">进行中</text>
        </view>
        <view class="kanban-card green">
          <text class="kanban-value">{{ overview.completedCount || 0 }}</text>
          <text class="kanban-label">已完工</text>
        </view>
        <view class="kanban-card orange">
          <text class="kanban-value">{{ overview.pendingSettleCount || 0 }}</text>
          <text class="kanban-label">待结算</text>
        </view>
        <view class="kanban-card red">
          <text class="kanban-value">{{ todoCount }}</text>
          <text class="kanban-label">待审批</text>
        </view>
      </view>
    </view>

    <!-- 我的项目列表 -->
    <view class="section">
      <view class="section-title">我的项目</view>
      <scroll-view scroll-y class="project-list" @scrolltolower="loadMore">
        <view class="project-item" v-for="item in projects" :key="item.id" @click="goArchive(item.id)">
          <view class="project-name">{{ item.projectName }}</view>
          <view class="project-info">
            <text class="project-status">{{ item.statusText }}</text>
            <text class="project-amount">{{ (item.contractAmount / 10000).toFixed(1) }}万</text>
          </view>
        </view>
        <view class="empty" v-if="!projects.length && !loading">
          <text>暂无项目</text>
        </view>
        <view class="loading-more" v-if="loading"><text>加载中...</text></view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getCompanyOverview, getProjectList, getTodoTasks } from '@/api/common'

const overview = ref<any>({})
const projects = ref<any[]>([])
const todoCount = ref(0)
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)

async function loadData() {
  try {
    const res: any = await getCompanyOverview()
    overview.value = res.data || {}
  } catch {}
  try {
    const res: any = await getTodoTasks({ page: 1, size: 1 })
    todoCount.value = res.data?.total || 0
  } catch {}
  page.value = 1
  hasMore.value = true
  await loadProjects()
}

async function loadProjects() {
  if (loading.value) return
  loading.value = true
  try {
    const res: any = await getProjectList({ page: page.value, size: 10 })
    const records = res.data?.records || []
    if (page.value === 1) {
      projects.value = records
    } else {
      projects.value.push(...records)
    }
    hasMore.value = records.length >= 10
  } catch {} finally {
    loading.value = false
  }
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  page.value++
  loadProjects()
}

function goArchive(projectId: number) {
  uni.navigateTo({ url: `/pages/project/archive?projectId=${projectId}` })
}

onShow(() => { loadData() })
</script>

<style scoped>
.workbench-page { padding: 20rpx; }
.section { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 24rpx; }
.section-title { font-size: 30rpx; font-weight: bold; color: #303133; margin-bottom: 20rpx; }
.kanban-cards { display: flex; gap: 12rpx; }
.kanban-card { flex: 1; padding: 20rpx 12rpx; border-radius: 8rpx; text-align: center; }
.kanban-card.blue { background: #ecf5ff; }
.kanban-card.green { background: #f0f9eb; }
.kanban-card.orange { background: #fdf6ec; }
.kanban-card.red { background: #fef0f0; }
.kanban-value { font-size: 36rpx; font-weight: bold; display: block; color: #303133; }
.kanban-label { font-size: 22rpx; color: #909399; margin-top: 4rpx; display: block; }
.project-list { max-height: 600rpx; }
.project-item { padding: 20rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.project-name { font-size: 28rpx; color: #303133; font-weight: 500; }
.project-info { display: flex; justify-content: space-between; margin-top: 8rpx; }
.project-status { font-size: 24rpx; color: #409eff; }
.project-amount { font-size: 24rpx; color: #909399; }
.empty { text-align: center; padding: 40rpx; color: #c0c4cc; font-size: 26rpx; }
.loading-more { text-align: center; padding: 20rpx; color: #909399; font-size: 24rpx; }
</style>
