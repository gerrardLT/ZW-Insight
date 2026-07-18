<template>
  <div class="project-container">
    <el-card shadow="never">
      <!-- 搜索区域 -->
      <el-form :model="queryParams" inline>
        <el-form-item label="项目名称">
          <el-input v-model="queryParams.projectName" placeholder="请输入项目名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已报备" value="FILED" />
            <el-option label="招标中" value="TENDERING" />
            <el-option label="已中标" value="WON" />
            <el-option label="施工中" value="CONSTRUCTION" />
            <el-option label="已竣工" value="COMPLETED" />
            <el-option label="结项审批中" value="CLOSING" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目类型">
          <el-input v-model="queryParams.projectType" placeholder="请输入项目类型" clearable />
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

      <!-- 操作栏 -->
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增项目
        </el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="projectCode" label="项目编号" width="140" />
        <el-table-column prop="projectName" label="项目名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="projectNature" label="项目性质" width="100" />
        <el-table-column prop="projectType" label="项目类型" width="100" />
        <el-table-column prop="ownerCompanyName" label="业主单位" width="150" show-overflow-tooltip />
        <el-table-column prop="signingCompanyName" label="签约公司" width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmit(row)">提交</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="handleDelete(row)">删除</el-button>
            <el-button v-if="row.status === 'COMPLETED'" link type="warning" @click="handleClose(row)">结项</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
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
import { getProjectPage, deleteProject, submitProject, closeProject, getProjectCloseCheck } from '@/api/project'

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  projectName: '',
  status: '',
  projectType: ''
})

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  FILED: { label: '已报备', type: 'primary' },
  TENDERING: { label: '招标中', type: 'warning' },
  WON: { label: '已中标', type: 'success' },
  CONSTRUCTION: { label: '施工中', type: '' },
  COMPLETED: { label: '已竣工', type: 'success' },
  CLOSING: { label: '结项审批中', type: 'warning' },
  CLOSED: { label: '已关闭', type: 'danger' }
}

function getStatusLabel(status: string) {
  return statusMap[status]?.label || status
}

function getStatusType(status: string) {
  return (statusMap[status]?.type || 'info') as any
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getProjectPage(queryParams.value)
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
  queryParams.value = { pageNum: 1, pageSize: 10, projectName: '', status: '', projectType: '' }
  loadData()
}

function handleAdd() {
  router.push('/project/create')
}

function handleEdit(row: any) {
  router.push(`/project/edit/${row.id}`)
}

function handleView(row: any) {
  router.push(`/project/detail/${row.id}`)
}

async function handleSubmit(row: any) {
  await ElMessageBox.confirm('确定要提交该项目吗？提交后不可修改。', '提示', { type: 'warning' })
  await submitProject(row.id)
  ElMessage.success('提交成功')
  loadData()
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除该项目吗？', '提示', { type: 'warning' })
  await deleteProject(row.id)
  ElMessage.success('删除成功')
  loadData()
}

async function handleClose(row: any) {
  // 先预检结项条件
  const res: any = await getProjectCloseCheck(row.id)
  const result = res.data || {}
  if (!result.allPassed) {
    const reasons = (result.failedReasons || []).join('；') || '结项条件不满足'
    ElMessageBox.alert(reasons, '无法结项', { type: 'warning' })
    return
  }
  await ElMessageBox.confirm('确定发起项目结项审批吗？提交后项目进入结项审批中，审批通过后自动关闭。', '提示', { type: 'warning' })
  await closeProject(row.id)
  ElMessage.success('结项审批已发起')
  loadData()
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.project-container {
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
