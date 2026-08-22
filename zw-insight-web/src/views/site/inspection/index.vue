<template>
  <div class="inspection-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="质量检查" name="quality" />
        <el-tab-pane label="安全检查" name="safety" />
      </el-tabs>

      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <ProjectSelector v-model="queryParams.projectId" style="width: 180px" />
        </el-form-item>
        <el-form-item label="是否有问题">
          <el-select v-model="queryParams.hasProblem" placeholder="全部" clearable style="width: 120px">
            <el-option label="无问题" :value="0" />
            <el-option label="有问题" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="整改状态">
          <el-select v-model="queryParams.rectificationStatus" placeholder="全部" clearable style="width: 130px">
            <el-option label="待整改" value="PENDING" />
            <el-option label="已提交" value="SUBMITTED" />
            <el-option label="已通过" value="APPROVED" />
            <el-option label="已驳回" value="REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增{{ activeTab === 'quality' ? '质量' : '安全' }}检查</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="projectName" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="inspectionContent" label="检查内容" min-width="180" show-overflow-tooltip />
        <el-table-column label="是否有问题" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.hasProblem === 1 ? 'danger' : 'success'" size="small">{{ row.hasProblem === 1 ? '有问题' : '无问题' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="problemDescription" label="问题描述" min-width="160" show-overflow-tooltip />
        <el-table-column prop="rectificationStatus" label="整改状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.rectificationStatus" :type="rectTagType(row.rectificationStatus)" size="small">{{ rectText(row.rectificationStatus) }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="检查时间" width="160" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDetail(row)">详情</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="canAssign(row)" link type="warning" @click="openAssignDialog(row)">指派整改</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <!-- 指派整改弹窗 -->
    <el-dialog v-model="assignVisible" title="指派整改" width="460px" destroy-on-close>
      <el-form ref="assignFormRef" :model="assignForm" :rules="assignRules" label-width="100px">
        <el-form-item label="整改责任人" prop="responsiblePersonId">
          <el-select
            v-model="assignForm.responsiblePersonId"
            placeholder="请选择整改责任人"
            filterable
            remote
            :remote-method="searchUsers"
            :loading="userSearchLoading"
            style="width: 100%"
          >
            <el-option v-for="u in userOptions" :key="u.id" :label="u.realName || u.username" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="整改期限" prop="rectificationDeadline">
          <el-date-picker v-model="assignForm.rectificationDeadline" type="date" value-format="YYYY-MM-DD" placeholder="选择整改期限" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assignLoading" @click="handleAssignSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { getQualityInspectionPage, getSafetyInspectionPage, deleteQualityInspection, deleteSafetyInspection, assignRectification } from '@/api/site'
import { getUserPage } from '@/api/system'
import ProjectSelector from '@/components/ProjectSelector.vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const activeTab = ref('quality')

const RECT_MAP: Record<string, { text: string; type: 'success' | 'warning' | 'info' | 'danger' }> = {
  PENDING: { text: '待整改', type: 'warning' },
  SUBMITTED: { text: '已提交', type: 'info' },
  APPROVED: { text: '已通过', type: 'success' },
  REJECTED: { text: '已驳回', type: 'danger' }
}
function rectText(s: string) { return RECT_MAP[s]?.text ?? s }
function rectTagType(s: string) { return RECT_MAP[s]?.type ?? 'info' }

const queryParams = ref<{ page: number; size: number; projectId: number | undefined; hasProblem: number | undefined; rectificationStatus: string }>({ page: 1, size: 10, projectId: undefined, hasProblem: undefined, rectificationStatus: '' })

const getPageApi = () => activeTab.value === 'quality' ? getQualityInspectionPage : getSafetyInspectionPage
const getDeleteApi = () => activeTab.value === 'quality' ? deleteQualityInspection : deleteSafetyInspection

async function loadData() {
  loading.value = true
  try {
    const res: any = await getPageApi()(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleTabChange() { queryParams.value.page = 1; loadData() }
function handleSearch() { queryParams.value.page = 1; loadData() }
function handleReset() { queryParams.value = { page: 1, size: 10, projectId: undefined, hasProblem: undefined, rectificationStatus: '' }; loadData() }

function handleAdd() {
  router.push({ path: '/site/inspection/form', query: { type: activeTab.value } })
}

function handleEdit(row: any) {
  router.push({ path: `/site/inspection/form/${row.id}`, query: { type: activeTab.value } })
}

function handleDetail(row: any) {
  router.push({ path: `/site/inspection/detail/${row.id}` })
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await getDeleteApi()(row.id)
  ElMessage.success('删除成功')
  loadData()
}

// ================= 指派整改 =================
const assignVisible = ref(false)
const assignLoading = ref(false)
const assignFormRef = ref<FormInstance>()
const userOptions = ref<any[]>([])
const userSearchLoading = ref(false)
const assignTargetId = ref<number>(0)

const assignForm = ref({
  responsiblePersonId: undefined as number | undefined,
  rectificationDeadline: ''
})
const assignRules: FormRules = {
  responsiblePersonId: [{ required: true, message: '请选择整改责任人', trigger: 'change' }],
  rectificationDeadline: [{ required: true, message: '请选择整改期限', trigger: 'change' }]
}

// 有问题且未闭环（非 APPROVED）的记录可指派/重新指派
function canAssign(row: any) {
  return row.hasProblem === 1 && row.rectificationStatus !== 'APPROVED'
}

async function searchUsers(query: string) {
  userSearchLoading.value = true
  try {
    const res: any = await getUserPage({ realName: query, page: 1, size: 20 })
    userOptions.value = res.data?.records || []
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '用户列表加载失败')
  } finally {
    userSearchLoading.value = false
  }
}

async function openAssignDialog(row: any) {
  assignTargetId.value = row.id
  assignForm.value = { responsiblePersonId: row.responsiblePersonId || undefined, rectificationDeadline: row.rectificationDeadline || '' }
  assignVisible.value = true
  await searchUsers('')
}

async function handleAssignSubmit() {
  await assignFormRef.value?.validate()
  assignLoading.value = true
  try {
    await assignRectification(assignTargetId.value, {
      responsiblePersonId: assignForm.value.responsiblePersonId,
      rectificationDeadline: assignForm.value.rectificationDeadline
    })
    ElMessage.success('已指派整改')
    assignVisible.value = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '指派整改失败')
  } finally {
    assignLoading.value = false
  }
}

onMounted(() => { loadData() })
</script>

<style scoped>
.inspection-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
