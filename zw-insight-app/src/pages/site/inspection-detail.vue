<template>
  <view class="inspection-detail-page">
    <!-- 加载中 -->
    <view v-if="pageLoading" class="loading-state">
      <text>加载中...</text>
    </view>

    <!-- 检查方案内容 -->
    <view v-else>
      <!-- 方案基本信息 -->
      <view class="info-card" v-if="schemeInfo.schemeName">
        <view class="info-row">
          <text class="info-label">检查方案</text>
          <text class="info-value">{{ schemeInfo.schemeName }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">检查项数</text>
          <text class="info-value">{{ checkItems.length }} 项</text>
        </view>
        <view class="info-row" v-if="inspection.hasProblem === 1">
          <text class="info-label">整改状态</text>
          <text class="info-value" :class="'rect-' + (inspection.rectificationStatus || 'PENDING')">
            {{ rectStatusText(inspection.rectificationStatus) }}
          </text>
        </view>
      </view>

      <!-- 无方案提示 -->
      <view class="empty-state" v-if="checkItems.length === 0">
        <text class="empty-text">当前检查记录未关联检查方案</text>
      </view>

      <!-- 检查项列表 -->
      <view class="check-list" v-if="checkItems.length > 0">
        <view class="check-item" v-for="(item, index) in checkItems" :key="index">
          <view class="item-header">
            <text class="item-index">{{ index + 1 }}</text>
            <text class="item-name">{{ item.itemName }}</text>
          </view>
          <view class="item-detail" v-if="item.checkStandard">
            <text class="detail-label">检查标准：</text>
            <text class="detail-text">{{ item.checkStandard }}</text>
          </view>
          <view class="item-detail" v-if="item.checkMethod">
            <text class="detail-label">检查方法：</text>
            <text class="detail-text">{{ item.checkMethod }}</text>
          </view>
          <!-- 检查结果标记 -->
          <view class="result-section">
            <text class="result-label">检查结果：</text>
            <view class="result-options">
              <view
                class="result-option"
                :class="{ active: item.result === 'PASS', pass: item.result === 'PASS' }"
                @click="markResult(index, 'PASS')"
              >
                <text>合格</text>
              </view>
              <view
                class="result-option"
                :class="{ active: item.result === 'FAIL', fail: item.result === 'FAIL' }"
                @click="markResult(index, 'FAIL')"
              >
                <text>不合格</text>
              </view>
              <view
                class="result-option"
                :class="{ active: item.result === 'UNCHECKED', unchecked: item.result === 'UNCHECKED' }"
                @click="markResult(index, 'UNCHECKED')"
              >
                <text>未检查</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 提交按钮 -->
      <view class="submit-area" v-if="checkItems.length > 0">
        <view class="summary">
          <text class="summary-text">
            合格: {{ passCount }} / 不合格: {{ failCount }} / 未检查: {{ uncheckedCount }}
          </text>
        </view>
        <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交检查结果</button>
      </view>

      <!-- 整改闭环区：仅有问题的检查记录展示 -->
      <view class="rect-card" v-if="inspection.hasProblem === 1">
        <view class="rect-title">整改闭环</view>

        <!-- 提交整改表单：仅待整改状态可提交 -->
        <template v-if="inspection.rectificationStatus === 'PENDING'">
          <view class="rect-form">
            <textarea
              v-model="rectForm.rectificationContent"
              placeholder="请描述整改措施与结果"
              class="rect-textarea"
              :maxlength="500"
            />
            <button class="photo-btn" :loading="photoUploading" @click="takeRectPhoto">📷 上传整改照片</button>
            <view class="rect-photo-list" v-if="rectPhotos.length">
              <view class="rect-photo-item" v-for="(p, idx) in rectPhotos" :key="idx">
                <image :src="p.localPath" mode="aspectFill" class="rect-thumb" @click="previewRectPhoto(idx)" />
                <text class="rect-remove" @click="removeRectPhoto(idx)">×</text>
              </view>
            </view>
            <button class="rect-submit-btn" :loading="rectSubmitting" @click="handleSubmitRectification">提交整改</button>
          </view>
          <view class="rect-divider"></view>
        </template>

        <!-- 整改记录列表 -->
        <view class="rect-loading" v-if="rectLoading"><text>加载整改记录...</text></view>
        <template v-else>
          <view class="rect-empty" v-if="rectifications.length === 0"><text>暂无整改记录</text></view>
          <view class="rect-record" v-for="rec in rectifications" :key="rec.id">
            <view class="rect-record-head">
              <text class="rect-status" :class="'st-' + rec.status">{{ rectStatusText(rec.status) }}</text>
              <text class="rect-time">{{ rec.createdAt }}</text>
            </view>
            <view class="rect-record-content"><text>{{ rec.rectificationContent }}</text></view>
            <button
              v-if="rec.status === 'SUBMITTED'"
              class="rect-approve-btn"
              @click="handleApprove(rec)"
            >复查通过</button>
          </view>
        </template>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  getInspectionDetail,
  submitInspectionResults,
  getRectifications,
  submitRectification,
  approveRectification,
  uploadRectificationPhoto
} from '@/api/common'

