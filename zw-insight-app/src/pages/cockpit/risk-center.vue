<template>
  <view class="risk-page">
    <OfflineBanner />

    <!-- 分级汇总（§11：数量 + 影响金额，点击即筛选） -->
    <view class="summary-row">
      <view v-for="s in summaryCards" :key="s.key" class="summary-card" :class="[s.cls, { on: severityFilter === s.value }]"
        @click="toggleSeverity(s.value)">
        <text class="s-icon">{{ s.icon }}</text>
        <text class="s-count">{{ s.count }}</text>
        <text class="s-label">{{ s.label }}</text>
        <text class="s-impact">{{ toWan(s.impact) }}</text>
      </view>
    </view>

    <!-- 处理状态筛选 -->
    <view class="filter-bar">
      <text v-for="opt in STATUS_OPTIONS" :key="opt.value" class="chip"
        :class="{ on: statusFilter === opt.value }" @click="switchStatus(opt.value)">{{ opt.label }}</text>
      <text class="scan-btn" @click="handleScan">{{ scanning ? '扫描中…' : '重新扫描' }}</text>
    </view>

    <!-- 风险列表 -->
    <scroll-view scroll-y class="risk-list" refresher-enabled :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh" @scrolltolower="loadMore">
      <view v-for="item in risks" :key="item.id" class="risk-item" @click="goDetail(item)">
        <view class="risk-head">
          <text class="risk-sev" :class="`sev-${item.severity.toLowerCase()}`">{{ severityIcon(item.severity) }}</text>
          <text class="risk-type">{{ riskTypeText(item.riskType) }}</text>
          <text class="risk-status" :class="`st-${item.handleStatus.toLowerCase()}`">
            {{ statusText(item.handleStatus) }}
          </text>
        </view>
        <view class="risk-title"><text>{{ item.title }}</text></view>
        <view class="risk-meta">
          <text class="meta-impact">影响 {{ toWan(item.impactAmount) }}</text>
          <text v-if="item.projectName" class="meta-project">{{ item.projectName }}</text>
          <text class="meta-owner">{{ item.ownerName || '责任人未指定' }}</text>
        </view>
        <view v-if="item.nextAction" class="risk-action">
          <text>建议：{{ item.nextAction }}</text>
        </view>
      </view>

      <view class="empty" v-if="!risks.length && !loading && !loadFailed">
        <text>暂无符合条件的风险</text>
      </view>
      <view class="failed-state" v-if="loadFailed">
        <text class="failed-tip">风险台账加载失败</text>
        <text class="retry-btn" @click="loadData(true)">重试</text>
      </view>
      <view class="loading-more" v-if="loading"><text>加载中...</text></view>
      <view class="no-more" v-if="!hasMore && risks.length"><text>没有更多了</text></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getRiskSummary, getRiskPage, scanRisks } from '@/api/common'
import { toWan } from '@/utils/format'
import OfflineBanner from '@/components/OfflineBanner.vue'

/** 风险类型中文（与后端 RiskRule.riskType() 值域一致） */
const RISK_TYPE_LABELS: Record<string, string> = {
  PROFIT_LOSS: '利润风险',
  BUDGET_OVER: '超预算',
  FUND_GAP: '资金缺口',
  RECEIVABLE_OVERDUE: '应收逾期',
  RETENTION_OVERDUE: '质保金逾期',
  WAGE_COMPLIANCE: '工资专户合规',
  ENTERTAINMENT_ANOMALY: '招待费异常'
}

const STATUS_OPTIONS = [
  { label: '待处理', value: 'OPEN' },
  { label: '处理中', value: 'PROCESSING' },
  { label: '已解决', value: 'RESOLVED' },
  { label: '全部', value: '' }
]

const risks = ref<any[]>([])
const loading = ref(false)
const loadFailed = ref(false)
const refreshing = ref(false)
const scanning = ref(false)
const hasMore = ref(true)
const page = ref(1)
const size = 20
const severityFilter = ref('')
const statusFilter = ref('OPEN')
const summary = ref<any>({
  redCount: 0, redImpact: 0, yellowCount: 0, yellowImpact: 0,
  infoCount: 0, infoImpact: 0, activeTotal: 0
})

const summaryCards = computed(() => [
  { key: 'red', icon: '🔴', label: '严重', value: 'RED', cls: 'card-red', count: summary.value.redCount || 0, impact: summary.value.redImpact || 0 },
  { key: 'yellow', icon: '🟡', label: '关注', value: 'YELLOW', cls: 'card-yellow', count: summary.value.yellowCount || 0, impact: summary.value.yellowImpact || 0 },
  { key: 'info', icon: '⚪', label: '提醒', value: 'INFO', cls: 'card-info', count: summary.value.infoCount || 0, impact: summary.value.infoImpact || 0 }
])

function riskTypeText(type: string) {
  return RISK_TYPE_LABELS[type] || type
}

