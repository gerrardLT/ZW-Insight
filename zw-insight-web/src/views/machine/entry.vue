<template>
  <div class="machine-entry-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="设备名称">
          <el-input v-model="queryParams.machineName" placeholder="设备名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.entryType" placeholder="全部" clearable style="width: 120px">
            <el-option label="进场" value="IN" />
            <el-option label="出场" value="OUT" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增登记</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="machineName" label="设备名称" min-width="150" />
        <el-table-column prop="machineCode" label="设备编号" width="130" />
        <el-table-column prop="projectName" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="entryType" label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.entryType === 'IN' ? 'success' : 'danger'" size="small">{{ row.entryType === 'IN' ? '进场' : '出场' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="entryDate" label="日期" width="110" />
        <el-table-column prop="operator" label="经办人" width="100" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑登记' : '新增进出场登记'" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="设备名称" prop="machineName"><el-input v-model="formData.machineName" /></el-form-item>
        <el-form-item label="项目名称"><el-input v-model="formData.projectName" /></el-form-item>
        <el-form-item label="类型" prop="entryType"><el-select v-model="formData.entryType" style="width: 100%"><el-option label="进场" value="IN" /><el-option label="出场" value="OUT" /></el-select></el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="formData.entryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="经办人"><el-input v-model="formData.operator" /></el-form-item>
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
import { getMachineEntryPage, createMachineEntry, updateMachineEntry, deleteMachineEntry } from '@/api/machine'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, machineName: '', entryType: '' })
const formData = ref({ id: undefined as number | undefined, machineName: '', projectName: '', entryType: 'IN', entryDate: '', operator: '', remark: '' })
const formRules = { machineName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }], entryType: [{ required: true, message: '请选择类型', trigger: 'change' }] }

async function loadData() { loading.value = true; try { const res: any = await getMachineEntryPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, machineName: '', entryType: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, machineName: '', projectName: '', entryType: 'IN', entryDate: '', operator: '', remark: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateMachineEntry(formData.value) : await createMachineEntry(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteMachineEntry(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.machine-entry-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
