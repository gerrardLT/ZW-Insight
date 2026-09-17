<template>
  <!-- ZwiFormPage 壳：sticky 底部提交条（critique P0 拇指区） -->
  <ZwiFormPage submitText="确认签认提交" :loading="submitting" @submit="handleSubmit">
    <OfflineBanner />
    <view class="form-card">
      <!-- 项目选择（S4.2）：ZwBottomSheetPicker 工业抽屉 -->
      <ZwBottomSheetPicker
        :options="projectOptions"
        :model-value="form.projectId"
        title="选择项目"
        placeholder="请选择项目"
        @change="onProjectPicked"
      >
        <template #trigger>
          <ZwiPickerField
            label="所属项目"
            :displayValue="form.projectName"
            placeholder="请选择项目"
          />
        </template>
      </ZwBottomSheetPicker>
      <ZwiPickerField
        label="劳务班组"
        :displayValue="form.teamName"
        placeholder="请选择班组(选填)"
        @open="openTeamPicker"
      />
    </view>

    <view class="form-card">
      <ZwiField label="工人姓名" v-model="form.workerName" placeholder="请输入工人姓名" />
      <!-- 用工类型 radio-group 保留原结构 -->
      <view class="form-item radio-item-row">
        <text class="form-label">用工类型</text>
        <view class="radio-group">
          <view class="radio-item" :class="{ active: form.orderType === 'TEMPORARY' }" @click="form.orderType = 'TEMPORARY'">
            <text>临时点工</text>
          </view>
          <view class="radio-item" :class="{ active: form.orderType === 'FIXED' }" @click="form.orderType = 'FIXED'">
            <text>固定班组</text>
          </view>
        </view>
      </view>
      <ZwiField label="工作日期" v-model="form.workDate" placeholder="YYYY-MM-DD" />
      <ZwiField label="正常工时(h)" v-model="form.hours" inputType="digit" placeholder="如 8 或 8.5" />
      <ZwiField label="时薪单价(元)" v-model="form.hourlyRate" inputType="digit" placeholder="每小时单价" />
      <ZwiField label="加班工时(h)" v-model="form.overtime" inputType="digit" placeholder="选填，如 2" />
      <ZwiField label="加班费率(元)" v-model="form.overtimeRate" inputType="digit" placeholder="不填则按正常时薪" />
      <!-- 合计金额高亮行保留原结构 -->
      <view class="form-item highlight">
        <text class="form-label font-bold">合计金额(元)</text>
        <text class="total-display">¥{{ calcTotal }}</text>
      </view>
    </view>

    <!-- 班组选择弹窗 -->
    <view class="picker-mask" v-if="showTeamPicker" @click="showTeamPicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text @click="showTeamPicker = false">取消</text>
          <text class="picker-title">选择劳务班组</text>
          <text></text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view class="picker-item" v-for="t in teams" :key="t.id" @click="selectTeam(t)">
            <text>{{ t.teamName || ('班组 #' + t.id) }} ({{ t.workType || '通用' }})</text>
          </view>
          <view class="empty" v-if="!teams.length"><text>该项目暂无劳务班组台账</text></view>
        </scroll-view>
      </view>
    </view>
  </ZwiFormPage>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { saveWorkOrder, getLaborTeamPage } from '@/api/common'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import { submitOrQueue } from '@/utils/offlineSubmit'
import { useFormSession } from '@/composables/useFormSession'
import OfflineBanner from '@/components/OfflineBanner.vue'
import ZwBottomSheetPicker from '@/components/ZwBottomSheetPicker.vue'
import ZwiFormPage from '@/components/zwi/ZwiFormPage.vue'
import ZwiField from '@/components/zwi/ZwiField.vue'
import ZwiPickerField from '@/components/zwi/ZwiPickerField.vue'

const submitting = ref(false)
const showProjectPicker = ref(false)
const showTeamPicker = ref(false)
const projects = ref<any[]>([])
const teams = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')

const form = ref({
  projectId: null as number | null,
  projectName: '',
  teamId: null as number | null,
  teamName: '',
  workerName: '',
  orderType: 'TEMPORARY',
  workDate: '',
  hours: '8',
  hourlyRate: '35',
  overtime: '',
  overtimeRate: ''
})

// 表单会话持久化（S3.3）：onHide 快照/onShow 还原（仅空表单），提交成功 clear；24h 过期
const formSession = useFormSession('labor-work-order-create', {
  getSnapshot: () => form.value,
  restoreTo: (data) => { Object.assign(form.value, data) },
  isEmpty: (d) => !d.projectId && !d.workerName,
})

