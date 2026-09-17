<template>
  <ZwiFormPage submitText="提交申请" :loading="submitting" @submit="handleSubmit">
    <OfflineBanner />
    <view class="form-card">
      <view class="form-item" @click="showProjectPicker = true">
        <text class="form-label">项目</text>
        <text class="form-value" :class="{ placeholder: !form.projectName }">{{ form.projectName || '请选择项目' }}</text>
      </view>
      <ZwiField label="申请人" v-model="form.applicant" placeholder="请输入申请人姓名" />
      <ZwiField label="申请金额" v-model="form.applyAmount" inputType="digit" placeholder="请输入申请金额" />
      <ZwiField label="申请日期" v-model="form.applyDate" placeholder="YYYY-MM-DD" />
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
import { saveReserveFundApply, submitReserveFundApply } from '@/api/common'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import { rejectIfOffline } from '@/utils/offlineSubmit'
import OfflineBanner from '@/components/OfflineBanner.vue'
import ZwiFormPage from '@/components/zwi/ZwiFormPage.vue'
import ZwiField from '@/components/zwi/ZwiField.vue'

const submitting = ref(false)
const showProjectPicker = ref(false)
const projects = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')
const form = ref({
  projectId: null as number | null,
  projectName: '',
  applicant: '',
  applyAmount: '',
  applyDate: ''
})

onMounted(async () => {
  const now = new Date()
  form.value.applyDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
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
  if (!form.value.applicant) {
    uni.showToast({ title: '请输入申请人', icon: 'none' }); return
  }
  const amount = Number(form.value.applyAmount)
  if (!Number.isFinite(amount) || amount <= 0) {
    uni.showToast({ title: '申请金额必须大于0', icon: 'none' }); return
  }
  // 两段式审批无法离线入队（只入队 save 会遗留永久 DRAFT），离线时明确拒绝（不静默）
  if (rejectIfOffline('备用金申请需联网提交审批，请联网后重试')) return
  submitting.value = true
  try {
    // 两段式提交（与 web 端一致）：save 落 DRAFT 返回 id → submit 启动审批置 APPROVED，
    // 否则记录永久 DRAFT 且在归还页（按 APPROVED 过滤）永不可见
    const res: any = await saveReserveFundApply({
      projectId: form.value.projectId,
      applicant: form.value.applicant,
      applyDate: form.value.applyDate,
      applyAmount: amount
    })
    await submitReserveFundApply(res.data)
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
