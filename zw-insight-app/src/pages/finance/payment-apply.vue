<template>
  <view class="form-page">
    <view class="form-section">
      <view class="form-item" @click="showProjectPicker = true">
        <text class="form-label">所属项目</text>
        <view class="form-input picker">
          <text :class="{ placeholder: !form.projectName }">{{ form.projectName || '请选择项目' }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
    </view>

    <view class="form-section">
      <view class="form-item">
        <text class="form-label">付款金额(元)</text>
        <input v-model="form.amount" type="digit" placeholder="请输入付款金额" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">收款方</text>
        <input v-model="form.payee" placeholder="请输入收款方名称" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">收款账号</text>
        <input v-model="form.payeeAccount" placeholder="请输入收款账号" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">开户行</text>
        <input v-model="form.payeeBank" placeholder="请输入开户行" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">付款事由</text>
        <input v-model="form.reason" placeholder="如：材料采购款" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">付款方式</text>
        <view class="radio-group">
          <view class="radio-item" :class="{ active: form.payMethod === '银行转账' }" @click="form.payMethod = '银行转账'">
            <text>转账</text>
          </view>
          <view class="radio-item" :class="{ active: form.payMethod === '承兑汇票' }" @click="form.payMethod = '承兑汇票'">
            <text>汇票</text>
          </view>
        </view>
      </view>
      <view class="form-item">
        <text class="form-label">期望付款日</text>
        <input v-model="form.expectedDate" placeholder="YYYY-MM-DD" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">备注</text>
        <input v-model="form.remark" placeholder="请输入备注" class="form-input" />
      </view>
    </view>

    <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交申请</button>

    <!-- 项目选择弹窗 -->
    <view class="picker-mask" v-if="showProjectPicker" @click="showProjectPicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text @click="showProjectPicker = false">取消</text>
          <text class="picker-title">选择项目</text>
          <text></text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view class="picker-item" v-for="p in projects" :key="p.id" @click="selectProject(p)">
            <text>{{ p.projectName }}</text>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getProjectList, savePaymentApply } from '@/api/common'

const submitting = ref(false)
const showProjectPicker = ref(false)
const projects = ref<any[]>([])

const form = ref({
  projectId: null as number | null,
  projectName: '',
  amount: '',
  payee: '',
  payeeAccount: '',
  payeeBank: '',
  reason: '',
  payMethod: '银行转账',
  expectedDate: '',
  remark: ''
})

onMounted(async () => {
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
  if (!form.value.amount) {
    uni.showToast({ title: '请输入付款金额', icon: 'none' }); return
  }
  if (!form.value.payee) {
    uni.showToast({ title: '请输入收款方', icon: 'none' }); return
  }
  submitting.value = true
  try {
    await savePaymentApply({
      projectId: form.value.projectId,
      amount: Number(form.value.amount),
      payee: form.value.payee,
      payeeAccount: form.value.payeeAccount,
      payeeBank: form.value.payeeBank,
      reason: form.value.reason,
      payMethod: form.value.payMethod,
      expectedDate: form.value.expectedDate,
      remark: form.value.remark
    })
    uni.showToast({ title: '提交成功', icon: 'success' })
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch {} finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.form-page { padding: 20rpx; padding-bottom: 120rpx; }
.form-section { background: #fff; border-radius: 12rpx; padding: 0 24rpx; margin-bottom: 20rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx 0; border-bottom: 1rpx solid #f5f5f5; }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: #303133; min-width: 160rpx; }
.form-input { flex: 1; font-size: 28rpx; color: #303133; text-align: right; }
.form-input.picker { display: flex; align-items: center; justify-content: flex-end; }
.placeholder { color: #c0c4cc; }
.arrow { margin-left: 8rpx; color: #c0c4cc; font-size: 32rpx; }
.radio-group { display: flex; gap: 20rpx; flex: 1; justify-content: flex-end; }
.radio-item { padding: 8rpx 24rpx; border: 1rpx solid #dcdfe6; border-radius: 6rpx; font-size: 26rpx; color: #606266; }
.radio-item.active { border-color: #409eff; color: #409eff; background: #ecf5ff; }
.submit-btn { margin: 40rpx 20rpx; height: 88rpx; line-height: 88rpx; background: #409eff; color: #fff; font-size: 32rpx; border-radius: 8rpx; border: none; }
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: #fff; border-radius: 24rpx 24rpx 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx 32rpx; border-bottom: 1rpx solid #f0f0f0; }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx 32rpx; border-bottom: 1rpx solid #f5f5f5; font-size: 28rpx; }
</style>