const calcTotal = computed(() => {
  const h = Number(form.value.hours) || 0
  const rate = Number(form.value.hourlyRate) || 0
  const ot = Number(form.value.overtime) || 0
  const otRate = Number(form.value.overtimeRate) || rate
  return (h * rate + ot * otRate).toFixed(2)
})

onMounted(async () => {
  const now = new Date()
  form.value.workDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  const res = await loadProjectList({ page: 1, size: 100 })
  projects.value = res.records
  projectEmptyTip.value = res.empty && res.fromCache ? NO_OFFLINE_DATA_TIP : '暂无项目'
})

async function selectProject(p: any) {
  form.value.projectId = p.id
  form.value.projectName = p.projectName
  form.value.teamId = null
  form.value.teamName = ''

  try {
    const tRes: any = await getLaborTeamPage({ projectId: p.id, size: 50 })
    teams.value = tRes?.data?.records || []
    if (teams.value.length === 1) {
      selectTeam(teams.value[0])
    }
  } catch {
    teams.value = []
  }
}

// 项目下拉选项（S4.2 BottomSheet）
const projectOptions = computed(() =>
  projects.value.map((p: any) => ({ label: p.projectName, value: p.id }))
)

// BottomSheet 确认回调 → 复用既有 selectProject 业务逻辑
function onProjectPicked(value: any, item: any) {
  if (!item) return
  selectProject({ id: value, projectName: item.label })
}

function openTeamPicker() {
  if (!form.value.projectId) {
    uni.showToast({ title: '请先选择项目', icon: 'none' }); return
  }
  showTeamPicker.value = true
}

function selectTeam(t: any) {
  form.value.teamId = t.id
  form.value.teamName = t.teamName || ('班组 #' + t.id)
  showTeamPicker.value = false
}

async function handleSubmit() {
  if (!form.value.projectId) {
    uni.showToast({ title: '请选择项目', icon: 'none' }); return
  }
  if (!form.value.workerName.trim()) {
    uni.showToast({ title: '请输入工人姓名', icon: 'none' }); return
  }
  const h = Number(form.value.hours)
  if (!form.value.hours || !Number.isFinite(h) || h <= 0 || h > 24) {
    uni.showToast({ title: '工时须在0到24小时之间', icon: 'none' }); return
  }
  const rate = Number(form.value.hourlyRate)
  if (!form.value.hourlyRate || !Number.isFinite(rate) || rate <= 0) {
    uni.showToast({ title: '时薪须大于0', icon: 'none' }); return
  }

  submitting.value = true
  try {
    const ot = Number(form.value.overtime) || 0
    const otRate = Number(form.value.overtimeRate) || rate
    const totalAmount = Number(calcTotal.value)

    const payload = {
      projectId: form.value.projectId,
      teamId: form.value.teamId || undefined,
      workerName: form.value.workerName.trim(),
      workDate: form.value.workDate,
      hours: h,
      hourlyRate: rate,
      overtime: ot,
      overtimeRate: otRate,
      totalAmount,
      orderType: form.value.orderType,
      status: 'APPROVED'
    }

    const { queued } = await submitOrQueue(() => saveWorkOrder(payload), {
      endpoint: '/v1/labor/work-order',
      payload
    })
    if (!queued) {
      uni.showToast({ title: '签认提交成功', icon: 'success' })
    }
    formSession.clear() // 提交成功清除会话快照（S3.3）
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
/* 合计金额高亮行保留原结构 */
.form-item.highlight { background: var(--zw-brand-light); padding: 24rpx; }
.form-label { font-size: 28rpx; color: var(--zw-text-primary); min-width: 180rpx; }
.form-label.font-bold { font-weight: bold; }
.total-display { flex: 1; text-align: right; font-size: 36rpx; font-weight: bold; color: var(--zw-brand); font-family: var(--zw-font-mono); }
.radio-group { display: flex; gap: 16rpx; justify-content: flex-end; flex: 1; }
.radio-item { padding: 0 20rpx; min-height: 44px; display: flex; align-items: center; justify-content: center; border: 1rpx solid var(--zw-border); border-radius: var(--zw-radius-sm); font-size: 24rpx; color: var(--zw-text-secondary); }
.radio-item.active { border-color: var(--zw-brand); background: var(--zw-brand-light); color: var(--zw-brand); font-weight: 500; }

/* 班组选择弹窗（原样式保留） */
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-lg) var(--zw-radius-lg) 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx 32rpx; min-height: 44px; border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx 32rpx; min-height: 44px; border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; }
.empty { text-align: center; padding: 40rpx; min-height: 44px; color: var(--zw-text-quaternary); }
</style>
