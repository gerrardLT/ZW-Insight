<template>
  <div class="machine-repair-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="设备名称">
          <el-input v-model="queryParams.machineName" placeholder="设备名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增维修记录</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="machineName" label="设备名称" min-width="140" />
        <el-table-column prop="projectName" label="项目名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="faultDescription" label="故障描述" min-width="180" show-overflow-tooltip />
        <el-table-column prop="reportDate" label="报修日期" width="110" />
        <el-table-column prop="repairPerson" label="维修人" width="100" />
        <el-table-column prop="repairDate" label="维修日期" width="110" />
        <el-table-column prop="repairCost" label="维修费用(元)" width="130" align="right">
          <template #default="{ row }">{{ row.repairCost?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="repairStatus" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.repairStatus)" size="small">{{ statusText(row.repairStatus) }}</el-tag>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑维修记录' : '新增维修记录'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="设备" prop="machineId"><MachineSelector v-model="formData.machineId" /></el-form-item>
        <el-form-item label="项目" prop="projectId"><ProjectSelector v-model="formData.projectId" /></el-form-item>
        <el-form-item label="故障描述" prop="faultDescription"><el-input v-model="formData.faultDescription" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="维修人"><el-input v-model="formData.repairPerson" /></el-form-item>
        <el-form-item label="维修日期"><el-date-picker v-model="formData.repairDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="维修费用"><el-input-number v-model="formData.repairCost" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item v-if="isEdit" label="状态"><el-select v-model="formData.repairStatus" style="width: 100%"><el-option label="已报修" value="REPORTED" /><el-option label="已派工" value="DISPATCHED" /><el-option label="维修中" value="REPAIRING" /><el-option label="已完成" value="COMPLETED" /></el-select></el-form-item>
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
import { getMachineRepairPage, createMachineRepair, updateMachineRepair, deleteMachineRepair } from '@/api/machine'
import MachineSelector from '@/components/MachineSelector.vue'
import ProjectSelector from '@/components/ProjectSelector.vue'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const STATUS_MAP: Record<string, { text: string; type: 'success' | 'warning' | 'info' | 'primary' }> = {
  REPORTED: { text: '已报修', type: 'info' },
  DISPATCHED: { text: '已派工', type: 'warning' },
  REPAIRING: { text: '维修中', type: 'primary' },
  COMPLETED: { text: '已完成', type: 'success' }
}
function statusText(s: string) { return STATUS_MAP[s]?.text ?? s ?? '-' }
function statusTagType(s: string) { return STATUS_MAP[s]?.type ?? 'info' }

function emptyForm() {
  return { id: undefined as number | undefined, machineId: undefined as number | undefined, projectId: undefined as number | undefined, faultDescription: '', repairPerson: '', repairDate: '', repairCost: 0, repairStatus: 'REPORTED' }
}

const queryParams = ref({ pageNum: 1, pageSize: 10, machineName: '' })
const formData = ref(emptyForm())
const formRules = { machineId: [{ required: true, message: '请选择设备', trigger: 'change' }], faultDescription: [{ required: true, message: '请输入故障描述', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getMachineRepairPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, machineName: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = emptyForm(); dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...emptyForm(), ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateMachineRepair(formData.value) : await createMachineRepair(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteMachineRepair(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.machine-repair-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
