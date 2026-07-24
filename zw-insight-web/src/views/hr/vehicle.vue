<template>
  <div class="vehicle-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="车牌号">
          <el-input v-model="queryParams.plateNumber" placeholder="车牌号" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="车辆类型">
          <el-input v-model="queryParams.vehicleType" placeholder="类型" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增车辆</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="plateNumber" label="车牌号" width="120" />
        <el-table-column prop="vehicleType" label="车辆类型" width="120" />
        <el-table-column prop="brand" label="品牌型号" width="130" />
        <el-table-column prop="driver" label="驾驶人" width="90" />
        <el-table-column prop="department" label="使用部门" width="120" />
        <el-table-column prop="insuranceExpiry" label="保险到期" width="110" />
        <el-table-column prop="inspectionExpiry" label="年检到期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.vehicleStatus === 'IN_USE' ? 'warning' : 'success'" size="small">{{ row.vehicleStatus === 'IN_USE' ? '使用中' : '闲置' }}</el-tag>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑车辆' : '新增车辆'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="车牌号" prop="plateNumber"><el-input v-model="formData.plateNumber" /></el-form-item>
        <el-form-item label="车辆类型"><el-input v-model="formData.vehicleType" /></el-form-item>
        <el-form-item label="品牌型号"><el-input v-model="formData.brand" /></el-form-item>
        <el-form-item label="驾驶人"><el-input v-model="formData.driver" /></el-form-item>
        <el-form-item label="使用部门"><el-input v-model="formData.department" /></el-form-item>
        <el-form-item label="保险到期"><el-date-picker v-model="formData.insuranceExpiry" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="年检到期"><el-date-picker v-model="formData.inspectionExpiry" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
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
import { getVehiclePage, createVehicle, updateVehicle, deleteVehicle } from '@/api/hr'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, plateNumber: '', vehicleType: '' })
const formData = ref({ id: undefined as number | undefined, plateNumber: '', vehicleType: '', brand: '', driver: '', department: '', insuranceExpiry: '', inspectionExpiry: '' })
const formRules = { plateNumber: [{ required: true, message: '请输入车牌号', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getVehiclePage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, plateNumber: '', vehicleType: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, plateNumber: '', vehicleType: '', brand: '', driver: '', department: '', insuranceExpiry: '', inspectionExpiry: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateVehicle(formData.value) : await createVehicle(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteVehicle(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.vehicle-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
