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
          <el-icon><Plus /></el-icon>新增其他费用付款
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="payerName" label="付款人" width="150" show-overflow-tooltip />
        <el-table-column prop="paymentAmount" label="付款金额" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.paymentAmount) }}</template>
        </el-table-column>
        <el-table-column prop="paymentDate" label="付款日期" width="120" />
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusLabel(row.status) }}</el-tag>
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

    <el-dialog v-model="dialogVisible" title="新增其他费用付款" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
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
        <el-form-item label="付款人" prop="payerName">
          <el-input v-model="formData.payerName" placeholder="请输入付款人" />
        </el-form-item>
        <el-form-item label="付款金额" prop="paymentAmount">
          <el-input-number v-model="formData.paymentAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款日期" prop="paymentDate">
          <el-date-picker v-model="formData.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" placeholder="选填" />
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
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getOtherPaymentPage, createOtherPayment } from '@/api/finance'
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
  payerName: '',
  paymentAmount: 0,
  paymentDate: '',
  remark: ''
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  payerName: [{ required: true, message: '请输入付款人', trigger: 'blur' }],
  paymentAmount: [{ required: true, message: '请输入付款金额', trigger: 'blur' }],
  paymentDate: [{ required: true, message: '请选择付款日期', trigger: 'change' }]
}

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
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
    const res: any = await getOtherPaymentPage(queryParams.value)
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
  formData.value = { projectId: undefined, payerName: '', paymentAmount: 0, paymentDate: '', remark: '' }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await createOtherPayment(formData.value)
    ElMessage.success('新增成功')
    dialogVisible.value = false
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
