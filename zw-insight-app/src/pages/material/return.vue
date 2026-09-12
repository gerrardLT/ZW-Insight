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
        <text class="form-label">入库单价</text>
        <input v-model="form.unitPrice" type="number" placeholder="退货退款时用于计算退款金额" class="form-input" />
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
.form-page { padding: 20rpx; min-height: 44px; padding-bottom: 120rpx; }
.form-section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border); border-radius: var(--zw-radius-xs); padding: 0 24rpx; margin-bottom: 20rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx; min-height: 44px;  border-bottom: 1rpx solid var(--zw-border-light); }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: var(--zw-text-primary); min-width: 180rpx; }
.form-value { flex: 1; font-size: 28rpx; color: var(--zw-text-primary); text-align: right; }
.form-value.placeholder { color: var(--zw-text-quaternary); }
.form-input { flex: 1; font-size: 28rpx; color: var(--zw-text-primary); text-align: right; font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.radio-row { display: flex; justify-content: flex-end; gap: 32rpx; }
.radio { font-size: 26rpx; color: var(--zw-text-tertiary); }
.radio.on { color: var(--zw-brand); font-weight: bold; }
.tips { padding: 12rpx 24rpx; }
.tip-text { font-size: 24rpx; color: var(--zw-text-tertiary); }
.submit-btn { margin: 40rpx 20rpx; min-height: 44px; display: flex; align-items: center; justify-content: center; line-height: 1;  background: var(--zw-brand); color: var(--zw-on-primary); font-size: 32rpx; font-weight: 600; border-radius: var(--zw-radius-xs); border: none; } /* 橙底深字承重规则 */
.picker-mask { position: fixed; left: 0; top: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); display: flex; align-items: flex-end; z-index: 99; }
.picker-panel { width: 100%; max-height: 60vh; background: var(--zw-bg-card); border-radius: var(--zw-radius-md) var(--zw-radius-md) 0 0; padding: 20rpx; padding-bottom: calc(20rpx + env(safe-area-inset-bottom)); overflow-y: auto; } /* P0 safe-area：底部弹层安全区 */
.picker-title { text-align: center; font-size: 30rpx; font-weight: bold; padding: 16rpx; }
.picker-item { padding: 24rpx; min-height: 44px; box-sizing: border-box; display: flex; align-items: center; border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; } /* P0 触控达标 */
</style>
