<template>
  <ZwiFormPage submitText="确认归还" :loading="submitting" @submit="handleSubmit">
    <OfflineBanner />
    <!-- 未还清备用金列表（status=APPROVED） -->
    <view class="form-card">
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

    <view class="form-card">
      <ZwiField label="归还金额" v-model="form.returnAmount" inputType="digit" placeholder="请输入归还金额" />
      <ZwiField label="归还日期" v-model="form.returnDate" placeholder="YYYY-MM-DD" />
    </view>

  </ZwiFormPage>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getReserveFundApplyPage, saveReserveFundReturn } from '@/api/common'
import { submitOrQueue } from '@/utils/offlineSubmit'
import OfflineBanner from '@/components/OfflineBanner.vue'
import ZwiFormPage from '@/components/zwi/ZwiFormPage.vue'
import ZwiField from '@/components/zwi/ZwiField.vue'

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
    // 已审批通过的备用金申请中筛未结清（returnedAmount + offsetAmount < applyAmount，
    // 与后端 ReserveFundReturnService 待归还口径一致）；按 total 循环拉全量避免超页遗漏
    const all: any[] = []
    let page = 1
    let total = Infinity
    while (all.length < total) {
      const res: any = await getReserveFundApplyPage({ page, size: 100, status: 'APPROVED' })
      const records = res.data?.records || []
      total = Number(res.data?.total || records.length)
      all.push(...records)
      if (!records.length) break
      page++
    }
    pendingList.value = all.filter((r: any) =>
      Number(r.returnedAmount || 0) + Number(r.offsetAmount || 0) < Number(r.applyAmount || 0))
  } catch {}
})

function remaining(item: any) {
  // 后端口径：applyAmount - returnedAmount - offsetAmount（报销冲抵会写大 offsetAmount）
  return Number((Number(item.applyAmount || 0)
    - Number(item.returnedAmount || 0)
    - Number(item.offsetAmount || 0)).toFixed(2))
}

function selectFund(item: any) {
  form.value.reserveApplyId = item.id
  form.value.returnAmount = String(remaining(item))
}

async function handleSubmit() {
  if (!form.value.reserveApplyId) {
    uni.showToast({ title: '请选择备用金记录', icon: 'none' }); return
  }
  const amount = Number(form.value.returnAmount)
  if (!Number.isFinite(amount) || amount <= 0) {
    uni.showToast({ title: '请输入归还金额', icon: 'none' }); return
  }
  const fund = pendingList.value.find((f) => f.id === form.value.reserveApplyId)
  if (fund && amount > remaining(fund)) {
    uni.showToast({ title: '归还金额不能超过剩余未还金额', icon: 'none' }); return
  }
  submitting.value = true
  try {
    // 后端 BizReserveFundReturn：reserveApplyId/returnAmount/returnDate
    const payload = {
      reserveApplyId: form.value.reserveApplyId,
      returnAmount: amount,
      returnDate: form.value.returnDate
    }
    // 离线时入队（需求 5.1），联网后由 syncEngine 自动提交
    const { queued } = await submitOrQueue(() => saveReserveFundReturn(payload), {
      endpoint: '/v1/finance/reserve-fund/return',
      payload
    })
    if (!queued) {
      uni.showToast({ title: '归还成功', icon: 'success' })
    }
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
