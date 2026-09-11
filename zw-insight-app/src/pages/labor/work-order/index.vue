<template>
  <view class="work-order-page">
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
      class="order-list"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view class="order-card" v-for="item in orders" :key="item.id">
        <view class="card-header">
          <view class="worker-info">
            <text class="worker-name">{{ item.workerName }}</text>
            <text class="type-tag" :class="item.orderType">{{ item.orderType === 'TEMPORARY' ? '临时点工' : '固定班组' }}</text>
          </view>
          <text class="order-amount">¥{{ item.totalAmount || 0 }}</text>
        </view>
        <view class="card-body">
          <view class="detail-line">
            <text class="detail-label">出勤工时：</text>
            <text class="detail-val">{{ item.hours }} 小时 <text class="ot-text" v-if="item.overtime">(加班 {{ item.overtime }}h)</text></text>
          </view>
          <view class="detail-line">
            <text class="detail-label">时薪费率：</text>
            <text class="detail-val">¥{{ item.hourlyRate }}/h</text>
          </view>
          <view class="meta-line">
            <text class="work-date">📅 {{ item.workDate }}</text>
            <text class="status-badge" :class="item.status">{{ item.status === 'APPROVED' ? '已确认' : '草稿' }}</text>
          </view>
        </view>
      </view>

      <view class="empty-state" v-if="!orders.length && !loading && !loadFailed">
        <text class="empty-text">暂无点工签认记录，点击右下角签认</text>
      </view>

      <view class="failed-state" v-if="loadFailed">
        <text class="failed-tip">点工记录加载失败</text>
        <text class="retry-btn" @click="loadData(true)">重试</text>
      </view>

      <view class="loading-more" v-if="loading"><text>加载中...</text></view>
      <view class="no-more" v-if="!hasMore && orders.length"><text>没有更多了</text></view>
    </scroll-view>

    <!-- 右下角悬浮填报按钮 (FAB) -->
    <button class="fab-btn" @click="goCreate">＋ 点工签认</button>

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
import { getWorkOrderPage } from '@/api/common'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import OfflineBanner from '@/components/OfflineBanner.vue'

const showProjectPicker = ref(false)
const projects = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')
const projectId = ref<number | null>(null)
const projectName = ref('')

const orders = ref<any[]>([])
const loading = ref(false)
const refreshing = ref(false)
const loadFailed = ref(false)
const page = ref(1)
const hasMore = ref(true)

function goCreate() {
  uni.navigateTo({ url: '/pages/labor/work-order/create' })
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
    orders.value = []
  }
  if (loading.value) return
  loading.value = true
  loadFailed.value = false

  try {
    const res: any = await getWorkOrderPage({
      page: page.value,
      size: 15,
      projectId: projectId.value || undefined
    })
    const records = res?.data?.records || []
    if (page.value === 1) {
      orders.value = records
    } else {
      orders.value.push(...records)
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
.work-order-page { display: flex; flex-direction: column; height: 100vh; background: var(--zw-bg-page); padding: 20rpx; position: relative; box-sizing: border-box; }
.project-header { margin-bottom: 20rpx; }
.project-selector { display: flex; align-items: center; justify-content: space-between; background: var(--zw-bg-card); padding: 20rpx 24rpx; border-radius: var(--zw-radius-xs); border: 1rpx solid var(--zw-border); }
.selector-label { font-size: 26rpx; color: var(--zw-text-tertiary); }
.selector-value { flex: 1; text-align: right; font-size: 28rpx; color: var(--zw-text-primary); font-weight: 500; }
.arrow { margin-left: 8rpx; color: var(--zw-text-quaternary); font-size: 32rpx; }
.order-list { flex: 1; overflow-y: auto; padding-bottom: 120rpx; }
.order-card { background: var(--zw-bg-card); border-radius: var(--zw-radius-sm); border: 1rpx solid var(--zw-border); padding: 24rpx; margin-bottom: 20rpx; box-shadow: var(--zw-shadow-card); }
.card-header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 16rpx; border-bottom: 1rpx solid var(--zw-border-light); }
.worker-info { display: flex; align-items: center; }
.worker-name { font-size: 30rpx; font-weight: bold; color: var(--zw-text-primary); margin-right: 12rpx; }
.type-tag { font-size: 20rpx; padding: 2rpx 10rpx; border-radius: var(--zw-radius-xs); }
.type-tag.TEMPORARY { background: var(--zw-warning-light); color: var(--zw-warning); }
.type-tag.FIXED { background: var(--zw-info-light); color: var(--zw-info); }
.order-amount { font-size: 32rpx; font-weight: bold; color: var(--zw-brand); font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.card-body { padding-top: 16rpx; }
.detail-line { display: flex; justify-content: space-between; font-size: 26rpx; color: var(--zw-text-secondary); margin-bottom: 8rpx; font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.ot-text { color: var(--zw-brand); font-size: 24rpx; margin-left: 8rpx; }
.meta-line { display: flex; justify-content: space-between; align-items: center; margin-top: 12rpx; font-size: 24rpx; color: var(--zw-text-tertiary); }
.status-badge { font-size: 22rpx; padding: 2rpx 12rpx; border-radius: var(--zw-radius-pill); }
.status-badge.APPROVED { background: var(--zw-success-light); color: var(--zw-success); }
.status-badge.DRAFT { background: var(--zw-bg-hover); color: var(--zw-text-tertiary); }
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
