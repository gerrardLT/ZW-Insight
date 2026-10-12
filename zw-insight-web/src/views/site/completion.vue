<template>
  <div class="completion-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <ProjectSelector v-model="queryParams.projectId" style="width: 200px" @change="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增竣工验收</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column label="项目名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ projectName(row.projectId) }}</template>
        </el-table-column>
        <el-table-column prop="acceptanceDate" label="验收日期" width="120" />
        <el-table-column prop="acceptanceReport" label="验收报告" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMap[row.status]?.type || 'info'" size="small">
              {{ statusMap[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmit(row)">提交</el-button>
            <span v-else style="color: var(--zw-text-tertiary)">-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增竣工验收" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="所属项目" prop="projectId"><ProjectSelector v-model="formData.projectId" /></el-form-item>
        <el-form-item label="验收日期" prop="acceptanceDate"><el-date-picker v-model="formData.acceptanceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="验收报告" prop="acceptanceReport"><el-input v-model="formData.acceptanceReport" type="textarea" :rows="4" placeholder="请输入竣工验收结论 / 报告摘要" /></el-form-item>
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
import { getAcceptancePage, createAcceptance, submitAcceptance } from '@/api/site'
import { getProjectList } from '@/api/project'
import ProjectSelector from '@/components/ProjectSelector.vue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
// projectId -> projectName 映射：后端 BizCompletionAcceptance 仅返回 projectId，无 projectName 列
const projectNames = ref<Record<string, string>>({})

// 竣工验收状态（后端 CompletionAcceptanceService：DRAFT→SUBMITTED→APPROVED）
const statusMap: Record<string, { label: string; type: 'success' | 'warning' | 'info' | 'danger' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  SUBMITTED: { label: '审批中', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' }
}

const queryParams = ref({ page: 1, size: 10, projectId: undefined as number | undefined })
const formData = ref({ projectId: undefined as number | undefined, acceptanceDate: '', acceptanceReport: '' })
const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  acceptanceDate: [{ required: true, message: '请选择验收日期', trigger: 'change' }]
}

function projectName(id: number | string | undefined) {
  if (id === undefined || id === null) return '-'
  return projectNames.value[String(id)] || String(id)
}

async function loadProjectNames() {
  try {
    const res: any = await getProjectList()
    const map: Record<string, string> = {}
    for (const p of res.data || []) map[String(p.id)] = p.projectName
    projectNames.value = map
  } catch {
    projectNames.value = {}
  }
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getAcceptancePage({ page: queryParams.value.page, size: queryParams.value.size, projectId: queryParams.value.projectId })
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally { loading.value = false }
}

function handleSearch() { queryParams.value.page = 1; loadData() }
function handleReset() { queryParams.value = { page: 1, size: 10, projectId: undefined }; loadData() }
function handleAdd() { formData.value = { projectId: undefined, acceptanceDate: '', acceptanceReport: '' }; dialogVisible.value = true }

async function handleFormSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await createAcceptance(formData.value)
    ElMessage.success('新增成功')
    dialogVisible.value = false
    loadData()
  } finally { submitLoading.value = false }
}

// 仅 DRAFT 可提交；审批通过后后端将项目置为已竣工（COMPLETED）
async function handleSubmit(row: any) {
  await ElMessageBox.confirm('确定提交该竣工验收单进入审批流程吗？审批通过后项目将置为已竣工。', '提示', { type: 'warning' })
  await submitAcceptance(row.id)
  ElMessage.success('已提交审批')
  loadData()
}

onMounted(() => { loadProjectNames(); loadData() })
</script>

<style scoped>
.completion-container { padding: var(--zw-space-md); }
.table-toolbar { margin-bottom: var(--zw-space-md); }
.pagination-wrap { margin-top: var(--zw-space-md); display: flex; justify-content: flex-end; }
</style>
