<template>
  <view class="form-page">
    <OfflineBanner />
    <view class="form-section">
      <view class="form-item" @click="showProjectPicker = true">
        <text class="form-label">所属项目</text>
        <view class="form-input picker">
          <text :class="{ placeholder: !form.projectName }">{{ form.projectName || '请选择项目' }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
      <view class="form-item" @click="openMachinePicker">
        <text class="form-label">机械设备</text>
        <view class="form-input picker">
          <text :class="{ placeholder: !form.machineName }">{{ form.machineName || '请选择设备' }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
    </view>

    <view class="form-section">
      <view class="form-item">
        <text class="form-label">工作日期</text>
        <input v-model="form.workDate" placeholder="YYYY-MM-DD" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">台班数</text>
        <input v-model="form.shiftCount" type="digit" placeholder="如 1 或 1.5" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">工程量</text>
        <input v-model="form.workQuantity" type="digit" placeholder="完成方量/米数(选填)" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">耗油量(升)</text>
        <input v-model="form.oilConsumption" type="digit" placeholder="油耗升数(选填)" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">工作备注</text>
        <input v-model="form.remark" placeholder="作业部位或工作内容说明" class="form-input" />
      </view>
    </view>

    <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交台班记录</button>

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

    <!-- 设备选择弹窗 -->
    <view class="picker-mask" v-if="showMachinePicker" @click="showMachinePicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text @click="showMachinePicker = false">取消</text>
          <text class="picker-title">选择机械设备</text>
          <text></text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view class="picker-item" v-for="m in machines" :key="m.id" @click="selectMachine(m)">
            <text>{{ m.machineName || m.machineCode || ('设备 #' + m.id) }}</text>
          </view>
          <view class="empty" v-if="!machines.length"><text>暂无可用机械设备台账</text></view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { saveMachineWorkLog, getMachineLedgerPage } from '@/api/common'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import { submitOrQueue } from '@/utils/offlineSubmit'
import OfflineBanner from '@/components/OfflineBanner.vue'

const submitting = ref(false)
const showProjectPicker = ref(false)
const showMachinePicker = ref(false)
const projects = ref<any[]>([])
const machines = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')

const form = ref({
  projectId: null as number | null,
  projectName: '',
  machineId: null as number | null,
  machineName: '',
  workDate: '',
  shiftCount: '1',
  workQuantity: '',
  oilConsumption: '',
  remark: ''
})

onMounted(async () => {
  const now = new Date()
  form.value.workDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  // 在线优先 + 离线回退缓存
  const res = await loadProjectList({ page: 1, size: 100 })
  projects.value = res.records
  projectEmptyTip.value = res.empty && res.fromCache ? NO_OFFLINE_DATA_TIP : '暂无项目'

  try {
    const mRes: any = await getMachineLedgerPage({ page: 1, size: 100 })
    machines.value = mRes?.data?.records || []
  } catch {
    machines.value = []
  }
})

function selectProject(p: any) {
  form.value.projectId = p.id
  form.value.projectName = p.projectName
  showProjectPicker.value = false
}

function openMachinePicker() {
  showMachinePicker.value = true
}

function selectMachine(m: any) {
  form.value.machineId = m.id
  form.value.machineName = m.machineName || m.machineCode || ('设备 #' + m.id)
  showMachinePicker.value = false
}

async function handleSubmit() {
  if (!form.value.projectId) {
    uni.showToast({ title: '请选择项目', icon: 'none' }); return
  }
  if (!form.value.machineId) {
    uni.showToast({ title: '请选择机械设备', icon: 'none' }); return
  }
  const shifts = Number(form.value.shiftCount)
  if (!form.value.shiftCount || !Number.isFinite(shifts) || shifts <= 0 || shifts > 3) {
    uni.showToast({ title: '台班数须在0到3之间', icon: 'none' }); return
  }
  submitting.value = true
  try {
    const payload = {
      projectId: form.value.projectId,
      machineId: form.value.machineId,
      workDate: form.value.workDate,
      shiftCount: shifts,
      workQuantity: form.value.workQuantity ? Number(form.value.workQuantity) : 0,
      oilConsumption: form.value.oilConsumption ? Number(form.value.oilConsumption) : 0,
      remark: form.value.remark
    }
    // 离线入队支持：地下室/基坑无网自动存离线队列
    const { queued } = await submitOrQueue(() => saveMachineWorkLog(payload), {
      endpoint: '/v1/machine/work-log',
      payload
    })
    if (!queued) {
      uni.showToast({ title: '上报成功', icon: 'success' })
    }
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch {} finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.form-page { padding: 20rpx; min-height: 44px; padding-bottom: 120rpx; }
.form-section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light); border-radius: var(--zw-radius-lg); padding: 0 24rpx; margin-bottom: 20rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx; min-height: 44px; 0; border-bottom: 1rpx solid var(--zw-border-light); }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: var(--zw-text-primary); min-width: 160rpx; }
.form-input { flex: 1; font-size: 28rpx; color: var(--zw-text-primary); text-align: right; }
.form-input.picker { display: flex; align-items: center; justify-content: flex-end; }
.placeholder { color: var(--zw-text-quaternary); }
.arrow { margin-left: 8rpx; color: var(--zw-text-quaternary); font-size: 32rpx; }
.submit-btn { margin: 40rpx 20rpx; min-height: 44px; display: flex; align-items: center; justify-content: center; line-height: 1;  background: var(--zw-brand); color: var(--zw-on-primary); font-size: 32rpx; font-weight: 600; border-radius: var(--zw-radius-sm); border: none; }
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-lg) var(--zw-radius-lg) 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx; min-height: 44px; 32rpx; border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx; min-height: 44px; 32rpx; border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; }
.empty { text-align: center; padding: 40rpx; min-height: 44px; color: var(--zw-text-quaternary); }
</style>
