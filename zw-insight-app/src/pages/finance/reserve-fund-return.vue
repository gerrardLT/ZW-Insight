<template>
  <view class="form-page">
    <OfflineBanner />
    <!-- 未还清备用金列表（status=APPROVED） -->
    <view class="form-section">
      <view class="section-title"><text>选择备用金记录</text></view>
      <view
        class="fund-item"
        v-for="item in pendingList"
        :key="item.id"
        :class="{ selected: form.reserveApplyId === item.id }"
        @click="selectFund(item)"
      >
        <view class="fund-line">
          <text class="fund-applicant">{{ item.applicant || '-' }}</text>
          <text class="fund-amount">申请 {{ item.applyAmount }} 元</text>
        </view>
        <view class="fund-line sub">
          <text>已还 {{ item.returnedAmount || 0 }} 元 · 剩余 {{ remaining(item) }} 元</text>
          <text>{{ item.applyDate }}</text>
        </view>
      </view>
      <view v-if="!pendingList.length" class="empty"><text>暂无未还清的备用金记录</text></view>
    </view>

    <view class="form-section">
      <view class="form-item">
        <text class="form-label">归还金额</text>
        <input v-model="form.returnAmount" type="number" placeholder="请输入归还金额" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">归还日期</text>
        <input v-model="form.returnDate" placeholder="YYYY-MM-DD" class="form-input" />
      </view>
    </view>

    <button class="submit-btn" :loading="submitting" @click="handleSubmit">确认归还</button>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getReserveFundApplyPage, saveReserveFundReturn } from '@/api/common'
import OfflineBanner from '@/components/OfflineBanner.vue'

const submitting = ref(false)
const pendingList = ref<any[]>([])
const form = ref({
  reserveApplyId: null as number | null,
  returnAmount: '',
  returnDate: ''
})

onMounted(async () => {
  const now = new Date()
  form.value.returnDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  try {
    // 已审批通过的备用金申请中筛未还清（returnedAmount < applyAmount）
    const res: any = await getReserveFundApplyPage({ page: 1, size: 100, status: 'APPROVED' })
    const records = res.data?.records || []
    pendingList.value = records.filter((r: any) => Number(r.returnedAmount || 0) < Number(r.applyAmount || 0))
  } catch {}
})

function remaining(item: any) {
  return Number((Number(item.applyAmount || 0) - Number(item.returnedAmount || 0)).toFixed(2))
}

function selectFund(item: any) {
  form.value.reserveApplyId = item.id
  form.value.returnAmount = String(remaining(item))
}

async function handleSubmit() {
  if (!form.value.reserveApplyId) {
    uni.showToast({ title: '请选择备用金记录', icon: 'none' }); return
  }
  if (!form.value.returnAmount || Number(form.value.returnAmount) <= 0) {
    uni.showToast({ title: '请输入归还金额', icon: 'none' }); return
  }
  const fund = pendingList.value.find((f) => f.id === form.value.reserveApplyId)
  if (fund && Number(form.value.returnAmount) > remaining(fund)) {
    uni.showToast({ title: '归还金额不能超过剩余未还金额', icon: 'none' }); return
  }
  submitting.value = true
  try {
    // 后端 BizReserveFundReturn：reserveApplyId/returnAmount/returnDate
    await saveReserveFundReturn({
      reserveApplyId: form.value.reserveApplyId,
      returnAmount: Number(form.value.returnAmount),
      returnDate: form.value.returnDate
    })
    uni.showToast({ title: '归还成功', icon: 'success' })
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch {} finally { submitting.value = false }
}
</script>

<style scoped>
.form-page { padding: 20rpx; }
.form-section { background: #fff; border-radius: 12rpx; padding: 0 24rpx; margin-bottom: 20rpx; }
.section-title { font-size: 30rpx; font-weight: bold; padding: 24rpx 0 12rpx; }
.fund-item { padding: 20rpx 16rpx; border: 2rpx solid #f0f0f0; border-radius: 10rpx; margin-bottom: 16rpx; }
.fund-item.selected { border-color: #409eff; background: #ecf5ff; }
.fund-line { display: flex; justify-content: space-between; font-size: 28rpx; color: #303133; }
.fund-line.sub { margin-top: 8rpx; font-size: 24rpx; color: #909399; }
.fund-amount { color: #409eff; }
.empty { text-align: center; padding: 40rpx; color: #c0c4cc; font-size: 26rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx 0; border-bottom: 1rpx solid #f5f5f5; }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: #303133; min-width: 160rpx; }
.form-input { flex: 1; font-size: 28rpx; color: #303133; text-align: right; }
.submit-btn { margin: 40rpx 20rpx; height: 88rpx; line-height: 88rpx; background: #409eff; color: #fff; font-size: 32rpx; border-radius: 8rpx; border: none; }
</style>
