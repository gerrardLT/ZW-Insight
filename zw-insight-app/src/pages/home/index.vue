<template>
  <view class="home-page">
    <OfflineBanner />
    <!-- 顶部卡片（蓝图角标：品牌签名组件）；首屏骨架（S3.1）加载中显示概览卡骨架 -->
    <ZwSkeleton v-if="overviewLoading" type="card" />
    <template v-else>
      <!-- 日期问候行（Calm Design：一眼定位今天的上下文） -->
      <view class="greeting-row">
        <view class="greeting-text">
          <text class="eyebrow-cap">TODAY</text>
          <text class="greeting-title">{{ greetingText }}</text>
        </view>
        <text class="greeting-date tabular-num">{{ todayText }}</text>
      </view>

      <!-- 概览 2×2：calm 数值卡（数字即焦点，呼吸留白） -->
      <view class="stat-cards">
        <ZwiStatCard :value="overview.projectTotal || 0" label="项目总数" tone="neutral" />
        <ZwiStatCard :value="formatWan(overview.totalContractAmount)" label="合同总额(万)" tone="brand" />
        <ZwiStatCard :value="formatWan(overview.totalIncome)" label="已收款(万)" tone="success" />
        <ZwiStatCard :value="formatWan(overview.advanceFund)" label="垫资(万)" tone="warning" />
      </view>
    </template>

    <!-- 快捷功能 -->
    <ZwiSectionCard eyebrow="Shortcuts" title="常用功能">
      <template #action>
        <view class="edit-entry" @click="goShortcutEdit">
          <text class="edit-icon">✎</text><text class="edit-text">编辑</text>
        </view>
      </template>
      <view class="shortcut-grid" v-if="shortcuts.length">
        <view
          class="shortcut-item"
          v-for="item in shortcuts"
          :key="item.shortcutId"
          @click="navigateTo(item.menuPath)"
        >
          <!-- 图标映射：已知业务域用 PNG 线性图标，其余 fallback menuIcon 首字符（不静默丢内容） -->
          <view class="shortcut-icon">
            <image v-if="shortcutIcon(item.menuIcon)" class="shortcut-icon-img" :src="shortcutIcon(item.menuIcon)" mode="aspectFit" />
            <text v-else class="shortcut-icon-fallback">{{ fallbackChar(item.menuIcon, item.menuName) }}</text>
          </view>
          <text class="shortcut-name">{{ item.menuName }}</text>
        </view>
      </view>
      <ZwiEmptyState v-else description="暂无快捷入口，点击右上角「编辑」添加" />
    </ZwiSectionCard>

    <!-- 消息提醒 -->
    <ZwiSectionCard eyebrow="Inbox">
      <template #title>
        <view class="msg-title-row">
          <text class="section-title">消息提醒</text>
          <text class="badge" v-if="unreadCount">{{ unreadCount }}</text>
        </view>
      </template>
      <template #action>
        <view class="edit-entry" @click="goMessageCenter">
          <text class="edit-text">信息中心</text><text class="more-arrow">›</text>
        </view>
      </template>
      <view class="msg-list" v-if="messages.length">
        <ZwiCell
          v-for="msg in messages"
          :key="msg.id"
          :title="msg.title"
          :value="formatTime(msg.createdAt)"
        />
      </view>
      <ZwiEmptyState v-else description="暂无消息" />
    </ZwiSectionCard>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { getCompanyOverview, getUnreadCount, getUnreadMessages } from '@/api/common'
import { getUserShortcuts, type UserShortcutConfig } from '@/api/shortcut'
import OfflineBanner from '@/components/OfflineBanner.vue'
import ZwSkeleton from '@/components/ZwSkeleton.vue'
import ZwiSectionCard from '@/components/zwi/ZwiSectionCard.vue'
import ZwiStatCard from '@/components/zwi/ZwiStatCard.vue'
import ZwiCell from '@/components/zwi/ZwiCell.vue'
import ZwiEmptyState from '@/components/zwi/ZwiEmptyState.vue'

const overview = ref<any>({})
const overviewLoading = ref(true)
const unreadCount = ref(0)
const messages = ref<any[]>([])
const shortcuts = ref<UserShortcutConfig[]>([])

function formatWan(val: number) {
  if (!val) return '0'
  return (val / 10000).toFixed(1)
}

/** 时间短格式：今日 HH:mm / 其他 MM-DD（列表降噪） */
function formatTime(t?: string) {
  if (!t) return ''
  const s = String(t)
  const today = new Date()
  const ymd = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  if (s.startsWith(ymd)) return s.slice(11, 16)
  return s.slice(5, 10)
}

