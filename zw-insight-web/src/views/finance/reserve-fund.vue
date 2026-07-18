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
        <el-button type="primary" @click="handleAddApply">
          <el-icon><Plus /></el-icon>新增备用金申请
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="applicant" label="申请人" width="130" show-overflow-tooltip />
        <el-table-column prop="applyAmount" label="申请金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.applyAmount) }}</template>
        </el-table-column>
        <el-table-column prop="returnedAmount" label="已归还" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.returnedAmount) }}</template>
        </el-table-column>
        <el-table-column prop="offsetAmount" label="已冲抵" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.offsetAmount) }}</template>
        </el-table-column>
        <el-table-column prop="applyDate" label="申请日期" width="120" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmitRow(row)">提交</el-button>
            <el-button v-if="row.status === 'APPROVED'" link type="primary" @click="handleReturn(row)">归还</el-button>
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

    <!-- 新增申请 -->
    <el-dialog v-model="applyVisible" title="新增备用金申请" width="600px" destroy-on-close>
      <el-form ref="applyRef" :model="applyForm" :rules="applyRules" label-width="100px">
        <el-form-item label="项目" prop="projectId">
          <el-select
            v-model="applyForm.projectId"
            placeholder="请选择项目"
            filterable
            remote
            :remote-method="searchProject"
            style="width: 100%"
          >
            <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请人" prop="applicant">
          <el-input v-model="applyForm.applicant" placeholder="请输入申请人" />
        </el-form-item>
        <el-form-item label="申请金额" prop="applyAmount">
          <el-input-number v-model="applyForm.applyAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="申请日期" prop="applyDate">
          <el-date-picker v-model="applyForm.applyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleApplySubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 归还登记 -->
    <el-dialog v-model="returnVisible" title="备用金归还" width="500px" destroy-on-close>
      <el-form ref="returnRef" :model="returnForm" :rules="returnRules" label-width="100px">
        <el-form-item label="归还金额" prop="returnAmount">
          <el-input-number v-model="returnForm.returnAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="归还日期" prop="returnDate">
          <el-date-picker v-model="returnForm.returnDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleReturnSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getReserveFundApplyPage,
  createReserveFundApply,
  submitReserveFundApply,
  createReserveFundReturn
} from '@/api/finance'
import { getProjectList } from '@/api/project'

const applyRef = ref<FormInstance>()
const returnRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const projectList = ref<any[]>([])
const applyVisible = ref(false)
const returnVisible = ref(false)
const submitLoading = ref(false)

const queryParams = ref({
  page: 1,
  size: 10,
  projectId: undefined as number | undefined
})

const applyForm = ref({
  projectId: undefined as number | undefined,
  applicant: '',
  applyAmount: 0,
  applyDate: ''
})

const returnForm = ref({
  reserveApplyId: undefined as number | undefined,
  returnAmount: 0,
  returnDate: ''
})

const applyRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  applicant: [{ required: true, message: '请输入申请人', trigger: 'blur' }],
  applyAmount: [{ required: true, message: '请输入申请金额', trigger: 'blur' }],
  applyDate: [{ required: true, message: '请选择申请日期', trigger: 'change' }]
}

const returnRules = {
  returnAmount: [{ required: true, message: '请输入归还金额', trigger: 'blur' }],
  returnDate: [{ required: true, message: '请选择归还日期', trigger: 'change' }]
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
    const res: any = await getReserveFundApplyPage(queryParams.value)
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
function handleAddApply() {
  applyForm.value = { projectId: undefined, applicant: '', applyAmount: 0, applyDate: '' }
  applyVisible.value = true
}

async function handleApplySubmit() {
  await applyRef.value?.validate()
  submitLoading.value = true
  try {
    await createReserveFundApply(applyForm.value)
    ElMessage.success('新增成功')
    applyVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmitRow(row: any) {
  await ElMessageBox.confirm('确定要提交该备用金申请吗？', '提示', { type: 'warning' })
  await submitReserveFundApply(row.id)
  ElMessage.success('提交成功')
  loadData()
}

function handleReturn(row: any) {
  returnForm.value = { reserveApplyId: row.id, returnAmount: 0, returnDate: '' }
  returnVisible.value = true
}

async function handleReturnSubmit() {
  await returnRef.value?.validate()
  submitLoading.value = true
  try {
    await createReserveFundReturn(returnForm.value as any)
    ElMessage.success('归还登记成功')
    returnVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
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
