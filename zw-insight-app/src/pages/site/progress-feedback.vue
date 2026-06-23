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
    </view>

    <view class="form-section">
      <view class="form-item">
        <text class="form-label">反馈日期</text>
        <input v-model="form.feedbackDate" placeholder="YYYY-MM-DD" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">施工部位</text>
        <input v-model="form.constructionPart" placeholder="请输入施工部位" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">计划进度(%)</text>
        <input v-model="form.plannedProgress" type="digit" placeholder="如：85" class="form-input" />
      </view>
      <view class="form-item">
        <text class="form-label">实际进度(%)</text>
        <input v-model="form.actualProgress" type="digit" placeholder="如：80" class="form-input" />
      </view>
      <view class="form-item vertical">
        <text class="form-label">进度说明</text>
        <textarea v-model="form.progressDescription" placeholder="请描述进度情况" class="textarea" :maxlength="500" />
      </view>
      <view class="form-item vertical">
        <text class="form-label">存在问题</text>
        <textarea v-model="form.issues" placeholder="请描述存在的问题（如无可不填）" class="textarea" :maxlength="500" />
      </view>
      <view class="form-item">
        <text class="form-label">备注</text>
        <input v-model="form.remark" placeholder="请输入备注" class="form-input" />
      </view>
    </view>

    <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交反馈</button>

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
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getProjectList, saveProgressFeedback } from '@/api/common'

const submitting = ref(false)
const showProjectPicker = ref(false)
const projects = ref<any[]>([])

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
  try {
    const res: any = await getProjectList({ page: 1, size: 100 })
    projects.value = res.data?.records || []
  } catch {}
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
    await saveProgressFeedback({
      projectId: form.value.projectId,
      feedbackDate: form.value.feedbackDate,
      constructionPart: form.value.constructionPart,
      plannedProgress: Number(form.value.plannedProgress),
      actualProgress: Number(form.value.actualProgress),
      progressDescription: form.value.progressDescription,
      issues: form.value.issues,
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
.form-item.vertical { flex-direction: column; align-items: flex-start; }
.form-item:last-child { border-bottom: none; }
.form-label { font-size: 28rpx; color: #303133; min-width: 160rpx; }
.form-input { flex: 1; font-size: 28rpx; color: #303133; text-align: right; }
.form-input.picker { display: flex; align-items: center; justify-content: flex-end; }
.placeholder { color: #c0c4cc; }
.arrow { margin-left: 8rpx; color: #c0c4cc; font-size: 32rpx; }
.textarea { width: 100%; height: 180rpx; border: 1rpx solid #f0f0f0; border-radius: 8rpx; padding: 16rpx; font-size: 26rpx; margin-top: 12rpx; box-sizing: border-box; }
.submit-btn { margin: 40rpx 20rpx; height: 88rpx; line-height: 88rpx; background: #409eff; color: #fff; font-size: 32rpx; border-radius: 8rpx; border: none; }
.picker-mask { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); z-index: 999; display: flex; align-items: flex-end; }
.picker-content { width: 100%; background: #fff; border-radius: 24rpx 24rpx 0 0; max-height: 70vh; }
.picker-header { display: flex; justify-content: space-between; align-items: center; padding: 24rpx 32rpx; border-bottom: 1rpx solid #f0f0f0; }
.picker-title { font-size: 30rpx; font-weight: bold; }
.picker-list { max-height: 60vh; }
.picker-item { padding: 24rpx 32rpx; border-bottom: 1rpx solid #f5f5f5; font-size: 28rpx; }
</style>
