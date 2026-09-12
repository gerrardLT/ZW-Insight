<template>
  <view class="cost-control-page">
    <OfflineBanner />

    <!-- 项目选择器 -->
    <view class="project-header" @click="showProjectPicker = true">
      <view class="project-selector">
        <text class="selector-label">监控项目：</text>
        <text class="selector-value">{{ projectName || '请选择项目' }}</text>
        <text class="arrow">›</text>
      </view>
    </view>

    <!-- 加载中/失败/数据展示 -->
    <view v-if="loading" class="loading-state"><text>加载成本数据中...</text></view>

    <view v-else-if="loadFailed" class="failed-state">
      <text class="failed-tip">成本数据加载失败</text>
      <text class="retry-btn" @click="loadData">重试</text>
    </view>

    <scroll-view
      v-else
      scroll-y
      class="scroll-area"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <!-- 汇总健康度卡片 -->
      <view class="summary-card zw-card">
        <view class="summary-head">
          <text class="summary-title">项目成本控制总览</text>
          <text class="health-badge" :class="isOverBudget ? 'danger' : 'safe'">
            {{ isOverBudget ? '⚠️ 存在超支风险' : '✅ 预算受控' }}
          </text>
        </view>

        <!-- 6 格核心指标 (万元) -->
        <view class="metrics-grid">
          <view class="metric-block">
            <text class="metric-label">基准预算</text>
            <text class="metric-value">{{ formatWan(summary.baselineBudget) }}万</text>
          </view>
          <view class="metric-block">
            <text class="metric-label">当前预算</text>
            <text class="metric-value font-bold">{{ formatWan(summary.currentBudget) }}万</text>
          </view>
          <view class="metric-block">
            <text class="metric-label">合同签约</text>
            <text class="metric-value">{{ formatWan(summary.commitmentCost) }}万</text>
          </view>
          <view class="metric-block">
            <text class="metric-label">实际发生成本</text>
            <text class="metric-value highlight">{{ formatWan(summary.actualCost) }}万</text>
          </view>
          <view class="metric-block">
            <text class="metric-label">预测总成本</text>
            <text class="metric-value">{{ formatWan(summary.forecastCost) }}万</text>
          </view>
          <view class="metric-block">
            <text class="metric-label">成本偏差(CV)</text>
            <text class="metric-value" :class="cvClass">{{ formatWan(summary.variance) }}万</text>
          </view>
        </view>

        <!-- 进度条与执行率 -->
        <view class="progress-section">
          <view class="progress-info">
            <text class="progress-label">预算执行消耗率</text>
            <text class="progress-pct">{{ execRate }}%</text>
          </view>
          <view class="progress-track">
            <view class="progress-fill" :style="{ width: Math.min(execRate, 100) + '%' }" :class="{ over: execRate > 100 }"></view>
          </view>
        </view>
      </view>

      <!-- CBS 费用科目拆解 -->
      <view class="section-title">CBS 费用科目明细</view>
      <view class="category-cards" v-if="categoryList.length">
        <view class="cat-card zw-card" v-for="(cat, idx) in categoryList" :key="idx">
          <view class="cat-header">
            <text class="cat-name">{{ cat.categoryName || cat.category }}</text>
            <text class="cat-status" :class="Number(cat.variance) < 0 ? 'bad' : 'good'">
              {{ Number(cat.variance) < 0 ? '超支 ' + formatWan(Math.abs(cat.variance)) + '万' : '节约 ' + formatWan(cat.variance) + '万' }}
            </text>
          </view>
          <view class="cat-body">
            <view class="cat-row">
              <text class="cat-col-label">当前预算：</text>
              <text class="cat-col-val">{{ formatWan(cat.currentBudget) }}万元</text>
            </view>
            <view class="cat-row">
              <text class="cat-col-label">实际发生：</text>
              <text class="cat-col-val font-bold">{{ formatWan(cat.actualCost) }}万元</text>
            </view>
            <view class="cat-row">
              <text class="cat-col-label">签约承诺：</text>
              <text class="cat-col-val">{{ formatWan(cat.commitmentCost) }}万元</text>
            </view>
          </view>
        </view>
      </view>
      <view class="empty-state" v-else><text>当前项目暂无 CBS 科目数据</text></view>
    </scroll-view>

    <!-- 项目选择弹窗 -->
    <view class="picker-mask" v-if="showProjectPicker" @click="showProjectPicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text @click="showProjectPicker = false">取消</text>
          <text class="picker-title">选择监控项目</text>
          <text></text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view class="picker-item" v-for="p in projects" :key="p.id" @click="selectProject(p)">
            <text>{{ p.projectName }}</text>
          </view>
          <view class="empty" v-if="!projects.length"><text>暂无项目</text></view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getProjectCostControl } from '@/api/common'
import { loadProjectList } from '@/utils/offlineData'
import OfflineBanner from '@/components/OfflineBanner.vue'

const showProjectPicker = ref(false)
const projects = ref<any[]>([])
const projectId = ref<number | null>(null)
const projectName = ref('')

const loading = ref(false)
const refreshing = ref(false)
const loadFailed = ref(false)

const summary = ref<any>({})
const categoryList = ref<any[]>([])

function formatWan(val: any) {
  if (val == null || val === '') return '0.00'
  const n = Number(val)
  if (!Number.isFinite(n)) return '0.00'
  return (n / 10000).toFixed(2)
}

const execRate = computed(() => {
  const cur = Number(summary.value.currentBudget || 0)
  const act = Number(summary.value.actualCost || 0)
  if (cur <= 0) return 0
  return Number(((act / cur) * 100).toFixed(1))
})

