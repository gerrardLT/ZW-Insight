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
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="审批中" value="APPROVING" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已付款" value="PAID" />
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
          <el-icon><Plus /></el-icon>新增付款申请
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="收款单位" width="150" show-overflow-tooltip />
        <el-table-column prop="applyAmount" label="申请金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.applyAmount) }}</template>
        </el-table-column>
        <el-table-column prop="paymentType" label="付款类型" width="120" />
        <el-table-column prop="applyDate" label="申请日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmitApply(row)">提交</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新增弹窗 -->
    <el-dialog v-model="dialogVisible" title="新增付款申请" width="600px" destroy-on-close>
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
        <el-form-item label="收款单位" prop="supplierName">
          <el-input v-model="formData.supplierName" placeholder="请输入收款单位" />
        </el-form-item>
        <el-form-item label="申请金额" prop="applyAmount">
          <el-input-number v-model="formData.applyAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="付款类型">
          <el-select v-model="formData.paymentType" style="width: 100%">
            <el-option label="材料款" value="材料款" />
            <el-option label="劳务款" value="劳务款" />
            <el-option label="机械租赁" value="机械租赁" />
            <el-option label="分包款" value="分包款" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请日期">
          <el-date-picker v-model="formData.applyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="3" />
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
import { getPaymentApplyPage, createPaymentApply, deletePaymentApply, submitPaymentApply } from '@/api/finance'
import { getProjectList } from '@/api/project'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const projectList = ref<any[]>([])
const dialogVisible = ref(false)
const submitLoading = ref(false)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  status: ''
})

const formData = ref({
  projectId: undefined as number | undefined,
  supplierName: '',
  applyAmount: 0,
  paymentType: '材料款',
  applyDate: '',
  remark: ''
})

const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  supplierName: [{ required: true, message: '请输入收款单位', trigger: 'blur' }],
  applyAmount: [{ required: true, message: '请输入申请金额', trigger: 'blur' }]
}

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  APPROVING: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  PAID: { label: '已付款', type: 'primary' }
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
    const res: any = await getPaymentApplyPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.value.pageNum = 1
  loadData()
}

function handleReset() {
  queryParams.value = { pageNum: 1, pageSize: 10, projectId: undefined, status: '' }
  loadData()
}

function handleAdd() {
  formData.value = { projectId: undefined, supplierName: '', applyAmount: 0, paymentType: '材料款', applyDate: '', remark: '' }
  dialogVisible.value = true
}

function handleView(row: any) {
  ElMessage.info('查看详情功能开发中')
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await createPaymentApply(formData.value)
    ElMessage.success('新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmitApply(row: any) {
  await ElMessageBox.confirm('确定要提交该付款申请吗？', '提示', { type: 'warning' })
  await submitPaymentApply(row.id)
  ElMessage.success('提交成功')
  loadData()
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await deletePaymentApply(row.id)
  ElMessage.success('删除成功')
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
