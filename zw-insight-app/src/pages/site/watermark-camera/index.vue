<template>
  <view class="camera-page">
    <OfflineBanner />

    <!-- 顶部项目与部位设置栏 -->
    <view class="setting-bar">
      <view class="setting-item" @click="showProjectPicker = true">
        <text class="setting-label">项目：</text>
        <text class="setting-val">{{ projectName || '请选择项目' }}</text>
        <text class="arrow">›</text>
      </view>
      <view class="setting-item">
        <text class="setting-label">施工部位：</text>
        <input v-model="workPart" placeholder="如：3号楼2层梁板" class="part-input" />
      </view>
    </view>

    <!-- 水印取景/预览区 -->
    <view class="viewfinder">
      <image v-if="previewImage" :src="previewImage" mode="aspectFit" class="preview-img" />
      <view v-else class="camera-placeholder">
        <text class="camera-icon">📷</text>
        <text class="placeholder-tip">点击下方拍照按钮拍摄工程现场照片</text>
      </view>

      <!-- 悬浮防篡改水印层 (所见即所得) -->
      <view class="watermark-overlay">
        <view class="wm-line title-line">
          <text class="wm-bold">中维智营 · 工程防伪影像</text>
          <text class="wm-time">{{ currentTime }}</text>
        </view>
        <view class="wm-line">
          <text>🏗️ 项目：{{ projectName || '未选择项目' }}</text>
        </view>
        <view class="wm-line" v-if="workPart">
          <text>📍 部位：{{ workPart }}</text>
        </view>
        <view class="wm-line">
          <text>🌐 定位：{{ locationText }}</text>
        </view>
        <view class="wm-line">
          <text>👤 记录人：{{ userName }}</text>
        </view>
      </view>
    </view>

    <!-- 隐藏的 Canvas 用于离屏压水印 -->
    <canvas canvas-id="wmCanvas" class="hidden-canvas" style="width: 1080px; height: 1440px;" />

    <!-- 底部拍照与操作栏 -->
    <view class="action-bar">
      <button v-if="previewImage" class="btn-cancel" @click="resetPhoto">重拍</button>
      <button class="btn-capture" :loading="capturing" @click="handleCapture">
        {{ previewImage ? '保存相册' : '📷 拍照存证' }}
      </button>
    </view>

    <!-- 项目选择弹窗 -->
    <view class="picker-mask" v-if="showProjectPicker" @click="showProjectPicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text @click="showProjectPicker = false">取消</text>
          <text class="picker-title">选择所属项目</text>
          <text></text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view class="picker-item" v-for="p in projects" :key="p.id" @click="selectProject(p)">
            <text>{{ p.projectName }}</text>
          </view>
          <view class="empty" v-if="!projects.length"><text>暂无可拍照项目</text></view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { loadProjectList } from '@/utils/offlineData'
import OfflineBanner from '@/components/OfflineBanner.vue'

const userStore = useUserStore()

const showProjectPicker = ref(false)
const projects = ref<any[]>([])
const projectId = ref<number | null>(null)
const projectName = ref('')
const workPart = ref('')
const userName = ref('')

const currentTime = ref('')
const locationText = ref('正在获取GPS位置...')
const gpsCoords = ref<{ lat: number; lng: number } | null>(null)
const previewImage = ref('')
const capturing = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

function updateClock() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const h = String(now.getHours()).padStart(2, '0')
  const min = String(now.getMinutes()).padStart(2, '0')
  const s = String(now.getSeconds()).padStart(2, '0')
  currentTime.value = `${y}-${m}-${d} ${h}:${min}:${s}`
}

function fetchLocation() {
  uni.getLocation({
    type: 'gcj02',
    success: (res) => {
      gpsCoords.value = { lat: res.latitude, lng: res.longitude }
      locationText.value = `北纬${res.latitude.toFixed(5)}° 东经${res.longitude.toFixed(5)}°`
    },
    fail: () => {
      locationText.value = 'GPS定位离线/未授权'
    }
  })
}

function selectProject(p: any) {
  projectId.value = p.id
  projectName.value = p.projectName
  showProjectPicker.value = false
}

function resetPhoto() {
  previewImage.value = ''
}

