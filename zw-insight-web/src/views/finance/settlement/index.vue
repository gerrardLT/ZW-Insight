<template>
  <div class="settlement-container">
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
            <el-option label="审批中" value="SUBMITTED" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
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
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>创建结算单
        </el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="settlementCode" label="结算单编号" width="160" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="totalIncome" label="总收入" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalIncome) }}</template>
        </el-table-column>
        <el-table-column prop="totalExpenditure" label="总支出" width="140" align="right">
          <template #default="{ row }">{{ formatMoney(row.totalExpenditure) }}</template>
        </el-table-column>
        <el-table-column prop="profit" label="利润" width="130" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.profit < 0 }">{{ formatMoney(row.profit) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="profitRate" label="利润率" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.profitRate < 0 }">{{ row.profitRate }}%</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看详情</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmit(row)">提交审批</el-button>
            <el-button link type="warning" @click="handleExport(row)">导出</el-button>
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

    <!-- 创建结算单弹窗：选择项目 -->
    <el-dialog v-model="createDialogVisible" title="创建结算单" width="450px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="项目" prop="projectId">
          <el-select
            v-model="createForm.projectId"
            placeholder="请选择已竣工的项目"
            filterable
            remote
            :remote-method="searchProject"
            style="width: 100%"
          >
            <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreateSubmit">确定创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getSettlementPage, createSettlement, submitSettlement, exportSettlement } from '@/api/settlement'
import { getProjectList } from '@/api/project'

const router = useRouter()
const createFormRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const projectList = ref<any[]>([])
const createDialogVisible = ref(false)
const createLoading = ref(false)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  status: ''
})

const createForm = ref({
  projectId: undefined as number | undefined
})

const createRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }]
}

function formatMoney(val: number) {
  if (val === null || val === undefined) return '-'
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

function getStatusType(status: string) {
  const map: Record<string, string> = {
    DRAFT: 'info',
    SUBMITTED: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger'
  }
  return map[status] || 'info'
}

function getStatusLabel(status: string) {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    SUBMITTED: '审批中',
    APPROVED: '已通过',
    REJECTED: '已驳回'
  }
  return map[status] || status
}

async function searchProject(query: string) {
  const res: any = await getProjectList({ projectName: query })
  projectList.value = res.data || []
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getSettlementPage(queryParams.value)
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

function handleCreate() {
  createForm.value = { projectId: undefined }
  createDialogVisible.value = true
}

async function handleCreateSubmit() {
  await createFormRef.value?.validate()
  createLoading.value = true
  try {
    const res: any = await createSettlement(createForm.value.projectId!)
    ElMessage.success('结算单创建成功')
    createDialogVisible.value = false
    // 跳转到详情页
    router.push({ name: 'SettlementDetail', params: { id: res.data.id } })
  } finally {
    createLoading.value = false
  }
}

function handleView(row: any) {
  router.push({ name: 'SettlementDetail', params: { id: row.id } })
}

async function handleSubmit(row: any) {
  await ElMessageBox.confirm('确定要提交该结算单进行审批吗？', '提示', { type: 'warning' })
  await submitSettlement(row.id)
  ElMessage.success('提交成功')
  loadData()
}

async function handleExport(row: any) {
  try {
    const res: any = await exportSettlement(row.id)
    // Blob 下载
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `结算报告_${row.settlementCode || row.id}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  loadData()
  searchProject('')
})
</script>

<style scoped>
.settlement-container {
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
.text-danger {
  color: #f56c6c;
}
</style>
