<template>
  <ZwiFormPage submitText="提交付款" :loading="submitting" @submit="handleSubmit">
    <view class="form-card">
      <view class="form-item" @click="showProjectPicker = true">
        <text class="form-label">项目</text>
        <text class="form-value" :class="{ placeholder: !form.projectName }">{{ form.projectName || '请选择项目' }}</text>
      </view>
      <ZwiField label="付款人" v-model="form.payerName" placeholder="请输入付款人" />
      <ZwiField label="付款金额" v-model="form.paymentAmount" inputType="digit" placeholder="请输入付款金额" />
      <ZwiField label="付款日期" v-model="form.paymentDate" placeholder="YYYY-MM-DD" />
      <ZwiField label="备注" v-model="form.remark" placeholder="请输入备注（费用类型/用途等）" />
    </view>


    <view v-if="showProjectPicker" class="picker-mask" @click="showProjectPicker = false">
      <view class="picker-panel" @click.stop>
        <view class="picker-title"><text>选择项目</text></view>
        <view class="picker-item" v-for="p in projects" :key="p.id" @click="selectProject(p)">
          <text>{{ p.projectName }}</text>
        </view>
        <view v-if="!projects.length" class="picker-item"><text>{{ projectEmptyTip }}</text></view>
      </view>
    </view>
  </ZwiFormPage>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { saveOtherPayment } from '@/api/common'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import { submitOrQueue } from '@/utils/offlineSubmit'
import ZwiFormPage from '@/components/zwi/ZwiFormPage.vue'
import ZwiField from '@/components/zwi/ZwiField.vue'

const submitting = ref(false)
const showProjectPicker = ref(false)
const projects = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')
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
    // paymentDate 用于 @FinanceLockCheck 封账校验（离线入队后同步时同样会被后端拦截）
    const payload = {
      projectId: form.value.projectId,
      payerName: form.value.payerName,
      paymentAmount: amount,
      paymentDate: form.value.paymentDate,
      remark: form.value.remark
    }
    // 离线时入队（需求 5.1），联网后由 syncEngine 自动提交
    const { queued } = await submitOrQueue(() => saveOtherPayment(payload), {
      endpoint: '/v1/finance/other-payment',
      payload
    })
    if (!queued) {
      uni.showToast({ title: '提交成功', icon: 'success' })
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
