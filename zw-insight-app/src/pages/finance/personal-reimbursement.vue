<template>
  <ZwiFormPage submitText="提交报销" :loading="submitting" @submit="handleSubmit">
    <view class="form-card">
      <ZwiField label="报销金额" v-model="form.totalAmount" inputType="digit" placeholder="请输入报销金额" />
      <ZwiField label="报销日期" v-model="form.reimbursementDate" placeholder="YYYY-MM-DD" />
      <ZwiField label="备注" v-model="form.remark" placeholder="请输入备注（费用事由等）" />
    </view>

    <view class="tips">
      <text class="tip-text">提交后自动进入审批流程，审批通过后生效。</text>
    </view>

  </ZwiFormPage>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { savePersonalReimbursement, submitPersonalReimbursement } from '@/api/common'
import { rejectIfOffline } from '@/utils/offlineSubmit'
import ZwiFormPage from '@/components/zwi/ZwiFormPage.vue'
import ZwiField from '@/components/zwi/ZwiField.vue'

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
  // 两段式审批无法离线入队（只入队 save 会遗留永久 DRAFT），离线时明确拒绝（不静默）
  if (rejectIfOffline('个人报销需联网提交审批，请联网后重试')) return
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
/* 表单卡壳：收敛后的通用容器（替代原 form-section 21 页重复样式） */
.form-card {
  background: var(--zw-bg-card);
  border: 1rpx solid var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  margin-bottom: 20rpx;
  overflow: hidden;
}
.form-card :deep(.form-item) {
  padding: 0 24rpx;
  margin-bottom: 0;
  border-bottom: 1rpx solid var(--zw-border-light);
}
.form-card :deep(.form-item:last-child) {
  border-bottom: none;
}
/* radio-group 行保留原结构 */
.radio-item-row { display: flex; align-items: center; padding: 24rpx; border-bottom: 1rpx solid var(--zw-border-light); }
/* textarea 行（vertical）保留原结构 */
.form-item.vertical { flex-direction: column; align-items: flex-start; padding: 24rpx; border-bottom: 1rpx solid var(--zw-border-light); }
.form-label { font-size: 28rpx; color: var(--zw-text-primary); min-width: 160rpx; }
.textarea { width: 100%; height: 180rpx; border: 1rpx solid var(--zw-border-light); border-radius: var(--zw-radius-sm); padding: 16rpx; min-height: 44px; font-size: 26rpx; margin-top: 12rpx; box-sizing: border-box; }
.radio-group { display: flex; gap: 20rpx; flex: 1; justify-content: flex-end; }
.radio-item { padding: 0 24rpx; min-height: 44px; display: flex; align-items: center; justify-content: center; border: 1rpx solid var(--zw-border); border-radius: var(--zw-radius-sm); font-size: 26rpx; color: var(--zw-text-secondary); }
.radio-item.active { border-color: var(--zw-brand); color: var(--zw-brand); background: var(--zw-brand-light); }
.total-display { flex: 1; text-align: right; font-size: 36rpx; font-weight: bold; color: var(--zw-brand); font-family: var(--zw-font-mono); }

/* 选择弹窗（原样式保留） */
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-lg) var(--zw-radius-lg) 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx; border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx; border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; }
.empty { text-align: center; padding: 40rpx; min-height: 44px; color: var(--zw-text-quaternary); }
</style>
