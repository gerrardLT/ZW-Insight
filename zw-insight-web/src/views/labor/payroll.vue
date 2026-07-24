<template>
  <div class="payroll-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="班组">
          <el-input v-model="queryParams.teamName" placeholder="班组名称" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已审批" value="APPROVED" />
            <el-option label="已结算" value="SETTLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">生成工资单</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="teamName" label="班组" width="150" />
        <el-table-column label="结算周期" width="220" align="center">
          <template #default="{ row }">{{ row.periodStart }} ~ {{ row.periodEnd }}</template>
        </el-table-column>
        <el-table-column prop="orderType" label="用工类型" width="100" align="center">
          <template #default="{ row }">{{ row.orderType === 'FIXED' ? '固定' : '临时' }}</template>
        </el-table-column>
        <el-table-column prop="totalSettlement" label="结算总额(元)" width="140" align="right">
          <template #default="{ row }">{{ row.totalSettlement?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="totalPaid" label="已付(元)" width="120" align="right">
          <template #default="{ row }">{{ row.totalPaid?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="unpaid" label="未付(元)" width="120" align="right">
          <template #default="{ row }">{{ row.unpaid?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmitPayroll(row)">提交</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="生成工资单" width="550px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 16px"
        title="结算金额由周期内已审批的用工单自动汇总，无需手工录入。" />
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="班组" prop="teamId">
          <el-select v-model="formData.teamId" placeholder="请选择班组" filterable style="width: 100%" @change="handleTeamChange">
            <el-option v-for="t in teamOptions" :key="t.id" :label="t.teamName" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="用工类型" prop="orderType">
          <el-select v-model="formData.orderType" placeholder="请选择" style="width: 100%">
            <el-option label="固定" value="FIXED" />
            <el-option label="临时" value="TEMPORARY" />
          </el-select>
        </el-form-item>
        <el-form-item label="结算周期" prop="period">
          <el-date-picker v-model="formData.period" type="daterange" value-format="YYYY-MM-DD" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getPayrollPage, createPayroll, deletePayroll, submitPayroll, getLaborTeamPage } from '@/api/labor'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const teamOptions = ref<any[]>([])

const queryParams = ref({ pageNum: 1, pageSize: 10, teamName: '', status: '' })
const formData = ref({ teamId: undefined as number | undefined, projectId: undefined as number | undefined, orderType: 'FIXED', period: [] as string[] })
const formRules = {
  teamId: [{ required: true, message: '请选择班组', trigger: 'change' }],
  orderType: [{ required: true, message: '请选择用工类型', trigger: 'change' }],
  period: [{ required: true, message: '请选择结算周期', trigger: 'change' }]
}

function statusLabel(status: string) {
  return status === 'SETTLED' ? '已结算' : status === 'APPROVED' ? '已审批' : '草稿'
}
function statusTagType(status: string) {
  return status === 'SETTLED' ? 'success' : status === 'APPROVED' ? 'warning' : 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getPayrollPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}
async function loadTeamOptions() {
  const res: any = await getLaborTeamPage({ pageNum: 1, pageSize: 200 })
  teamOptions.value = res.data?.records || []
}
function handleTeamChange(teamId: number) {
  const team = teamOptions.value.find(t => t.id === teamId)
  formData.value.projectId = team?.projectId
}
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, teamName: '', status: '' }; loadData() }
function handleAdd() {
  formData.value = { teamId: undefined, projectId: undefined, orderType: 'FIXED', period: [] }
  dialogVisible.value = true
}
async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    const payload = {
      teamId: formData.value.teamId,
      projectId: formData.value.projectId,
      orderType: formData.value.orderType,
      periodStart: formData.value.period?.[0],
      periodEnd: formData.value.period?.[1]
    }
    await createPayroll(payload)
    ElMessage.success('生成成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}
async function handleSubmitPayroll(row: any) {
  await ElMessageBox.confirm('确定要提交此工资单吗？', '提示', { type: 'warning' })
  await submitPayroll(row.id)
  ElMessage.success('提交成功')
  loadData()
}
async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await deletePayroll(row.id)
  ElMessage.success('删除成功')
  loadData()
}
onMounted(() => { loadData(); loadTeamOptions() })
</script>

<style scoped>
.payroll-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
