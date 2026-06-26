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
      <view class="form-item" @click="showDatePicker">
        <text class="form-label">入库日期</text>
        <view class="form-input picker">
          <text :class="{ placeholder: !form.inboundDate }">{{ form.inboundDate || '请选择日期' }}</text>
          <text class="arrow">›</text>
        </view>
      </view>
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
import { saveMaterialInbound } from '@/api/common'
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

function showDatePicker() {
  uni.showDatePicker
  const now = new Date()
  const dateStr = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  // #ifdef H5
  form.value.inboundDate = dateStr
  // #endif
  // #ifndef H5
  uni.showModal({
    title: '选择入库日期',
    editable: true,
    placeholderText: 'YYYY-MM-DD',
    success: (res) => {
      if (res.confirm && res.content) {
        form.value.inboundDate = res.content
      }
    }
  })
  // #endif
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
    await saveMaterialInbound({
      projectId: form.value.projectId,
      materialName: form.value.materialName,
      specification: form.value.specification,
      quantity: Number(form.value.quantity),
      unit: form.value.unit,
      unitPrice: Number(form.value.unitPrice),
      supplierName: form.value.supplierName,
      inboundDate: form.value.inboundDate,
      remark: form.value.remark
    })
    uni.showToast({ title: '提交成功', icon: 'success' })
    setTimeout(() => { uni.navigateBack() }, 1500)
  } catch {} finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.form-page { padding: 20rpx; padding-bottom: 120rpx; }
.form-section { background: #fff; border-radius: 12rpx; padding: 0 24rpx; margin-bottom: 20rpx; }
.form-item { display: flex; align-items: center; padding: 24rpx 0; border-bottom: 1rpx solid #f5f5f5; }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: #303133; min-width: 160rpx; }
.form-input { flex: 1; font-size: 28rpx; color: #303133; text-align: right; }
.form-input.picker { display: flex; align-items: center; justify-content: flex-end; }
.placeholder { color: #c0c4cc; }
.arrow { margin-left: 8rpx; color: #c0c4cc; font-size: 32rpx; }
.submit-btn { margin: 40rpx 20rpx; height: 88rpx; line-height: 88rpx; background: #409eff; color: #fff; font-size: 32rpx; border-radius: 8rpx; border: none; }
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: #fff; border-radius: 24rpx 24rpx 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx 32rpx; border-bottom: 1rpx solid #f0f0f0; }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx 32rpx; border-bottom: 1rpx solid #f5f5f5; font-size: 28rpx; }
.empty { text-align: center; padding: 40rpx; color: #c0c4cc; }
</style>
