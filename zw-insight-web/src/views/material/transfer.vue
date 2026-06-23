<template>
  <div class="material-transfer-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="调出项目">
          <el-input v-model="queryParams.fromProject" placeholder="调出项目" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="调入项目">
          <el-input v-model="queryParams.toProject" placeholder="调入项目" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增调拨单</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="transferNo" label="调拨单号" width="150" />
        <el-table-column prop="materialName" label="材料名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="quantity" label="调拨数量" width="100" align="right" />
        <el-table-column prop="fromProject" label="调出项目" width="160" show-overflow-tooltip />
        <el-table-column prop="toProject" label="调入项目" width="160" show-overflow-tooltip />
        <el-table-column prop="transferDate" label="调拨日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'warning'" size="small">{{ row.status === 'COMPLETED' ? '已完成' : '待确认' }}</el-tag>
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑调拨单' : '新增调拨单'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="材料名称" prop="materialName"><el-input v-model="formData.materialName" /></el-form-item>
        <el-form-item label="调拨数量" prop="quantity"><el-input-number v-model="formData.quantity" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="调出项目" prop="fromProject"><el-input v-model="formData.fromProject" /></el-form-item>
        <el-form-item label="调入项目" prop="toProject"><el-input v-model="formData.toProject" /></el-form-item>
        <el-form-item label="调拨日期"><el-date-picker v-model="formData.transferDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
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
import { getMaterialTransferPage, createMaterialTransfer, updateMaterialTransfer, deleteMaterialTransfer } from '@/api/material'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, fromProject: '', toProject: '' })
const formData = ref({ id: undefined as number | undefined, materialName: '', quantity: 0, fromProject: '', toProject: '', transferDate: '', remark: '' })
const formRules = { materialName: [{ required: true, message: '请输入材料名称', trigger: 'blur' }], quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }], fromProject: [{ required: true, message: '请输入调出项目', trigger: 'blur' }], toProject: [{ required: true, message: '请输入调入项目', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getMaterialTransferPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, fromProject: '', toProject: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, materialName: '', quantity: 0, fromProject: '', toProject: '', transferDate: '', remark: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateMaterialTransfer(formData.value) : await createMaterialTransfer(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteMaterialTransfer(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.material-transfer-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
