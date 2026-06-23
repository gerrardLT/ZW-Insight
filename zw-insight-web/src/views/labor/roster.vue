<template>
  <div class="labor-roster-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="姓名">
          <el-input v-model="queryParams.workerName" placeholder="工人姓名" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="班组">
          <el-input v-model="queryParams.teamName" placeholder="所属班组" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="工种">
          <el-input v-model="queryParams.workType" placeholder="工种" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增人员</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="workerName" label="姓名" width="100" />
        <el-table-column prop="idCard" label="身份证号" width="180" />
        <el-table-column prop="phone" label="联系电话" width="130" />
        <el-table-column prop="teamName" label="所属班组" width="130" />
        <el-table-column prop="workType" label="工种" width="100" />
        <el-table-column prop="entryDate" label="进场日期" width="110" />
        <el-table-column prop="exitDate" label="退场日期" width="110" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'IN' ? 'success' : 'info'" size="small">{{ row.status === 'IN' ? '在场' : '退场' }}</el-tag>
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
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑人员' : '新增人员'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="姓名" prop="workerName"><el-input v-model="formData.workerName" /></el-form-item>
        <el-form-item label="身份证号" prop="idCard"><el-input v-model="formData.idCard" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="formData.phone" /></el-form-item>
        <el-form-item label="所属班组"><el-input v-model="formData.teamName" /></el-form-item>
        <el-form-item label="工种"><el-input v-model="formData.workType" /></el-form-item>
        <el-form-item label="进场日期"><el-date-picker v-model="formData.entryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
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
import { getLaborRosterPage, createLaborRoster, updateLaborRoster, deleteLaborRoster } from '@/api/labor'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, workerName: '', teamName: '', workType: '' })
const formData = ref({ id: undefined as number | undefined, workerName: '', idCard: '', phone: '', teamName: '', workType: '', entryDate: '' })
const formRules = { workerName: [{ required: true, message: '请输入姓名', trigger: 'blur' }], idCard: [{ required: true, message: '请输入身份证号', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getLaborRosterPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, workerName: '', teamName: '', workType: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, workerName: '', idCard: '', phone: '', teamName: '', workType: '', entryDate: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateLaborRoster(formData.value) : await createLaborRoster(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteLaborRoster(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.labor-roster-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