function statusText(status: string) {
  return { OPEN: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', IGNORED: '已忽略' }[status] || status
}

function severityIcon(severity: string) {
  return { RED: '🔴', YELLOW: '🟡', INFO: '⚪' }[severity] || '⚪'
}

async function loadSummary() {
  try {
    const res: any = await getRiskSummary()
    summary.value = res.data || summary.value
  } catch {
    // 汇总失败不阻断列表（列表自身有独立失败态与重试）
    loadFailed.value = true
  }
}

async function loadData(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 1
    hasMore.value = true
  }
  if (!hasMore.value) return
  loading.value = true
  loadFailed.value = false
  try {
    const res: any = await getRiskPage({
      page: page.value,
      size,
      severity: severityFilter.value || undefined,
      handleStatus: statusFilter.value || undefined
    })
    const records = res.data?.records || []
    risks.value = reset ? records : [...risks.value, ...records]
    const total = Number(res.data?.total) || 0
    hasMore.value = risks.value.length < total
    if (hasMore.value) page.value += 1
  } catch {
    loadFailed.value = true
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function loadMore() {
  if (hasMore.value && !loading.value) loadData()
}

async function onRefresh() {
  refreshing.value = true
  try {
    await Promise.all([loadSummary(), loadData(true)])
  } finally {
    // 兜底复位：loadData 在 loading 中会提前 return，不经过其内部 finally，
    // 若不在此处复位会导致下拉刷新圈永久卡住（2026-09-23 审查发现的缺陷）
    refreshing.value = false
  }
}

function toggleSeverity(value: string) {
  severityFilter.value = severityFilter.value === value ? '' : value
  loadData(true)
}

function switchStatus(value: string) {
  statusFilter.value = value
  loadData(true)
}

/** 主动扫描：定时任务每日 02:00 执行，移动端可手动触发；失败规则如实提示不静默 */
async function handleScan() {
  if (scanning.value) return
  scanning.value = true
  uni.showLoading({ title: '扫描中…' })
  try {
    const res: any = await scanRisks()
    const d = res.data || {}
    uni.hideLoading()
    if (d.failedRules?.length) {
      uni.showToast({ title: `${d.failedRules.length} 条规则执行失败`, icon: 'none', duration: 3000 })
    } else {
      uni.showToast({ title: `命中 ${d.findings ?? 0} 项`, icon: 'none' })
    }
    await Promise.all([loadSummary(), loadData(true)])
  } catch {
    uni.hideLoading()
    // request 拦截器已 toast 具体错误，此处不重复提示
  } finally {
    scanning.value = false
  }
}

function goDetail(item: any) {
  uni.navigateTo({ url: `/pages/cockpit/risk-detail?id=${item.id}` })
}

onMounted(() => {
  loadSummary()
  loadData(true)
})

// 详情页处理风险后返回，需刷新列表与汇总（否则状态停留在旧值）
onShow(() => {
  if (risks.value.length) {
    loadSummary()
    loadData(true)
  }
})
</script>

<style scoped>
.risk-page { padding: 20rpx; padding-bottom: 40rpx; }

.summary-row { display: flex; gap: 16rpx; margin-bottom: 20rpx; }
.summary-card {
  flex: 1; display: flex; flex-direction: column; align-items: center; gap: 6rpx;
  padding: 20rpx 12rpx; background: var(--zw-bg-card);
  border: 2rpx solid var(--zw-border-light); border-radius: var(--zw-radius-lg);
}
.summary-card.on { border-color: var(--zw-brand); background: var(--zw-bg-hover); }
.card-red { border-left: 6rpx solid var(--zw-danger); }
.card-yellow { border-left: 6rpx solid var(--zw-warning); }
.card-info { border-left: 6rpx solid var(--zw-text-quaternary); }
.s-icon { font-size: 28rpx; }
.s-count { font-size: 40rpx; font-weight: 600; color: var(--zw-text-primary); }
.s-label { font-size: 24rpx; color: var(--zw-text-tertiary); }
.s-impact { font-size: 22rpx; color: var(--zw-text-quaternary); }

.filter-bar {
  display: flex; align-items: center; flex-wrap: wrap; gap: 12rpx;
  margin-bottom: 20rpx;
}
.chip {
  padding: 8rpx 20rpx; font-size: 24rpx; color: var(--zw-text-secondary);
  background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light);
  border-radius: 999rpx;
}
.chip.on { color: #fff; background: var(--zw-brand); border-color: var(--zw-brand); }
.scan-btn { margin-left: auto; font-size: 24rpx; color: var(--zw-brand); }

.risk-list { max-height: 100vh; }
.risk-item {
  background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light);
  border-radius: var(--zw-radius-lg); padding: 20rpx; margin-bottom: 16rpx;
}
.risk-head { display: flex; align-items: center; gap: 12rpx; margin-bottom: 10rpx; }
.risk-sev { font-size: 24rpx; }
.risk-type { font-size: 22rpx; color: var(--zw-text-tertiary); }
.risk-status { margin-left: auto; font-size: 22rpx; padding: 2rpx 12rpx; border-radius: var(--zw-radius-sm); }
.st-open { color: var(--zw-danger); background: color-mix(in srgb, var(--zw-danger) 10%, transparent); }
.st-processing { color: var(--zw-warning); background: color-mix(in srgb, var(--zw-warning) 12%, transparent); }
.st-resolved { color: var(--zw-success); background: color-mix(in srgb, var(--zw-success) 10%, transparent); }
.st-ignored { color: var(--zw-text-quaternary); background: var(--zw-bg-hover); }

.risk-title { font-size: 28rpx; color: var(--zw-text-primary); line-height: 1.5; margin-bottom: 10rpx; }
.risk-meta { display: flex; flex-wrap: wrap; gap: 16rpx; font-size: 22rpx; color: var(--zw-text-tertiary); }
.meta-impact { color: var(--zw-danger); font-weight: 500; }
.risk-action {
  margin-top: 10rpx; padding-top: 10rpx; border-top: 1rpx dashed var(--zw-border-light);
  font-size: 22rpx; color: var(--zw-text-quaternary); line-height: 1.5;
}

.empty, .loading-more, .no-more {
  text-align: center; padding: 40rpx 0; font-size: 24rpx; color: var(--zw-text-quaternary);
}
.failed-state { display: flex; flex-direction: column; align-items: center; gap: 16rpx; padding: 40rpx 0; }
.failed-tip { font-size: 26rpx; color: var(--zw-danger); }
.retry-btn { font-size: 26rpx; color: var(--zw-brand); }
</style>
