<template>
  <view class="form-page">
    <OfflineBanner />
    <!-- 项目选择 -->
    <view class="form-section">
      <view class="form-item" @click="showProjectPicker = true">
        <text class="form-label">所属项目</text>
        <view class="form-input picker">
          <text :class="{ placeholder: !form.projectName }">{{ form.projectName || '请选择项目' }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
    </view>

    <!-- 入库信息 -->
    <view class="form-section">
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
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { saveMaterialInbound, getMaterialByCode } from '@/api/common'
import OfflineBanner from '@/components/OfflineBanner.vue'
import { loadProjectList, NO_OFFLINE_DATA_TIP } from '@/utils/offlineData'
import { submitOrQueue } from '@/utils/offlineSubmit'

const submitting = ref(false)
const showProjectPicker = ref(false)
const projects = ref<any[]>([])
const projectEmptyTip = ref('暂无项目')
const materialCode = ref('')

const form = ref({
  projectId: null as number | null,
  projectName: '',
  materialName: '',
  specification: '',
  quantity: '',
  unit: '',
  unitPrice: '',
  supplierName: '',
  inboundDate: '',
  remark: ''
})

onMounted(async () => {
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

// ── 扫码/手输编码带出材料信息（P0 Req6）──
// #ifndef H5
function handleScan() {
  uni.scanCode({
    onlyFromCamera: false,
    success: (res) => {
      materialCode.value = res.result
      fetchMaterialByCode(res.result)
    },
    fail: () => {
      uni.showToast({ title: '扫码已取消或失败，可手动输入编码', icon: 'none' })
    }
  })
}
// #endif

function handleCodeConfirm() {
  const code = materialCode.value.trim()
  if (!code) {
    uni.showToast({ title: '请输入材料编码', icon: 'none' }); return
  }
  fetchMaterialByCode(code)
}

async function fetchMaterialByCode(code: string) {
  try {
    const res: any = await getMaterialByCode(code)
    const m = res.data || {}
    form.value.materialName = m.materialName || ''
    form.value.specification = m.specification || ''
    form.value.unit = m.unit || ''
    uni.showToast({ title: '已带出材料信息', icon: 'success' })
  } catch {
    // 编码不存在时请求层已 toast 后端提示「材料编码不存在，请先维护材料字典」，不自动创建材料
  }
}

// picker mode=date 三端统一日期选择（替换原失效的 uni.showDatePicker 调用）
function onDateChange(e: any) {
  form.value.inboundDate = e.detail.value
}

async function handleSubmit() {
  if (!form.value.projectId) {
    uni.showToast({ title: '请选择项目', icon: 'none' }); return
  }
  if (!form.value.materialName) {
    uni.showToast({ title: '请输入材料名称', icon: 'none' }); return
  }
  if (!form.value.quantity) {
    uni.showToast({ title: '请输入数量', icon: 'none' }); return
  }
  submitting.value = true
  try {
    // 后端 BizMaterialInbound 契约为单头+明细数组（details），单价/数量随明细提交；
    // 离线时入队（需求 5.1），联网后由 syncEngine 自动提交
    const payload = {
      projectId: form.value.projectId,
      inboundDate: form.value.inboundDate,
      totalAmount: Number((Number(form.value.quantity) * Number(form.value.unitPrice || 0)).toFixed(2)),
      details: [{
        materialName: form.value.materialName,
        specification: form.value.specification,
        unit: form.value.unit,
        quantity: Number(form.value.quantity),
        unitPrice: Number(form.value.unitPrice || 0)
      }]
    }
    const { queued } = await submitOrQueue(() => saveMaterialInbound(payload), {
      endpoint: '/v1/material/inbound',
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
.form-section { background: var(--zw-bg-card); border: 1rpx solid var(--zw-border-light); border-radius: var(--zw-radius-lg); padding: 0 24rpx; margin-bottom: 20rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx 0; border-bottom: 1rpx solid var(--zw-border-light); }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: var(--zw-text-primary); min-width: 160rpx; }
.form-input { flex: 1; font-size: 28rpx; color: var(--zw-text-primary); text-align: right; }
.form-input.picker { display: flex; align-items: center; justify-content: flex-end; }
.code-wrap { display: flex; align-items: center; justify-content: flex-end; }
.code-input { flex: 1; font-size: 28rpx; color: var(--zw-text-primary); text-align: right; }
.scan-btn { margin-left: 16rpx; padding: 6rpx 20rpx; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 24rpx; border-radius: var(--zw-radius-sm); flex-shrink: 0; } /* 橙底深字承重规则 */
.placeholder { color: var(--zw-text-quaternary); }
.arrow { margin-left: 8rpx; color: var(--zw-text-quaternary); font-size: 32rpx; }
.submit-btn { margin: 40rpx 20rpx; height: 88rpx; line-height: 88rpx; background: var(--zw-brand); color: var(--zw-on-primary); font-size: 32rpx; font-weight: 600; border-radius: var(--zw-radius-sm); border: none; } /* 橙底深字承重规则 */
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-lg) var(--zw-radius-lg) 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx 32rpx; border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx 32rpx; border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; }
.empty { text-align: center; padding: 40rpx; color: var(--zw-text-quaternary); }
</style>
