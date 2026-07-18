<template>
  <div class="finance-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增个人报销
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="totalAmount" label="报销总金额" width="150" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column prop="reimbursementDate" label="报销日期" width="130" />
        <el-table-column prop="remark" label="备注" min-width="200" show-overflow-tooltip />
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

    <el-dialog v-model="dialogVisible" title="新增个人报销" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="报销总金额" prop="totalAmount">
          <el-input-number v-model="formData.totalAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="报销日期" prop="reimbursementDate">
          <el-date-picker v-model="formData.reimbursementDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getPersonalReimbursementPage, createPersonalReimbursement, submitPersonalReimbursement } from '@/api/finance'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)

const queryParams = ref({
  page: 1,
  size: 10
})

const formData = ref({
  totalAmount: 0,
  reimbursementDate: '',
  remark: ''
})

const formRules = {
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

async function loadData() {
  loading.value = true
  try {
    const res: any = await getPersonalReimbursementPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  formData.value = { totalAmount: 0, reimbursementDate: '', remark: '' }
  dialogVisible.value = true
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await createPersonalReimbursement(formData.value)
    ElMessage.success('新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmitRow(row: any) {
  await ElMessageBox.confirm('确定要提交该个人报销吗？', '提示', { type: 'warning' })
  await submitPersonalReimbursement(row.id)
  ElMessage.success('提交成功')
  loadData()
}

onMounted(() => {
  loadData()
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
