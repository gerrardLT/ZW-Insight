<template>
  <div class="boq-upload-container">
    <!-- 合同信息展示 -->
    <el-card shadow="never" class="contract-info-card">
      <template #header>
        <span>合同信息</span>
      </template>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="合同编号">{{ contractInfo.contractCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="合同名称">{{ contractInfo.contractName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="合同状态">
          <el-tag :type="getStatusType(contractInfo.status)" size="small">
            {{ getStatusLabel(contractInfo.status) }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 文件上传区域 -->
    <el-card shadow="never" class="upload-card">
      <template #header>
        <div class="upload-header">
          <span>工程量清单上传</span>
          <el-button
            v-if="boqLoaded"
            type="danger"
            plain
            size="small"
            @click="handleDeleteBoq"
          >
            <el-icon><Delete /></el-icon>清除清单
          </el-button>
        </div>
      </template>

      <el-upload
        ref="uploadRef"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xlsx"
        :on-change="handleFileChange"
        :on-exceed="handleExceed"
        :file-list="fileList"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          将 Excel 文件拖拽至此处，或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            仅支持 .xlsx 格式文件，单文件不超过 20MB
          </div>
        </template>
      </el-upload>

      <div class="upload-action">
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="!selectedFile"
          @click="handleUpload"
        >
          <el-icon><Upload /></el-icon>开始上传解析
        </el-button>
      </div>
    </el-card>

    <!-- 上传结果展示 -->
    <el-card v-if="uploadResult" shadow="never" class="result-card">
      <template #header>
        <span>上传结果</span>
      </template>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-statistic title="总条目数" :value="uploadResult.totalItems" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="层级数" :value="uploadResult.levelCount" />
        </el-col>
        <el-col :span="8">
          <el-statistic title="合计金额（元）" :value="uploadResult.totalAmount" :precision="2" />
        </el-col>
      </el-row>
    </el-card>

    <!-- 清单树形表格 -->
    <el-card v-if="boqLoaded" shadow="never" class="tree-table-card">
      <template #header>
        <span>工程量清单明细</span>
      </template>
      <el-table
        :data="boqTreeData"
        v-loading="treeLoading"
        border
        row-key="id"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        default-expand-all
      >
        <el-table-column prop="itemCode" label="项目编码" width="150" />
        <el-table-column prop="itemName" label="项目名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column prop="quantity" label="工程数量" width="120" align="right">
          <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
        </el-table-column>
        <el-table-column prop="unitPrice" label="综合单价" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column prop="totalPrice" label="合价" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalPrice) }}</template>
        </el-table-column>
        <el-table-column prop="completedQuantity" label="已完成工程量" width="130" align="right">
          <template #default="{ row }">{{ formatNumber(row.completedQuantity) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile, UploadInstance, UploadRawFile } from 'element-plus'
import { uploadBoq, getBoqTree, deleteBoq } from '@/api/boq'
import { getContractDetail } from '@/api/contract'

const route = useRoute()
const contractId = computed(() => Number(route.params.contractId || route.params.id))

// 合同信息
const contractInfo = ref<any>({})

// 上传相关
const uploadRef = ref<UploadInstance>()
const fileList = ref<UploadFile[]>([])
const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const uploadResult = ref<{
  totalItems: number
  levelCount: number
  totalAmount: number
} | null>(null)

// 清单树形数据
const boqTreeData = ref<any[]>([])
const treeLoading = ref(false)
const boqLoaded = computed(() => boqTreeData.value.length > 0)

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  EFFECTIVE: { label: '已生效', type: 'success' },
  CHANGING: { label: '变更中', type: 'warning' },
  SETTLED: { label: '已结算', type: 'primary' },
  CLOSED: { label: '已关闭', type: 'danger' }
}

function getStatusLabel(status: string) {
  return statusMap[status]?.label || status || '-'
}

function getStatusType(status: string) {
  return (statusMap[status]?.type || 'info') as any
}

function formatMoney(val: number | null | undefined) {
  if (val == null) return '-'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function formatNumber(val: number | null | undefined) {
  if (val == null) return '-'
  return Number(val).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}

/**
 * 文件选择变更处理
 */
function handleFileChange(file: UploadFile) {
  const rawFile = file.raw as UploadRawFile
  // 校验文件类型
  if (!rawFile.name.endsWith('.xlsx')) {
    ElMessage.error('仅支持 .xlsx 格式文件')
    uploadRef.value?.clearFiles()
    selectedFile.value = null
    return
  }
  // 校验文件大小（20MB）
  const maxSize = 20 * 1024 * 1024
  if (rawFile.size > maxSize) {
    ElMessage.error('文件大小不能超过 20MB')
    uploadRef.value?.clearFiles()
    selectedFile.value = null
    return
  }
  selectedFile.value = rawFile
}

/**
 * 文件数量超限处理
 */
function handleExceed() {
  ElMessage.warning('仅允许上传一个文件，请先移除已选文件')
}

/**
 * 执行上传
 */
async function handleUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  uploading.value = true
  try {
    const res: any = await uploadBoq(contractId.value, selectedFile.value)
    uploadResult.value = res.data
    ElMessage.success('工程量清单上传解析成功')
    // 上传成功后加载清单树
    await loadBoqTree()
    // 清除上传组件状态
    uploadRef.value?.clearFiles()
    selectedFile.value = null
  } catch (error: any) {
    // 错误已由 request 拦截器统一处理
  } finally {
    uploading.value = false
  }
}

/**
 * 加载清单树形数据
 */
async function loadBoqTree() {
  treeLoading.value = true
  try {
    const res: any = await getBoqTree(contractId.value)
    boqTreeData.value = buildTree(res.data || [])
  } finally {
    treeLoading.value = false
  }
}

/**
 * 将平铺数据按 parentId 构建树形结构
 */
function buildTree(list: any[]): any[] {
  // 如果后端已返回树形结构（带 children），直接使用
  if (list.length > 0 && list[0].children !== undefined) {
    return list
  }
  // 否则根据 parentId 前端构建树
  const map = new Map<number, any>()
  const roots: any[] = []

  list.forEach(item => {
    map.set(item.id, { ...item, children: [] })
  })

  list.forEach(item => {
    const node = map.get(item.id)!
    if (!item.parentId || item.parentId === 0) {
      roots.push(node)
    } else {
      const parent = map.get(item.parentId)
      if (parent) {
        parent.children.push(node)
      } else {
        roots.push(node)
      }
    }
  })

  return roots
}

/**
 * 清除清单
 */
async function handleDeleteBoq() {
  await ElMessageBox.confirm(
    '确定要清除当前合同的工程量清单吗？此操作不可恢复。',
    '清除确认',
    { type: 'warning', confirmButtonText: '确定清除', cancelButtonText: '取消' }
  )
  try {
    await deleteBoq(contractId.value)
    ElMessage.success('清单已清除')
    boqTreeData.value = []
    uploadResult.value = null
  } catch (error: any) {
    // 错误已由 request 拦截器统一处理
  }
}

/**
 * 加载合同详情
 */
async function loadContractInfo() {
  try {
    const res: any = await getContractDetail(contractId.value)
    contractInfo.value = res.data || {}
  } catch (error: any) {
    // 错误已由 request 拦截器统一处理
  }
}

onMounted(() => {
  if (contractId.value) {
    loadContractInfo()
    loadBoqTree()
  }
})
</script>

<style scoped>
.boq-upload-container {
  padding: 16px;
}

.contract-info-card {
  margin-bottom: 16px;
}

.upload-card {
  margin-bottom: 16px;
}

.upload-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.upload-action {
  margin-top: 16px;
  text-align: center;
}

.result-card {
  margin-bottom: 16px;
}

.tree-table-card {
  margin-bottom: 16px;
}
</style>
