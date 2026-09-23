<template>
  <view class="workbench-page">
    <!-- 项目概览（Calm 化：白卡彩色大数字，去浅彩底积木感） -->
    <ZwiSectionCard title="项目看板">
      <template #action>
        <view class="quick-link" @click="navigateTo('/pages/project/cost-control/index')">
          <text class="link-text">成本监控 </text>
          <text class="more-arrow">›</text>
        </view>
      </template>
      <view v-if="overviewFailed" class="failed-state">
        <text class="failed-tip">项目看板加载失败</text>
        <text class="retry-btn" @click="loadOverview">重试</text>
      </view>
      <view v-else class="kanban-cards">
        <ZwiStatCard :value="overview.inProgressCount || 0" label="进行中" tone="info" plain />
        <ZwiStatCard :value="overview.completedCount || 0" label="已完工" tone="success" plain />
        <ZwiStatCard :value="overview.pendingSettleCount || 0" label="待结算" tone="warning" plain />
        <ZwiStatCard :value="todoCount" label="待审批" tone="danger" plain />
      </view>
    </ZwiSectionCard>

    <!-- 常用功能入口（含变更事件等核心现场业务） -->
    <ZwiSectionCard title="常用现场业务">
      <view class="biz-grid">
        <view class="biz-item" v-for="biz in BIZ_ENTRIES" :key="biz.path" @click="navigateTo(biz.path)">
          <view class="biz-icon-wrap">
            <image class="biz-icon" :src="biz.icon" mode="aspectFit" />
          </view>
          <text class="biz-name">{{ biz.name }}</text>
        </view>
      </view>
    </ZwiSectionCard>

    <!-- 待办审批（P0 Req7：前 5 条，点击跳审批详情） -->
    <ZwiSectionCard title="待办审批">
      <template #title>
        <view class="msg-title-row">
          <text class="section-title">待办审批</text>
          <text class="badge" v-if="todoCount">{{ todoCount }}</text>
        </view>
      </template>
      <view v-if="todoFailed" class="failed-state">
        <text class="failed-tip">待办任务加载失败</text>
        <text class="retry-btn" @click="refreshTodo">重试</text>
      </view>
      <template v-else>
        <ZwiCell
          v-for="item in todoTasks"
          :key="item.id || item.processInstanceId"
          class="todo-item"
          :title="item.processName || item.taskName"
          :desc="`申请人：${item.startUserName}`"
          clickable
          arrow
          @click="goApprovalDetail(item)"
        />
        <ZwiEmptyState v-if="!todoTasks.length" description="暂无待办审批" />
      </template>
    </ZwiSectionCard>

    <!-- 我的项目列表 -->
    <ZwiSectionCard title="我的项目">
      <view v-if="projectsFailed && !projects.length" class="failed-state">
        <text class="failed-tip">项目列表加载失败</text>
        <text class="retry-btn" @click="loadProjects">重试</text>
      </view>
      <scroll-view v-else scroll-y class="project-list" @scrolltolower="loadMore">
        <ZwiCell
          v-for="(item, i) in projects"
          :key="item.id"
          class="project-item"
          :title="item.projectName"
          clickable
          arrow
          :last="i === projects.length - 1 && !loading"
          @click="goArchive(item.id)"
        >
          <template #value>
            <text class="project-status status-badge status-badge-info">{{ item.statusText }}</text>
            <text class="project-amount">{{ (item.contractAmount / 10000).toFixed(1) }}万</text>
          </template>
        </ZwiCell>
        <ZwiEmptyState v-if="!projects.length && !loading" description="暂无项目" />
        <view class="loading-more" v-if="loading"><text>加载中...</text></view>
      </scroll-view>
    </ZwiSectionCard>
  </view>
</template>

<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { getCompanyOverview, getProjectList, getTodoTasks } from '@/api/common'
import { useNetworkStore } from '@/stores/network'
import ZwiSectionCard from '@/components/zwi/ZwiSectionCard.vue'
import ZwiStatCard from '@/components/zwi/ZwiStatCard.vue'
import ZwiCell from '@/components/zwi/ZwiCell.vue'
import ZwiEmptyState from '@/components/zwi/ZwiEmptyState.vue'

const network = useNetworkStore()
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

