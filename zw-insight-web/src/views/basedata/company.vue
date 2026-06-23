<template>
  <div class="company-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="公司名称">
          <el-input v-model="queryParams.companyName" placeholder="公司名称" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增公司</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="companyName" label="公司名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="shortName" label="简称" width="120" />
        <el-table-column prop="legalPerson" label="法人" width="100" />
        <el-table-column prop="creditCode" label="统一社会信用代码" width="200" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column prop="address" label="注册地址" min-width="180" show-overflow-tooltip />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑公司' : '新增公司'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="130px">
        <el-form-item label="公司名称" prop="companyName"><el-input v-model="formData.companyName" /></el-form-item>
        <el-form-item label="简称"><el-input v-model="formData.shortName" /></el-form-item>
        <el-form-item label="法人"><el-input v-model="formData.legalPerson" /></el-form-item>
        <el-form-item label="统一社会信用代码"><el-input v-model="formData.creditCode" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="formData.contactPhone" /></el-form-item>
        <el-form-item label="注册地址"><el-input v-model="formData.address" /></el-form-item>
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
import { getCompanyPage, createCompany, updateCompany, deleteCompany } from '@/api/basedata'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, companyName: '' })
const formData = ref({ id: undefined as number | undefined, companyName: '', shortName: '', legalPerson: '', creditCode: '', contactPhone: '', address: '' })
const formRules = { companyName: [{ required: true, message: '请输入公司名称', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getCompanyPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, companyName: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, companyName: '', shortName: '', legalPerson: '', creditCode: '', contactPhone: '', address: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateCompany(formData.value) : await createCompany(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteCompany(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.company-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
