<template>
  <view class="form-page">
    <view class="form-section">
      <view class="form-item" @click="showProjectPicker = true">
        <text class="form-label">所属项目</text>
        <view class="form-input picker">
          <text :class="{ placeholder: !form.projectName }">{{ form.projectName || '请选择项目' }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
      <view class="form-item" @click="openContractPicker">
        <text class="form-label">关联合同</text>
        <view class="form-input picker">
          <text :class="{ placeholder: !form.contractName }">{{ form.contractName || '请选择合同(选填)' }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
    </view>

    <view class="form-section">
      <view class="form-item">
        <text class="form-label">回款金额(元)</text>
        <input v-model="form.amount" type="digit" placeholder="请输入回款金额" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">回款日期</text>
        <input v-model="form.receivedDate" placeholder="YYYY-MM-DD" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">付款方</text>
        <input v-model="form.payer" placeholder="请输入付款方" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">回款方式</text>
        <view class="radio-group">
          <view class="radio-item" :class="{ active: form.payMethod === '银行转账' }" @click="form.payMethod = '银行转账'">
            <text>转账</text>
          </view>
          <view class="radio-item" :class="{ active: form.payMethod === '支票' }" @click="form.payMethod = '支票'">
            <text>支票</text>
          </view>
          <view class="radio-item" :class="{ active: form.payMethod === '承兑汇票' }" @click="form.payMethod = '承兑汇票'">
            <text>汇票</text>
          </view>
        </view>
      </view>
      <view class="form-item">
        <text class="form-label">备注</text>
        <input v-model="form.remark" placeholder="请输入备注" class="form-input" />
      </view>
    </view>

    <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交登记</button>

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

    <!-- 合同选择弹窗 -->
    <view class="picker-mask" v-if="showContractPicker" @click="showContractPicker = false">
      <view class="picker-content" @click.stop>
        <view class="picker-header">
          <text @click="showContractPicker = false">取消</text>
          <text class="picker-title">选择关联合同</text>
          <text></text>
        </view>
        <scroll-view scroll-y class="picker-list">
          <view class="picker-item" v-for="c in contracts" :key="c.id" @click="selectContract(c)">
            <text>{{ c.contractName || c.contractCode || ('合同 #' + c.id) }}</text>
          </view>
          <view class="empty" v-if="!contracts.length"><text>暂无合同数据</text></view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { savePaymentReceived, getContractPage } from '@/api/common'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import { submitOrQueue } from '@/utils/offlineSubmit'

const submitting = ref(false)
const showProjectPicker = ref(false)
const showContractPicker = ref(false)
const projects = ref<any[]>([])
const contracts = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')

const form = ref({
  projectId: null as number | null,
  projectName: '',
  contractId: null as number | null,
  contractName: '',
  amount: '',
  receivedDate: '',
  payer: '',
  payMethod: '银行转账',
  remark: ''
})

onMounted(async () => {
  const now = new Date()
  form.value.receivedDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  // 在线优先 + 离线回退缓存（需求 4.2、4.8）
  const res = await loadProjectList({ page: 1, size: 100 })
  projects.value = res.records
  projectEmptyTip.value = res.empty && res.fromCache ? NO_OFFLINE_DATA_TIP : '暂无项目'
})

async function selectProject(p: any) {
  form.value.projectId = p.id
  form.value.projectName = p.projectName
  form.value.contractId = null
  form.value.contractName = ''
  showProjectPicker.value = false
  try {
    const res: any = await getContractPage({ projectId: p.id, size: 50 })
    contracts.value = res?.data?.records || []
    if (contracts.value.length === 1) {
      form.value.contractId = contracts.value[0].id
      form.value.contractName = contracts.value[0].contractName || contracts.value[0].contractCode
    }
  } catch {
    contracts.value = []
  }
}

function openContractPicker() {
  if (!form.value.projectId) {
    uni.showToast({ title: '请先选择项目', icon: 'none' })
    return
  }
  showContractPicker.value = true
}

function selectContract(c: any) {
  form.value.contractId = c.id
  form.value.contractName = c.contractName || c.contractCode || ('合同 #' + c.id)
  showContractPicker.value = false
}

async function handleSubmit() {
  if (!form.value.projectId) {
    uni.showToast({ title: '请选择项目', icon: 'none' }); return
  }
  const amountNum = Number(form.value.amount)
  if (!form.value.amount || !Number.isFinite(amountNum) || amountNum <= 0) {
    uni.showToast({ title: '请输入回款金额', icon: 'none' }); return
  }
  submitting.value = true
  try {
    // 适配后端 BizPaymentReceived：receiveAmount/receiveDate/receiveType/contractId
    const payload = {
      projectId: form.value.projectId,
      contractId: form.value.contractId,
      amount: amountNum,
      receiveAmount: amountNum,
      receivedDate: form.value.receivedDate,
      receiveDate: form.value.receivedDate,
      payer: form.value.payer,
      payMethod: form.value.payMethod,
      receiveType: form.value.payMethod,
      contractName: form.value.contractName,
      remark: form.value.remark
    }
    // 离线时入队（需求 5.1），联网后由 syncEngine 自动提交
    const { queued } = await submitOrQueue(() => savePaymentReceived(payload), {
      endpoint: '/v1/finance/payment-received',
      payload
    })
    if (!queued) {
      uni.showToast({ title: '提交成功', icon: 'success' })
    }
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch {} finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.form-page { padding: 20rpx; padding-bottom: 120rpx; }
.form-section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border); border-radius: var(--zw-radius-xs); padding: 0 24rpx; margin-bottom: 20rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx 0; border-bottom: 1rpx solid var(--zw-border-light); }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: var(--zw-text-primary); min-width: 160rpx; }
.form-input { flex: 1; font-size: 28rpx; color: var(--zw-text-primary); text-align: right; font-family: var(--zw-font-mono); font-variant-numeric: tabular-nums; }
.form-input.picker { display: flex; align-items: center; justify-content: flex-end; }
.placeholder { color: var(--zw-text-quaternary); }
.arrow { margin-left: 8rpx; color: var(--zw-text-quaternary); font-size: 32rpx; }
.radio-group { display: flex; gap: 16rpx; flex: 1; justify-content: flex-end; flex-wrap: wrap; }
.radio-item { padding: 8rpx 20rpx; border: 1rpx solid var(--zw-border); border-radius: var(--zw-radius-xs); font-size: 24rpx; color: var(--zw-text-secondary); }
.radio-item.active { border-color: var(--zw-brand); color: var(--zw-brand); background: var(--zw-brand-light); font-weight: 500; }
.submit-btn { margin: 40rpx 20rpx; height: 88rpx; line-height: 88rpx; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 32rpx; font-weight: 600; border-radius: var(--zw-radius-xs); border: none; } /* 橙底深字承重规则 */
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-md) var(--zw-radius-md) 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx 32rpx; border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx 32rpx; border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; }
.empty { text-align: center; padding: 40rpx; color: var(--zw-text-quaternary); }
</style>
