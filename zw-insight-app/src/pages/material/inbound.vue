<template>
  <view class="form-page">
    <OfflineBanner />
    <!-- 项目与合同选择 -->
    <view class="form-section">
      <view class="form-item" @click="showProjectPicker = true">
        <text class="form-label">所属项目</text>
        <view class="form-input picker">
          <text :class="{ placeholder: !form.projectName }">{{ form.projectName || '请选择项目' }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
      <!-- 入库类型切换 -->
      <view class="form-item">
        <text class="form-label">入库模式</text>
        <view class="mode-chips">
          <text class="chip" :class="{ on: inboundMode === 'PURCHASE' }" @click="inboundMode = 'PURCHASE'">采购合同核验</text>
          <text class="chip" :class="{ on: inboundMode === 'DIRECT' }" @click="inboundMode = 'DIRECT'">零星/扫码入库</text>
        </view>
      </view>
      <!-- 采购合同选择（仅采购核验模式） -->
      <view class="form-item" v-if="inboundMode === 'PURCHASE'" @click="openContractPicker">
        <text class="form-label">采购合同</text>
        <view class="form-input picker">
          <text :class="{ placeholder: !form.contractName }">{{ form.contractName || '请选择采购合同' }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
    </view>

    <!-- 采购合同核验材料明细清单 -->
    <view class="form-section" v-if="inboundMode === 'PURCHASE' && form.contractId">
      <view class="section-title-sub">
        <text>采购材料到货核验清单</text>
        <text class="batch-select" @click="toggleSelectAll">
          {{ isAllSelected ? '取消全选' : '全选到货' }}
        </text>
      </view>
      <view class="detail-cards" v-if="contractDetails.length">
        <view
          class="detail-card"
          v-for="(item, idx) in contractDetails"
          :key="idx"
          :class="{ selected: item.checked }"
          @click="item.checked = !item.checked"
        >
          <view class="card-top">
            <view class="check-box" :class="{ on: item.checked }">{{ item.checked ? '✓' : '' }}</view>
            <text class="mat-name">{{ item.materialName }}</text>
            <text class="mat-spec">{{ item.specification || '-' }}</text>
          </view>
          <view class="card-mid">
            <text class="mat-plan">合同采购量: {{ item.quantity }} {{ item.unit }}</text>
            <text class="mat-price">单价: ¥{{ item.unitPrice }}</text>
          </view>
          <view class="card-bot" v-if="item.checked" @click.stop>
            <text class="input-label">本次实收数:</text>
            <input
              v-model="item.receiveQty"
              type="digit"
              placeholder="请输入实收数量"
              class="receive-input"
            />
            <text class="unit-text">{{ item.unit }}</text>
          </view>
        </view>
      </view>
      <view class="empty-inline" v-else><text>该采购合同暂无材料明细</text></view>
    </view>

    <!-- 零星/单项入库信息 -->
    <view class="form-section" v-if="inboundMode === 'DIRECT'">
      <view class="form-item">
        <text class="form-label">材料编码</text>
        <view class="form-input code-wrap">
          <input v-model="materialCode" placeholder="扫码或输入编码" class="code-input" />
          <!-- #ifndef H5 -->
          <text class="scan-btn" @click="handleScan">扫码</text>
          <!-- #endif -->
          <!-- #ifdef H5 -->
          <text class="scan-btn" @click="handleCodeConfirm">手动输入编码·查询</text>
          <!-- #endif -->
        </view>
      </view>
      <view class="form-item">
        <text class="form-label">材料名称</text>
        <input v-model="form.materialName" placeholder="请输入材料名称" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">规格型号</text>
        <input v-model="form.specification" placeholder="请输入规格型号" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">数量</text>
        <input v-model="form.quantity" type="digit" placeholder="请输入数量" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">单位</text>
        <input v-model="form.unit" placeholder="如：吨、米、个" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">单价(元)</text>
        <input v-model="form.unitPrice" type="digit" placeholder="请输入单价" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">供应商</text>
        <input v-model="form.supplierName" placeholder="请输入供应商" class="form-input" />
      </view>
      <picker mode="date" :value="form.inboundDate" @change="onDateChange">
        <view class="form-item">
          <text class="form-label">入库日期</text>
          <view class="form-input picker">
            <text :class="{ placeholder: !form.inboundDate }">{{ form.inboundDate || '请选择日期' }}</text>
            <text class="arrow">›</text>
          </view>
        </view>
      </picker>
      <view class="form-item">
        <text class="form-label">备注</text>
        <input v-model="form.remark" placeholder="请输入备注" class="form-input" />
      </view>
    </view>

    <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交入库</button>

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

    <!-- 采购合同选择弹窗 -->
    <view class="picker-mask" v-if="showContractPicker" @click="showContractPicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text @click="showContractPicker = false">取消</text>
          <text class="picker-title">选择采购合同</text>
          <text></text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view class="picker-item" v-for="c in contracts" :key="c.id" @click="selectContract(c)">
            <text>{{ c.contractName || c.contractCode || ('采购合同 #' + c.id) }}</text>
          </view>
          <view class="empty" v-if="!contracts.length"><text>该项目暂无采购合同</text></view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import {
  saveMaterialInbound,
  getMaterialByCode,
  getPurchaseContractPage,
  getPurchaseContractDetails
} from '@/api/common'
import OfflineBanner from '@/components/OfflineBanner.vue'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import { submitOrQueue } from '@/utils/offlineSubmit'

const submitting = ref(false)
const inboundMode = ref<'PURCHASE' | 'DIRECT'>('DIRECT')
const showProjectPicker = ref(false)
const showContractPicker = ref(false)
const projects = ref<any[]>([])
const contracts = ref<any[]>([])
const contractDetails = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')
const materialCode = ref('')

const form = ref({
  projectId: null as number | null,
  projectName: '',
  contractId: null as number | null,
  contractName: '',
  materialName: '',
  specification: '',
  quantity: '',
  unit: '',
  unitPrice: '',
  supplierName: '',
  inboundDate: '',
  remark: ''
})

const isAllSelected = computed(() => {
  return contractDetails.value.length > 0 && contractDetails.value.every((d) => d.checked)
})

function toggleSelectAll() {
  const target = !isAllSelected.value
  contractDetails.value.forEach((d) => { d.checked = target })
}

onMounted(async () => {
  const now = new Date()
  form.value.inboundDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  const res = await loadProjectList({ page: 1, size: 100 })
  projects.value = res.records
  projectEmptyTip.value = res.empty && res.fromCache ? NO_OFFLINE_DATA_TIP : '暂无项目'
})

async function selectProject(p: any) {
  form.value.projectId = p.id
  form.value.projectName = p.projectName
  form.value.contractId = null
  form.value.contractName = ''
  contractDetails.value = []
  showProjectPicker.value = false

  try {
    const cRes: any = await getPurchaseContractPage({ projectId: p.id, size: 50 })
    contracts.value = cRes?.data?.records || []
    if (contracts.value.length === 1) {
      await selectContract(contracts.value[0])
    }
  } catch {
    contracts.value = []
  }
}

function openContractPicker() {
  if (!form.value.projectId) {
    uni.showToast({ title: '请先选择项目', icon: 'none' }); return
  }
  showContractPicker.value = true
}

async function selectContract(c: any) {
  form.value.contractId = c.id
  form.value.contractName = c.contractName || c.contractCode || ('采购合同 #' + c.id)
  showContractPicker.value = false

  try {
    const dRes: any = await getPurchaseContractDetails(c.id)
    const list = dRes?.data || []
    contractDetails.value = list.map((item: any) => ({
      ...item,
      checked: true,
      receiveQty: String(item.quantity || 1)
    }))
  } catch {
    contractDetails.value = []
  }
}

function onDateChange(e: any) {
  form.value.inboundDate = e.detail.value
}

// ── 扫码/手输编码带出材料信息（P0 Req6）──
// #ifndef H5
function handleScan() {
  uni.scanCode({
    onlyFromCamera: true,
    scanType: ['barCode', 'qrCode'],
    success: async (res) => {
      materialCode.value = res.result
      await fetchMaterialByCode(res.result)
    },
    fail: () => {
      uni.showToast({ title: '扫码已取消或失败，可手动输入编码', icon: 'none' })
    }
  })
}
// #endif

async function handleCodeConfirm() {
  if (!materialCode.value.trim()) {
    uni.showToast({ title: '请输入材料编码', icon: 'none' }); return
  }
  await fetchMaterialByCode(materialCode.value.trim())
}

async function fetchMaterialByCode(code: string) {
  try {
    const res: any = await getMaterialByCode(code)
    const m = res.data
    if (m) {
      form.value.materialName = m.materialName || ''
      form.value.specification = m.specification || ''
      form.value.unit = m.unit || ''
      if (m.unitPrice != null) form.value.unitPrice = String(m.unitPrice)
      if (m.supplierName) form.value.supplierName = m.supplierName
      uni.showToast({ title: '已带出材料信息', icon: 'success' })
    }
  } catch {}
}

async function handleSubmit() {
  if (!form.value.projectId) {
    uni.showToast({ title: '请选择项目', icon: 'none' }); return
  }

  // 采购合同模式
  if (inboundMode.value === 'PURCHASE') {
    const selected = contractDetails.value.filter((d) => d.checked)
    if (!selected.length) {
      uni.showToast({ title: '请至少勾选一种到货材料', icon: 'none' }); return
    }
    const details = selected.map((s) => {
      const qty = Number(s.receiveQty)
      const price = Number(s.unitPrice || 0)
      return {
        materialName: s.materialName,
        specification: s.specification,
        unit: s.unit,
        unitPrice: price,
        quantity: qty,
        totalPrice: Number((qty * price).toFixed(2))
      }
    })
    const totalAmount = details.reduce((sum, d) => sum + d.totalPrice, 0)

    submitting.value = true
    try {
      const payload = {
        projectId: form.value.projectId,
        contractId: form.value.contractId,
        inboundDate: form.value.inboundDate,
        totalAmount,
        details,
        // 兼容单项字段
        materialName: details[0]?.materialName,
        quantity: details[0]?.quantity,
        unitPrice: details[0]?.unitPrice
      }
      const { queued } = await submitOrQueue(() => saveMaterialInbound(payload), {
        endpoint: '/v1/material/inbound',
        payload
      })
      if (!queued) {
        uni.showToast({ title: '入库成功', icon: 'success' })
      }
      setTimeout(() => { uni.navigateBack() }, 1500)
    } catch {} finally {
      submitting.value = false
    }
    return
  }

  // 零星/单项入库模式（兼容既有模式）
  if (!form.value.materialName) {
    uni.showToast({ title: '请输入材料名称', icon: 'none' }); return
  }
  if (!form.value.quantity) {
    uni.showToast({ title: '请输入数量', icon: 'none' }); return
  }
  submitting.value = true
  try {
    const qty = Number(form.value.quantity)
    const price = Number(form.value.unitPrice) || 0
    const totalAmount = Number((qty * price).toFixed(2))
    const payload: any = {
      projectId: form.value.projectId,
      inboundDate: form.value.inboundDate,
      totalAmount,
      details: [{
        materialName: form.value.materialName,
        specification: form.value.specification,
        unit: form.value.unit,
        quantity: qty,
        unitPrice: price
      }]
    }
    const { queued } = await submitOrQueue(() => saveMaterialInbound(payload), {
      endpoint: '/v1/material/inbound',
      payload
    })
    if (!queued) {
      uni.showToast({ title: '入库成功', icon: 'success' })
    }
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch {} finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.form-page { padding: 20rpx; min-height: 44px; padding-bottom: 120rpx; }
.form-section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border); border-radius: var(--zw-radius-xs); padding: 0 24rpx; margin-bottom: 20rpx; box-shadow: var(--zw-shadow-card); }
.form-item { display: flex; align-items: center; padding: 24rpx;   border-bottom: 1rpx solid var(--zw-border-light); }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: var(--zw-text-primary); min-width: 160rpx; }
.form-input { flex: 1; font-size: 28rpx; color: var(--zw-text-primary); text-align: right; font-family: var(--zw-font-mono); }
.form-input.picker { display: flex; align-items: center; justify-content: flex-end; }
.placeholder { color: var(--zw-text-quaternary); }
.arrow { margin-left: 8rpx; color: var(--zw-text-quaternary); font-size: 32rpx; }
.code-wrap { display: flex; align-items: center; justify-content: flex-end; }
.code-input { text-align: right; font-size: 28rpx; flex: 1; font-family: var(--zw-font-mono); }
.scan-btn { margin-left: 16rpx; padding: 6rpx;   background: var(--zw-brand); color: var(--zw-on-primary); font-size: 24rpx; border-radius: var(--zw-radius-xs); white-space: nowrap; }
.mode-chips { display: flex; gap: 16rpx; justify-content: flex-end; flex: 1; }
.chip { padding: 8rpx;   border: 1rpx solid var(--zw-border); border-radius: var(--zw-radius-xs); font-size: 24rpx; color: var(--zw-text-secondary); }
.chip.on { border-color: var(--zw-brand); background: var(--zw-brand-light); color: var(--zw-brand); font-weight: 500; }
.section-title-sub { display: flex; justify-content: space-between; align-items: center; padding: 20rpx;   font-size: 28rpx; font-weight: bold; border-bottom: 1rpx solid var(--zw-border-light); }
.batch-select { font-size: 24rpx; color: var(--zw-brand); font-weight: normal; }
.detail-cards { padding: 16rpx;   }
.detail-card { border: 2rpx solid var(--zw-border-light); border-radius: var(--zw-radius-md); padding: 20rpx; min-height: 44px; margin-bottom: 16rpx; background: var(--zw-bg-page); }
.detail-card.selected { border-color: var(--zw-brand); background: var(--zw-bg-card); }
.card-top { display: flex; align-items: center; }
.check-box { width: 36rpx; height: 36rpx; border: 2rpx solid var(--zw-border); border-radius: 6rpx; display: flex; align-items: center; justify-content: center; font-size: 24rpx; margin-right: 16rpx; color: var(--zw-on-primary); }
.check-box.on { background: var(--zw-brand); border-color: var(--zw-brand); }
.mat-name { font-size: 28rpx; font-weight: bold; color: var(--zw-text-primary); }
.mat-spec { font-size: 24rpx; color: var(--zw-text-tertiary); margin-left: 12rpx; }
.card-mid { display: flex; justify-content: space-between; font-size: 24rpx; color: var(--zw-text-secondary); margin: 12rpx 0; }
.card-bot { display: flex; align-items: center; padding-top: 12rpx; border-top: 1rpx dashed var(--zw-border-light); }
.input-label { font-size: 24rpx; color: var(--zw-brand); font-weight: 500; }
.receive-input { flex: 1; border: 1rpx solid var(--zw-border); border-radius: var(--zw-radius-xs); padding: 6rpx;   font-size: 26rpx; text-align: right; margin: 0 12rpx; background: #fff; }
.unit-text { font-size: 24rpx; color: var(--zw-text-tertiary); }
.empty-inline { text-align: center; padding: 40rpx;   font-size: 24rpx; color: var(--zw-text-quaternary); }
.submit-btn { margin: 40rpx 20rpx; min-height: 44px; display: flex; align-items: center; justify-content: center; line-height: 1;  background: var(--zw-brand); color: var(--zw-on-primary); font-size: 32rpx; font-weight: 600; border-radius: var(--zw-radius-sm); border: none; }
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-lg) var(--zw-radius-lg) 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx;   border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx;   border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; }
.empty { text-align: center; padding: 40rpx; min-height: 44px; color: var(--zw-text-quaternary); }
</style>
