<template>
  <div class="work-order-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <ProjectSelector v-model="queryParams.projectId" width="180px" />
        </el-form-item>
        <el-form-item label="班组">
          <TeamSelector v-model="queryParams.teamId" width="160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已确认" value="APPROVED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增派工单</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="workerName" label="工人" width="120" />
        <el-table-column prop="workDate" label="工作日期" width="120" />
        <el-table-column prop="orderType" label="用工类型" width="100" align="center">
          <template #default="{ row }">{{ row.orderType === 'FIXED' ? '固定' : '临时' }}</template>
        </el-table-column>
        <el-table-column prop="hours" label="工时" width="90" align="right" />
        <el-table-column prop="hourlyRate" label="时薪(元)" width="100" align="right" />
        <el-table-column prop="overtime" label="加班工时" width="100" align="right" />
        <el-table-column prop="totalAmount" label="合计(元)" width="120" align="right">
          <template #default="{ row }">{{ row.totalAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : 'info'" size="small">
              {{ row.status === 'APPROVED' ? '已确认' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmit(row)">提交</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑派工单' : '新增派工单'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="项目" prop="projectId"><ProjectSelector v-model="formData.projectId" /></el-form-item>
        <el-form-item label="班组" prop="teamId"><TeamSelector v-model="formData.teamId" /></el-form-item>
        <el-form-item label="工人姓名" prop="workerName"><el-input v-model="formData.workerName" /></el-form-item>
        <el-form-item label="用工类型" prop="orderType">
          <el-select v-model="formData.orderType" style="width: 100%">
            <el-option label="固定" value="FIXED" />
            <el-option label="临时" value="TEMPORARY" />
          </el-select>
        </el-form-item>
        <el-form-item label="工作日期" prop="workDate"><el-date-picker v-model="formData.workDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="工时" prop="hours"><el-input-number v-model="formData.hours" :min="0" :precision="1" style="width: 100%" /></el-form-item>
        <el-form-item label="时薪(元)" prop="hourlyRate"><el-input-number v-model="formData.hourlyRate" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="加班工时"><el-input-number v-model="formData.overtime" :min="0" :precision="1" style="width: 100%" /></el-form-item>
        <el-form-item label="加班费率"><el-input-number v-model="formData.overtimeRate" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="合计预览">
          <span style="font-weight: 600">{{ totalPreview.toFixed(2) }} 元</span>
          <span style="color: #909399; margin-left: 8px; font-size: 12px">（最终以后端计算为准）</span>
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getWorkOrderPage, createWorkOrder, updateWorkOrder, deleteWorkOrder, submitWorkOrder } from '@/api/labor'
import ProjectSelector from '@/components/ProjectSelector.vue'
import TeamSelector from '@/components/TeamSelector.vue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ page: 1, size: 10, projectId: undefined as number | undefined, teamId: undefined as number | undefined, status: '' })
const defaultForm = () => ({ id: undefined as number | undefined, projectId: undefined as number | undefined, teamId: undefined as number | undefined, workerName: '', orderType: 'TEMPORARY', workDate: '', hours: 0, hourlyRate: 0, overtime: 0, overtimeRate: 0 })
const formData = ref(defaultForm())
const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  teamId: [{ required: true, message: '请选择班组', trigger: 'change' }],
  workerName: [{ required: true, message: '请输入工人姓名', trigger: 'blur' }],
  orderType: [{ required: true, message: '请选择用工类型', trigger: 'change' }],
  workDate: [{ required: true, message: '请选择工作日期', trigger: 'change' }]
}

const totalPreview = computed(() => (formData.value.hours || 0) * (formData.value.hourlyRate || 0) + (formData.value.overtime || 0) * (formData.value.overtimeRate || 0))

async function loadData() { loading.value = true; try { const res: any = await getWorkOrderPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.page = 1; loadData() }
function handleReset() { queryParams.value = { page: 1, size: 10, projectId: undefined, teamId: undefined, status: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = defaultForm(); dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...defaultForm(), ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateWorkOrder(formData.value) : await createWorkOrder(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleSubmit(row: any) { await ElMessageBox.confirm('确定要提交吗？', '提示', { type: 'warning' }); await submitWorkOrder(row.id); ElMessage.success('提交成功'); loadData() }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteWorkOrder(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.work-order-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
