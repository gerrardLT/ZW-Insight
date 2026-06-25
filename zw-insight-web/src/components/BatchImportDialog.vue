<template>
  <el-dialog v-model="dialogVisible" title="批量导入" width="560px" destroy-on-close @close="handleClose">
    <!-- 上传区域 -->
    <template v-if="!importResult">
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="handleFileChange"
        drag
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">仅支持 .xlsx / .xls 格式</div>
        </template>
      </el-upload>
      <div style="margin-top: 12px">
        <el-button link type="primary" @click="handleDownloadTemplate">下载导入模板</el-button>
      </div>
    </template>

    <!-- 导入结果 -->
    <template v-else>
      <el-result
        :icon="importResult.failedRows === 0 ? 'success' : 'warning'"
        :title="importResult.failedRows === 0 ? '导入成功' : '部分导入成功'"
        :sub-title="`总行数 ${importResult.totalRows}，成功 ${importResult.successRows}，失败 ${importResult.failedRows}`"
      />
      <el-table v-if="importResult.errors?.length" :data="importResult.errors" border size="small" max-height="200" style="margin-top: 12px">
        <el-table-column prop="row" label="行号" width="70" />
        <el-table-column prop="message" label="错误原因" />
      </el-table>
    </template>

    <template #footer>
      <el-button @click="dialogVisible = false">{{ importResult ? '关闭' : '取消' }}</el-button>
      <el-button v-if="!importResult" type="primary" :loading="uploading" :disabled="!selectedFile" @click="handleImport">
        开始导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { importData, downloadTemplate } from '@/api/batch'

const props = defineProps<{ visible: boolean; moduleCode: string; projectId?: number }>()
const emit = defineEmits<{ (e: 'update:visible', v: boolean): void; (e: 'success'): void }>()

const dialogVisible = ref(false)
const uploading = ref(false)
const selectedFile = ref<File | null>(null)
const importResult = ref<any>(null)

watch(() => props.visible, v => { dialogVisible.value = v; if (v) { selectedFile.value = null; importResult.value = null } })
watch(dialogVisible, v => emit('update:visible', v))

function handleFileChange(file: any) { selectedFile.value = file.raw }
function handleClose() { emit('update:visible', false) }

async function handleImport() {
  if (!selectedFile.value) return
  uploading.value = true
  try {
    const res: any = await importData(props.moduleCode, selectedFile.value, props.projectId)
    importResult.value = res.data
    if (res.data?.failedRows === 0) emit('success')
  } catch { ElMessage.error('导入失败') } finally { uploading.value = false }
}

async function handleDownloadTemplate() {
  try {
    const res: any = await downloadTemplate(props.moduleCode)
    const url = URL.createObjectURL(new Blob([res]))
    const a = document.createElement('a'); a.href = url; a.download = `${props.moduleCode}_模板.xlsx`; a.click(); URL.revokeObjectURL(url)
  } catch { ElMessage.error('模板下载失败') }
}
</script>
