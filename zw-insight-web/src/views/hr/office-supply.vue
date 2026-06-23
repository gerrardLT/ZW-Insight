<template>
  <div class="office-supply-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="物品名称">
          <el-input v-model="queryParams.itemName" placeholder="物品名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增领用申请</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="applyNo" label="申请单号" width="150" />
        <el-table-column prop="itemName" label="物品名称" min-width="150" />
        <el-table-column prop="specification" label="规格" width="120" />
        <el-table-column prop="quantity" label="数量" width="80" align="center" />
        <el-table-column prop="applicant" label="申请人" width="90" />
        <el-table-column prop="applyDate" label="申请日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : row.status === 'PENDING' ? 'warning' : 'info'" size="small">
              {{ row.status === 'APPROVED' ? '已领用' : row.status === 'PENDING' ? '审批中' : '草稿' }}
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑' : '新增领用申请'" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="物品名称" prop="itemName"><el-input v-model="formData.itemName" /></el-form-item>
        <el-form-item label="规格"><el-input v-model="formData.specification" /></el-form-item>
        <el-form-item label="数量" prop="quantity"><el-input-number v-model="formData.quantity" :min="1" style="width: 100%" /></el-form-item>
        <el-form-item label="用途"><el-input v-model="formData.purpose" type="textarea" :rows="2" /></el-form-item>
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
import { getOfficeSupplyPage, createOfficeSupply, updateOfficeSupply, deleteOfficeSupply } from '@/api/hr'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, itemName: '' })
const formData = ref({ id: undefined as number | undefined, itemName: '', specification: '', quantity: 1, purpose: '' })
const formRules = { itemName: [{ required: true, message: '请输入物品名称', trigger: 'blur' }], quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getOfficeSupplyPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, itemName: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, itemName: '', specification: '', quantity: 1, purpose: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateOfficeSupply(formData.value) : await createOfficeSupply(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteOfficeSupply(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.office-supply-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
