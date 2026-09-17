<template>
  <!-- ZwiFormPage 壳：sticky 底部提交条（critique P0 拇指区） -->
  <ZwiFormPage submitText="提交反馈" :loading="submitting" @submit="handleSubmit">
    <view class="form-card">
      <ZwiPickerField
        label="所属项目"
        :displayValue="form.projectName"
        placeholder="请选择项目"
        @open="showProjectPicker = true"
      />
    </view>

    <view class="form-card">
      <ZwiField label="反馈日期" v-model="form.feedbackDate" placeholder="YYYY-MM-DD" />
      <ZwiField label="施工部位" v-model="form.constructionPart" placeholder="请输入施工部位" />
      <ZwiField label="计划进度(%)" v-model="form.plannedProgress" inputType="digit" placeholder="如：85" />
      <ZwiField label="实际进度(%)" v-model="form.actualProgress" inputType="digit" placeholder="如：80" />
      <!-- textarea 行保留原结构（ZwiField 无 textarea 形态） -->
      <view class="form-item vertical">
        <text class="form-label">进度说明</text>
        <textarea v-model="form.progressDescription" placeholder="请描述进度情况" class="textarea" :maxlength="500" />
      </view>
      <view class="form-item vertical">
        <text class="form-label">存在问题</text>
        <textarea v-model="form.issues" placeholder="请描述存在的问题（如无可不填）" class="textarea" :maxlength="500" />
      </view>
      <ZwiField label="备注" v-model="form.remark" placeholder="请输入备注" />
    </view>

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
  </ZwiFormPage>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { saveProgressFeedback } from '@/api/common'
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
  feedbackDate: '',
  constructionPart: '',
  plannedProgress: '',
  actualProgress: '',
  progressDescription: '',
  issues: '',
  remark: ''
})

onMounted(async () => {
  const now = new Date()
  form.value.feedbackDate = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
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
  if (!form.value.actualProgress) {
    uni.showToast({ title: '请填写实际进度', icon: 'none' }); return
  }
  submitting.value = true
  try {
    const payload = {
      projectId: form.value.projectId,
      feedbackDate: form.value.feedbackDate,
      constructionPart: form.value.constructionPart,
      plannedProgress: Number(form.value.plannedProgress),
      actualProgress: Number(form.value.actualProgress),
      progressDescription: form.value.progressDescription,
      issues: form.value.issues,
      remark: form.value.remark
    }
    // 离线时入队（需求 5.1），联网后由 syncEngine 自动提交
    const { queued } = await submitOrQueue(() => saveProgressFeedback(payload), {
      endpoint: '/v1/site/schedule/feedback',
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
/* textarea 行（vertical）保留原结构 */
.form-item.vertical { flex-direction: column; align-items: flex-start; padding: 24rpx; border-bottom: 1rpx solid var(--zw-border-light); }
.form-label { font-size: 28rpx; color: var(--zw-text-primary); min-width: 160rpx; }
.textarea { width: 100%; height: 180rpx; border: 1rpx solid var(--zw-border-light); border-radius: var(--zw-radius-sm); padding: 16rpx; min-height: 44px; font-size: 26rpx; margin-top: 12rpx; box-sizing: border-box; }

/* 项目选择弹窗（原样式保留） */
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: var(--zw-bg-mask); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: var(--zw-bg-card); border-radius: var(--zw-radius-lg) var(--zw-radius-lg) 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx 32rpx; min-height: 44px; border-bottom: 1rpx solid var(--zw-border-light); }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx 32rpx; min-height: 44px; border-bottom: 1rpx solid var(--zw-border-light); font-size: 28rpx; }
.empty { text-align: center; padding: 40rpx; min-height: 44px; color: var(--zw-text-quaternary); }
</style>
