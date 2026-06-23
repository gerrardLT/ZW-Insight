<template>
  <div class="payroll-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="班组">
          <el-input v-model="queryParams.teamName" placeholder="班组名称" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="月份">
          <el-date-picker v-model="queryParams.month" type="month" value-format="YYYY-MM" placeholder="选择月份" style="width: 140px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已发放" value="PAID" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增工资单</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="payrollNo" label="工资单号" width="150" />
        <el-table-column prop="teamName" label="班组" width="130" />
        <el-table-column prop="month" label="月份" width="100" align="center" />
        <el-table-column prop="workerCount" label="人数" width="80" align="center" />
        <el-table-column prop="totalAmount" label="应发总额(元)" width="140" align="right">
          <template #default="{ row }">{{ row.totalAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="deductAmount" label="扣款(元)" width="110" align="right" />
        <el-table-column prop="actualAmount" label="实发(元)" width="130" align="right">
          <template #default="{ row }">{{ row.actualAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PAID' ? 'success' : 'info'" size="small">{{ row.status === 'PAID' ? '已发放' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmitPayroll(row)">发放</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑工资单' : '新增工资单'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="班组" prop="teamName"><el-input v-model="formData.teamName" /></el-form-item>
        <el-form-item label="月份" prop="month"><el-date-picker v-model="formData.month" type="month" value-format="YYYY-MM" style="width: 100%" /></el-form-item>
        <el-form-item label="应发总额" prop="totalAmount"><el-input-number v-model="formData.totalAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="扣款"><el-input-number v-model="formData.deductAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item>
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
import { getPayrollPage, createPayroll, updatePayroll, deletePayroll, submitPayroll } from '@/api/labor'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, teamName: '', month: '', status: '' })
const formData = ref({ id: undefined as number | undefined, teamName: '', month: '', totalAmount: 0, deductAmount: 0, remark: '' })
const formRules = { teamName: [{ required: true, message: '请输入班组', trigger: 'blur' }], month: [{ required: true, message: '请选择月份', trigger: 'change' }], totalAmount: [{ required: true, message: '请输入应发总额', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getPayrollPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, teamName: '', month: '', status: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, teamName: '', month: '', totalAmount: 0, deductAmount: 0, remark: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updatePayroll(formData.value) : await createPayroll(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleSubmitPayroll(row: any) { await ElMessageBox.confirm('确定要发放此工资单吗？', '提示', { type: 'warning' }); await submitPayroll(row.id); ElMessage.success('发放成功'); loadData() }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deletePayroll(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.payroll-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
