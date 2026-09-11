<template>
  <view class="zw-watermark-camera">
    <!-- 未拍摄态：带工业十字瞄准标尺的拍照触发卡片 -->
    <view v-if="!imageSrc" class="reticle-box" @click="handleTakePhoto">
      <view class="reticle-corner top-left"></view>
      <view class="reticle-corner top-right"></view>
      <view class="reticle-corner bottom-left"></view>
      <view class="reticle-corner bottom-right"></view>
      <view class="reticle-crosshair"></view>
      <view class="reticle-center-dot"></view>
      <view class="reticle-content">
        <view class="camera-icon">📷</view>
        <text class="reticle-title">现场工程水印拍照</text>
        <text class="reticle-sub mono-num">GPS · TIMESTAMP · SERIAL SEAL</text>
      </view>
    </view>

    <!-- 已拍摄态：展示压印后的现场真实照片与工程印章 -->
    <view v-else class="preview-box">
      <image :src="imageSrc" mode="aspectFill" class="preview-img" @click="previewImage" />
      <view class="preview-stamp">
        <view class="stamp-bar"></view>
        <view class="stamp-content">
          <text class="stamp-project">{{ projectName }}</text>
          <text class="stamp-meta mono-num">单号：{{ serialNumber }}</text>
          <text class="stamp-meta mono-num">时间：{{ captureTime }}</text>
          <text class="stamp-meta mono-num">坐标：{{ locationText }}</text>
        </view>
        <view class="stamp-seal">工程留样</view>
      </view>
      <view class="retake-btn" @click="handleTakePhoto">重新采集</view>
    </view>

    <!-- 用于压印合成的隐藏 Canvas -->
    <canvas
      canvas-id="watermarkCanvas"
      id="watermarkCanvas"
      class="hidden-canvas"
      :style="{ width: canvasWidth + 'px', height: canvasHeight + 'px' }"
    ></canvas>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const props = withDefaults(defineProps<{
  modelValue?: string
  projectName?: string
  serialNumber?: string
}>(), {
  modelValue: '',
  projectName: '中维智营标杆项目',
  serialNumber: 'ZW-SN-20260911001'
})

const emit = defineEmits<{
  (e: 'update:modelValue', path: string): void
  (e: 'captured', data: { path: string, time: string, location: string }): void
}>()

const imageSrc = ref(props.modelValue)
const captureTime = ref('')
const locationText = ref('N 31°14\'22" E 121°28\'05"')
const canvasWidth = ref(600)
const canvasHeight = ref(800)