// 常用现场业务入口（静态数据收敛为单一事实源，图标 bizicons 与工作台/首页快捷入口同源）
const BIZ_ENTRIES = [
  // 风险中心置于首位（V2026_59）：老板/管理层移动高频场景——「今天需要处理什么」；
  // 图标复用 biz-inspect.png（巡检语义最贴近，未新增图片资产避免包体膨胀）
  { name: '风险中心', path: '/pages/cockpit/risk-center', icon: '/static/bizicons/biz-inspect.png' },
  { name: '变更事件', path: '/pages/contract/change-event/index', icon: '/static/bizicons/biz-change.png' },
  { name: '机械台班', path: '/pages/machine/work-log/index', icon: '/static/bizicons/biz-machine.png' },
  { name: '劳务点工', path: '/pages/labor/work-order/index', icon: '/static/bizicons/biz-labor.png' },
  { name: '水印相机', path: '/pages/site/watermark-camera/index', icon: '/static/bizicons/biz-camera.png' },
  { name: '施工日志', path: '/pages/site/construction-log', icon: '/static/bizicons/biz-log.png' },
  { name: '材料入库', path: '/pages/material/inbound', icon: '/static/bizicons/biz-material.png' },
  { name: '质量检查', path: '/pages/site/quality-check', icon: '/static/bizicons/biz-inspect.png' },
]

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

/** Batch 4.5：离线队列角标——tabBar 工作台(index=1)显示待同步数，>0 时红点数字，=0 时移除。
 * 可选链 + try/catch：测试环境 uni stub 无 setTabBarBadge、部分平台不支持时静默降级，不阻断页面。 */
function updateTabBarBadge() {
  const count = network.queueCount
  try {
    if (count > 0) {
      uni.setTabBarBadge?.({ index: 1, text: String(count) })?.catch?.(() => {})
    } else {
      uni.removeTabBarBadge?.({ index: 1 })?.catch?.(() => {})
    }
  } catch {
    /* 平台不支持 tabBar 角标：静默降级 */
  }
}

onShow(() => { loadData(); startTodoPolling(); updateTabBarBadge() })
onHide(() => { stopTodoPolling() })
onUnmounted(() => { stopTodoPolling() })
</script>

<style scoped>
.workbench-page { padding: 24rpx; }

.quick-link { display: flex; align-items: center; font-size: 24rpx; color: var(--zw-brand); font-weight: 500; min-height: 44px; padding: 0 8rpx; margin-right: -8rpx; box-sizing: border-box; }
.link-text { margin-right: 4rpx; }
.more-arrow { font-size: 28rpx; color: var(--zw-brand); }

/* Calm 概览四联（plain 模式嵌卡内，彩数字灰标签） */
.kanban-cards { display: flex; gap: 8rpx; }

/* 业务入口网格：白底行 + 品牌浅底圆承托 */
.biz-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20rpx; }
.biz-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding: 8rpx 4rpx;
  min-height: 88rpx;
  box-sizing: border-box;
}
.biz-item:active { opacity: 0.75; }
.biz-icon-wrap {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--zw-brand-light);
  border-radius: var(--zw-radius-md);
}
.biz-icon { width: 64rpx; height: 64rpx; }
.biz-name { font-size: 24rpx; color: var(--zw-text-primary); }

/* 待办角标（富标题行） */
.msg-title-row { display: flex; align-items: center; gap: 12rpx; }
.badge {
  background: var(--zw-danger);
  color: var(--zw-text-inverse);
  font-size: 22rpx;
  padding: 2rpx 12rpx;
  border-radius: 9999px;
  font-family: var(--zw-font-mono);
}

.project-list { max-height: 600rpx; }
.project-amount { font-size: 24rpx; color: var(--zw-text-tertiary); font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.loading-more { text-align: center; padding: 20rpx; color: var(--zw-text-tertiary); font-size: 24rpx; }

/* 三分区失败态（契约：文案 + 重试） */
.failed-state { display: flex; align-items: center; justify-content: center; padding: 40rpx 0; }
.failed-tip { font-size: 26rpx; color: var(--zw-danger); }
.retry-btn { margin-left: 20rpx; padding: 0 24rpx; min-height: 44px; display: inline-flex; align-items: center; justify-content: center; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 24rpx; border-radius: var(--zw-radius-sm); }
</style>