interface CheckItem {
  itemName: string
  checkStandard: string
  checkMethod: string
  result: 'PASS' | 'FAIL' | 'UNCHECKED'
}

interface SchemeInfo {
  schemeId: number | null
  schemeName: string
}

interface RectPhoto {
  localPath: string
}

const RECT_MAP: Record<string, string> = {
  PENDING: '待整改',
  SUBMITTED: '待复查',
  APPROVED: '已闭环',
  REJECTED: '已驳回'
}
function rectStatusText(s?: string) {
  return s ? (RECT_MAP[s] ?? s) : '待整改'
}

const pageLoading = ref(true)
const submitting = ref(false)
const inspectionId = ref<number>(0)
const schemeInfo = ref<SchemeInfo>({ schemeId: null, schemeName: '' })
const checkItems = ref<CheckItem[]>([])
const inspection = ref<any>({})

// 整改闭环状态
const rectifications = ref<any[]>([])
const rectLoading = ref(false)
const rectSubmitting = ref(false)
const rectForm = ref({ rectificationContent: '' })
const rectPhotos = ref<RectPhoto[]>([])
const photoUploading = ref(false)

// 统计
const passCount = computed(() => checkItems.value.filter(i => i.result === 'PASS').length)
const failCount = computed(() => checkItems.value.filter(i => i.result === 'FAIL').length)
const uncheckedCount = computed(() => checkItems.value.filter(i => i.result === 'UNCHECKED').length)

onMounted(() => {
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1] as any
  const options = currentPage.$page?.options || currentPage.options || {}
  inspectionId.value = Number(options.id) || 0

  if (inspectionId.value) {
    loadInspectionDetail()
    loadRectifications()
  } else {
    pageLoading.value = false
  }
})

async function loadInspectionDetail() {
  try {
    const res: any = await getInspectionDetail(inspectionId.value)
    const inspectionData = res.data
    inspection.value = inspectionData || {}

    // 解析 schemeSnapshot JSON
    if (inspectionData.schemeSnapshot) {
      const snapshot = typeof inspectionData.schemeSnapshot === 'string'
        ? JSON.parse(inspectionData.schemeSnapshot)
        : inspectionData.schemeSnapshot

      schemeInfo.value = {
        schemeId: snapshot.schemeId || null,
        schemeName: snapshot.schemeName || ''
      }

      // 构建检查项列表，合并已有结果
      const existingResults = inspectionData.details || []
      const items: CheckItem[] = (snapshot.items || []).map((item: any, idx: number) => {
        const existing = existingResults[idx]
        return {
          itemName: item.itemName || '',
          checkStandard: item.checkStandard || '',
          checkMethod: item.checkMethod || '',
          result: existing?.result || 'UNCHECKED'
        }
      })
      checkItems.value = items
    }
  } catch (e) {
    uni.showToast({ title: '加载检查详情失败', icon: 'none' })
  } finally {
    pageLoading.value = false
  }
}

function markResult(index: number, result: 'PASS' | 'FAIL' | 'UNCHECKED') {
  checkItems.value[index].result = result
}

async function handleSubmit() {
  // 检查是否有未标记的项
  const unmarked = checkItems.value.filter(i => i.result === 'UNCHECKED')
  if (unmarked.length > 0) {
    uni.showModal({
      title: '提示',
      content: `还有 ${unmarked.length} 项未检查，确定提交吗？`,
      success: (res) => {
        if (res.confirm) {
          doSubmit()
        }
      }
    })
  } else {
    doSubmit()
  }
}

