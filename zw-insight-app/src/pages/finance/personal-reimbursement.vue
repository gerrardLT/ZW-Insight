<template>
  <view class="form-page">
    <view class="form-section">
      <view class="form-item">
        <text class="form-label">报销金额</text>
        <input v-model="form.totalAmount" type="number" placeholder="请输入报销金额" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">报销日期</text>
        <input v-model="form.reimbursementDate" placeholder="YYYY-MM-DD" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">备注</text>
        <input v-model="form.remark" placeholder="请输入备注（费用事由等）" class="form-input" />
      </view>
    </view>

    <view class="tips">
      <text class="tip-text">提交后自动进入审批流程，审批通过后生效。</text>
    </view>

    <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交报销</button>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { savePersonalReimbursement, submitPersonalReimbursement } from '@/api/common'

const submitting = ref(false)
// 表单字段对齐后端 BizPersonalReimbursement：totalAmount/reimbursementDate/remark
const form = ref({ totalAmount: '', reimbursementDate: '', remark: '' })

onMounted(() => {
  const now = new Date()
  form.value.reimbursementDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
})

async function handleSubmit() {
  const amount = Number(form.value.totalAmount)
  if (!Number.isFinite(amount) || amount <= 0) {
    uni.showToast({ title: '请输入报销金额', icon: 'none' }); return
  }
  if (!form.value.reimbursementDate) {
    uni.showToast({ title: '请输入报销日期', icon: 'none' }); return
  }
  submitting.value = true
  try {
    // 两段式提交（与 web 端一致）：save 落 DRAFT 返回 id → submit 启动审批
    const res: any = await savePersonalReimbursement({
      totalAmount: amount,
      reimbursementDate: form.value.reimbursementDate,
      remark: form.value.remark
    })
    await submitPersonalReimbursement(res.data)
    uni.showToast({ title: '提交成功', icon: 'success' })
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch {} finally { submitting.value = false }
}
</script>

<style scoped>
.form-page { padding: 20rpx; }
.form-section { background: #fff; border-radius: 12rpx; padding: 0 24rpx; margin-bottom: 20rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx 0; border-bottom: 1rpx solid #f5f5f5; }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: #303133; min-width: 160rpx; }
.form-input { flex: 1; font-size: 28rpx; color: #303133; text-align: right; }
.tips { padding: 12rpx 24rpx; }
.tip-text { font-size: 24rpx; color: #909399; }
.submit-btn { margin: 40rpx 20rpx; height: 88rpx; line-height: 88rpx; background: #409eff; color: #fff; font-size: 32rpx; border-radius: 8rpx; border: none; }
</style>
