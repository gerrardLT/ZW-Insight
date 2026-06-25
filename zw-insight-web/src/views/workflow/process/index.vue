<template>
  <div class="process-container">
    <el-card shadow="never">
      <!-- 操作栏 -->
      <div class="table-toolbar">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :show-file-list="false"
          accept=".bpmn,.bpmn20.xml,.xml"
          :on-change="handleFileChange"
        >
          <template #trigger>
            <el-button type="primary">
              <el-icon><Upload /></el-icon>部署流程
            </el-button>
          </template>
        </el-upload>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="name" label="流程名称" min-width="180" />
        <el-table-column prop="key" label="流程标识" width="180" />
        <el-table-column prop="version" label="版本" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small">V{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deploymentTime" label="部署时间" width="170" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewImage(row)">查看流程图</el-button>
            <el-button link type="info" @click="handleViewVersions(row)">历史版本</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 流程图弹窗 -->
    <el-dialog v-model="imageDialogVisible" title="流程图" width="800px" destroy-on-close>
      <div class="process-image-wrap">
        <el-image
          :src="currentImageUrl"
          fit="contain"
          style="width: 100%; min-height: 300px"
        >
          <template #error>
            <div class="image-error">
              <el-icon size="48"><Picture /></el-icon>
              <p>流程图加载失败</p>
            </div>
          </template>
        </el-image>
      </div>
    </el-dialog>

    <!-- 历史版本弹窗 -->
    <el-dialog v-model="versionDialogVisible" title="历史版本" width="600px" destroy-on-close>
      <el-table :data="versionList" v-loading="versionLoading" border>
        <el-table-column prop="name" label="流程名称" min-width="150" />
        <el-table-column prop="version" label="版本" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small">V{{ row.version }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deploymentTime" label="部署时间" width="170" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewImage(row)">流程图</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { getProcessList, deployProcess, getProcessImage, getProcessVersions } from '@/api/workflow'

const uploadRef = ref()
const loading = ref(false)
const tableData = ref<any[]>([])
const imageDialogVisible = ref(false)
const currentImageUrl = ref('')
const versionDialogVisible = ref(false)
const versionLoading = ref(false)
const versionList = ref<any[]>([])

async function loadData() {
  loading.value = true
  try {
    const res: any = await getProcessList()
    tableData.value = res.data || []
  } finally {
    loading.value = false
  }
}

async function handleFileChange(file: UploadFile) {
  if (!file.raw) return
  const formData = new FormData()
  formData.append('file', file.raw)
  try {
    await deployProcess(formData)
    ElMessage.success('部署成功')
    loadData()
  } catch {
    ElMessage.error('部署失败')
  }
}

function handleViewImage(row: any) {
  currentImageUrl.value = getProcessImage(row.id)
  imageDialogVisible.value = true
}

async function handleViewVersions(row: any) {
  versionDialogVisible.value = true
  versionLoading.value = true
  try {
    const res: any = await getProcessVersions(row.key)
    versionList.value = res.data || []
  } finally {
    versionLoading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.process-container {
  padding: 16px;
}
.table-toolbar {
  margin-bottom: 16px;
}
.process-image-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 300px;
}
.image-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #909399;
  gap: 8px;
}
</style>
