<template>
  <div class="machine-ledger-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="设备名称">
          <el-input v-model="queryParams.machineName" placeholder="设备名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="设备类型">
          <el-input v-model="queryParams.machineType" placeholder="设备类型" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增设备</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="machineCode" label="设备编号" width="130" />
        <el-table-column prop="machineName" label="设备名称" min-width="150" />
        <el-table-column prop="machineType" label="设备类型" width="120" />
        <el-table-column prop="brand" label="品牌" width="100" />
        <el-table-column prop="specification" label="规格型号" width="130" />
        <el-table-column prop="ownerType" label="权属" width="80" align="center">
          <template #default="{ row }">{{ row.ownerType === 'OWN' ? '自有' : '租赁' }}</template>
        </el-table-column>
        <el-table-column prop="currentProject" label="当前项目" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'IN_FIELD' ? 'success' : row.status === 'OUT_FIELD' ? 'info' : 'warning'" size="small">
              {{ row.status === 'IN_FIELD' ? '在场' : row.status === 'OUT_FIELD' ? '已退场' : '已登记' }}
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑设备' : '新增设备'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="设备名称" prop="machineName"><el-input v-model="formData.machineName" /></el-form-item>
        <el-form-item label="设备类型"><el-input v-model="formData.machineType" /></el-form-item>
        <el-form-item label="品牌"><el-input v-model="formData.brand" /></el-form-item>
        <el-form-item label="规格型号"><el-input v-model="formData.specification" /></el-form-item>
        <el-form-item label="权属"><el-select v-model="formData.ownerType" style="width: 100%"><el-option label="自有" value="OWN" /><el-option label="租赁" value="RENT" /></el-select></el-form-item>
        <el-form-item label="当前项目"><el-input v-model="formData.currentProject" /></el-form-item>
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
import { getMachineLedgerPage, createMachineLedger, updateMachineLedger, deleteMachineLedger } from '@/api/machine'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, machineName: '', machineType: '' })
const formData = ref({ id: undefined as number | undefined, machineName: '', machineType: '', brand: '', specification: '', ownerType: 'OWN', currentProject: '' })
const formRules = { machineName: [{ required: true, message: '请输入设备名称', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getMachineLedgerPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, machineName: '', machineType: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, machineName: '', machineType: '', brand: '', specification: '', ownerType: 'OWN', currentProject: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateMachineLedger(formData.value) : await createMachineLedger(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteMachineLedger(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.machine-ledger-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
