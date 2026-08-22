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
      <view class="tab-item" :class="{ active: activeTab === 'initiated' }" @click="switchTab('initiated')">
        <text>我发起</text>
      </view>
    </view>

    <!-- 列表 -->
    <scroll-view scroll-y class="task-list" @scrolltolower="loadMore" refresher-enabled @refresherrefresh="onRefresh" :refresher-triggered="refreshing">
      <view class="task-item" v-for="item in tasks" :key="item.id || item.processInstanceId" @click="goDetail(item)">
        <view class="task-header">
          <view class="task-check" v-if="activeTab === 'todo'" @click.stop="toggleSelect(item)">
            <text class="checkbox" :class="{ checked: isSelected(item) }">{{ isSelected(item) ? '✓' : '' }}</text>
          </view>
          <text class="task-title">{{ item.processName || item.taskName }}</text>
          <text class="task-status" :class="item.status">{{ statusText(item) }}</text>
        </view>
        <view class="task-info">
          <text class="task-applicant" v-if="activeTab !== 'initiated'">申请人：{{ item.startUserName }}</text>
          <text class="task-applicant" v-else>发起时间</text>
          <text class="task-time">{{ item.createTime || item.startTime }}</text>
        </view>
        <view class="task-desc" v-if="item.businessTitle">
          <text>{{ item.businessTitle }}</text>
        </view>
      </view>
      <view class="empty" v-if="!tasks.length && !loading && !loadFailed">
        <text>暂无{{ tabLabel }}任务</text>
      </view>
      <view class="failed-state" v-if="loadFailed">
        <text class="failed-tip">任务列表加载失败</text>
        <text class="retry-btn" @click="loadData">重试</text>
      </view>
      <view class="loading-more" v-if="loading"><text>加载中...</text></view>
      <view class="no-more" v-if="!hasMore && tasks.length"><text>没有更多了</text></view>
    </scroll-view>

    <!-- 待办 tab 批量操作栏（P0 Req8：未勾选时禁用） -->
    <view class="batch-bar" v-if="activeTab === 'todo'">
      <view class="batch-check" @click="toggleSelectAll">
        <text class="checkbox" :class="{ checked: isAllSelected && tasks.length > 0 }">{{ isAllSelected && tasks.length > 0 ? '✓' : '' }}</text>
        <text class="batch-check-label">全选</text>
      </view>
      <button class="batch-btn" :disabled="!selectedIds.length || batchApproving" :loading="batchApproving" @click="handleBatchApprove">批量同意({{ selectedIds.length }})</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getTodoTasks, getDoneTasks, getMyInitiatedTasks, batchApproveTasks } from '@/api/common'

const activeTab = ref('todo')
const tasks = ref<any[]>([])
const loading = ref(false)
const refreshing = ref(false)
const loadFailed = ref(false)
const page = ref(1)
const hasMore = ref(true)
// P0 Req8：待办多选与批量同意
const selectedIds = ref<string[]>([])
const batchApproving = ref(false)

const isAllSelected = computed(() => tasks.value.length > 0 && selectedIds.value.length === tasks.value.length)

const tabLabel = computed(() => {
  if (activeTab.value === 'todo') return '待办'
  if (activeTab.value === 'done') return '已办'
  return '我发起的'
})

function statusText(item: any) {
  if (activeTab.value === 'initiated') {
    return item.status === 'RUNNING' ? '审批中' : '已完成'
  }
  return item.statusText || '待处理'
}

function switchTab(tab: string) {
  activeTab.value = tab
  page.value = 1
  hasMore.value = true
  tasks.value = []
  selectedIds.value = []
  loadData()
}

async function loadData() {
  if (loading.value) return
  loading.value = true
  loadFailed.value = false
  try {
    let api = getTodoTasks
    if (activeTab.value === 'done') api = getDoneTasks
    else if (activeTab.value === 'initiated') api = getMyInitiatedTasks
    const res: any = await api({ page: page.value, size: 15 })
    const records = res.data?.records || []
    if (page.value === 1) {
      tasks.value = records
    } else {
      tasks.value.push(...records)
    }
    hasMore.value = records.length >= 15
  } catch {
    // P0 Req8.5：禁止空 catch，失败展示失败态 + 重试入口（请求层已 toast 后端错误信息）
    loadFailed.value = true
  } finally {
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
  const taskId = item.id || item.taskId || ''
  uni.navigateTo({
    url: `/pages/approval/detail?taskId=${taskId}&processInstanceId=${item.processInstanceId}`
  })
}

// ── P0 Req8：批量同意 ──
function taskIdOf(item: any) {
  return String(item.id || item.taskId || '')
}

function isSelected(item: any) {
  return selectedIds.value.includes(taskIdOf(item))
}

function toggleSelect(item: any) {
  const id = taskIdOf(item)
  if (!id) return
  const idx = selectedIds.value.indexOf(id)
  if (idx >= 0) {
    selectedIds.value.splice(idx, 1)
  } else {
    selectedIds.value.push(id)
  }
}

function toggleSelectAll() {
  if (isAllSelected.value) {
    selectedIds.value = []
  } else {
    selectedIds.value = tasks.value.map(taskIdOf).filter(Boolean)
  }
}

function handleBatchApprove() {
  if (!selectedIds.value.length || batchApproving.value) return
  uni.showModal({
    title: '批量同意',
    content: `确定通过所选 ${selectedIds.value.length} 条审批吗？`,
    success: async (res) => {
      if (!res.confirm) return
      batchApproving.value = true
      try {
        await batchApproveTasks({ taskIds: selectedIds.value, comment: '' })
        uni.showToast({ title: '批量审批成功', icon: 'success' })
        selectedIds.value = []
      } catch {
        // P0 Req8.3：失败时请求层已 toast 后端返回的失败信息（含部分失败原因），
        // 后端事务回滚，随后刷新列表以最新状态为准
      } finally {
        batchApproving.value = false
        page.value = 1
        hasMore.value = true
        loadData()
      }
    }
  })
}

onShow(() => {
  page.value = 1
  hasMore.value = true
  selectedIds.value = []
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
.failed-state { display: flex; align-items: center; justify-content: center; padding: 60rpx 0; }
.failed-tip { font-size: 26rpx; color: #f56c6c; }
.retry-btn { margin-left: 20rpx; padding: 6rpx 24rpx; background: #409eff; color: #fff; font-size: 24rpx; border-radius: 8rpx; }
.task-check { margin-right: 16rpx; display: flex; align-items: center; }
.checkbox { display: inline-flex; align-items: center; justify-content: center; width: 36rpx; height: 36rpx; border: 2rpx solid #c0c4cc; border-radius: 6rpx; font-size: 24rpx; color: #fff; background: #fff; }
.checkbox.checked { background: #409eff; border-color: #409eff; }
.batch-bar { display: flex; align-items: center; justify-content: space-between; background: #fff; padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid #f0f0f0; }
.batch-check { display: flex; align-items: center; }
.batch-check-label { margin-left: 12rpx; font-size: 26rpx; color: #606266; }
.batch-btn { margin: 0; padding: 0 40rpx; height: 72rpx; line-height: 72rpx; background: #409eff; color: #fff; font-size: 28rpx; border-radius: 8rpx; }
.batch-btn[disabled] { background: #a0cfff; color: #fff; }
</style>
