<template>
  <view class="workbench-page">
    <!-- 项目概览 -->
    <view class="section">
      <view class="section-title">项目看板</view>
      <view v-if="overviewFailed" class="failed-state">
        <text class="failed-tip">项目看板加载失败</text>
        <text class="retry-btn" @click="loadOverview">重试</text>
      </view>
      <view v-else class="kanban-cards">
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

    <!-- 待办审批（P0 Req7：前 5 条，点击跳审批详情） -->
    <view class="section">
      <view class="section-title">待办审批</view>
      <view v-if="todoFailed" class="failed-state">
        <text class="failed-tip">待办任务加载失败</text>
        <text class="retry-btn" @click="refreshTodo">重试</text>
      </view>
      <template v-else>
        <view class="todo-item" v-for="item in todoTasks" :key="item.id || item.processInstanceId" @click="goApprovalDetail(item)">
          <text class="todo-title">{{ item.processName || item.taskName }}</text>
          <text class="todo-applicant">申请人：{{ item.startUserName }}</text>
        </view>
        <view class="empty" v-if="!todoTasks.length"><text>暂无待办审批</text></view>
      </template>
    </view>

    <!-- 我的项目列表 -->
    <view class="section">
      <view class="section-title">我的项目</view>
      <view v-if="projectsFailed && !projects.length" class="failed-state">
        <text class="failed-tip">项目列表加载失败</text>
        <text class="retry-btn" @click="loadProjects">重试</text>
      </view>
      <scroll-view v-else scroll-y class="project-list" @scrolltolower="loadMore">
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
import { ref, onUnmounted } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { getCompanyOverview, getProjectList, getTodoTasks } from '@/api/common'

const overview = ref<any>({})
const overviewFailed = ref(false)
const projects = ref<any[]>([])
const projectsFailed = ref(false)
const todoCount = ref(0)
const todoTasks = ref<any[]>([])
const todoFailed = ref(false)
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)
let todoTimer: ReturnType<typeof setInterval> | null = null

// P0 Req7：三分区各自失败态 + 重试，禁止空 catch 吞错误
async function loadOverview() {
  overviewFailed.value = false
  try {
    const res: any = await getCompanyOverview()
    overview.value = res.data || {}
  } catch {
    overviewFailed.value = true
  }
}

// 待办数 + 前 5 条待办列表（轮询复用同一方法）
async function refreshTodo() {
  todoFailed.value = false
  try {
    const res: any = await getTodoTasks({ page: 1, size: 5 })
    todoCount.value = res.data?.total || 0
    todoTasks.value = res.data?.records || []
  } catch {
    todoFailed.value = true
  }
}

async function loadProjects() {
  if (loading.value) return
  loading.value = true
  projectsFailed.value = false
  try {
    const res: any = await getProjectList({ page: page.value, size: 10 })
    const records = res.data?.records || []
    if (page.value === 1) {
      projects.value = records
    } else {
      projects.value.push(...records)
    }
    hasMore.value = records.length >= 10
  } catch {
    projectsFailed.value = true
  } finally {
    loading.value = false
  }
}

async function loadData() {
  page.value = 1
  hasMore.value = true
  await Promise.all([loadOverview(), refreshTodo(), loadProjects()])
}

// P0 Req7：待办数 60s 轮询，onShow 恢复 / onHide 停止
function startTodoPolling() {
  stopTodoPolling()
  todoTimer = setInterval(refreshTodo, 60 * 1000)
}
function stopTodoPolling() {
  if (todoTimer) {
    clearInterval(todoTimer)
    todoTimer = null
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

function goApprovalDetail(item: any) {
  const taskId = item.id || item.taskId || ''
  uni.navigateTo({ url: `/pages/approval/detail?taskId=${taskId}&processInstanceId=${item.processInstanceId}` })
}

onShow(() => { loadData(); startTodoPolling() })
onHide(() => { stopTodoPolling() })
onUnmounted(() => { stopTodoPolling() })
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
.failed-state { display: flex; align-items: center; justify-content: center; padding: 40rpx 0; }
.failed-tip { font-size: 26rpx; color: #f56c6c; }
.retry-btn { margin-left: 20rpx; padding: 6rpx 24rpx; background: #409eff; color: #fff; font-size: 24rpx; border-radius: 8rpx; }
.todo-item { display: flex; justify-content: space-between; align-items: center; padding: 20rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.todo-item:last-child { border-bottom: none; }
.todo-title { font-size: 28rpx; color: #303133; flex: 1; }
.todo-applicant { font-size: 24rpx; color: #909399; margin-left: 16rpx; }
</style>