async function doSubmit() {
  submitting.value = true
  try {
    const results = checkItems.value.map((item, index) => ({
      index,
      itemName: item.itemName,
      result: item.result
    }))
    await submitInspectionResults(inspectionId.value, { results })
    uni.showToast({ title: '提交成功', icon: 'success' })
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch (e) {
    // 错误已在 request 层统一 toast 提示，此处不重复弹框
  } finally {
    submitting.value = false
  }
}

// ================= 整改闭环 =================
async function loadRectifications() {
  rectLoading.value = true
  try {
    const res: any = await getRectifications(inspectionId.value)
    rectifications.value = res.data || []
  } catch (e) {
    // 错误已在 request 层统一 toast 提示
    rectifications.value = []
  } finally {
    rectLoading.value = false
  }
}

/** 选择整改佐证照片（本地缓存，提交整改时逐张上传） */
function takeRectPhoto() {
  uni.chooseImage({
    count: 9 - rectPhotos.value.length,
    sourceType: ['camera', 'album'],
    success: (res) => {
      const paths = res.tempFilePaths || []
      paths.forEach((p) => rectPhotos.value.push({ localPath: p }))
    },
    fail: (err) => {
      // 用户取消不提示；其他错误明示
      if ((err as any)?.errMsg && !(err as any).errMsg.includes('cancel')) {
        uni.showToast({ title: '选取照片失败', icon: 'none' })
      }
    }
  })
}

function previewRectPhoto(idx: number) {
  uni.previewImage({ urls: rectPhotos.value.map(p => p.localPath), current: idx })
}

function removeRectPhoto(idx: number) {
  rectPhotos.value.splice(idx, 1)
}

async function handleSubmitRectification() {
  if (!rectForm.value.rectificationContent || !rectForm.value.rectificationContent.trim()) {
    uni.showToast({ title: '请填写整改内容', icon: 'none' })
    return
  }
  rectSubmitting.value = true
  photoUploading.value = true
  try {
    // 逐张上传照片，收集附件 ID（上传失败会在 uploadRectificationPhoto 内 toast 并 reject）
    const attachmentIds: number[] = []
    for (const photo of rectPhotos.value) {
      const fileId = await uploadRectificationPhoto(photo.localPath, inspectionId.value)
      attachmentIds.push(fileId)
    }
    await submitRectification(inspectionId.value, {
      rectificationContent: rectForm.value.rectificationContent.trim(),
      attachmentIds: attachmentIds.join(',')
    })
    uni.showToast({ title: '整改提交成功', icon: 'success' })
    rectForm.value.rectificationContent = ''
    rectPhotos.value = []
    // 刷新检查详情与整改记录
    await Promise.all([loadInspectionDetail(), loadRectifications()])
  } catch (e) {
    // 错误已在 request/upload 层统一 toast 提示
  } finally {
    rectSubmitting.value = false
    photoUploading.value = false
  }
}

function handleApprove(rec: any) {
  uni.showModal({
    title: '复查确认',
    content: '确认该整改已复查通过并闭环？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await approveRectification(rec.id)
        uni.showToast({ title: '复查通过', icon: 'success' })
        await Promise.all([loadInspectionDetail(), loadRectifications()])
      } catch (e) {
        // 错误已在 request 层统一 toast 提示
      }
    }
  })
}
</script>

