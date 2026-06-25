<template>
  <el-dialog v-model="dialogVisible" title="数据导出" width="420px" :close-on-click-modal="false" @close="handleClose">
    <div class="export-status">
      <template v-if="status === 'PENDING' || status === 'PROCESSING'">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p>正在导出数据，请稍候...</p>
        <el-progress :percentage="progress" :stroke-width="12" style="margin-top: 16px" />
      </template>
      <template v-else-if="status === 'COMPLETED'">
        <el-result icon="success" title="导出完成" />
        <el-button type="primary" @click="handleDownload">下载文件</el-button>
      </template>
      <template v-else-if="status === 'FAILED'">
        <el-result icon="error" title="导出失败" :sub-title="errorMsg" />
      </template>
    </div>
    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'
import { startExport, getExportStatus, downloadExportFile } from '@/api/batch'

const props = defineProps<{ visible: boolean; moduleCode: string; params?: any }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void }>()

const dialogVisible = ref(false)
const status = ref('PENDING')
const progress = ref(0)
const errorMsg = ref('')
const taskId = ref<number>(0)
let pollTimer: ReturnType<typeof setInterval> | null = null

watch(() => props.visible, async (v) => {
  dialogVisible.value = v
  if (v) { status.value = 'PENDING'; progress.value = 0; errorMsg.value = ''; await doExport() }
})
watch(dialogVisible, v => emit('update:visible', v))

async function doExport() {
  try {
    const res: any = await startExport(props.moduleCode, props.params)
    taskId.value = res.data
    startPolling()
  } catch { status.value = 'FAILED'; errorMsg.value = '启动导出失败' }
}

function startPolling() {
  pollTimer = setInterval(async () => {
    try {
      const res: any = await getExportStatus(taskId.value)
      const s = res.data
      status.value = s.status; progress.value = s.progress || 0; errorMsg.value = s.errorMsg || ''
      if (s.status === 'COMPLETED' || s.status === 'FAILED') stopPolling()
    } catch { stopPolling(); status.value = 'FAILED'; errorMsg.value = '查询状态失败' }
  }, 2000)
}

function stopPolling() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null } }

async function handleDownload() {
  try {
    const res: any = await downloadExportFile(taskId.value)
    const url = URL.createObjectURL(new Blob([res]))
    const a = document.createElement('a'); a.href = url; a.download = `${props.moduleCode}_导出.xlsx`; a.click(); URL.revokeObjectURL(url)
  } catch { ElMessage.error('下载失败') }
}

function handleClose() { stopPolling(); emit('update:visible', false) }
onBeforeUnmount(() => stopPolling())
</script>

<style scoped>
.export-status { text-align: center; padding: 24px 0; }
.export-status p { margin-top: 12px; color: #606266; }
</style>
