<template>
  <view class="form-page">
    <OfflineBanner />
    <view class="form-section">
      <view class="form-item" @click="showProjectPicker = true">
        <text class="form-label">项目</text>
        <text class="form-value" :class="{ placeholder: !form.projectName }">{{ form.projectName || '请选择项目' }}</text>
      </view>
      <view class="form-item">
        <text class="form-label">材料名称</text>
        <input v-model="form.materialName" placeholder="请输入材料名称" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">规格</text>
        <input v-model="form.specification" placeholder="请输入规格" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">单位</text>
        <input v-model="form.unit" placeholder="如：吨/根/车" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">退货数量</text>
        <input v-model="form.quantity" type="number" placeholder="不能超过库存数量" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">退货类型</text>
        <view class="form-value radio-row">
          <text class="radio" :class="{ on: form.returnType === 'RETURN_ONLY' }" @click="form.returnType = 'RETURN_ONLY'">仅退货</text>
          <text class="radio" :class="{ on: form.returnType === 'RETURN_REFUND' }" @click="form.returnType = 'RETURN_REFUND'">退货退款</text>
        </view>
      </view>
      <view class="form-item" v-if="form.returnType === 'RETURN_REFUND'">
        <text class="form-label">采购合同ID</text>
        <input v-model="form.contractId" type="number" placeholder="退款关联的采购合同ID" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">出库日期</text>
        <input v-model="form.outboundDate" placeholder="YYYY-MM-DD" class="form-input" />
      </view>
    </view>

    <view class="tips">
      <text class="tip-text">退货提交后自动扣减库存；「退货退款」关联采购合同时将自动生成退款申请并进入审批。</text>
    </view>

    <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交退货</button>

    <view v-if="showProjectPicker" class="picker-mask" @click="showProjectPicker = false">
      <view class="picker-panel" @click.stop>
        <view class="picker-title"><text>选择项目</text></view>
        <view class="picker-item" v-for="p in projects" :key="p.id" @click="selectProject(p)">
          <text>{{ p.projectName }}</text>
        </view>
        <view v-if="!projects.length" class="picker-item"><text>{{ projectEmptyTip }}</text></view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { saveMaterialOutbound } from '@/api/common'
import OfflineBanner from '@/components/OfflineBanner.vue'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'

const submitting = ref(false)
const showProjectPicker = ref(false)
const projects = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')

const form = ref({
  projectId: null as number | null,
  projectName: '',
  materialName: '',
  specification: '',
  unit: '',
  quantity: '',
  returnType: 'RETURN_ONLY',
  contractId: '',
  outboundDate: ''
})

onMounted(async () => {
  const now = new Date()
  form.value.outboundDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
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
  if (!form.value.materialName) {
    uni.showToast({ title: '请输入材料名称', icon: 'none' }); return
  }
  if (!form.value.quantity || Number(form.value.quantity) <= 0) {
    uni.showToast({ title: '请输入退货数量', icon: 'none' }); return
  }
  if (form.value.returnType === 'RETURN_REFUND' && !form.value.contractId) {
    uni.showToast({ title: '退货退款需填写采购合同ID', icon: 'none' }); return
  }
  submitting.value = true
  try {
    // 材料退货 = 出库单 outboundType=RETURN（后端自动扣库存；关联采购合同时
    // 发布 MaterialReturnCreatedEvent 自动生成退款申请并提交审批）
    await saveMaterialOutbound({
      projectId: form.value.projectId,
      outboundType: 'RETURN',
      outboundDate: form.value.outboundDate,
      returnType: form.value.returnType,
      contractId: form.value.returnType === 'RETURN_REFUND' ? Number(form.value.contractId) : null,
      details: [{
        materialName: form.value.materialName,
        specification: form.value.specification,
        unit: form.value.unit,
        quantity: Number(form.value.quantity)
      }]
    })
    uni.showToast({ title: '退货提交成功', icon: 'success' })
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch {} finally { submitting.value = false }
}
</script>

<style scoped>
.form-page { padding: 20rpx; padding-bottom: 120rpx; }
.form-section { background: #fff; border-radius: 12rpx; padding: 0 24rpx; margin-bottom: 20rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx 0; border-bottom: 1rpx solid #f5f5f5; }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: #303133; min-width: 180rpx; }
.form-value { flex: 1; font-size: 28rpx; color: #303133; text-align: right; }
.form-value.placeholder { color: #c0c4cc; }
.form-input { flex: 1; font-size: 28rpx; color: #303133; text-align: right; }
.radio-row { display: flex; justify-content: flex-end; gap: 32rpx; }
.radio { font-size: 26rpx; color: #909399; }
.radio.on { color: #409eff; font-weight: bold; }
.tips { padding: 12rpx 24rpx; }
.tip-text { font-size: 24rpx; color: #909399; }
.submit-btn { margin: 40rpx 20rpx; height: 88rpx; line-height: 88rpx; background: #409eff; color: #fff; font-size: 32rpx; border-radius: 8rpx; border: none; }
.picker-mask { position: fixed; left: 0; top: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: flex-end; z-index: 99; }
.picker-panel { width: 100%; max-height: 60vh; background: #fff; border-radius: 24rpx 24rpx 0 0; padding: 20rpx; overflow-y: auto; }
.picker-title { text-align: center; font-size: 30rpx; font-weight: bold; padding: 16rpx 0; }
.picker-item { padding: 24rpx; border-bottom: 1rpx solid #f5f5f5; font-size: 28rpx; }
</style>
