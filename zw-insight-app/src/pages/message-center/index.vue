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

    <!-- 列表 -->
    <scroll-view scroll-y class="list" @scrolltolower="loadMore">
      <view
        class="item"
        v-for="item in list"
        :key="item.id"
        @click="onItemClick(item)"
      >
        <view class="item-header">
          <text class="item-title">{{ item.title }}</text>
          <view
            class="dot"
            v-if="activeTab === 'message' && item.isRead === 0"
          ></view>
        </view>
        <text class="item-content" v-if="item.content">{{ item.content }}</text>
        <text class="item-time">{{ formatTime(item) }}</text>
      </view>

      <view class="empty" v-if="!list.length && !loading">
        <text>暂无{{ currentLabel }}</text>
      </view>
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
.mc-page { display: flex; flex-direction: column; height: 100vh; background: #f5f5f5; }
.tabs { display: flex; background: #fff; border-bottom: 1rpx solid #f0f0f0; }
.tab-item { flex: 1; text-align: center; padding: 24rpx 0; font-size: 28rpx; color: #606266; position: relative; }
.tab-item.active { color: #409eff; font-weight: bold; }
.tab-item.active::after { content: ''; position: absolute; bottom: 0; left: 50%; transform: translateX(-50%); width: 60rpx; height: 4rpx; background: #409eff; border-radius: 2rpx; }
.toolbar { display: flex; justify-content: flex-end; padding: 12rpx 24rpx; background: #fff; }
.read-all { font-size: 24rpx; color: #409eff; }
.list { flex: 1; padding: 20rpx; }
.item { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 16rpx; }
.item-header { display: flex; justify-content: space-between; align-items: center; }
.item-title { font-size: 28rpx; color: #303133; font-weight: 500; flex: 1; }
.dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: #f56c6c; margin-left: 12rpx; }
.item-content { display: block; margin-top: 12rpx; font-size: 24rpx; color: #909399; overflow: hidden; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.item-time { display: block; margin-top: 12rpx; font-size: 22rpx; color: #c0c4cc; }
.empty { text-align: center; padding: 80rpx; color: #c0c4cc; font-size: 26rpx; }
.loading-more { text-align: center; padding: 20rpx; color: #909399; font-size: 24rpx; }
.no-more { text-align: center; padding: 20rpx; color: #c0c4cc; font-size: 22rpx; }
</style>
