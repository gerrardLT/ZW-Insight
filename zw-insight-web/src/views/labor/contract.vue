<template>
  <div class="labor-contract-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="合同名称">
          <el-input v-model="queryParams.contractName" placeholder="合同名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="施工队伍">
          <el-input v-model="queryParams.teamName" placeholder="施工队伍" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="生效" value="EFFECTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增劳务合同</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="contractCode" label="合同编号" width="150" />
        <el-table-column prop="contractName" label="合同名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="teamName" label="施工队伍" width="150" />
        <el-table-column prop="contractAmount" label="合同金额(元)" width="140" align="right">
          <template #default="{ row }">{{ row.contractAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="startDate" label="开始日期" width="110" />
        <el-table-column prop="endDate" label="结束日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'EFFECTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'EFFECTIVE' ? '生效' : '草稿' }}
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑劳务合同' : '新增劳务合同'" width="650px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="合同名称" prop="contractName"><el-input v-model="formData.contractName" /></el-form-item>
        <el-form-item label="施工队伍" prop="teamName"><el-input v-model="formData.teamName" /></el-form-item>
        <el-form-item label="合同金额" prop="contractAmount"><el-input-number v-model="formData.contractAmount" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="开始日期"><el-date-picker v-model="formData.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="结束日期"><el-date-picker v-model="formData.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
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
import { getLaborContractPage, createLaborContract, updateLaborContract, deleteLaborContract } from '@/api/labor'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, contractName: '', teamName: '', status: '' })
const formData = ref({ id: undefined as number | undefined, contractName: '', teamName: '', contractAmount: 0, startDate: '', endDate: '', remark: '' })
const formRules = { contractName: [{ required: true, message: '请输入合同名称', trigger: 'blur' }], teamName: [{ required: true, message: '请输入施工队伍', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getLaborContractPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, contractName: '', teamName: '', status: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, contractName: '', teamName: '', contractAmount: 0, startDate: '', endDate: '', remark: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateLaborContract(formData.value) : await createLaborContract(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteLaborContract(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.labor-contract-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
