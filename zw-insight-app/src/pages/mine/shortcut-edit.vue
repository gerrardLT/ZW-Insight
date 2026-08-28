<template>
  <view class="shortcut-edit-page">
    <!-- 已选功能区 -->
    <view class="section">
      <view class="section-header">
        <text class="section-title">已选入口</text>
        <text class="section-tip">{{ selected.length }}/{{ MAX_COUNT }} · 长按拖动排序</text>
      </view>
      <view v-if="selected.length" class="selected-list">
        <view
          v-for="(item, index) in selected"
          :key="item.id"
          class="selected-item"
          :class="{ dragging: dragIndex === index }"
          :data-index="index"
          @longpress="onLongPress(index)"
          @touchmove.stop.prevent="onTouchMove"
          @touchend="onTouchEnd"
          @touchcancel="onTouchEnd"
        >
          <view class="drag-handle">⠿</view>
          <text class="item-icon">{{ item.icon || '📌' }}</text>
          <text class="item-name">{{ item.name }}</text>
          <view class="remove-btn" @click.stop="removeItem(index)">
            <text class="remove-icon">✕</text>
          </view>
        </view>
      </view>
      <view v-else class="empty"><text>请至少添加 1 个快捷入口</text></view>
    </view>

    <!-- 可选功能区 -->
    <view class="section">
      <view class="section-header">
        <text class="section-title">可选功能</text>
        <text class="section-tip">点击添加到已选</text>
      </view>
      <view v-if="available.length" class="available-grid">
        <view
          v-for="item in available"
          :key="item.id"
          class="available-item"
          @click="addItem(item)"
        >
          <text class="item-icon">{{ item.icon || '📌' }}</text>
          <text class="available-name">{{ item.name }}</text>
          <view class="add-badge"><text>＋</text></view>
        </view>
      </view>
      <view v-else class="empty"><text>暂无更多可选功能</text></view>
    </view>

    <!-- 保存按钮 -->
    <view class="footer">
      <button class="save-btn" :disabled="saving || !selected.length" @click="handleSave">
        {{ saving ? '保存中...' : '保存' }}
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, getCurrentInstance } from 'vue'
import {
  getAvailableShortcuts,
  getUserShortcuts,
  batchSaveShortcuts,
  type AvailableShortcut,
  type UserShortcutConfig
} from '@/api/shortcut'

interface ShortcutItem {
  id: number
  name: string
  icon: string
  routePath: string
}

const MAX_COUNT = 8
const MIN_COUNT = 1
const SAVE_TIMEOUT = 10000 // 保存请求超时时间 10 秒

const selected = ref<ShortcutItem[]>([])
const available = ref<ShortcutItem[]>([])
const saving = ref(false)

const instance = getCurrentInstance()

// ---------- 数据加载 ----------
async function loadData() {
  let availableList: AvailableShortcut[] = []
  let userList: UserShortcutConfig[] = []

  try {
    const res: any = await getAvailableShortcuts()
    availableList = res?.data || []
  } catch {
    uni.showToast({ title: '可选功能加载失败', icon: 'none' })
  }

  try {
    const res: any = await getUserShortcuts()
    userList = res?.data || []
  } catch {
    uni.showToast({ title: '已选配置加载失败', icon: 'none' })
  }

  // 已选区：按 sortOrder 升序
  selected.value = [...userList]
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    .map((u) => ({
      id: u.shortcutId,
      name: u.menuName,
      icon: u.menuIcon,
      routePath: u.menuPath
    }))

  // 可选区：启用状态 且 未被选中的功能
  const selectedIds = new Set(selected.value.map((s) => s.id))
  available.value = availableList
    .filter((a) => a.status !== 'DISABLED' && !selectedIds.has(a.id))
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    .map((a) => ({
      id: a.id,
      name: a.name,
      icon: a.icon,
      routePath: a.routePath
    }))
}

// ---------- 添加 / 移除 ----------
function addItem(item: ShortcutItem) {
  if (selected.value.length >= MAX_COUNT) {
    uni.showToast({ title: '已达到最大可选数量', icon: 'none' })
    return
  }
  // 添加到已选区末尾
  selected.value.push(item)
  available.value = available.value.filter((a) => a.id !== item.id)
}

function removeItem(index: number) {
  if (selected.value.length <= MIN_COUNT) {
    uni.showToast({ title: '至少保留1个快捷入口', icon: 'none' })
    return
  }
  const [removed] = selected.value.splice(index, 1)
  if (removed) {
    available.value.push(removed)
  }
}

// ---------- 长按拖拽排序 ----------
const dragIndex = ref(-1)
let itemRects: { top: number; bottom: number }[] = []

function onLongPress(index: number) {
  dragIndex.value = index
  // 触觉反馈（视觉反馈由 .dragging 样式提供，长按阈值 ~300ms）
  // #ifdef APP-PLUS || MP
  uni.vibrateShort?.({ success: () => {}, fail: () => {} })
  // #endif
  measureItems()
}

function measureItems() {
  const query = uni.createSelectorQuery().in(instance)
  query
    .selectAll('.selected-item')
    .boundingClientRect((rects: any) => {
      if (Array.isArray(rects)) {
        itemRects = rects.map((r: any) => ({ top: r.top, bottom: r.bottom }))
      }
    })
    .exec()
}

