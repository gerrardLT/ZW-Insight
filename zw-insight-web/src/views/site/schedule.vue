<template>
  <div class="schedule-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <ProjectSelector v-model="queryParams.projectId" width="200px" default-first @change="handleSearch" />
        </el-form-item>
        <el-form-item label="任务名称">
          <el-input v-model="queryParams.taskName" placeholder="任务名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增进度计划</el-button>
      </div>

            <!-- 首屏骨架（感知性能 S1.4）：无数据且加载中显示骨架；翻页保留 v-loading 遮罩 -->
            <el-skeleton v-if="loading && !tableData.length" :rows="5" animated />
            <el-table v-else :data="tableData" v-loading="loading" border>
        <el-table-column prop="taskName" label="任务名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="projectName" label="所属项目" min-width="160" show-overflow-tooltip />
        <el-table-column prop="planStartDate" label="计划开始" width="110" />
        <el-table-column prop="planEndDate" label="计划完成" width="110" />
        <el-table-column prop="progress" label="完成进度" width="120" align="center">
          <template #default="{ row }">
            <el-progress :percentage="row.progress || 0" :stroke-width="14" :text-inside="true" />
          </template>
        </el-table-column>
        <el-table-column prop="responsible" label="负责人" width="90" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.taskStatus === 'COMPLETED' ? 'success' : row.taskStatus === 'DELAYED' ? 'danger' : 'warning'" size="small">
              {{ row.taskStatus === 'COMPLETED' ? '已完成' : row.taskStatus === 'DELAYED' ? '滞后' : '进行中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="success" @click="handleFeedback(row)">反馈</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>

      <!-- 甘特图视图 -->
      <el-card shadow="never" style="margin-top: var(--zw-space-md)">
        <template #header>
          <div style="display: flex; align-items: center; gap: var(--zw-space-sm-md);">
            <span>甘特图视图</span>
            <ProjectSelector v-model="ganttProjectId" placeholder="选择项目" width="240px" default-first />
          </div>
        </template>
        <GanttChart
          v-if="ganttProjectId && ganttHasData"
          ref="ganttRef"
          :project-id="ganttProjectId"
          :editable="true"
          @task-updated="handleGanttTaskUpdated"
        />
        <ZwEmptyState type="data" v-else-if="ganttProjectId && !ganttHasData" description="暂无进度计划数据" />
        <ZwEmptyState type="data" v-else description="请先选择项目以查看甘特图" />
      </el-card>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑计划' : '新增进度计划'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="任务名称" prop="taskName"><el-input v-model="formData.taskName" /></el-form-item>
        <el-form-item label="所属项目" prop="projectId"><ProjectSelector v-model="formData.projectId" @change="loadWbsOptions" /></el-form-item>
        <el-form-item label="关联WBS">
          <el-select v-model="formData.wbsNodeId" placeholder="可不选；挂接后可与成本对账" clearable filterable style="width: 100%">
            <el-option v-for="n in wbsOptions" :key="n.id" :label="`${n.nodeCode} ${n.nodeName}`" :value="n.id!" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开始" prop="planStartDate"><el-date-picker v-model="formData.planStartDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="计划完成" prop="planEndDate"><el-date-picker v-model="formData.planEndDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="负责人"><el-input v-model="formData.responsible" /></el-form-item>
        <el-form-item label="完成进度(%)"><el-slider v-model="formData.progress" :max="100" show-input /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleFormSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 进度反馈（2026-10-12 新增）：填实际起止/进度/状态，提交即回写计划 -->
    <el-dialog v-model="feedbackVisible" title="进度反馈" width="520px" destroy-on-close>
      <el-form ref="feedbackFormRef" :model="feedbackForm" :rules="feedbackRules" label-width="110px">
        <el-form-item label="任务">
          <el-input :model-value="feedbackForm.taskName" disabled />
        </el-form-item>
        <el-form-item label="实际开始" prop="actualStartDate">
          <el-date-picker v-model="feedbackForm.actualStartDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="实际结束">
          <el-date-picker v-model="feedbackForm.actualEndDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="完成进度(%)" prop="progress">
          <el-slider v-model="feedbackForm.progress" :max="100" show-input />
        </el-form-item>
        <el-form-item label="任务状态">
          <el-select v-model="feedbackForm.taskStatus" style="width: 100%">
            <el-option label="未开始" value="NOT_STARTED" />
            <el-option label="进行中" value="IN_PROGRESS" />
            <el-option label="已完成" value="COMPLETED" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="feedbackForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackVisible = false">取消</el-button>
        <el-button type="primary" :loading="feedbackLoading" @click="handleFeedbackSubmit">提交并生效</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import ZwEmptyState from '@/components/ZwEmptyState.vue'
import { ref, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getSchedulePage, getSchedulePlanTree, createSchedule, updateSchedule, deleteSchedule, applyScheduleFeedback } from '@/api/site'
import { getWbsSelectList, type WbsNode } from '@/api/wbs'
import GanttChart from '@/components/GanttChart.vue'
import ProjectSelector from '@/components/ProjectSelector.vue'

const formRef = ref<FormInstance>()
const ganttRef = ref<InstanceType<typeof GanttChart>>()
const route = useRoute()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const ganttProjectId = ref<number | undefined>(undefined)
const ganttHasData = ref(false)

const queryParams = ref({ page: 1, size: 10, projectId: undefined as number | string | undefined, taskName: '' })
const formData = ref({ id: undefined as number | undefined, projectId: undefined as number | string | undefined, wbsNodeId: undefined as number | undefined, taskName: '', planStartDate: '', planEndDate: '', responsible: '', progress: 0 })
const formRules = { projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }], taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }], planStartDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }], planEndDate: [{ required: true, message: '请选择完成日期', trigger: 'change' }] }

