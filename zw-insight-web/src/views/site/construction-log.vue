<template>
  <div class="construction-log-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-input v-model="queryParams.projectName" placeholder="项目名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="queryParams.logDate" type="date" value-format="YYYY-MM-DD" style="width: 150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增施工日志</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="logDate" label="日期" width="110" />
        <el-table-column prop="projectName" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="weather" label="天气" width="80" align="center" />
        <el-table-column prop="temperature" label="气温" width="80" align="center" />
        <el-table-column prop="workContent" label="施工内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="workerCount" label="出工人数" width="90" align="center" />
        <el-table-column prop="recorder" label="记录人" width="90" />
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
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑日志' : '新增施工日志'" width="650px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="项目名称" prop="projectName"><el-input v-model="formData.projectName" /></el-form-item>
        <el-form-item label="日期" prop="logDate"><el-date-picker v-model="formData.logDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"><el-form-item label="天气"><el-input v-model="formData.weather" placeholder="如: 晴" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="气温"><el-input v-model="formData.temperature" placeholder="如: 25℃" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="出工人数"><el-input-number v-model="formData.workerCount" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="施工内容" prop="workContent"><el-input v-model="formData.workContent" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="存在问题"><el-input v-model="formData.issues" type="textarea" :rows="2" /></el-form-item>
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
import { getConstructionLogPage, createConstructionLog, updateConstructionLog, deleteConstructionLog } from '@/api/site'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, projectName: '', logDate: '' })
const formData = ref({ id: undefined as number | undefined, projectName: '', logDate: '', weather: '', temperature: '', workerCount: 0, workContent: '', issues: '' })
const formRules = { projectName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }], logDate: [{ required: true, message: '请选择日期', trigger: 'change' }], workContent: [{ required: true, message: '请输入施工内容', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getConstructionLogPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, projectName: '', logDate: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, projectName: '', logDate: '', weather: '', temperature: '', workerCount: 0, workContent: '', issues: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateConstructionLog(formData.value) : await createConstructionLog(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteConstructionLog(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.construction-log-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
