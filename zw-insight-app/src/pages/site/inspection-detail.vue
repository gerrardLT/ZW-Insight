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
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getInspectionDetail, submitInspectionResults } from '@/api/common'

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

const pageLoading = ref(true)
const submitting = ref(false)
const inspectionId = ref<number>(0)
const schemeInfo = ref<SchemeInfo>({ schemeId: null, schemeName: '' })
const checkItems = ref<CheckItem[]>([])

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
  } else {
    pageLoading.value = false
  }
})

async function loadInspectionDetail() {
  try {
    const res: any = await getInspectionDetail(inspectionId.value)
    const inspection = res.data

    // 解析 schemeSnapshot JSON
    if (inspection.schemeSnapshot) {
      const snapshot = typeof inspection.schemeSnapshot === 'string'
        ? JSON.parse(inspection.schemeSnapshot)
        : inspection.schemeSnapshot

      schemeInfo.value = {
        schemeId: snapshot.schemeId || null,
        schemeName: snapshot.schemeName || ''
      }

      // 构建检查项列表，合并已有结果
      const existingResults = inspection.details || []
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
    // 错误已在 request 中统一处理
  } finally {
    submitting.value = false
  }
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
</style>