// ================= 问候行（Calm Design 上下文锚点） =================
const greetingText = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜班辛苦了'
  if (h < 12) return '上午好'
  if (h < 18) return '下午好'
  return '晚上好'
})
const todayText = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getMonth() + 1}月${d.getDate()}日 周${week}`
})

// ================= 快捷入口图标映射（emoji/PNG 混合来源归一） =================
/** menuIcon → 业务域 PNG 图标路径（bizicons 与 workbench 同源）；未知返回 null */
const ICON_MAP: Record<string, string> = {
  '📑': '/static/bizicons/biz-change.png',
  '🚜': '/static/bizicons/biz-machine.png',
  '👷': '/static/bizicons/biz-labor.png',
  '📷': '/static/bizicons/biz-camera.png',
  '📝': '/static/bizicons/biz-log.png',
  '📦': '/static/bizicons/biz-material.png',
  '🔍': '/static/bizicons/biz-inspect.png',
  '📊': '/static/bizicons/biz-cost.png',
}
function shortcutIcon(icon?: string): string | null {
  return (icon && ICON_MAP[icon]) || null
}
/** fallback 首字符（menuIcon 为空取 menuName 首字）；返回单字符文本渲染 */
function fallbackChar(icon: string | undefined, name: string): string {
  const src = (icon && icon.trim()) || name || ''
  return src.charAt(0)
}

function navigateTo(url: string) {
  uni.navigateTo({ url })
}

function goShortcutEdit() {
  uni.navigateTo({ url: '/pages/mine/shortcut-edit' })
}

function goMessageCenter() {
  uni.navigateTo({ url: '/pages/message-center/index' })
}

// 加载用户个性化快捷入口（未配置时后端返回系统默认项，≤4 项）
async function loadShortcuts() {
  try {
    const res: any = await getUserShortcuts()
    const list: UserShortcutConfig[] = res?.data || []
    shortcuts.value = [...list].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  } catch {
    uni.showToast({ title: '快捷入口加载失败', icon: 'none' })
  }
}

async function loadData() {
  try {
    const res1: any = await getCompanyOverview()
    overview.value = res1.data || {}
  } catch {} finally {
    overviewLoading.value = false
  }
  try {
    const res2: any = await getUnreadCount()
    unreadCount.value = res2.data || 0
  } catch {}
  try {
    const res3: any = await getUnreadMessages({ page: 1, size: 5 })
    messages.value = res3.data?.records || []
  } catch {}
  // 从编辑页返回时通过 onShow 重新加载，保证编辑结果即时生效
  await loadShortcuts()
}

onShow(() => { loadData() })
onMounted(() => { loadData() })

// 下拉刷新（S3.2）：首页概览/消息/快捷入口强制重拉，完成后停止刷新动画
onPullDownRefresh(async () => {
  try {
    await loadData()
  } finally {
    uni.stopPullDownRefresh()
  }
})
</script>

<style scoped>
.home-page { padding: 24rpx; }

/* 问候行 */
.greeting-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding: 8rpx 4rpx 20rpx;
}
.greeting-text { display: flex; flex-direction: column; gap: 4rpx; }
.greeting-title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--zw-text-primary);
  line-height: 1.2;
}
.greeting-date {
  font-size: 24rpx;
  color: var(--zw-text-tertiary);
  font-family: var(--zw-font-mono);
}

/* 概览 2×2 */
.stat-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

/* 快捷入口网格 */
.shortcut-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20rpx; }
.shortcut-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  padding: 8rpx 0;
  min-height: 88rpx; /* 触控底线 */
  box-sizing: border-box;
}
.shortcut-item:active { opacity: 0.75; }
.shortcut-icon {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--zw-brand-light);
  border-radius: var(--zw-radius-md);
  overflow: hidden;
}
.shortcut-icon-img { width: 56rpx; height: 56rpx; }
.shortcut-icon-fallback {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--zw-brand);
}
.shortcut-name {
  font-size: 24rpx;
  color: var(--zw-text-secondary);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

/* 消息标题行（富标题：title + 未读角标同行） */
.msg-title-row { display: flex; align-items: center; gap: 12rpx; }
.badge {
  background: var(--zw-danger);
  color: var(--zw-text-inverse);
  font-size: 22rpx;
  padding: 2rpx 12rpx;
  border-radius: 9999px;
  font-weight: normal;
  font-family: var(--zw-font-mono);
}

/* ZwiSectionCard 覆盖：富标题插槽场景 heading 保持纵向对齐 */
:deep(.section-heading) { align-self: stretch; }
</style>
