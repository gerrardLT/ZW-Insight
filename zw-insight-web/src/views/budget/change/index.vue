<template>
  <div class="budget-change-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <ProjectSelector v-model="queryParams.projectId" width="200px" @change="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="审批中" value="SUBMITTED" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已撤回" value="WITHDRAWN" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新建变更</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="changeCode" label="变更单编号" width="160" show-overflow-tooltip />
        <el-table-column prop="projectName" label="项目名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="changeReason" label="变更原因" min-width="200" show-overflow-tooltip />
        <el-table-column prop="totalAdjustAmount" label="调整总额(元)" width="140" align="right">
          <template #default="{ row }">
            <span :class="{ 'text-danger': row.totalAdjustAmount < 0, 'text-success': row.totalAdjustAmount > 0 }">
              {{ row.totalAdjustAmount?.toLocaleString() }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmit(row)">提交</el-button>
            <el-button v-if="row.status === 'SUBMITTED'" link type="warning" @click="handleWithdraw(row)">撤回</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import ProjectSelector from '@/components/ProjectSelector.vue'
import {
  listBudgetChanges,
  deleteBudgetChange,
  submitBudgetChange,
  withdrawBudgetChange
} from '@/api/budget-change'

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  status: ''
})

const statusMap: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '审批中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  WITHDRAWN: '已撤回'
}

const statusTagTypeMap: Record<string, 'success' | 'primary' | 'warning' | 'info' | 'danger'> = {
  DRAFT: 'info',
  SUBMITTED: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'info'
}

function statusLabel(status: string) {
  return statusMap[status] || status
}

function statusTagType(status: string) {
  return statusTagTypeMap[status] || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await listBudgetChanges(queryParams.value)
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
  router.push('/budget/change/form')
}

function handleView(row: any) {
  router.push(`/budget/change/form?id=${row.id}&mode=view`)
}

function handleEdit(row: any) {
  router.push(`/budget/change/form?id=${row.id}`)
}

async function handleSubmit(row: any) {
  await ElMessageBox.confirm('确定要提交该变更单进入审批流程吗？', '提示', { type: 'warning' })
  await submitBudgetChange(row.id)
  ElMessage.success('提交成功')
  loadData()
}

async function handleWithdraw(row: any) {
  await ElMessageBox.confirm('确定要撤回该变更单吗？', '提示', { type: 'warning' })
  await withdrawBudgetChange(row.id)
  ElMessage.success('撤回成功')
  loadData()
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除该变更单吗？删除后不可恢复。', '提示', { type: 'warning' })
  await deleteBudgetChange(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.budget-change-container {
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
.text-success {
  color: #67c23a;
}
</style>
