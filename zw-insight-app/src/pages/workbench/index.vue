<template>
  <view class="workbench-page">
    <!-- 项目概览 -->
    <view class="section zw-card">
      <view class="section-header-flex">
        <view class="section-title">项目看板</view>
        <view class="quick-link" @click="navigateTo('/pages/project/cost-control/index')">
          <text class="link-text">成本监控 </text>
          <text class="more-arrow">›</text>
        </view>
      </view>
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

    <!-- 常用功能入口（含变更事件等核心现场业务） -->
    <view class="section zw-card">
      <view class="section-title">常用现场业务</view>
      <view class="biz-grid">
        <view class="biz-item" @click="navigateTo('/pages/contract/change-event/index')">
          <image class="biz-icon" src="/static/bizicons/biz-change.png" mode="aspectFit" />
          <text class="biz-name">变更事件</text>
        </view>
        <view class="biz-item" @click="navigateTo('/pages/machine/work-log/index')">
          <image class="biz-icon" src="/static/bizicons/biz-machine.png" mode="aspectFit" />
          <text class="biz-name">机械台班</text>
        </view>
        <view class="biz-item" @click="navigateTo('/pages/labor/work-order/index')">
          <image class="biz-icon" src="/static/bizicons/biz-labor.png" mode="aspectFit" />
          <text class="biz-name">劳务点工</text>
        </view>
        <view class="biz-item" @click="navigateTo('/pages/site/watermark-camera/index')">
          <image class="biz-icon" src="/static/bizicons/biz-camera.png" mode="aspectFit" />
          <text class="biz-name">水印相机</text>
        </view>
        <view class="biz-item" @click="navigateTo('/pages/site/construction-log')">
          <image class="biz-icon" src="/static/bizicons/biz-log.png" mode="aspectFit" />
          <text class="biz-name">施工日志</text>
        </view>
        <view class="biz-item" @click="navigateTo('/pages/material/inbound')">
          <image class="biz-icon" src="/static/bizicons/biz-material.png" mode="aspectFit" />
          <text class="biz-name">材料入库</text>
        </view>
        <view class="biz-item" @click="navigateTo('/pages/site/quality-check')">
          <image class="biz-icon" src="/static/bizicons/biz-inspect.png" mode="aspectFit" />
          <text class="biz-name">质量检查</text>
        </view>
      </view>
    </view>

    <!-- 待办审批（P0 Req7：前 5 条，点击跳审批详情） -->
    <view class="section zw-card">
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
    <view class="section zw-card">
      <view class="section-title">我的项目</view>
      <view v-if="projectsFailed && !projects.length" class="failed-state">
        <text class="failed-tip">项目列表加载失败</text>
        <text class="retry-btn" @click="loadProjects">重试</text>
      </view>
      <scroll-view v-else scroll-y class="project-list" @scrolltolower="loadMore">
        <view class="project-item" v-for="item in projects" :key="item.id" @click="goArchive(item.id)">
          <view class="project-name">{{ item.projectName }}</view>
          <view class="project-info">
            <text class="project-status status-badge status-badge-info">{{ item.statusText }}</text>
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

function navigateTo(url: string) {
  uni.navigateTo({ url })
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
.section { padding: 24rpx; margin-bottom: 24rpx; } /* 底色/描边/直角由全局 .zw-card 提供 */
.section-title { font-size: 30rpx; font-weight: bold; color: var(--zw-text-primary); margin-bottom: 20rpx; }
.section-header-flex { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20rpx; }
.section-header-flex .section-title { margin-bottom: 0; }
.quick-link { display: flex; align-items: center; font-size: 24rpx; color: var(--zw-brand); font-weight: 500; min-height: 44px; padding: 0 8rpx; margin-right: -8rpx; box-sizing: border-box; } /* P0 触控达标：34rpx→min-height 44px */
.link-text { margin-right: 4rpx; }
.more-arrow { font-size: 28rpx; color: var(--zw-brand); }
.biz-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16rpx; text-align: center; }
.biz-item { display: flex; flex-direction: column; align-items: center; padding: 16rpx 8rpx; border-radius: var(--zw-radius-sm); background: var(--zw-bg-hover); }
.biz-icon {
  width: 56rpx;
  height: 56rpx;
}
.biz-name { font-size: 24rpx; color: var(--zw-text-primary); }
.kanban-cards { display: flex; gap: 12rpx; }
.kanban-card { flex: 1; padding: 20rpx 12rpx; border-radius: var(--zw-radius-xs); text-align: center; border: 1rpx solid transparent; }
.kanban-card.blue { background: var(--zw-info-light); border-color: rgba(43, 108, 176, 0.15); }
.kanban-card.green { background: var(--zw-success-light); border-color: rgba(31, 157, 85, 0.15); }
.kanban-card.orange { background: var(--zw-warning-light); border-color: rgba(247, 181, 0, 0.2); }
.kanban-card.red { background: var(--zw-danger-light); border-color: rgba(217, 45, 32, 0.15); }
.kanban-value { font-size: 36rpx; font-weight: bold; display: block; color: var(--zw-text-primary); font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.kanban-label { font-size: 22rpx; color: var(--zw-text-tertiary); margin-top: 4rpx; display: block; }
.project-list { max-height: 600rpx; }
.project-item { padding: 20rpx 0; border-bottom: 1rpx solid var(--zw-border-light); }
.project-name { font-size: 28rpx; color: var(--zw-text-primary); font-weight: 500; }
.project-info { display: flex; justify-content: space-between; align-items: center; margin-top: 8rpx; }
.project-status { font-size: 24rpx; } /* 徽章底色由全局 .status-badge-info 提供 */
.project-amount { font-size: 24rpx; color: var(--zw-text-tertiary); font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.empty { text-align: center; padding: 40rpx; color: var(--zw-text-quaternary); font-size: 26rpx; }
.loading-more { text-align: center; padding: 20rpx; color: var(--zw-text-tertiary); font-size: 24rpx; }
.failed-state { display: flex; align-items: center; justify-content: center; padding: 40rpx 0; }
.failed-tip { font-size: 26rpx; color: var(--zw-danger); }
.retry-btn { margin-left: 20rpx; padding: 0 24rpx; min-height: 44px; display: inline-flex; align-items: center; justify-content: center; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 24rpx; border-radius: var(--zw-radius-sm); } /* 橙底深字承重规则；P0 触控达标 44px */
.todo-item { display: flex; justify-content: space-between; align-items: center; padding: 20rpx 0; border-bottom: 1rpx solid var(--zw-border-light); }
.todo-item:last-child { border-bottom: none; }
.todo-title { font-size: 28rpx; color: var(--zw-text-primary); flex: 1; }
.todo-applicant { font-size: 24rpx; color: var(--zw-text-tertiary); margin-left: 16rpx; }
</style>