async function loadData() { loading.value = true; try { const res: any = await getSchedulePage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.page = 1; loadData() }
function handleReset() { queryParams.value = { page: 1, size: 10, projectId: undefined, taskName: '' }; loadData() }
/** WBS 节点下拉（可选挂接，用于成本-进度对账） */
const wbsOptions = ref<WbsNode[]>([])
async function loadWbsOptions() {
  const pid = formData.value.projectId
  if (pid == null || String(pid).trim() === '') {
    wbsOptions.value = []
    return
  }
  try {
    const res: any = await getWbsSelectList(pid as number)
    wbsOptions.value = res.data || []
  } catch {
    wbsOptions.value = []
  }
}

function handleAdd() { isEdit.value = false; formData.value = { id: undefined, projectId: undefined, wbsNodeId: undefined, taskName: '', planStartDate: '', planEndDate: '', responsible: '', progress: 0 }; wbsOptions.value = []; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; loadWbsOptions(); dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateSchedule(formData.value) : await createSchedule(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData(); ganttRef.value?.refresh() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteSchedule(row.id); ElMessage.success('删除成功'); loadData(); ganttRef.value?.refresh() }

// ======================== 进度反馈（2026-10-12 新增） ========================
// 后端 biz_schedule_feedback 表 / ScheduleFeedbackService / API 早已就绪，但一直没有页面入口，
// 导致「实际进度」永远录不进来 → 计划与实际无法比对。此入口补上这一环。
const feedbackVisible = ref(false)
const feedbackLoading = ref(false)
const feedbackFormRef = ref<FormInstance>()
const feedbackForm = ref({
  planId: undefined as number | undefined,
  projectId: undefined as number | undefined,
  taskName: '',
  actualStartDate: '',
  actualEndDate: '',
  progress: 0,
  taskStatus: 'IN_PROGRESS',
  remark: ''
})
const feedbackRules = {
  actualStartDate: [{ required: true, message: '请选择实际开始日期', trigger: 'change' }]
}

function handleFeedback(row: any) {
  feedbackForm.value = {
    planId: row.id,
    projectId: row.projectId,
    taskName: row.taskName,
    actualStartDate: row.actualStartDate || '',
    actualEndDate: row.actualEndDate || '',
    progress: Number(row.progress) || 0,
    taskStatus: row.taskStatus === 'DELAYED' ? 'IN_PROGRESS' : (row.taskStatus || 'IN_PROGRESS'),
    remark: ''
  }
  feedbackVisible.value = true
}

async function handleFeedbackSubmit() {
  await feedbackFormRef.value?.validate()
  feedbackLoading.value = true
  try {
    await applyScheduleFeedback({
      planId: feedbackForm.value.planId,
      projectId: feedbackForm.value.projectId,
      actualStartDate: feedbackForm.value.actualStartDate,
      actualEndDate: feedbackForm.value.actualEndDate || undefined,
      progress: feedbackForm.value.progress,
      taskStatus: feedbackForm.value.taskStatus,
      remark: feedbackForm.value.remark || undefined
    })
    ElMessage.success('反馈已提交，计划已回写')
    feedbackVisible.value = false
    loadData()
    ganttRef.value?.refresh()
  } finally {
    feedbackLoading.value = false
  }
}
function handleGanttTaskUpdated(_id: number) { loadData() }

/** 监听甘特图项目选择变化，查询数据并设置 ganttHasData */
watch(ganttProjectId, async (newId) => {
  if (!newId) {
    ganttHasData.value = false
    return
  }
  try {
    const res: any = await getSchedulePlanTree(newId)
    const data = res.data || []
    ganttHasData.value = data.length > 0
  } catch {
    ganttHasData.value = false
  }
})

onMounted(() => {
  // 承接下钻携带的项目上下文（风险中心「工期滞后」→ /site/schedule?projectId=xx）。
  // setup 期取初值，避免与 ProjectSelector 的异步加载竞态；雪花 ID 保持字符串不强转。
  const raw = route?.query?.projectId
  const pid = Array.isArray(raw) ? raw[0] : raw
  if (pid != null && String(pid).trim() !== '') {
    queryParams.value.projectId = pid as any
  }
  loadData()
})
</script>

<style scoped>
.schedule-container { padding: var(--zw-space-md); }
.table-toolbar { margin-bottom: var(--zw-space-md); }
.pagination-wrap { margin-top: var(--zw-space-md); display: flex; justify-content: flex-end; }
</style>
