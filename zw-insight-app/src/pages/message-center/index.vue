<template>
  <view class="mc-page">
    <!-- Tab切换 -->
    <view class="tabs">
      <view
        class="tab-item"
        v-for="tab in tabList"
        :key="tab.key"
        :class="{ active: activeTab === tab.key }"
        @click="switchTab(tab.key)"
      >
        <text>{{ tab.label }}</text>
      </view>
    </view>

    <!-- 消息 tab 的全部已读操作 -->
    <view class="toolbar" v-if="activeTab === 'message'">
      <text class="read-all" @click="handleReadAll">全部已读</text>
    </view>

    <!-- 列表：S3.1 卡片流 → 单一分组卡 + ZwiCell 行（hairline 分隔降噪）
         未读点与时间走 #value 插槽；内容摘要仍限两行（:deep 覆盖 .cell-desc） -->
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view class="list-card zw-card" v-if="list.length">
        <ZwiCell
          v-for="(item, idx) in list"
          :key="item.id"
          :title="item.title"
          :desc="item.content"
          clickable
          :last="idx === list.length - 1"
          @click="onItemClick(item)"
        >
          <template #value>
            <text class="msg-dot" v-if="isUnread(item)"></text>
            <text class="msg-time">{{ shortTime(formatTime(item)) }}</text>
          </template>
        </ZwiCell>
      </view>

      <!-- S3.1：.empty → ZwiEmptyState（critique P3：插画 + 与失败态可区分） -->
      <ZwiEmptyState v-if="!list.length && !loading" :description="`暂无${currentLabel}`" />
      <view class="loading-more" v-if="loading"><text>加载中...</text></view>
      <view class="no-more" v-if="!hasMore && list.length"><text>没有更多了</text></view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import {
  getAnnouncements,
  getNotices,
  getAllMessages,
  markMessageRead,
  markAllMessagesRead
} from '@/api/common'
import { shortTime } from '@/utils/format'
import ZwiCell from '@/components/zwi/ZwiCell.vue'
import ZwiEmptyState from '@/components/zwi/ZwiEmptyState.vue'

const PAGE_SIZE = 15

const tabList = [
  { key: 'announcement', label: '公告' },
  { key: 'notice', label: '通知' },
  { key: 'message', label: '消息预警' }
]

const activeTab = ref('announcement')
const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const hasMore = ref(true)

const currentLabel = computed(
  () => tabList.find((t) => t.key === activeTab.value)?.label || ''
)

function apiByTab(params: any) {
  if (activeTab.value === 'announcement') return getAnnouncements(params)
  if (activeTab.value === 'notice') return getNotices(params)
  return getAllMessages(params)
}

function switchTab(tab: string) {
  if (activeTab.value === tab) return
  activeTab.value = tab
  page.value = 1
  hasMore.value = true
  list.value = []
  loadData()
}

async function loadData() {
  if (loading.value) return
  loading.value = true
  try {
    const params: any = { page: page.value, size: PAGE_SIZE }
    // 公告仅展示已发布
    if (activeTab.value === 'announcement') params.status = 'PUBLISHED'
    const res: any = await apiByTab(params)
    const records = res.data?.records || []
    if (page.value === 1) {
      list.value = records
    } else {
      list.value.push(...records)
    }
    hasMore.value = records.length >= PAGE_SIZE
  } catch {
    // request 工具已统一 Toast 提示，无需重复处理
  } finally {
    loading.value = false
    uni.stopPullDownRefresh()
  }
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  page.value++
  loadData()
}

async function onItemClick(item: any) {
  // 消息预览：点击标记已读
  if (activeTab.value === 'message' && item.isRead === 0) {
    try {
      await markMessageRead(item.id)
      item.isRead = 1
    } catch {}
  }
}

async function handleReadAll() {
  try {
    await markAllMessagesRead()
    uni.showToast({ title: '已全部标记为已读', icon: 'success' })
    list.value.forEach((m) => (m.isRead = 1))
  } catch {}
}

function formatTime(item: any) {
  return item.publishTime || item.createTime || item.createdAt || ''
}

/** 未读判定：仅消息预警 tab 后端返回 isRead，公告/通知无此语义 */
function isUnread(item: any) {
  return activeTab.value === 'message' && item.isRead === 0
}

onShow(() => {
  page.value = 1
  hasMore.value = true
  loadData()
})

onPullDownRefresh(() => {
  page.value = 1
  hasMore.value = true
  loadData()
})
</script>

<style scoped>
.mc-page { display: flex; flex-direction: column; height: 100vh; background: var(--zw-bg-page); }
.tabs { display: flex; background: var(--zw-bg-card); border-bottom: 1rpx solid var(--zw-border-light); }
.tab-item { flex: 1; text-align: center; padding: 24rpx 0; font-size: 28rpx; color: var(--zw-text-secondary); position: relative; display: flex; align-items: center; justify-content: center; min-height: 44px; box-sizing: border-box; } /* P0 触控达标 */
.tab-item.active { color: var(--zw-brand); font-weight: bold; }
.tab-item.active::after { content: ''; position: absolute; bottom: 0; left: 50%; transform: translateX(-50%); width: 60rpx; height: 4rpx; background: var(--zw-brand); border-radius: 2rpx; }
.toolbar { display: flex; justify-content: flex-end; padding: 0 24rpx; background: var(--zw-bg-card); }
.read-all { font-size: 24rpx; color: var(--zw-brand); min-height: 44px; display: inline-flex; align-items: center; padding: 0 8rpx; margin-right: -8rpx; box-sizing: border-box; } /* P0 触控热区 */
.list { flex: 1; padding: 20rpx; padding-bottom: calc(20rpx + var(--zw-safe-bottom)); box-sizing: border-box; }
.list-card { overflow: hidden; }
/* ZwiCell 行内边距跟卡内呼吸（卡已有 1px hairline 边框，行左右补齐 24rpx） */
.list-card :deep(.cell-row) { padding-left: 24rpx; padding-right: 24rpx; }
/* 内容摘要限两行（原 .item-content 语义保留） */
.list-card :deep(.cell-desc) {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.msg-dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: var(--zw-danger); flex-shrink: 0; }
.msg-time { font-size: 24rpx; color: var(--zw-text-quaternary); font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; white-space: nowrap; }
.loading-more { text-align: center; padding: 20rpx; color: var(--zw-text-tertiary); font-size: 24rpx; }
.no-more { text-align: center; padding: 20rpx; color: var(--zw-text-quaternary); font-size: 22rpx; }
</style>
