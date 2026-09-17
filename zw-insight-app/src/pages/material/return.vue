<template>
  <!-- ZwiFormPage 壳：sticky 底部提交条（critique P0 拇指区）；业务逻辑原样保留 -->
  <ZwiFormPage submitText="提交退货" :loading="submitting" @submit="handleSubmit">
    <OfflineBanner />
    <view class="form-card">
      <ZwiPickerField
        label="项目"
        :displayValue="form.projectName"
        placeholder="请选择项目"
        @open="showProjectPicker = true"
      />
      <ZwiField label="材料名称" v-model="form.materialName" placeholder="请输入材料名称" />
      <ZwiField label="规格" v-model="form.specification" placeholder="请输入规格" />
      <ZwiField label="单位" v-model="form.unit" placeholder="如：吨/根/车" />
      <ZwiField label="退货数量" v-model="form.quantity" inputType="digit" placeholder="不能超过库存数量" />
      <ZwiField label="入库单价" v-model="form.unitPrice" inputType="digit" placeholder="退货退款时用于计算退款金额" />
      <!-- 退货类型（radio 行保留原结构——ZwiField 无 radio 形态） -->
      <view class="form-item radio-item">
        <text class="form-label">退货类型</text>
        <view class="radio-row">
          <text class="radio" :class="{ on: form.returnType === 'RETURN_ONLY' }" @click="form.returnType = 'RETURN_ONLY'">仅退货</text>
          <text class="radio" :class="{ on: form.returnType === 'RETURN_REFUND' }" @click="form.returnType = 'RETURN_REFUND'">退货退款</text>
        </view>
      </view>
      <ZwiField
        v-if="form.returnType === 'RETURN_REFUND'"
        label="采购合同ID"
        v-model="form.contractId"
        inputType="digit"
        placeholder="退款关联的采购合同ID"
      />
      <ZwiField label="出库日期" v-model="form.outboundDate" placeholder="YYYY-MM-DD" />
    </view>

    <view class="tips">
      <text class="tip-text">退货提交后自动扣减库存；「退货退款」关联采购合同时将自动生成退款申请并进入审批。</text>
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
import { saveMaterialOutbound } from '@/api/common'
import OfflineBanner from '@/components/OfflineBanner.vue'
import ZwiFormPage from '@/components/zwi/ZwiFormPage.vue'
import ZwiField from '@/components/zwi/ZwiField.vue'
import ZwiPickerField from '@/components/zwi/ZwiPickerField.vue'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import { submitOrQueue } from '@/utils/offlineSubmit'

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
  unitPrice: '',
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
  const qty = Number(form.value.quantity)
  if (!Number.isFinite(qty) || qty <= 0) {
    uni.showToast({ title: '请输入退货数量', icon: 'none' }); return
  }
  const price = Number(form.value.unitPrice || 0)
  if (!Number.isFinite(price) || price < 0) {
    uni.showToast({ title: '入库单价格式不正确', icon: 'none' }); return
  }
  let contractId: number | null = null
  if (form.value.returnType === 'RETURN_REFUND') {
    // 非数字/NaN 会序列化为 null 使退款事件不发布（静默退化为仅退货），必须正整数校验
    const cid = Number(form.value.contractId)
    if (!form.value.contractId || !Number.isInteger(cid) || cid <= 0) {
      uni.showToast({ title: '退货退款需填写有效的采购合同ID', icon: 'none' }); return
    }
    if (price <= 0) {
      uni.showToast({ title: '退货退款需填写入库单价（用于计算退款金额）', icon: 'none' }); return
    }
    contractId = cid
  }
  submitting.value = true
  try {
    // 材料退货 = 出库单 outboundType=RETURN（后端自动扣库存；关联采购合同时
    // 发布 MaterialReturnCreatedEvent，按明细 unitPrice 自动生成退款申请并提交审批）；
    // 离线时入队（需求 5.1），联网后由 syncEngine 自动提交，事件回写仍走真实后端链路
    const payload = {
      projectId: form.value.projectId,
      outboundType: 'RETURN',
      outboundDate: form.value.outboundDate,
      returnType: form.value.returnType,
      contractId,
      details: [{
        materialName: form.value.materialName,
        specification: form.value.specification,
        unit: form.value.unit,
        quantity: qty,
        unitPrice: price
      }]
    }
    const { queued } = await submitOrQueue(() => saveMaterialOutbound(payload), {
      endpoint: '/v1/material/outbound',
      payload
    })
    if (!queued) {
      uni.showToast({ title: '退货提交成功', icon: 'success' })
    }
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch {} finally { submitting.value = false }
}
</script>

<style scoped>
/* 表单卡壳：收敛后的通用容器 */
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

/* radio 行（ZwiField 无 radio 形态，保留原行结构但收敛边距） */
.radio-item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  min-height: 44px;
  border-bottom: 1rpx solid var(--zw-border-light);
}
.form-label { font-size: 28rpx; color: var(--zw-text-primary); min-width: 160rpx; }
.radio-row { display: flex; justify-content: flex-end; gap: 32rpx; flex: 1; }
.radio { font-size: 26rpx; color: var(--zw-text-tertiary); padding: 8rpx 12rpx; min-height: 44px; display: inline-flex; align-items: center; box-sizing: border-box; } /* P0 触控热区 */
.radio.on { color: var(--zw-brand); font-weight: bold; }

.tips { padding: 12rpx 24rpx; }
.tip-text { font-size: 24rpx; color: var(--zw-text-tertiary); }

/* 项目选择弹窗（原样式保留） */
.picker-mask { position: fixed; left: 0; top: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); display: flex; align-items: flex-end; z-index: 99; }
.picker-panel { width: 100%; max-height: 60vh; background: var(--zw-bg-card); border-radius: var(--zw-radius-md) var(--zw-radius-md) 0 0; padding: 20rpx; padding-bottom: calc(20rpx + env(safe-area-inset-bottom)); overflow-y: auto; }
.picker-title { text-align: center; font-size: 30rpx; font-weight: bold; padding: 16rpx; }
.picker-item { padding: 24rpx; min-height: 44px; box-sizing: border-box; display: flex; align-items: center; border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; }
</style>
