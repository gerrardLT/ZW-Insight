<template>
  <div class="finance-container">
    <el-card shadow="never">
      <el-alert
        v-if="expiringList.length > 0"
        :title="`有 ${expiringList.length} 笔质保金将在 30 天内到期，请及时处理返还`"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

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
          <el-icon><Plus /></el-icon>新增质保金
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="retentionAmount" label="质保金金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.retentionAmount) }}</template>
        </el-table-column>
        <el-table-column prop="retentionRate" label="比例(%)" width="90" align="right" />
        <el-table-column prop="retentionPeriod" label="质保期(月)" width="100" align="right" />
        <el-table-column prop="startDate" label="质保开始" width="120" />
        <el-table-column prop="expireDate" label="质保到期" width="120" />
        <el-table-column prop="returnedAmount" label="已返还" width="120" align="right">
          <template #default="{ row }">{{ formatMoney(row.returnedAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'RETURNED'" link type="primary" @click="handleReturn(row)">返还</el-button>
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

    <!-- 新增质保金 -->
    <el-dialog v-model="dialogVisible" title="新增质保金" width="600px" destroy-on-close>
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
        <el-form-item label="质保金金额" prop="retentionAmount">
          <el-input-number v-model="formData.retentionAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="质保金比例">
          <el-input-number v-model="formData.retentionRate" :min="0" :max="100" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="质保期(月)">
          <el-input-number v-model="formData.retentionPeriod" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="质保开始日期">
          <el-date-picker v-model="formData.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="质保到期日期">
          <el-date-picker v-model="formData.expireDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 返还登记 -->
    <el-dialog v-model="returnVisible" title="质保金返还" width="500px" destroy-on-close>
      <el-form ref="returnRef" :model="returnForm" :rules="returnRules" label-width="100px">
        <el-form-item label="返还金额" prop="returnAmount">
          <el-input-number v-model="returnForm.returnAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="返还日期" prop="returnDate">
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
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import {
  getRetentionPage,
  createRetention,
  getExpiringRetention,
  createRetentionReturn
} from '@/api/finance'
import { getProjectList } from '@/api/project'

const formRef = ref<FormInstance>()
const returnRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const projectList = ref<any[]>([])
const expiringList = ref<any[]>([])
const dialogVisible = ref(false)
const returnVisible = ref(false)
const submitLoading = ref(false)

const queryParams = ref({
  page: 1,
  size: 10,
  projectId: undefined as number | undefined
})

const formData = ref({
  projectId: undefined as number | undefined,
  retentionAmount: 0,
  retentionRate: 0,
  retentionPeriod: 0,
  startDate: '',
  expireDate: ''
})

const returnForm = ref({
  retentionId: undefined as number | undefined,
  returnAmount: 0,
  returnDate: ''
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  retentionAmount: [{ required: true, message: '请输入质保金金额', trigger: 'blur' }]
}

const returnRules = {
  returnAmount: [{ required: true, message: '请输入返还金额', trigger: 'blur' }],
  returnDate: [{ required: true, message: '请选择返还日期', trigger: 'change' }]
}

const statusMap: Record<string, { label: string; type: string }> = {
  ACTIVE: { label: '有效', type: 'success' },
  EXPIRED: { label: '已到期', type: 'warning' },
  RETURNED: { label: '已返还', type: 'info' }
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
    const res: any = await getRetentionPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadExpiring() {
  const res: any = await getExpiringRetention(30)
  expiringList.value = res.data || []
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
  formData.value = { projectId: undefined, retentionAmount: 0, retentionRate: 0, retentionPeriod: 0, startDate: '', expireDate: '' }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await createRetention(formData.value)
    ElMessage.success('新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

function handleReturn(row: any) {
  returnForm.value = { retentionId: row.id, returnAmount: 0, returnDate: '' }
  returnVisible.value = true
}

async function handleReturnSubmit() {
  await returnRef.value?.validate()
  submitLoading.value = true
  try {
    await createRetentionReturn(returnForm.value as any)
    ElMessage.success('返还登记成功')
    returnVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

onMounted(() => {
  loadData()
  loadExpiring()
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