function formatNow(): string {
  const d = new Date()
  const pad = (n: number) => (n < 10 ? '0' + n : '' + n)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function handleTakePhoto() {
  uni.chooseImage({
    count: 1,
    sourceType: ['camera', 'album'],
    success: (res) => {
      const tempPath = res.tempFilePaths[0]
      applyWatermark(tempPath)
    },
    fail: () => {}
  })
}

function applyWatermark(tempFilePath: string) {
  captureTime.value = formatNow()
  try {
    uni.getLocation({
      type: 'wgs84',
      success: (loc) => {
        locationText.value = `N ${loc.latitude.toFixed(4)}° E ${loc.longitude.toFixed(4)}°`
      },
      complete: () => {
        renderCanvasWatermark(tempFilePath)
      }
    })
  } catch {
    renderCanvasWatermark(tempFilePath)
  }
}

function renderCanvasWatermark(tempFilePath: string) {
  uni.getImageInfo({
    src: tempFilePath,
    success: (info) => {
      const w = 600
      const h = Math.round((info.height / info.width) * w)
      canvasWidth.value = w
      canvasHeight.value = h

      const ctx = uni.createCanvasContext('watermarkCanvas')
      ctx.drawImage(tempFilePath, 0, 0, w, h)

      // 绘制底部工业暗黑半透明压印铭牌条
      const barH = 110
      ctx.setFillStyle('rgba(16, 18, 20, 0.85)')
      ctx.fillRect(0, h - barH, w, barH)

      // 左侧橙色立线
      ctx.setFillStyle('#ff6b00')
      ctx.fillRect(0, h - barH, 6, barH)

      // 水印文字
      ctx.setFillStyle('#ffffff')
      ctx.setFontSize(16)
      ctx.fillText(props.projectName, 18, h - barH + 26)

      ctx.setFillStyle('#c6c9cc')
      ctx.setFontSize(13)
      ctx.fillText(`单号: ${props.serialNumber}`, 18, h - barH + 50)
      ctx.fillText(`时间: ${captureTime.value}`, 18, h - barH + 72)
      ctx.fillText(`坐标: ${locationText.value}`, 18, h - barH + 94)

      // 绘制工程印章框
      ctx.setStrokeStyle('#ff6b00')
      ctx.setLineWidth(2)
      ctx.strokeRect(w - 110, h - barH + 20, 90, 70)
      ctx.setFillStyle('#ff6b00')
      ctx.setFontSize(15)
      ctx.fillText('现场质检', w - 95, h - barH + 50)
      ctx.setFontSize(11)
      ctx.fillText('VERIFIED', w - 93, h - barH + 72)

      ctx.draw(false, () => {
        uni.canvasToTempFilePath({
          canvasId: 'watermarkCanvas',
          success: (cRes) => {
            imageSrc.value = cRes.tempFilePath
            emit('update:modelValue', cRes.tempFilePath)
            emit('captured', {
              path: cRes.tempFilePath,
              time: captureTime.value,
              location: locationText.value
            })
          },
          fail: () => {
            // fallback 原图
            imageSrc.value = tempFilePath
            emit('update:modelValue', tempFilePath)
          }
        })
      })
    },
    fail: () => {
      imageSrc.value = tempFilePath
      emit('update:modelValue', tempFilePath)
    }
  })
}

function previewImage() {
  if (!imageSrc.value) return
  uni.previewImage({
    urls: [imageSrc.value]
  })
}

onMounted(() => {
  if (props.modelValue) {
    imageSrc.value = props.modelValue
  }
})
</script>

<style scoped>
.zw-watermark-camera {
  width: 100%;
}

/* 工业瞄准取景框 */
.reticle-box {
  position: relative;
  width: 100%;
  height: 280rpx;
  background: var(--zw-bg-hover);
  border: 1rpx dashed var(--zw-border-strong);
  border-radius: var(--zw-radius-xs);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: pointer;
}

.reticle-corner {
  position: absolute;
  width: 24rpx;
  height: 24rpx;
  border-color: var(--zw-brand);
  border-style: solid;
  border-width: 0;
}

.top-left {
  top: 12rpx;
  left: 12rpx;
  border-top-width: 4rpx;
  border-left-width: 4rpx;
}

.top-right {
  top: 12rpx;
  right: 12rpx;
  border-top-width: 4rpx;
  border-right-width: 4rpx;
}

.bottom-left {
  bottom: 12rpx;
  left: 12rpx;
  border-bottom-width: 4rpx;
  border-left-width: 4rpx;
}

.bottom-right {
  bottom: 12rpx;
  right: 12rpx;
  border-bottom-width: 4rpx;
  border-right-width: 4rpx;
}

/* 十字瞄准准星 */
.reticle-crosshair {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(to right, transparent calc(50% - 0.5px), rgba(255, 107, 0, 0.2) calc(50% - 0.5px), rgba(255, 107, 0, 0.2) calc(50% + 0.5px), transparent calc(50% + 0.5px)),
    linear-gradient(to bottom, transparent calc(50% - 0.5px), rgba(255, 107, 0, 0.2) calc(50% - 0.5px), rgba(255, 107, 0, 0.2) calc(50% + 0.5px), transparent calc(50% + 0.5px));
}

.reticle-center-dot {
  position: absolute;
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: var(--zw-brand);
}

.reticle-content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.camera-icon {
  font-size: 48rpx;
}

.reticle-title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--zw-text-primary);
}

.reticle-sub {
  font-size: 20rpx;
  color: var(--zw-brand);
  letter-spacing: 0.5px;
}

/* 预览图与水印铭牌 */
.preview-box {
  position: relative;
  width: 100%;
  height: 380rpx;
  border-radius: var(--zw-radius-xs);
  overflow: hidden;
  border: 1rpx solid var(--zw-border);
}

.preview-img {
  width: 100%;
  height: 100%;
}

.preview-stamp {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: rgba(16, 18, 20, 0.85);
  padding: 16rpx 20rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stamp-bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6rpx;
  background: var(--zw-brand);
}

.stamp-content {
  display: flex;
  flex-direction: column;
  gap: 2rpx;
}

.stamp-project {
  font-size: 24rpx;
  font-weight: bold;
  color: #fff;
}

.stamp-meta {
  font-size: 18rpx;
  color: rgba(255, 255, 255, 0.7);
}

.stamp-seal {
  border: 2rpx solid var(--zw-brand);
  color: var(--zw-brand);
  font-size: 20rpx;
  font-weight: bold;
  padding: 6rpx 12rpx;
  transform: rotate(-8deg);
  border-radius: var(--zw-radius-xs);
}

.retake-btn {
  position: absolute;
  top: 12rpx;
  right: 12rpx;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  font-size: 22rpx;
  padding: 6rpx 16rpx;
  border-radius: var(--zw-radius-xs);
}

.hidden-canvas {
  position: fixed;
  left: -9999px;
  top: -9999px;
}
</style>
