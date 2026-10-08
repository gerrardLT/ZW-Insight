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
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="handleViewImage(row)">查看流程图</el-button>
            <el-button link type="info" @click="handleViewVersions(row)">历史版本</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 流程图弹窗（支持 bpmn-js 矢量流程图与服务端回退双模式） -->
    <el-dialog
      v-model="imageDialogVisible"
      title="流程图"
      width="860px"
      destroy-on-close
      @opened="renderBpmn"
      @closed="cleanupViewer"
    >
      <div class="process-image-wrap" v-loading="viewerLoading">
        <div ref="viewerContainerRef" class="bpmn-viewer-container" v-show="!renderFailed"></div>
        <el-image
          v-if="renderFailed"
          :src="currentImageUrl"
          fit="contain"
          alt="流程图"
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
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import BpmnViewer from 'bpmn-js/lib/Viewer'
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import { getProcessList, deployProcess, getProcessImage, getProcessVersions, getProcessXml } from '@/api/workflow'

const router = useRouter()
const uploadRef = ref()
const loading = ref(false)
const tableData = ref<any[]>([])
const imageDialogVisible = ref(false)
const currentImageUrl = ref('')
const currentProcessId = ref<string>('')
const viewerContainerRef = ref<HTMLDivElement>()
const viewerLoading = ref(false)
const renderFailed = ref(false)
let bpmnViewer: InstanceType<typeof BpmnViewer> | null = null

const versionDialogVisible = ref(false)
const versionLoading = ref(false)
const versionList = ref<any[]>([])

function normalizeItem(item: any) {
  if (!item) return {}
  return {
    ...item,
    id: item.id,
    name: item.processName || item.name || item.processKey || '-',
    key: item.processKey || item.key || '-',
    version: item.versionNum ?? item.version ?? 0,
    deploymentTime: item.createdAt || item.deploymentTime || '-'
  }
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getProcessList()
    const rawList = Array.isArray(res.data) ? res.data : (res.data?.records || [])
    tableData.value = rawList.map(normalizeItem)
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

/** 编辑 = 在设计器中载入该流程；改完「部署到服务器」会以同一标识生成新版本，在途实例仍走旧版 */
function handleEdit(row: any) {
  router.push({ path: '/workflow/designer', query: { id: String(row.id) } })
}

function handleViewImage(row: any) {
  currentProcessId.value = String(row.id || '')
  currentImageUrl.value = getProcessImage(row.id)
  renderFailed.value = false
  imageDialogVisible.value = true
}

async function renderBpmn() {
  if (!viewerContainerRef.value || !currentProcessId.value) return
  cleanupViewer()
  viewerLoading.value = true
  renderFailed.value = false

  try {
    const res: any = await getProcessXml(currentProcessId.value)
    const xml = typeof res === 'string' ? res : (res?.data || '')
    if (!xml) throw new Error('BPMN XML为空')

    bpmnViewer = new BpmnViewer({
      container: viewerContainerRef.value
    })
    await bpmnViewer.importXML(xml)
    const canvas: any = bpmnViewer.get('canvas')
    canvas.zoom('fit-viewport')
  } catch (err) {
    console.warn('前端BPMN渲染失败，自动回退到服务端图片', err)
    renderFailed.value = true
  } finally {
    viewerLoading.value = false
  }
}

function cleanupViewer() {
  if (bpmnViewer) {
    try {
      bpmnViewer.destroy()
    } catch {
      // 忽略
    }
    bpmnViewer = null
  }
}

async function handleViewVersions(row: any) {
  versionDialogVisible.value = true
  versionLoading.value = true
  try {
    const res: any = await getProcessVersions(row.key || row.processKey)
    const rawList = Array.isArray(res.data) ? res.data : (res.data?.records || [])
    versionList.value = rawList.map(normalizeItem)
  } finally {
    versionLoading.value = false
  }
}

onMounted(() => {
  loadData()
})

onBeforeUnmount(() => {
  cleanupViewer()
})
</script>

<style scoped>
.process-container {
  padding: var(--zw-space-md);
}
.table-toolbar {
  margin-bottom: var(--zw-space-md);
}
.process-image-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 320px;
  width: 100%;
}
.bpmn-viewer-container {
  width: 100%;
  height: 380px;
  background: var(--zw-bg-page);
  border-radius: var(--zw-radius-sm);
  border: 1px solid var(--zw-border-light);
}
.image-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: var(--zw-text-tertiary);
}
</style>
