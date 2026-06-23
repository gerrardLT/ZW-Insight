<template>
  <div class="machine-contract-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="合同名称">
          <el-input v-model="queryParams.contractName" placeholder="合同名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="设备供应商">
          <el-input v-model="queryParams.supplierName" placeholder="设备供应商" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增机械合同</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="contractNo" label="合同编号" width="150" />
        <el-table-column prop="contractName" label="合同名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="设备供应商" width="150" />
        <el-table-column prop="machineName" label="设备名称" width="130" />
        <el-table-column prop="contractAmount" label="合同金额(元)" width="140" align="right">
          <template #default="{ row }">{{ row.contractAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="rentalType" label="租赁方式" width="100" align="center" />
        <el-table-column prop="startDate" label="开始日期" width="110" />
        <el-table-column prop="endDate" label="结束日期" width="110" />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑机械合同' : '新增机械合同'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="合同名称" prop="contractName"><el-input v-model="formData.contractName" /></el-form-item>
        <el-form-item label="设备供应商" prop="supplierName"><el-input v-model="formData.supplierName" /></el-form-item>
        <el-form-item label="设备名称" prop="machineName"><el-input v-model="formData.machineName" /></el-form-item>
        <el-form-item label="合同金额" prop="contractAmount"><el-input-number v-model="formData.contractAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="租赁方式"><el-select v-model="formData.rentalType" style="width: 100%"><el-option label="月租" value="月租" /><el-option label="台班" value="台班" /><el-option label="包月" value="包月" /></el-select></el-form-item>
        <el-form-item label="开始日期"><el-date-picker v-model="formData.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="结束日期"><el-date-picker v-model="formData.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
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
import { getMachineContractPage, createMachineContract, updateMachineContract, deleteMachineContract } from '@/api/machine'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, contractName: '', supplierName: '' })
const formData = ref({ id: undefined as number | undefined, contractName: '', supplierName: '', machineName: '', contractAmount: 0, rentalType: '月租', startDate: '', endDate: '' })
const formRules = { contractName: [{ required: true, message: '请输入合同名称', trigger: 'blur' }], supplierName: [{ required: true, message: '请输入供应商', trigger: 'blur' }], machineName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getMachineContractPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, contractName: '', supplierName: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, contractName: '', supplierName: '', machineName: '', contractAmount: 0, rentalType: '月租', startDate: '', endDate: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateMachineContract(formData.value) : await createMachineContract(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteMachineContract(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.machine-contract-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
