<template>
  <div class="machine-worklog-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="设备名称">
          <el-input v-model="queryParams.machineName" placeholder="设备名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="工作日期">
          <el-date-picker v-model="queryParams.workDate" type="date" value-format="YYYY-MM-DD" style="width: 150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增工作日志</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="machineName" label="设备名称" min-width="140" />
        <el-table-column prop="projectName" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="workDate" label="工作日期" width="110" />
        <el-table-column prop="shiftCount" label="台班数" width="100" align="right" />
        <el-table-column prop="workQuantity" label="工作量" width="100" align="right" />
        <el-table-column prop="oilConsumption" label="油耗(L)" width="100" align="right" />
        <el-table-column prop="settlementStatus" label="结算状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.settlementStatus === 'SETTLED' ? 'success' : 'info'" size="small">{{ row.settlementStatus === 'SETTLED' ? '已结算' : '未结算' }}</el-tag>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑工作日志' : '新增工作日志'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="设备" prop="machineId"><MachineSelector v-model="formData.machineId" /></el-form-item>
        <el-form-item label="项目" prop="projectId"><ProjectSelector v-model="formData.projectId" /></el-form-item>
        <el-form-item label="工作日期" prop="workDate"><el-date-picker v-model="formData.workDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="台班数"><el-input-number v-model="formData.shiftCount" :min="0" :precision="1" style="width: 100%" /></el-form-item>
        <el-form-item label="工作量"><el-input-number v-model="formData.workQuantity" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="油耗(L)"><el-input-number v-model="formData.oilConsumption" :min="0" :precision="1" style="width: 100%" /></el-form-item>
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
import { getMachineWorkLogPage, createMachineWorkLog, updateMachineWorkLog, deleteMachineWorkLog } from '@/api/machine'
import MachineSelector from '@/components/MachineSelector.vue'
import ProjectSelector from '@/components/ProjectSelector.vue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

function emptyForm() {
  return { id: undefined as number | undefined, machineId: undefined as number | undefined, projectId: undefined as number | undefined, workDate: '', shiftCount: 0, workQuantity: 0, oilConsumption: 0 }
}

const queryParams = ref({ pageNum: 1, pageSize: 10, machineName: '', workDate: '' })
const formData = ref(emptyForm())
const formRules = { machineId: [{ required: true, message: '请选择设备', trigger: 'change' }], workDate: [{ required: true, message: '请选择日期', trigger: 'change' }] }

async function loadData() { loading.value = true; try { const res: any = await getMachineWorkLogPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, machineName: '', workDate: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = emptyForm(); dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...emptyForm(), ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateMachineWorkLog(formData.value) : await createMachineWorkLog(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteMachineWorkLog(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.machine-worklog-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
