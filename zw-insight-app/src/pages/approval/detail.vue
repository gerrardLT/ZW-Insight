<template>
  <view class="detail-page">
    <view class="loading-mask" v-if="loading">
      <text>加载中...</text>
    </view>

    <template v-if="!loading">
      <!-- 业务信息 -->
      <view class="section">
        <view class="section-title">审批信息</view>
        <view class="info-row">
          <text class="info-label">流程名称</text>
          <text class="info-value">{{ detail.processName }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">申请人</text>
          <text class="info-value">{{ detail.startUserName }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">申请时间</text>
          <text class="info-value">{{ detail.createTime }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">业务摘要</text>
          <text class="info-value">{{ detail.businessTitle || '-' }}</text>
        </view>
      </view>

      <!-- 业务详情 -->
      <view class="section" v-if="detail.businessData">
        <view class="section-title">业务详情</view>
        <view class="info-row" v-for="(val, key) in detail.businessData" :key="key">
          <text class="info-label">{{ key }}</text>
          <text class="info-value">{{ val }}</text>
        </view>
      </view>

      <!-- 审批意见 -->
      <view class="section" v-if="detail.status !== 'done'">
        <view class="section-title">审批意见</view>
        <view class="textarea-wrap">
          <textarea v-model="comment" placeholder="请输入审批意见" class="textarea" :maxlength="500" />
        </view>
      </view>

      <!-- 操作按钮 -->
      <view class="action-bar" v-if="detail.status !== 'done'">
        <button class="btn-reject" @click="handleReject" :loading="submitting">退回</button>
        <button class="btn-approve" @click="handleApprove" :loading="submitting">通过</button>
      </view>

      <!-- 审批记录 -->
      <view class="section" v-if="detail.approvalRecords && detail.approvalRecords.length">
        <view class="section-title">审批记录</view>
        <view class="record-item" v-for="record in detail.approvalRecords" :key="record.id">
          <view class="record-header">
            <text class="record-user">{{ record.assigneeName }}</text>
            <text class="record-result" :class="record.result">{{ record.resultText }}</text>
          </view>
          <text class="record-comment" v-if="record.comment">{{ record.comment }}</text>
          <text class="record-time">{{ record.endTime }}</text>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { completeTask, rejectTask } from '@/api/common'
import request from '@/utils/request'

const detail = ref<any>({})
const comment = ref('')
const loading = ref(true)
const submitting = ref(false)
let taskId = ''
let processInstanceId = ''

onLoad((options: any) => {
  taskId = options.taskId || ''
  processInstanceId = options.processInstanceId || ''
  loadDetail()
})

async function loadDetail() {
  loading.value = true
  try {
    const res: any = await request({
      url: `/v1/workflow/approval/detail/${taskId}`,
    })
    detail.value = res.data || {}
  } catch {} finally {
    loading.value = false
  }
}

async function handleApprove() {
  if (submitting.value) return
  submitting.value = true
  try {
    await completeTask({ taskId, comment: comment.value, processInstanceId })
    uni.showToast({ title: '审批通过', icon: 'success' })
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch {} finally {
    submitting.value = false
  }
}

async function handleReject() {
  if (submitting.value) return
  if (!comment.value.trim()) {
    uni.showToast({ title: '退回需填写意见', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await rejectTask({ taskId, comment: comment.value, processInstanceId })
    uni.showToast({ title: '已退回', icon: 'success' })
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch {} finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.detail-page { padding: 20rpx; padding-bottom: 160rpx; }
.loading-mask { display: flex; justify-content: center; align-items: center; height: 400rpx; color: #909399; }
.section { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 20rpx; }
.section-title { font-size: 28rpx; font-weight: bold; color: #303133; margin-bottom: 16rpx; padding-bottom: 12rpx; border-bottom: 1rpx solid #f0f0f0; }
.info-row { display: flex; justify-content: space-between; padding: 12rpx 0; }
.info-label { font-size: 26rpx; color: #909399; min-width: 160rpx; }
.info-value { font-size: 26rpx; color: #303133; flex: 1; text-align: right; }
.textarea-wrap { margin-top: 12rpx; }
.textarea { width: 100%; height: 200rpx; border: 1rpx solid #dcdfe6; border-radius: 8rpx; padding: 16rpx; font-size: 26rpx; box-sizing: border-box; }
.action-bar { position: fixed; bottom: 0; left: 0; right: 0; display: flex; gap: 20rpx; padding: 20rpx 30rpx; background: #fff; box-shadow: 0 -2rpx 8rpx rgba(0,0,0,0.05); }
.btn-reject { flex: 1; height: 80rpx; line-height: 80rpx; background: #fff; color: #f56c6c; border: 1rpx solid #f56c6c; border-radius: 8rpx; font-size: 28rpx; }
.btn-approve { flex: 1; height: 80rpx; line-height: 80rpx; background: #409eff; color: #fff; border: none; border-radius: 8rpx; font-size: 28rpx; }
.record-item { padding: 16rpx 0; border-bottom: 1rpx solid #f0f0f0; }
.record-item:last-child { border-bottom: none; }
.record-header { display: flex; justify-content: space-between; align-items: center; }
.record-user { font-size: 26rpx; color: #303133; }
.record-result { font-size: 24rpx; padding: 2rpx 12rpx; border-radius: 4rpx; }
.record-result.approved { background: #f0f9eb; color: #67c23a; }
.record-result.rejected { background: #fef0f0; color: #f56c6c; }
.record-comment { font-size: 24rpx; color: #606266; margin-top: 8rpx; display: block; }
.record-time { font-size: 22rpx; color: #c0c4cc; margin-top: 4rpx; display: block; }
</style>
