<template>
  <div class="subcontract-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="合同名称">
          <el-input v-model="queryParams.contractName" placeholder="合同名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="分包方">
          <el-input v-model="queryParams.subcontractor" placeholder="分包方" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增分包合同</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="contractNo" label="合同编号" width="150" />
        <el-table-column prop="contractName" label="合同名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="subcontractor" label="分包方" width="150" />
        <el-table-column prop="contractAmount" label="合同金额(元)" width="140" align="right">
          <template #default="{ row }">{{ row.contractAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="projectName" label="所属项目" min-width="160" show-overflow-tooltip />
        <el-table-column prop="signDate" label="签订日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'EXECUTING' ? 'warning' : 'info'" size="small">
              {{ row.status === 'COMPLETED' ? '已完成' : row.status === 'EXECUTING' ? '执行中' : '草稿' }}
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑分包合同' : '新增分包合同'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="合同名称" prop="contractName"><el-input v-model="formData.contractName" /></el-form-item>
        <el-form-item label="分包方" prop="subcontractor"><el-input v-model="formData.subcontractor" /></el-form-item>
        <el-form-item label="合同金额" prop="contractAmount"><el-input-number v-model="formData.contractAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="分包内容"><el-input v-model="formData.content" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="签订日期"><el-date-picker v-model="formData.signDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
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
import { getSubcontractPage, createSubcontract, updateSubcontract, deleteSubcontract } from '@/api/subcontract'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, contractName: '', subcontractor: '' })
const formData = ref({ id: undefined as number | undefined, contractName: '', subcontractor: '', contractAmount: 0, content: '', signDate: '' })
const formRules = { contractName: [{ required: true, message: '请输入合同名称', trigger: 'blur' }], subcontractor: [{ required: true, message: '请输入分包方', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getSubcontractPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, contractName: '', subcontractor: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, contractName: '', subcontractor: '', contractAmount: 0, content: '', signDate: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateSubcontract(formData.value) : await createSubcontract(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteSubcontract(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.subcontract-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
