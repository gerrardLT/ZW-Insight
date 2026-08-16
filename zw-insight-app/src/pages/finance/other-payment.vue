<template>
  <view class="form-page">
    <view class="form-section">
      <view class="form-item" @click="showProjectPicker = true">
        <text class="form-label">项目</text>
        <text class="form-value" :class="{ placeholder: !form.projectName }">{{ form.projectName || '请选择项目' }}</text>
      </view>
      <view class="form-item">
        <text class="form-label">付款人</text>
        <input v-model="form.payerName" placeholder="请输入付款人" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">付款金额</text>
        <input v-model="form.paymentAmount" type="number" placeholder="请输入付款金额" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">付款日期</text>
        <input v-model="form.paymentDate" placeholder="YYYY-MM-DD" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">备注</text>
        <input v-model="form.remark" placeholder="请输入备注（费用类型/用途等）" class="form-input" />
      </view>
    </view>

    <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交付款</button>

    <view v-if="showProjectPicker" class="picker-mask" @click="showProjectPicker = false">
      <view class="picker-panel" @click.stop>
        <view class="picker-title"><text>选择项目</text></view>
        <view class="picker-item" v-for="p in projects" :key="p.id" @click="selectProject(p)">
          <text>{{ p.projectName }}</text>
        </view>
        <view v-if="!projects.length" class="picker-item"><text>暂无项目</text></view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getProjectList, saveOtherPayment } from '@/api/common'

const submitting = ref(false)
const showProjectPicker = ref(false)
const projects = ref<any[]>([])
// 表单字段对齐后端 BizOtherPayment：projectId/payerName/paymentDate/paymentAmount/remark
const form = ref({
  projectId: null as number | null,
  projectName: '',
  payerName: '',
  paymentAmount: '',
  paymentDate: '',
  remark: ''
})

onMounted(async () => {
  const now = new Date()
  form.value.paymentDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  try {
    const res: any = await getProjectList({ page: 1, size: 100 })
    projects.value = res.data?.records || []
  } catch {}
})

function selectProject(p: any) {
  form.value.projectId = p.id
  form.value.projectName = p.projectName
  showProjectPicker.value = false
}

async function handleSubmit() {
  if (!form.value.projectId) {
    uni.showToast({ title: '请选择项目', icon: 'none' }); return
  }
  if (!form.value.payerName) {
    uni.showToast({ title: '请输入付款人', icon: 'none' }); return
  }
  const amount = Number(form.value.paymentAmount)
  if (!Number.isFinite(amount) || amount <= 0) {
    uni.showToast({ title: '付款金额必须大于0', icon: 'none' }); return
  }
  submitting.value = true
  try {
    // 后端保存即生效（status=APPROVED）并回写项目其他总支出；
    // paymentDate 用于 @FinanceLockCheck 封账校验
    await saveOtherPayment({
      projectId: form.value.projectId,
      payerName: form.value.payerName,
      paymentAmount: amount,
      paymentDate: form.value.paymentDate,
      remark: form.value.remark
    })
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
.form-value { flex: 1; font-size: 28rpx; color: #303133; text-align: right; }
.form-value.placeholder { color: #c0c4cc; }
.form-input { flex: 1; font-size: 28rpx; color: #303133; text-align: right; }
.submit-btn { margin: 40rpx 20rpx; height: 88rpx; line-height: 88rpx; background: #409eff; color: #fff; font-size: 32rpx; border-radius: 8rpx; border: none; }
.picker-mask { position: fixed; left: 0; top: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: flex-end; z-index: 99; }
.picker-panel { width: 100%; max-height: 60vh; background: #fff; border-radius: 24rpx 24rpx 0 0; padding: 20rpx; overflow-y: auto; }
.picker-title { text-align: center; font-size: 30rpx; font-weight: bold; padding: 16rpx 0; }
.picker-item { padding: 24rpx; border-bottom: 1rpx solid #f5f5f5; font-size: 28rpx; }
</style>
