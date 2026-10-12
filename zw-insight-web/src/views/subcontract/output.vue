<template>
  <div class="subcontract-output-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <ProjectSelector v-model="queryParams.projectId" width="180px" />
        </el-form-item>
        <el-form-item label="分包合同">
          <el-select v-model="queryParams.contractId" placeholder="全部分包合同" clearable filterable style="width: 220px" @change="handleSearch">
            <el-option v-for="c in contractOptions" :key="c.id" :label="c.contractName || c.contractCode" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">上报分包产值</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column label="关联分包合同" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ contractLabel(row.contractId) }}</template>
        </el-table-column>
        <el-table-column prop="currentOutput" label="本期产值(元)" width="150" align="right">
          <template #default="{ row }">{{ formatAmount(row.currentOutput) }}</template>
        </el-table-column>
        <el-table-column prop="cumulativeOutput" label="累计产值(元)" width="150" align="right">
          <template #default="{ row }">{{ formatAmount(row.cumulativeOutput) }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上报时间" width="170" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : 'info'" size="small">
              {{ row.status === 'APPROVED' ? '已确认' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmit(row)">提交</el-button>
            <span v-else style="color: var(--zw-text-tertiary)">已生效</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="上报分包产值" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="120px">
        <el-form-item label="所属项目" prop="projectId">
          <ProjectSelector v-model="formData.projectId" style="width: 100%" @change="onProjectChange" />
        </el-form-item>
        <el-form-item label="关联分包合同" prop="contractId">
          <el-select v-model="formData.contractId" placeholder="请选择分包合同" filterable style="width: 100%">
            <el-option v-for="c in contractOptions" :key="c.id" :label="c.contractName || c.contractCode" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="本期产值(元)" prop="currentOutput">
          <el-input-number v-model="formData.currentOutput" :min="0.01" :precision="2" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getSubcontractOutputPage,
  createSubcontractOutput,
  submitSubcontractOutput,
  getSubcontractPage
} from '@/api/subcontract'
import ProjectSelector from '@/components/ProjectSelector.vue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const contractOptions = ref<any[]>([])

const queryParams = ref({
  page: 1,
  size: 10,
  projectId: undefined as number | undefined,
  contractId: undefined as number | undefined
})

const defaultForm = () => ({
  projectId: undefined as number | undefined,
  contractId: undefined as number | undefined,
  currentOutput: undefined as number | undefined
})

const formData = ref(defaultForm())

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  contractId: [{ required: true, message: '请选择分包合同', trigger: 'change' }],
  currentOutput: [{ required: true, message: '请输入本期产值', trigger: 'blur' }]
}

const contractNameMap = computed(() => {
  const map: Record<string, string> = {}
  for (const c of contractOptions.value) {
    map[String(c.id)] = c.contractName || c.contractCode || String(c.id)
  }
  return map
})

function contractLabel(contractId?: number) {
  if (contractId == null) return '-'
  return contractNameMap.value[String(contractId)] || `#${contractId}`
}

function formatAmount(val: any) {
  if (val == null) return '0.00'
  return Number(val).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadContracts(projectId?: number) {
  try {
    const params: any = { page: 1, size: 100 }
    if (projectId) params.projectId = projectId
    const res: any = await getSubcontractPage(params)
    contractOptions.value = res.data?.records || []
  } catch {
    contractOptions.value = []
  }
}

function onProjectChange(val: any) {
  formData.value.contractId = undefined
  loadContracts(val)
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getSubcontractOutputPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.value.page = 1
  loadData()
}

function handleReset() {
  queryParams.value = { page: 1, size: 10, projectId: undefined, contractId: undefined }
  loadData()
}

function handleAdd() {
  formData.value = defaultForm()
  dialogVisible.value = true
}

async function handleFormSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitLoading.value = true
  try {
    await createSubcontractOutput(formData.value)
    ElMessage.success('上报成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmit(row: any) {
  try {
    await ElMessageBox.confirm('提交后产值将直接生效并回写分包合同累计产值，确定提交吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  await submitSubcontractOutput(row.id)
  ElMessage.success('提交成功，产值已生效')
  loadData()
}

onMounted(() => {
  loadContracts()
  loadData()
})
</script>

<style scoped>
.subcontract-output-container {
  padding: var(--zw-space-md);
}
.table-toolbar {
  margin-bottom: var(--zw-space-md);
}
.pagination-wrap {
  margin-top: var(--zw-space-md);
  display: flex;
  justify-content: flex-end;
}
</style>
