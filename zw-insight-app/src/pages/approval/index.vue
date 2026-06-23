<template>
  <view class="approval-page">
    <!-- Tab切换 -->
    <view class="tabs">
      <view class="tab-item" :class="{ active: activeTab === 'todo' }" @click="switchTab('todo')">
        <text>待办</text>
      </view>
      <view class="tab-item" :class="{ active: activeTab === 'done' }" @click="switchTab('done')">
        <text>已办</text>
      </view>
    </view>

    <!-- 列表 -->
    <scroll-view scroll-y class="task-list" @scrolltolower="loadMore" refresher-enabled @refresherrefresh="onRefresh" :refresher-triggered="refreshing">
      <view class="task-item" v-for="item in tasks" :key="item.id" @click="goDetail(item)">
        <view class="task-header">
          <text class="task-title">{{ item.processName }}</text>
          <text class="task-status" :class="item.status">{{ item.statusText || '待处理' }}</text>
        </view>
        <view class="task-info">
          <text class="task-applicant">申请人：{{ item.startUserName }}</text>
          <text class="task-time">{{ item.createTime }}</text>
        </view>
        <view class="task-desc" v-if="item.businessTitle">
          <text>{{ item.businessTitle }}</text>
        </view>
      </view>
      <view class="empty" v-if="!tasks.length && !loading">
        <text>暂无{{ activeTab === 'todo' ? '待办' : '已办' }}任务</text>
      </view>
      <view class="loading-more" v-if="loading"><text>加载中...</text></view>
      <view class="no-more" v-if="!hasMore && tasks.length"><text>没有更多了</text></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getTodoTasks, getDoneTasks } from '@/api/common'

const activeTab = ref('todo')
const tasks = ref<any[]>([])
const loading = ref(false)
const refreshing = ref(false)
const page = ref(1)
const hasMore = ref(true)

function switchTab(tab: string) {
  activeTab.value = tab
  page.value = 1
  hasMore.value = true
  tasks.value = []
  loadData()
}

async function loadData() {
  if (loading.value) return
  loading.value = true
  try {
    const api = activeTab.value === 'todo' ? getTodoTasks : getDoneTasks
    const res: any = await api({ page: page.value, size: 15 })
    const records = res.data?.records || []
    if (page.value === 1) {
      tasks.value = records
    } else {
      tasks.value.push(...records)
    }
    hasMore.value = records.length >= 15
  } catch {} finally {
    loading.value = false
    refreshing.value = false
  }
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  page.value++
  loadData()
}

function onRefresh() {
  refreshing.value = true
  page.value = 1
  hasMore.value = true
  loadData()
}

function goDetail(item: any) {
  uni.navigateTo({
    url: `/pages/approval/detail?taskId=${item.id}&processInstanceId=${item.processInstanceId}`
  })
}

onShow(() => {
  page.value = 1
  hasMore.value = true
  loadData()
})
</script>

<style scoped>
.approval-page { display: flex; flex-direction: column; height: 100vh; background: #f5f5f5; }
.tabs { display: flex; background: #fff; border-bottom: 1rpx solid #f0f0f0; }
.tab-item { flex: 1; text-align: center; padding: 24rpx 0; font-size: 28rpx; color: #606266; position: relative; }
.tab-item.active { color: #409eff; font-weight: bold; }
.tab-item.active::after { content: ''; position: absolute; bottom: 0; left: 50%; transform: translateX(-50%); width: 60rpx; height: 4rpx; background: #409eff; border-radius: 2rpx; }
.task-list { flex: 1; padding: 20rpx; }
.task-item { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.task-header { display: flex; justify-content: space-between; align-items: center; }
.task-title { font-size: 28rpx; color: #303133; font-weight: 500; }
.task-status { font-size: 24rpx; padding: 4rpx 12rpx; border-radius: 4rpx; background: #ecf5ff; color: #409eff; }
.task-info { display: flex; justify-content: space-between; margin-top: 12rpx; }
.task-applicant { font-size: 24rpx; color: #606266; }
.task-time { font-size: 22rpx; color: #c0c4cc; }
.task-desc { margin-top: 12rpx; font-size: 24rpx; color: #909399; }
.empty { text-align: center; padding: 80rpx; color: #c0c4cc; font-size: 26rpx; }
.loading-more { text-align: center; padding: 20rpx; color: #909399; font-size: 24rpx; }
.no-more { text-align: center; padding: 20rpx; color: #c0c4cc; font-size: 22rpx; }
</style>
