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
          <view class="empty" v-if="!projects.length"><text>{{ projectEmptyTip }}</text></view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { savePaymentApply } from '@/api/common'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import { submitOrQueue } from '@/utils/offlineSubmit'

const submitting = ref(false)
const showProjectPicker = ref(false)
const projects = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')

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
  const now = new Date()
  form.value.expectedDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  // 在线优先 + 离线回退缓存（需求 4.2、4.8）
  const res = await loadProjectList({ page: 1, size: 100 })
  projects.value = res.records
  projectEmptyTip.value = res.empty && res.fromCache ? NO_OFFLINE_DATA_TIP : '暂无项目'
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
  const amountNum = Number(form.value.amount)
  if (!form.value.amount || !Number.isFinite(amountNum) || amountNum <= 0) {
    uni.showToast({ title: '请输入付款金额', icon: 'none' }); return
  }
  if (!form.value.payee) {
    uni.showToast({ title: '请输入收款方', icon: 'none' }); return
  }
  submitting.value = true
  try {
    const payload = {
      projectId: form.value.projectId,
      amount: amountNum,
      paymentAmount: amountNum,
      supplierName: form.value.payee,
      payee: form.value.payee,
      payeeAccount: form.value.payeeAccount,
      payeeBank: form.value.payeeBank,
      reason: form.value.reason,
      payMethod: form.value.payMethod,
      expectedDate: form.value.expectedDate,
      paymentDate: form.value.expectedDate,
      remark: form.value.remark
    }
    // 离线时入队（需求 5.1），联网后由 syncEngine 自动提交
    const { queued } = await submitOrQueue(() => savePaymentApply(payload), {
      endpoint: '/v1/finance/payment-apply',
      payload
    })
    if (!queued) {
      uni.showToast({ title: '提交成功', icon: 'success' })
    }
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch {} finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.form-page { padding: 20rpx; min-height: 44px; padding-bottom: 120rpx; }
.form-section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border); border-radius: var(--zw-radius-xs); padding: 0 24rpx; margin-bottom: 20rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx; min-height: 44px;  border-bottom: 1rpx solid var(--zw-border-light); }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: var(--zw-text-primary); min-width: 160rpx; }
.form-input { flex: 1; font-size: 28rpx; color: var(--zw-text-primary); text-align: right; font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.form-input.picker { display: flex; align-items: center; justify-content: flex-end; }
.placeholder { color: var(--zw-text-quaternary); }
.arrow { margin-left: 8rpx; color: var(--zw-text-quaternary); font-size: 32rpx; }
.radio-group { display: flex; gap: 20rpx; flex: 1; justify-content: flex-end; }
.radio-item { padding: 0 24rpx; min-height: 44px; display: flex; align-items: center; justify-content: center; border: 1rpx solid var(--zw-border); border-radius: var(--zw-radius-xs); font-size: 26rpx; color: var(--zw-text-secondary); }
.radio-item.active { border-color: var(--zw-brand); color: var(--zw-brand); background: var(--zw-brand-light); font-weight: 500; }
.submit-btn { margin: 40rpx 20rpx; min-height: 44px; display: flex; align-items: center; justify-content: center; line-height: 1; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 32rpx; font-weight: 600; border-radius: var(--zw-radius-xs); border: none; } /* 橙底深字承重规则；P0 触控达标：88rpx→min-height 44px */
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-md) var(--zw-radius-md) 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx; ;  border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx; ;  border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; }
.empty { text-align: center; padding: 40rpx; min-height: 44px; color: var(--zw-text-quaternary); }
</style>
