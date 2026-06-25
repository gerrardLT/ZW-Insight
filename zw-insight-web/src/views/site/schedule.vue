<template>
  <div class="schedule-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-input v-model="queryParams.projectName" placeholder="项目名称" clearable style="width: 200px" />
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

      <el-table :data="tableData" v-loading="loading" border>
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
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'DELAYED' ? 'danger' : 'warning'" size="small">
              {{ row.status === 'COMPLETED' ? '已完成' : row.status === 'DELAYED' ? '滞后' : '进行中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>

      <!-- 甘特图视图 -->
      <el-card shadow="never" style="margin-top: 16px">
        <template #header><span>甘特图视图</span></template>
        <GanttChart
          v-if="ganttProjectId"
          ref="ganttRef"
          :project-id="ganttProjectId"
          :editable="true"
          @task-updated="handleGanttTaskUpdated"
        />
        <el-empty v-else description="请先选择项目以查看甘特图" />
      </el-card>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑计划' : '新增进度计划'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="任务名称" prop="taskName"><el-input v-model="formData.taskName" /></el-form-item>
        <el-form-item label="所属项目"><el-input v-model="formData.projectName" /></el-form-item>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getSchedulePage, createSchedule, updateSchedule, deleteSchedule } from '@/api/site'
import GanttChart from '@/components/GanttChart.vue'

const formRef = ref<FormInstance>()
const ganttRef = ref<InstanceType<typeof GanttChart>>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)
const ganttProjectId = ref<number>(0)

const queryParams = ref({ pageNum: 1, pageSize: 10, projectName: '', taskName: '' })
const formData = ref({ id: undefined as number | undefined, taskName: '', projectName: '', planStartDate: '', planEndDate: '', responsible: '', progress: 0 })
const formRules = { taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }], planStartDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }], planEndDate: [{ required: true, message: '请选择完成日期', trigger: 'change' }] }

async function loadData() { loading.value = true; try { const res: any = await getSchedulePage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0; /* 从列表中提取 projectId 用于甘特图 */ if (tableData.value.length > 0 && tableData.value[0].projectId) { ganttProjectId.value = tableData.value[0].projectId } } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, projectName: '', taskName: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, taskName: '', projectName: '', planStartDate: '', planEndDate: '', responsible: '', progress: 0 }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateSchedule(formData.value) : await createSchedule(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData(); ganttRef.value?.refresh() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteSchedule(row.id); ElMessage.success('删除成功'); loadData(); ganttRef.value?.refresh() }
function handleGanttTaskUpdated(_id: number) { loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.schedule-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
