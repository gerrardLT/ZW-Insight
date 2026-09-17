<template>
  <!-- ZwiFormPage 壳：sticky 底部提交条（critique P0 拇指区）；业务逻辑原样保留 -->
  <ZwiFormPage submitText="提交出库" :loading="submitting" @submit="handleSubmit">
    <OfflineBanner />
    <!-- 项目选择 -->
    <view class="form-card">
      <ZwiPickerField
        label="所属项目"
        :displayValue="form.projectName"
        placeholder="请选择项目"
        @open="showProjectPicker = true"
      />
    </view>

    <!-- 出库信息 -->
    <view class="form-card">
      <ZwiField label="材料编码" v-model="materialCode" placeholder="扫码或输入编码">
        <template #suffix>
          <!-- #ifndef H5 -->
          <text class="scan-btn" @click="handleScan">扫码</text>
          <!-- #endif -->
          <!-- #ifdef H5 -->
          <text class="scan-btn" @click="handleCodeConfirm">查询</text>
          <!-- #endif -->
        </template>
      </ZwiField>
      <ZwiField label="材料名称" v-model="form.materialName" placeholder="请输入材料名称" />
      <ZwiField label="规格型号" v-model="form.specification" placeholder="请输入规格型号" />
      <ZwiField label="出库数量" v-model="form.quantity" inputType="digit" placeholder="请输入数量" />
      <ZwiField label="单位" v-model="form.unit" placeholder="如：吨、米、个" />
      <ZwiField label="领用人" v-model="form.receiver" placeholder="请输入领用人" />
      <ZwiField label="用途说明" v-model="form.purpose" placeholder="请输入用途" />
      <ZwiField label="出库日期" v-model="form.outboundDate" placeholder="YYYY-MM-DD" />
      <ZwiField label="备注" v-model="form.remark" placeholder="请输入备注" />
    </view>

    <!-- 项目选择弹窗（原有自绘弹层保留——契约 selectProject 业务流不变） -->
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
  </ZwiFormPage>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { saveMaterialOutbound, getMaterialByCode } from '@/api/common'
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
const materialCode = ref('')

const form = ref({
  projectId: null as number | null,
  projectName: '',
  materialName: '',
  specification: '',
  quantity: '',
  unit: '',
  receiver: '',
  purpose: '',
  outboundDate: '',
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
    // 后端 BizMaterialOutbound 契约为单头+明细数组；领料 outboundType=PICK，
    // 后端按明细扣减库存（不足报「库存不足，无法领料」）；离线时入队（需求 5.1）
    const payload = {
      projectId: form.value.projectId,
      outboundType: 'PICK',
      outboundDate: form.value.outboundDate,
      operatorName: form.value.receiver,
      details: [{
        materialName: form.value.materialName,
        specification: form.value.specification,
        unit: form.value.unit,
        quantity: Number(form.value.quantity)
      }]
    }
    const { queued } = await submitOrQueue(() => saveMaterialOutbound(payload), {
      endpoint: '/v1/material/outbound',
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
/* 表单卡壳：收敛后的通用容器（原 form-section 21 页重复的样式） */
.form-card {
  background: var(--zw-bg-card);
  border: 1rpx solid var(--zw-border-light);
  border-radius: var(--zw-radius-sm);
  margin-bottom: 20rpx;
  overflow: hidden;
}
.form-card > .form-item:last-child,
.form-card > .form-item:nth-last-child(1) {
  margin-bottom: 0;
}
/* 壳组件行边距收缩（ZwiField 自带 margin-bottom 24rpx，卡内末行归零） */
.form-card :deep(.form-item) {
  padding: 0 24rpx;
  margin-bottom: 0;
  border-bottom: 1rpx solid var(--zw-border-light);
}
.form-card :deep(.form-item:last-child) {
  border-bottom: none;
}

.scan-btn {
  margin-left: 16rpx;
  padding: 6rpx 12rpx;
  background: var(--zw-brand);
  color: var(--zw-on-primary);
  font-size: 24rpx;
  border-radius: var(--zw-radius-xs);
  white-space: nowrap;
  line-height: 1.2;
  flex-shrink: 0;
}

/* 项目选择弹窗（原样式保留） */
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-md) var(--zw-radius-md) 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx; border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx; border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; }
.empty { text-align: center; padding: 40rpx; min-height: 44px; color: var(--zw-text-quaternary); }
</style>