const isOverBudget = computed(() => {
  return Number(summary.value.variance || 0) < 0 || execRate.value > 100
})

const cvClass = computed(() => {
  const v = Number(summary.value.variance || 0)
  if (v < 0) return 'danger'
  if (v > 0) return 'safe'
  return ''
})

function selectProject(p: any) {
  projectId.value = p.id
  projectName.value = p.projectName
  showProjectPicker.value = false
  loadData()
}

async function loadData() {
  if (!projectId.value) return
  loading.value = true
  loadFailed.value = false
  try {
    const res: any = await getProjectCostControl(projectId.value)
    if (res?.data) {
      summary.value = res.data.summary || res.data
      categoryList.value = res.data.categorySummaries || res.data.categories || []
    }
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function onRefresh() {
  refreshing.value = true
  await loadData()
}

onMounted(async () => {
  try {
    const res = await loadProjectList({ page: 1, size: 50 })
    projects.value = res.records || []
    if (projects.value.length > 0) {
      projectId.value = projects.value[0].id
      projectName.value = projects.value[0].projectName
      await loadData()
    }
  } catch {}
})
</script>

<style scoped>
.cost-control-page { display: flex; flex-direction: column; height: 100vh; background: var(--zw-bg-page); padding: 20rpx; min-height: 44px; box-sizing: border-box; }
.project-header { margin-bottom: 20rpx; }
.project-selector { display: flex; align-items: center; justify-content: space-between; background: var(--zw-bg-card); padding: 20rpx 24rpx; min-height: 44px; box-sizing: border-box; border-radius: var(--zw-radius-xs); border: 1rpx solid var(--zw-border); }
.selector-label { font-size: 26rpx; color: var(--zw-text-tertiary); }
.selector-value { flex: 1; text-align: right; font-size: 28rpx; color: var(--zw-text-primary); font-weight: 500; }
.arrow { margin-left: 8rpx; color: var(--zw-text-quaternary); font-size: 32rpx; }
.scroll-area { flex: 1; overflow-y: auto; }
.summary-card { background: var(--zw-bg-card); padding: 24rpx; min-height: 44px; border-radius: var(--zw-radius-xs); border: 1rpx solid var(--zw-border); margin-bottom: 24rpx; }
.summary-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20rpx; padding-bottom: 16rpx; border-bottom: 1rpx solid var(--zw-border-light); }
.summary-title { font-size: 30rpx; font-weight: bold; color: var(--zw-text-primary); }
.health-badge { font-size: 22rpx; padding: 4rpx 14rpx; border-radius: var(--zw-radius-xs); }
.health-badge.safe { background: var(--zw-success-light); color: var(--zw-success); }
.health-badge.danger { background: var(--zw-danger-light); color: var(--zw-danger); }
.metrics-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16rpx; text-align: center; }
.metric-block { display: flex; flex-direction: column; padding: 12rpx; }
.metric-label { font-size: 22rpx; color: var(--zw-text-tertiary); margin-bottom: 6rpx; }
.metric-value { font-size: 28rpx; font-weight: 500; color: var(--zw-text-primary); font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.metric-value.font-bold { font-weight: bold; }
.metric-value.highlight { color: var(--zw-brand); font-weight: bold; }
.metric-value.danger { color: var(--zw-danger); }
.metric-value.safe { color: var(--zw-success); }
.progress-section { margin-top: 24rpx; padding-top: 16rpx; border-top: 1rpx solid var(--zw-border-light); }
.progress-info { display: flex; justify-content: space-between; font-size: 24rpx; margin-bottom: 8rpx; }
.progress-label { color: var(--zw-text-secondary); }
.progress-pct { font-weight: bold; font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; color: var(--zw-brand); }
.progress-track { height: 16rpx; background: var(--zw-bg-hover); border-radius: 2rpx; overflow: hidden; }
.progress-fill { height: 100%; width: 100%; background: var(--zw-brand); border-radius: 2rpx; transform-origin: left center; transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1); will-change: transform; }
.progress-fill.over { background: var(--zw-danger); }
.section-title { font-size: 28rpx; font-weight: bold; color: var(--zw-text-primary); margin-bottom: 16rpx; }
.cat-card { background: var(--zw-bg-card); padding: 20rpx 24rpx; min-height: 44px; box-sizing: border-box; border-radius: var(--zw-radius-xs); border: 1rpx solid var(--zw-border); margin-bottom: 16rpx; }
.cat-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.cat-name { font-size: 28rpx; font-weight: bold; color: var(--zw-text-primary); }
.cat-status { font-size: 22rpx; font-weight: 500; }
.cat-status.good { color: var(--zw-success); }
.cat-status.bad { color: var(--zw-danger); }
.cat-body { display: flex; justify-content: space-between; font-size: 24rpx; color: var(--zw-text-secondary); }
.cat-col-label { color: var(--zw-text-tertiary); }
.cat-col-val { font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.empty-state { text-align: center; padding: 60rpx; color: var(--zw-text-quaternary); font-size: 26rpx; }
.loading-state, .failed-state { text-align: center; padding: 100rpx; color: var(--zw-text-tertiary); font-size: 28rpx; }
.failed-tip { color: var(--zw-danger); margin-right: 16rpx; }
.retry-btn { padding: 6rpx 20rpx; min-height: 44px; display: inline-flex; align-items: center; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 24rpx; border-radius: var(--zw-radius-xs); }
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-md) var(--zw-radius-md) 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx; min-height: 44px; border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx; min-height: 44px; border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; }
</style>
