<template>
  <view class="work-log-page">
    <OfflineBanner />

    <!-- 项目选择器 -->
    <view class="project-header" @click="showProjectPicker = true">
      <view class="project-selector">
        <text class="selector-label">当前项目：</text>
        <text class="selector-value">{{ projectName || '请选择项目' }}</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <!-- 列表展示 -->
    <scroll-view
      scroll-y
      class="log-list"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view class="log-card" v-for="item in logs" :key="item.id">
        <view class="card-header">
          <text class="machine-name">{{ item.machineName || ('设备 #' + item.machineId) }}</text>
          <text class="settle-badge" :class="item.settlementStatus === 'SETTLED' ? 'settled' : 'unsettled'">
            {{ item.settlementStatus === 'SETTLED' ? '已结算' : '未结算' }}
          </text>
        </view>
        <view class="card-body">
          <view class="metric-row">
            <view class="metric-item">
              <text class="metric-label">台班数</text>
              <text class="metric-val">{{ item.shiftCount }}</text>
            </view>
            <view class="metric-item">
              <text class="metric-label">完成工程量</text>
              <text class="metric-val">{{ item.workQuantity || 0 }}</text>
            </view>
            <view class="metric-item">
              <text class="metric-label">油耗(L)</text>
              <text class="metric-val">{{ item.oilConsumption || 0 }}</text>
            </view>
          </view>
          <view class="meta-row">
            <text class="work-date">📅 {{ item.workDate }}</text>
            <text class="work-remark" v-if="item.remark">{{ item.remark }}</text>
          </view>
        </view>
      </view>

      <view class="empty-state" v-if="!logs.length && !loading && !loadFailed">
        <text class="empty-text">暂无台班记录，点击右下角填报</text>
      </view>

      <view class="failed-state" v-if="loadFailed">
        <text class="failed-tip">台班记录加载失败</text>
        <text class="retry-btn" @click="loadData(true)">重试</text>
      </view>

      <view class="loading-more" v-if="loading"><text>加载中...</text></view>
      <view class="no-more" v-if="!hasMore && logs.length"><text>没有更多了</text></view>
    </scroll-view>

    <!-- 右下角悬浮填报按钮 (FAB) -->
    <button class="fab-btn" @click="goCreate">＋ 填报台班</button>

    <!-- 项目选择弹窗 -->
    <view class="picker-mask" v-if="showProjectPicker" @click="showProjectPicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text @click="showProjectPicker = false">取消</text>
          <text class="picker-title">选择项目</text>
          <text></text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view class="picker-item" v-for="p in projects" :key="p.id" @click="selectProject(p)">
            <text>{{ p.projectName }}</text>
          </view>
          <view class="empty" v-if="!projects.length"><text>{{ projectEmptyTip }}</text></view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getMachineWorkLogPage } from '@/api/common'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import OfflineBanner from '@/components/OfflineBanner.vue'

const showProjectPicker = ref(false)
const projects = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')
const projectId = ref<number | null>(null)
const projectName = ref('')

const logs = ref<any[]>([])
const loading = ref(false)
const refreshing = ref(false)
const loadFailed = ref(false)
const page = ref(1)
const hasMore = ref(true)

function goCreate() {
  uni.navigateTo({ url: '/pages/machine/work-log/create' })
}

function selectProject(p: any) {
  projectId.value = p.id
  projectName.value = p.projectName
  showProjectPicker.value = false
  loadData(true)
}

