<template>
  <div class="hr-entry-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="姓名">
          <el-input v-model="queryParams.name" placeholder="姓名" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.type" placeholder="全部" clearable style="width: 120px">
            <el-option label="入职" value="ENTRY" />
            <el-option label="离职" value="LEAVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增入职申请</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="department" label="部门" width="130" />
        <el-table-column prop="position" label="岗位" width="120" />
        <el-table-column prop="type" label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 'ENTRY' ? 'success' : 'danger'" size="small">{{ row.type === 'ENTRY' ? '入职' : '离职' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="applyDate" label="申请日期" width="110" />
        <el-table-column prop="effectDate" label="生效日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'PENDING' ? 'warning' : 'info'" size="small">
              {{ row.status === 'APPROVED' ? '已通过' : row.status === 'PENDING' ? '审批中' : '草稿' }}
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
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑' : '新增入职申请'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="姓名" prop="name"><el-input v-model="formData.name" /></el-form-item>
        <el-form-item label="部门" prop="department"><el-input v-model="formData.department" /></el-form-item>
        <el-form-item label="岗位" prop="position"><el-input v-model="formData.position" /></el-form-item>
        <el-form-item label="类型"><el-select v-model="formData.type" style="width: 100%"><el-option label="入职" value="ENTRY" /><el-option label="离职" value="LEAVE" /></el-select></el-form-item>
        <el-form-item label="生效日期"><el-date-picker v-model="formData.effectDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="formData.remark" type="textarea" :rows="2" /></el-form-item>
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
import { getHrEntryPage, createHrEntry, updateHrEntry, deleteHrEntry } from '@/api/hr'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, name: '', type: '' })
const formData = ref({ id: undefined as number | undefined, name: '', department: '', position: '', type: 'ENTRY', effectDate: '', remark: '' })
const formRules = { name: [{ required: true, message: '请输入姓名', trigger: 'blur' }], department: [{ required: true, message: '请输入部门', trigger: 'blur' }], position: [{ required: true, message: '请输入岗位', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getHrEntryPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, name: '', type: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, name: '', department: '', position: '', type: 'ENTRY', effectDate: '', remark: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateHrEntry(formData.value) : await createHrEntry(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteHrEntry(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.hr-entry-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
