<template>
  <div class="certificate-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="证件名称">
          <el-input v-model="queryParams.certName" placeholder="证件名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="持证人">
          <el-input v-model="queryParams.holderName" placeholder="持证人" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="有效" value="VALID" />
            <el-option label="即将过期" value="EXPIRING" />
            <el-option label="已过期" value="EXPIRED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增证件</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="certName" label="证件名称" min-width="160" />
        <el-table-column prop="certNo" label="证件编号" width="160" />
        <el-table-column prop="holderName" label="持证人" width="100" />
        <el-table-column prop="issueDate" label="发证日期" width="110" />
        <el-table-column prop="expiryDate" label="到期日期" width="110" />
        <el-table-column prop="issueOrgan" label="发证机关" width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'VALID' ? 'success' : row.status === 'EXPIRING' ? 'warning' : 'danger'" size="small">
              {{ row.status === 'VALID' ? '有效' : row.status === 'EXPIRING' ? '即将过期' : '已过期' }}
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑证件' : '新增证件'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="证件名称" prop="certName"><el-input v-model="formData.certName" /></el-form-item>
        <el-form-item label="证件编号" prop="certNo"><el-input v-model="formData.certNo" /></el-form-item>
        <el-form-item label="持证人" prop="holderName"><el-input v-model="formData.holderName" /></el-form-item>
        <el-form-item label="发证日期"><el-date-picker v-model="formData.issueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="到期日期"><el-date-picker v-model="formData.expiryDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="发证机关"><el-input v-model="formData.issueOrgan" /></el-form-item>
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
import { getCertificatePage, createCertificate, updateCertificate, deleteCertificate } from '@/api/tender'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, certName: '', holderName: '', status: '' })
const formData = ref({ id: undefined as number | undefined, certName: '', certNo: '', holderName: '', issueDate: '', expiryDate: '', issueOrgan: '' })
const formRules = { certName: [{ required: true, message: '请输入证件名称', trigger: 'blur' }], certNo: [{ required: true, message: '请输入证件编号', trigger: 'blur' }], holderName: [{ required: true, message: '请输入持证人', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getCertificatePage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, certName: '', holderName: '', status: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, certName: '', certNo: '', holderName: '', issueDate: '', expiryDate: '', issueOrgan: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateCertificate(formData.value) : await createCertificate(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteCertificate(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.certificate-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