<style scoped>
.inspection-detail-page {
  padding: 20rpx;
  padding-bottom: 200rpx;
  min-height: 100vh;
  background: #f5f5f5;
}
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 100rpx 0;
  color: #909399;
  font-size: 28rpx;
}
.info-card {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
}
.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12rpx 0;
}
.info-label {
  font-size: 26rpx;
  color: #909399;
}
.info-value {
  font-size: 28rpx;
  color: #303133;
  font-weight: 500;
}
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 120rpx 0;
}
.empty-text {
  font-size: 28rpx;
  color: #c0c4cc;
}
.check-list {
  margin-bottom: 20rpx;
}
.check-item {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.item-header {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
}
.item-index {
  width: 44rpx;
  height: 44rpx;
  line-height: 44rpx;
  text-align: center;
  background: #409eff;
  color: #fff;
  border-radius: 50%;
  font-size: 24rpx;
  margin-right: 16rpx;
  flex-shrink: 0;
}
.item-name {
  font-size: 30rpx;
  color: #303133;
  font-weight: 500;
  flex: 1;
}
.item-detail {
  display: flex;
  padding: 8rpx 0 8rpx 60rpx;
}
.detail-label {
  font-size: 24rpx;
  color: #909399;
  flex-shrink: 0;
}
.detail-text {
  font-size: 24rpx;
  color: #606266;
  flex: 1;
}
.result-section {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #f0f0f0;
}
.result-label {
  font-size: 26rpx;
  color: #606266;
  margin-right: 16rpx;
  flex-shrink: 0;
}
.result-options {
  display: flex;
  gap: 16rpx;
  flex: 1;
}
.result-option {
  padding: 10rpx 24rpx;
  border: 1rpx solid #dcdfe6;
  border-radius: 6rpx;
  font-size: 24rpx;
  color: #606266;
  text-align: center;
}
.result-option.active.pass {
  border-color: #67c23a;
  color: #67c23a;
  background: #f0f9eb;
}
.result-option.active.fail {
  border-color: #f56c6c;
  color: #f56c6c;
  background: #fef0f0;
}
.result-option.active.unchecked {
  border-color: #e6a23c;
  color: #e6a23c;
  background: #fdf6ec;
}
.submit-area {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  padding: 20rpx 32rpx;
  box-shadow: 0 -4rpx 12rpx rgba(0, 0, 0, 0.06);
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
}
.summary {
  text-align: center;
  margin-bottom: 16rpx;
}
.summary-text {
  font-size: 24rpx;
  color: #909399;
}
.submit-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: #409eff;
  color: #fff;
  font-size: 32rpx;
  border-radius: 8rpx;
  border: none;
}
/* 整改状态文案色 */
.rect-PENDING { color: #e6a23c; }
.rect-SUBMITTED { color: #909399; }
.rect-APPROVED { color: #67c23a; }
.rect-REJECTED { color: #f56c6c; }
/* 整改闭环卡片 */
.rect-card {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-top: 20rpx;
}
.rect-title {
  font-size: 30rpx;
  color: #303133;
  font-weight: 600;
  margin-bottom: 20rpx;
}
.rect-form {
  padding-bottom: 8rpx;
}
.rect-textarea {
  width: 100%;
  height: 180rpx;
  border: 1rpx solid #f0f0f0;
  border-radius: 8rpx;
  padding: 16rpx;
  font-size: 26rpx;
  box-sizing: border-box;
  margin-bottom: 16rpx;
}
.photo-btn {
  width: 100%;
  height: 80rpx;
  line-height: 80rpx;
  background: #ecf5ff;
  color: #409eff;
  font-size: 28rpx;
  border-radius: 8rpx;
  border: 1rpx dashed #409eff;
  margin-bottom: 16rpx;
}
.rect-photo-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 16rpx;
}
.rect-photo-item {
  position: relative;
  width: 160rpx;
  height: 160rpx;
}
.rect-thumb {
  width: 160rpx;
  height: 160rpx;
  border-radius: 8rpx;
}
.rect-remove {
  position: absolute;
  top: -12rpx;
  right: -12rpx;
  width: 36rpx;
  height: 36rpx;
  line-height: 32rpx;
  text-align: center;
  background: #f56c6c;
  color: #fff;
  border-radius: 50%;
  font-size: 28rpx;
}
.rect-submit-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  background: #67c23a;
  color: #fff;
  font-size: 32rpx;
  border-radius: 8rpx;
  border: none;
}
.rect-divider {
  height: 1rpx;
  background: #f0f0f0;
  margin: 24rpx 0;
}
.rect-loading,
.rect-empty {
  text-align: center;
  padding: 40rpx 0;
  color: #c0c4cc;
  font-size: 26rpx;
}
.rect-record {
  border: 1rpx solid #f0f0f0;
  border-radius: 8rpx;
  padding: 20rpx;
  margin-bottom: 16rpx;
}
.rect-record-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.rect-status {
  font-size: 26rpx;
  font-weight: 500;
}
.rect-status.st-SUBMITTED { color: #909399; }
.rect-status.st-APPROVED { color: #67c23a; }
.rect-status.st-REJECTED { color: #f56c6c; }
.rect-status.st-PENDING { color: #e6a23c; }
.rect-time {
  font-size: 24rpx;
  color: #c0c4cc;
}
.rect-record-content {
  font-size: 26rpx;
  color: #606266;
  margin-bottom: 12rpx;
}
.rect-approve-btn {
  width: 100%;
  height: 72rpx;
  line-height: 72rpx;
  background: #67c23a;
  color: #fff;
  font-size: 28rpx;
  border-radius: 8rpx;
  border: none;
}
</style>