function onTouchMove(e: any) {
  if (dragIndex.value < 0) return
  const touch = e.touches?.[0] || e.changedTouches?.[0]
  if (!touch || !itemRects.length) return
  const y = touch.clientY ?? touch.pageY
  // 找到当前触点落在哪个项目区间
  let targetIndex = -1
  for (let i = 0; i < itemRects.length; i++) {
    if (y >= itemRects[i].top && y <= itemRects[i].bottom) {
      targetIndex = i
      break
    }
  }
  // 触点超出顶部/底部时归位到首尾
  if (targetIndex < 0) {
    if (y < itemRects[0].top) targetIndex = 0
    else if (y > itemRects[itemRects.length - 1].bottom) targetIndex = itemRects.length - 1
  }
  if (targetIndex >= 0 && targetIndex !== dragIndex.value) {
    const list = selected.value
    const [moved] = list.splice(dragIndex.value, 1)
    list.splice(targetIndex, 0, moved)
    dragIndex.value = targetIndex
    measureItems()
  }
}

function onTouchEnd() {
  dragIndex.value = -1
}

// ---------- 保存 ----------
async function handleSave() {
  if (!selected.value.length) {
    uni.showToast({ title: '至少保留1个快捷入口', icon: 'none' })
    return
  }
  saving.value = true
  const ids = selected.value.map((s) => s.id)

  // 10 秒超时控制
  const timeoutPromise = new Promise((_, reject) => {
    setTimeout(() => reject(new Error('TIMEOUT')), SAVE_TIMEOUT)
  })

  try {
    await Promise.race([batchSaveShortcuts(ids), timeoutPromise])
    uni.showToast({ title: '保存成功', icon: 'success' })
    // 成功后 1.5 秒返回首页
    setTimeout(() => {
      backToHome()
    }, 1500)
  } catch (err: any) {
    const msg = err?.message === 'TIMEOUT' ? '保存超时，请重试' : '保存失败，请重试'
    uni.showToast({ title: msg, icon: 'none' })
    // 保留当前编辑状态，允许重新保存
    saving.value = false
  }
}

function backToHome() {
  const pages = getCurrentPages()
  if (pages.length > 1) {
    uni.navigateBack()
  } else {
    uni.reLaunch({ url: '/pages/home/index' })
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.shortcut-edit-page { padding: 20rpx; padding-bottom: 160rpx; }
.section { background: var(--zw-bg-card); border-radius: var(--zw-radius-lg); padding: 24rpx; margin-bottom: 24rpx; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20rpx; }
.section-title { font-size: 30rpx; font-weight: bold; color: var(--zw-text-primary); }
.section-tip { font-size: 22rpx; color: var(--zw-text-tertiary); }

/* 已选功能区 - 竖向列表，便于拖拽排序 */
.selected-list { display: flex; flex-direction: column; gap: 16rpx; }
.selected-item {
  display: flex; align-items: center; padding: 20rpx 16rpx;
  background: var(--zw-bg-hover); border-radius: var(--zw-radius-sm); transition: transform 0.15s, background 0.15s;
}
.selected-item.dragging {
  background: var(--zw-brand-light); border: 1rpx solid var(--zw-brand);
  transform: scale(1.03); z-index: 10;
}
.drag-handle { font-size: 36rpx; color: var(--zw-text-quaternary); margin-right: 16rpx; }
.item-icon { font-size: 40rpx; margin-right: 16rpx; }
.item-name { flex: 1; font-size: 28rpx; color: var(--zw-text-primary); }
.remove-btn {
  width: 48rpx; height: 48rpx; border-radius: 50%; background: var(--zw-danger-light);
  display: flex; align-items: center; justify-content: center;
}
.remove-icon { font-size: 24rpx; color: var(--zw-danger); }

/* 可选功能区 - 网格 */
.available-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20rpx; }
.available-item {
  position: relative; display: flex; flex-direction: column; align-items: center;
  padding: 24rpx 0; background: var(--zw-bg-hover); border-radius: var(--zw-radius-sm);
}
.available-name { font-size: 22rpx; color: var(--zw-text-secondary); margin-top: 8rpx; }
.add-badge {
  position: absolute; top: 6rpx; right: 6rpx; width: 32rpx; height: 32rpx;
  border-radius: 50%; background: var(--zw-brand); color: var(--zw-on-primary);
  display: flex; align-items: center; justify-content: center; font-size: 24rpx; line-height: 1;
}

.empty { text-align: center; padding: 40rpx; color: var(--zw-text-quaternary); font-size: 26rpx; }

/* 底部保存栏 */
.footer {
  position: fixed; left: 0; right: 0; bottom: 0; padding: 20rpx 32rpx;
  background: var(--zw-bg-card); border-top: 1rpx solid var(--zw-border-light);
}
.save-btn { background: var(--zw-brand); color: var(--zw-on-primary); border-radius: var(--zw-radius-sm); font-size: 30rpx; }
.save-btn:active { transform: translateY(1px); } /* 压合反馈，替代悬浮上浮 */
.save-btn[disabled] { background: var(--zw-brand); opacity: 0.4; }
</style>
