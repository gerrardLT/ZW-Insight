<template>
  <div class="finance-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-select
            v-model="queryParams.projectId"
            placeholder="请选择项目"
            filterable
            remote
            :remote-method="searchProject"
            clearable
            style="width: 220px"
          >
            <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增项目报销
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="totalAmount" label="报销总金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="reimbursementDate" label="报销日期" width="120" />
        <el-table-column label="冲抵备用金" width="110" align="center">
          <template #default="{ row }">{{ row.offsetReserve === 1 ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="offsetAmount" label="冲抵金额" width="130" align="right">
          <template #default="{ row }">{{ row.offsetReserve === 1 ? formatMoney(row.offsetAmount) : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmitRow(row)">提交</el-button>
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

    <el-dialog v-model="dialogVisible" title="新增项目报销" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="项目" prop="projectId">
          <el-select
            v-model="formData.projectId"
            placeholder="请选择项目"
            filterable
            remote
            :remote-method="searchProject"
            style="width: 100%"
          >
            <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="报销总金额" prop="totalAmount">
          <el-input-number v-model="formData.totalAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="报销日期" prop="reimbursementDate">
          <el-date-picker v-model="formData.reimbursementDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="冲抵备用金">
          <el-switch v-model="formData.offsetReserve" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item v-if="formData.offsetReserve === 1" label="冲抵金额">
          <el-input-number v-model="formData.offsetAmount" :min="0" :precision="2" style="width: 100%" />
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getProjectReimbursementPage, createProjectReimbursement, submitProjectReimbursement } from '@/api/finance'
import { getProjectList } from '@/api/project'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const projectList = ref<any[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)

const queryParams = ref({
  page: 1,
  size: 10,
  projectId: undefined as number | undefined
})

const formData = ref({
  projectId: undefined as number | undefined,
  totalAmount: 0,
  reimbursementDate: '',
  offsetReserve: 0,
  offsetAmount: 0
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  totalAmount: [{ required: true, message: '请输入报销总金额', trigger: 'blur' }],
  reimbursementDate: [{ required: true, message: '请选择报销日期', trigger: 'change' }]
}

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  APPROVING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' }
}

function getStatusLabel(status: string) {
  return statusMap[status]?.label || status
}
function getStatusType(status: string) {
  return (statusMap[status]?.type || 'info') as any
}
function formatMoney(val: number) {
  if (!val && val !== 0) return '-'
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

async function searchProject(query: string) {
  const res: any = await getProjectList({ projectName: query })
  projectList.value = res.data || []
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getProjectReimbursementPage(queryParams.value)
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
  queryParams.value = { page: 1, size: 10, projectId: undefined }
  loadData()
}
function handleAdd() {
  formData.value = { projectId: undefined, totalAmount: 0, reimbursementDate: '', offsetReserve: 0, offsetAmount: 0 }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await createProjectReimbursement(formData.value)
    ElMessage.success('新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmitRow(row: any) {
  await ElMessageBox.confirm('确定要提交该项目报销吗？', '提示', { type: 'warning' })
  await submitProjectReimbursement(row.id)
  ElMessage.success('提交成功')
  loadData()
}

onMounted(() => {
  loadData()
  searchProject('')
})
</script>

<style scoped>
.finance-container {
  padding: 16px;
}
.table-toolbar {
  margin-bottom: 16px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