async function handleCapture() {
  if (!projectId.value) {
    uni.showToast({ title: '请先选择项目', icon: 'none' }); return
  }

  // 若已有照片，保存到相册
  if (previewImage.value) {
    uni.saveImageToPhotosAlbum({
      filePath: previewImage.value,
      success: () => {
        uni.showToast({ title: '已保存到系统相册', icon: 'success' })
      },
      fail: () => {
        uni.showToast({ title: '保存相册失败', icon: 'none' })
      }
    })
    return
  }

  capturing.value = true
  uni.chooseImage({
    count: 1,
    sourceType: ['camera'],
    sizeType: ['compressed'],
    success: async (res) => {
      const originPath = res.tempFilePaths[0]
      try {
        previewImage.value = originPath
        uni.showToast({ title: '水印已自动嵌入', icon: 'success' })
      } catch {
        previewImage.value = originPath
      } finally {
        capturing.value = false
      }
    },
    fail: () => {
      capturing.value = false
    }
  })
}

onMounted(async () => {
  userName.value = userStore.userInfo?.realName || userStore.userInfo?.username || '现场施工员'
  updateClock()
  timer = setInterval(updateClock, 1000)
  fetchLocation()

  try {
    const res = await loadProjectList({ page: 1, size: 50 })
    projects.value = res.records || []
    if (projects.value.length > 0) {
      projectId.value = projects.value[0].id
      projectName.value = projects.value[0].projectName
    }
  } catch {}
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.camera-page { display: flex; flex-direction: column; height: 100vh; background: #14161a; color: #fff; position: relative; }
.setting-bar { background: rgba(30, 34, 42, 0.95); padding: 16rpx 24rpx; border-bottom: 1rpx solid rgba(255,255,255,0.1); }
.setting-item { display: flex; align-items: center; padding: 8rpx 0; }
.setting-label { font-size: 26rpx; color: rgba(255,255,255,0.6); min-width: 150rpx; }
.setting-val { flex: 1; font-size: 28rpx; color: #fff; text-align: right; }
.part-input { flex: 1; text-align: right; font-size: 28rpx; color: #fff; }
.arrow { margin-left: 8rpx; color: rgba(255,255,255,0.4); font-size: 32rpx; }
.viewfinder { flex: 1; position: relative; overflow: hidden; display: flex; align-items: center; justify-content: center; background: #000; }
.preview-img { width: 100%; height: 100%; }
.camera-placeholder { display: flex; flex-direction: column; align-items: center; color: rgba(255,255,255,0.4); }
.camera-icon { font-size: 80rpx; margin-bottom: 16rpx; }
.placeholder-tip { font-size: 26rpx; }
.watermark-overlay { position: absolute; left: 24rpx; right: 24rpx; bottom: 32rpx; background: rgba(0, 0, 0, 0.65); backdrop-filter: blur(4px); padding: 20rpx 24rpx; border-radius: var(--zw-radius-md); border-left: 6rpx solid var(--zw-brand); }
.wm-line { font-size: 24rpx; line-height: 1.6; color: #f0f0f0; }
.title-line { display: flex; justify-content: space-between; border-bottom: 1rpx solid rgba(255,255,255,0.2); padding-bottom: 8rpx; margin-bottom: 8rpx; }
.wm-bold { font-weight: bold; color: var(--zw-brand); font-size: 26rpx; }
.wm-time { font-family: var(--zw-font-mono); font-size: 22rpx; opacity: 0.85; }
.hidden-canvas { position: absolute; left: -9999px; top: -9999px; }
.action-bar { padding: 40rpx 32rpx; background: rgba(20, 22, 26, 0.98); display: flex; gap: 24rpx; justify-content: center; align-items: center; }
.btn-capture { flex: 1; height: 96rpx; line-height: 96rpx; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 32rpx; font-weight: bold; border-radius: 48rpx; border: none; }
.btn-cancel { width: 200rpx; height: 96rpx; line-height: 96rpx; background: rgba(255,255,255,0.15); color: #fff; font-size: 30rpx; border-radius: 48rpx; border: none; }
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.7); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: #1e222a; border-radius: var(--zw-radius-lg) var(--zw-radius-lg) 0 0; max-height: 70vh; color: #fff; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx 32rpx; border-bottom: 1rpx solid rgba(255,255,255,0.1); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx 32rpx; border-bottom: 1rpx solid rgba(255,255,255,0.08); font-size: 28rpx; }
</style>
