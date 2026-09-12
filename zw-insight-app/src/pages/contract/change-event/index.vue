<template>
  <view class="change-event-page">
    <OfflineBanner />

    <!-- 项目选择 -->
    <view class="form-section">
      <view class="form-item" @click="openProjectPicker">
        <text class="form-label">所属项目</text>
        <view class="form-input picker">
          <text :class="{ placeholder: !projectName }">{{ projectName || '请选择项目' }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
      <!-- 状态筛选：现场只关心「我登记的」与「待我处理的」 -->
      <view class="form-item">
        <text class="form-label">状态筛选</text>
        <view class="status-chips">
          <text
            v-for="opt in STATUS_OPTIONS"
            :key="opt.value"
            class="chip"
            :class="{ on: statusFilter === opt.value }"
            @click="switchStatus(opt.value)"
          >{{ opt.label }}</text>
        </view>
      </view>
    </view>

    <!-- 列表 -->
    <scroll-view
      v-if="projectId"
      scroll-y
      class="event-list"
      refresher-enabled
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view
        v-for="item in events"
        :key="item.id"
        class="event-item"
        @click="goDetail(item)"
      >
        <view class="event-header">
          <text class="event-number">{{ item.eventNumber }}</text>
          <text class="event-status" :class="item.status">{{ statusText(item.status) }}</text>
        </view>
        <view class="event-title">
          <text>{{ item.title }}</text>
        </view>
        <view class="event-meta">
          <text class="meta-source">{{ sourceText(item.sourceType) }}</text>
          <text v-if="item.costDelta" class="meta-cost" :class="{ negative: Number(item.costDelta) < 0 }">
            {{ Number(item.costDelta) > 0 ? '+' : '' }}{{ toWan(item.costDelta) }}
          </text>
          <text class="meta-time">{{ formatDate(item.createdAt) }}</text>
        </view>
      </view>

      <view class="empty" v-if="!events.length && !loading && !loadFailed">
        <text>暂无变更事件，点击右下角登记</text>
      </view>
      <view class="failed-state" v-if="loadFailed">
        <text class="failed-tip">变更事件加载失败</text>
        <text class="retry-btn" @click="loadData(true)">重试</text>
      </view>
      <view class="loading-more" v-if="loading"><text>加载中...</text></view>
      <view class="no-more" v-if="!hasMore && events.length"><text>没有更多了</text></view>
    </scroll-view>

    <view class="empty" v-else>
      <text>请先选择项目</text>
    </view>

    <!-- 登记按钮：现场发现变更就地登记，不要求先回办公室 -->
    <button class="fab-create" v-if="projectId" @click="goCreate">＋ 登记变更</button>

    <!-- 项目选择弹窗（复用 construction-log 的既有交互） -->
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
import { ref } from 'vue'
import { onShow, onLoad } from '@dcloudio/uni-app'
import OfflineBanner from '@/components/OfflineBanner.vue'
import { getChangeEventPage } from '@/api/common'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'

/** 格式化金额（万元单位，显示 2 位小数） */
function formatAmount(value: number | string | null): string {
  if (value == null || value === '' || value === undefined) return '-'
  const num = typeof value === 'string' ? parseFloat(value) : Number(value)
  if (isNaN(num)) return '-万'
  return (num / 10000).toFixed(2) + '万'
}
import { toWan } from '@/utils/format'

/** 状态筛选项：与后端 ChangeEventStatus 枚举严格一致 */
const STATUS_OPTIONS = [
  { label: '全部', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '评估中', value: 'ASSESSING' },
  { label: '审批中', value: 'APPROVING' },
  { label: '已批准', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' }
]

const projectId = ref<number | null>(null)
const projectName = ref('')
const statusFilter = ref('')
const events = ref<any[]>([])
const loading = ref(false)
const refreshing = ref(false)
const loadFailed = ref(false)
const hasMore = ref(true)
const page = ref(1)
const PAGE_SIZE = 20

const showProjectPicker = ref(false)
const projects = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')

onLoad(async (options: any) => {
  // 支持从工作台/消息带项目上下文直达，减少现场一次选择
  if (options?.projectId) {
    projectId.value = Number(options.projectId)
    projectName.value = options.projectName ? decodeURIComponent(options.projectName) : ''
  }
  const res = await loadProjectList({ page: 1, size: 100 })
  projects.value = res.records
  projectEmptyTip.value = res.empty && res.fromCache ? NO_OFFLINE_DATA_TIP : '暂无项目'
  if (!projectName.value && projectId.value) {
    const hit = projects.value.find((p: any) => p.id === projectId.value)
    if (hit) projectName.value = hit.projectName
  }
})

// 登记/详情返回后刷新，保证列表与刚提交的数据一致
onShow(() => {
  if (projectId.value) loadData(true)
})

function openProjectPicker() {
  showProjectPicker.value = true
}

function selectProject(p: any) {
  projectId.value = p.id
  projectName.value = p.projectName
  showProjectPicker.value = false
  loadData(true)
}

function switchStatus(value: string) {
  statusFilter.value = value
  if (projectId.value) loadData(true)
}

/**
 * 加载列表。
 * @param reset true=回到第一页（切换项目/状态、下拉刷新、页面重现）
 */
async function loadData(reset = false) {
  if (!projectId.value) return
  if (reset) {
    page.value = 1
    hasMore.value = true
  }
  loading.value = true
  loadFailed.value = false
  try {
    const res: any = await getChangeEventPage({
      page: page.value,
      size: PAGE_SIZE,
      projectId: projectId.value,
      status: statusFilter.value || undefined
    })
    const records = res?.records || []
    events.value = reset ? records : events.value.concat(records)
    hasMore.value = records.length >= PAGE_SIZE
  } catch (e) {
    // 失败必须显式呈现并提供重试，不静默展示空列表（空列表会被误读为「没有变更」）
    loadFailed.value = true
    if (reset) events.value = []
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function onRefresh() {
  refreshing.value = true
  loadData(true)
}

function loadMore() {
  if (loading.value || !hasMore.value) return
  page.value += 1
  loadData(false)
}

function goDetail(item: any) {
  uni.navigateTo({ url: `/pages/contract/change-event/detail?id=${item.id}&projectId=${projectId.value}` })
}

function goCreate() {
  uni.navigateTo({ url: `/pages/contract/change-event/create?projectId=${projectId.value}&projectName=${encodeURIComponent(projectName.value || '')}` })
}

function statusText(status?: string) {
  return (STATUS_OPTIONS.find(o => o.value === status)?.label)
    || ({ CANCELLED: '已作废' } as Record<string, string>)[status || '']
    || status
    || '-'
}

function sourceText(type?: string) {
  const map: Record<string, string> = {
    FIELD_EVENT: '现场事件',
    DESIGN_CHANGE: '设计变更',
    OWNER_REQUEST: '业主指令',
    VARIATION_ORDER: '清单变更',
    OTHER: '其他'
  }
  return map[type || ''] || type || '-'
}

function formatDate(s?: string) {
  if (!s) return '-'
  return String(s).replace('T', ' ').slice(0, 16)
}
</script>

<style scoped>
.change-event-page { padding: 20rpx; min-height: 44px; padding-bottom: 160rpx; }

.form-section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light); border-radius: var(--zw-radius-lg); padding: 0 24rpx; margin-bottom: 20rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx; ;  border-bottom: 1rpx solid var(--zw-border-light); }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: var(--zw-text-primary); min-width: 160rpx; }
.form-input { flex: 1; font-size: 28rpx; color: var(--zw-text-primary); text-align: right; }
.form-input.picker { display: flex; align-items: center; justify-content: flex-end; }
.placeholder { color: var(--zw-text-quaternary); }
.arrow { margin-left: 8rpx; color: var(--zw-text-quaternary); font-size: 32rpx; }

.status-chips { flex: 1; display: flex; flex-wrap: wrap; gap: 12rpx; justify-content: flex-end; }
.chip { font-size: 24rpx; padding: 6rpx; ;  border-radius: var(--zw-radius-full); background: var(--zw-bg-tag); color: var(--zw-text-secondary); }
.chip.on { background: var(--zw-brand); color: var(--zw-on-primary); }

.event-list { max-height: calc(100vh - 360rpx); }
.event-item { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light); border-radius: var(--zw-radius-lg); padding: 24rpx; min-height: 44px; margin-bottom: 16rpx; }
.event-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.event-number { font-family: var(--zw-font-mono); font-size: 26rpx; color: var(--zw-brand); }
.event-status { font-size: 24rpx; padding: 4rpx; ;  border-radius: var(--zw-radius-sm); background: var(--zw-bg-tag); color: var(--zw-text-secondary); }
.event-status.APPROVED { background: var(--zw-success-light); color: var(--zw-success); }
.event-status.REJECTED { background: var(--zw-danger-light); color: var(--zw-danger); }
.event-status.APPROVING, .event-status.ASSESSING { background: var(--zw-warning-light); color: #a8790a; }
.event-title { font-size: 30rpx; color: var(--zw-text-primary); line-height: 1.5; margin-bottom: 12rpx; }
.event-meta { display: flex; align-items: center; gap: 16rpx; font-size: 24rpx; color: var(--zw-text-tertiary); }
.meta-cost { font-family: var(--zw-font-mono); color: var(--zw-text-secondary); }
.meta-cost.negative { color: var(--zw-success); }
.meta-time { margin-left: auto; }

.empty { padding: 80rpx; ;  text-align: center; font-size: 26rpx; color: var(--zw-text-quaternary); }
.failed-state { padding: 60rpx; ;  text-align: center; }
.failed-tip { display: block; font-size: 26rpx; color: var(--zw-danger); margin-bottom: 16rpx; }
.retry-btn { display: inline-block; font-size: 26rpx; color: var(--zw-brand); border: 1rpx solid var(--zw-brand); padding: 8rpx; ;  border-radius: var(--zw-radius-sm); }
.loading-more, .no-more { padding: 24rpx; ;  text-align: center; font-size: 24rpx; color: var(--zw-text-quaternary); }

.fab-create { position: fixed; right: 32rpx; bottom: calc(48rpx + env(safe-area-inset-bottom));  height: 88rpx; line-height: 88rpx; padding: 0 40rpx; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 30rpx; border-radius: var(--zw-radius-full); border: none; box-shadow: var(--zw-shadow-overlay); }

.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-lg) var(--zw-radius-lg) 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx; ;  border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 28rpx; ;  font-size: 28rpx; color: var(--zw-text-primary); border-bottom: 1rpx solid var(--zw-border-light); }
</style>