async function loadData(reset = false) {
  if (reset) {
    page.value = 1
    hasMore.value = true
    logs.value = []
  }
  if (loading.value) return
  loading.value = true
  loadFailed.value = false

  try {
    const res: any = await getMachineWorkLogPage({
      page: page.value,
      size: 15,
      projectId: projectId.value || undefined
    })
    const records = res?.data?.records || []
    if (page.value === 1) {
      logs.value = records
    } else {
      logs.value.push(...records)
    }
    hasMore.value = records.length >= 15
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  await loadData(true)
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  page.value++
  loadData(false)
}

async function initPage() {
  try {
    const res = await loadProjectList({ page: 1, size: 50 })
    projects.value = res.records || []
    projectEmptyTip.value = res.empty && res.fromCache ? NO_OFFLINE_DATA_TIP : '暂无项目'
    if (!projectId.value && projects.value.length > 0) {
      projectId.value = projects.value[0].id
      projectName.value = projects.value[0].projectName
    }
  } catch {}
  loadData(true)
}

onMounted(() => {
  initPage()
})

onShow(() => {
  initPage()
})
</script>

<style scoped>
.work-log-page { display: flex; flex-direction: column; height: 100vh; background: var(--zw-bg-page); padding: 20rpx; position: relative; box-sizing: border-box; }
.project-header { margin-bottom: 20rpx; }
.project-selector { display: flex; align-items: center; justify-content: space-between; background: var(--zw-bg-card); padding: 20rpx 24rpx; border-radius: var(--zw-radius-xs); border: 1rpx solid var(--zw-border); }
.selector-label { font-size: 26rpx; color: var(--zw-text-tertiary); }
.selector-value { flex: 1; text-align: right; font-size: 28rpx; color: var(--zw-text-primary); font-weight: 500; }
.arrow { margin-left: 8rpx; color: var(--zw-text-quaternary); font-size: 32rpx; }
.log-list { flex: 1; overflow-y: auto; padding-bottom: 120rpx; }
.log-card { background: var(--zw-bg-card); border-radius: var(--zw-radius-sm); border: 1rpx solid var(--zw-border); padding: 24rpx; margin-bottom: 20rpx; box-shadow: var(--zw-shadow-card); }
.card-header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 16rpx; border-bottom: 1rpx solid var(--zw-border-light); }
.machine-name { font-size: 30rpx; font-weight: bold; color: var(--zw-text-primary); }
.settle-badge { font-size: 22rpx; padding: 4rpx 14rpx; border-radius: var(--zw-radius-pill); }
.settle-badge.unsettled { background: var(--zw-warning-light); color: var(--zw-warning); }
.settle-badge.settled { background: var(--zw-success-light); color: var(--zw-success); }
.card-body { padding-top: 16rpx; }
.metric-row { display: flex; justify-content: space-around; margin-bottom: 16rpx; }
.metric-item { display: flex; flex-direction: column; align-items: center; }
.metric-label { font-size: 22rpx; color: var(--zw-text-tertiary); margin-bottom: 4rpx; }
.metric-val { font-size: 30rpx; font-weight: bold; color: var(--zw-text-primary); font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.meta-row { display: flex; justify-content: space-between; font-size: 24rpx; color: var(--zw-text-secondary); }
.work-remark { color: var(--zw-text-tertiary); max-width: 50%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.empty-state { text-align: center; padding: 80rpx 0; color: var(--zw-text-quaternary); font-size: 28rpx; }
.failed-state { display: flex; align-items: center; justify-content: center; padding: 40rpx 0; }
.failed-tip { font-size: 26rpx; color: var(--zw-danger); }
.retry-btn { margin-left: 20rpx; padding: 6rpx 24rpx; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 24rpx; border-radius: var(--zw-radius-xs); }
.loading-more, .no-more { text-align: center; padding: 20rpx; color: var(--zw-text-tertiary); font-size: 24rpx; }
.fab-btn { position: fixed; right: 32rpx; bottom: 48rpx; height: 80rpx; line-height: 80rpx; padding: 0 36rpx; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 28rpx; font-weight: 600; border-radius: var(--zw-radius-xs); box-shadow: 0 4rpx 14rpx rgba(20,22,26,0.15); border: none; z-index: 10; }
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-xs) var(--zw-radius-xs) 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx 32rpx; border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx 32rpx; border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; }
</style>
