<template>
  <ZwiFormPage submitText="提交报销" :loading="submitting" @submit="handleSubmit">
    <OfflineBanner />
    <view class="form-card">
      <ZwiPickerField
        label="所属项目"
        :displayValue="form.projectName"
        placeholder="请选择项目"
        @open="showProjectPicker = true"
      />
    </view>

    <view class="form-card">
      <ZwiField label="报销金额(元)" v-model="form.amount" inputType="digit" placeholder="请输入报销金额" />
      <view class="form-item">
        <text class="form-label">费用类型</text>
        <view class="radio-group-wrap">
          <view class="radio-item" :class="{ active: form.expenseType === '差旅费' }" @click="form.expenseType = '差旅费'">
            <text>差旅费</text>
          </view>
          <view class="radio-item" :class="{ active: form.expenseType === '交通费' }" @click="form.expenseType = '交通费'">
            <text>交通费</text>
          </view>
          <view class="radio-item" :class="{ active: form.expenseType === '办公费' }" @click="form.expenseType = '办公费'">
            <text>办公费</text>
          </view>
          <view class="radio-item" :class="{ active: form.expenseType === '招待费' }" @click="form.expenseType = '招待费'">
            <text>招待费</text>
          </view>
          <view class="radio-item" :class="{ active: form.expenseType === '其他' }" @click="form.expenseType = '其他'">
            <text>其他</text>
          </view>
        </view>
      </view>
      <ZwiField label="发生日期" v-model="form.expenseDate" placeholder="YYYY-MM-DD" />
      <view class="form-item vertical">
        <text class="form-label">费用说明</text>
        <textarea v-model="form.description" placeholder="请描述费用明细" class="textarea" :maxlength="500" />
      </view>
      <ZwiField label="发票张数" v-model="form.invoiceCount" inputType="digit" placeholder="请输入发票张数" />
      <ZwiField label="备注" v-model="form.remark" placeholder="请输入备注" />
    </view>


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
  </ZwiFormPage>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { saveReimbursement, submitReimbursement } from '@/api/common'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import { rejectIfOffline } from '@/utils/offlineSubmit'
import OfflineBanner from '@/components/OfflineBanner.vue'

const submitting = ref(false)
const showProjectPicker = ref(false)
const projects = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')

const form = ref({
  projectId: null as number | null,
  projectName: '',
  amount: '',
  expenseType: '差旅费',
  expenseDate: '',
  description: '',
  invoiceCount: '',
  remark: ''
})

onMounted(async () => {
  const now = new Date()
  form.value.expenseDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
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
    uni.showToast({ title: '请输入报销金额', icon: 'none' }); return
  }
  if (!form.value.description) {
    uni.showToast({ title: '请填写费用说明', icon: 'none' }); return
  }
  // 两段式审批无法离线入队（避免遗留永久 DRAFT），离线时明确拒绝（不静默）
  if (rejectIfOffline('项目报销需联网提交审批，请联网后重试')) return
  submitting.value = true
  try {
    const payload = {
      projectId: form.value.projectId,
      amount: amountNum,
      totalAmount: amountNum,
      expenseType: form.value.expenseType,
      expenseDate: form.value.expenseDate,
      reimbursementDate: form.value.expenseDate,
      description: form.value.description,
      invoiceCount: Number(form.value.invoiceCount) || 0,
      remark: form.value.remark
    }
    // 两段式提交：save 写入草稿返回 id → 链式调用 submit 启动审批流置 APPROVED
    const res: any = await saveReimbursement(payload)
    if (res?.data) {
      await submitReimbursement(res.data)
    }
    uni.showToast({ title: '提交成功', icon: 'success' })
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch {} finally {
    submitting.value = false
  }
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
