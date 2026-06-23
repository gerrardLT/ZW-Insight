<template>
  <div class="material-outbound-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="材料名称">
          <el-input v-model="queryParams.materialName" placeholder="材料名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="queryParams.outType" placeholder="全部" clearable style="width: 120px">
            <el-option label="领料" value="PICK" />
            <el-option label="退货" value="RETURN" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增出库单</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="outboundNo" label="出库单号" width="150" />
        <el-table-column prop="materialName" label="材料名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" width="120" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="outType" label="出库类型" width="100" align="center">
          <template #default="{ row }">{{ row.outType === 'RETURN' ? '退货' : '领料' }}</template>
        </el-table-column>
        <el-table-column prop="receiver" label="领料人" width="100" />
        <el-table-column prop="outboundDate" label="出库日期" width="110" />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑出库单' : '新增出库单'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="材料名称" prop="materialName"><el-input v-model="formData.materialName" /></el-form-item>
        <el-form-item label="规格型号"><el-input v-model="formData.specification" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="formData.unit" style="width: 120px" /></el-form-item>
        <el-form-item label="数量" prop="quantity"><el-input-number v-model="formData.quantity" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="出库类型">
          <el-select v-model="formData.outType" style="width: 100%">
            <el-option label="领料" value="PICK" />
            <el-option label="退货" value="RETURN" />
          </el-select>
        </el-form-item>
        <el-form-item label="领料人"><el-input v-model="formData.receiver" /></el-form-item>
        <el-form-item label="出库日期"><el-date-picker v-model="formData.outboundDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
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
import { getMaterialOutboundPage, createMaterialOutbound, updateMaterialOutbound, deleteMaterialOutbound } from '@/api/material'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, materialName: '', outType: '' })
const formData = ref({ id: undefined as number | undefined, materialName: '', specification: '', unit: '', quantity: 0, outType: 'PICK', receiver: '', outboundDate: '' })
const formRules = { materialName: [{ required: true, message: '请输入材料名称', trigger: 'blur' }], quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getMaterialOutboundPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, materialName: '', outType: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, materialName: '', specification: '', unit: '', quantity: 0, outType: 'PICK', receiver: '', outboundDate: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateMaterialOutbound(formData.value) : await createMaterialOutbound(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteMaterialOutbound(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.material-outbound-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
