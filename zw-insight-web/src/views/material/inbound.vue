<template>
  <div class="material-inbound-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="材料名称">
          <el-input v-model="queryParams.materialName" placeholder="材料名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="queryParams.supplierName" placeholder="供应商" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="入库日期">
          <el-date-picker v-model="queryParams.inboundDate" type="date" value-format="YYYY-MM-DD" style="width: 150px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增入库单</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="inboundNo" label="入库单号" width="150" />
        <el-table-column prop="materialName" label="材料名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="specification" label="规格型号" width="120" />
        <el-table-column prop="unit" label="单位" width="70" align="center" />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column prop="unitPrice" label="单价(元)" width="100" align="right" />
        <el-table-column prop="totalPrice" label="总价(元)" width="120" align="right">
          <template #default="{ row }">{{ row.totalPrice?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商" width="140" show-overflow-tooltip />
        <el-table-column prop="inboundDate" label="入库日期" width="110" />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑入库单' : '新增入库单'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="材料名称" prop="materialName"><el-input v-model="formData.materialName" /></el-form-item>
        <el-form-item label="规格型号"><el-input v-model="formData.specification" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="formData.unit" style="width: 120px" /></el-form-item>
        <el-form-item label="数量" prop="quantity"><el-input-number v-model="formData.quantity" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="单价" prop="unitPrice"><el-input-number v-model="formData.unitPrice" :min="0" :precision="2" style="width: 100%" /></el-form-item>
        <el-form-item label="供应商"><el-input v-model="formData.supplierName" /></el-form-item>
        <el-form-item label="入库日期"><el-date-picker v-model="formData.inboundDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
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
import { getMaterialInboundPage, createMaterialInbound, updateMaterialInbound, deleteMaterialInbound } from '@/api/material'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, materialName: '', supplierName: '', inboundDate: '' })
const formData = ref({ id: undefined as number | undefined, materialName: '', specification: '', unit: '', quantity: 0, unitPrice: 0, supplierName: '', inboundDate: '', remark: '' })
const formRules = { materialName: [{ required: true, message: '请输入材料名称', trigger: 'blur' }], quantity: [{ required: true, message: '请输入数量', trigger: 'blur' }], unitPrice: [{ required: true, message: '请输入单价', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getMaterialInboundPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, materialName: '', supplierName: '', inboundDate: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, materialName: '', specification: '', unit: '', quantity: 0, unitPrice: 0, supplierName: '', inboundDate: '', remark: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateMaterialInbound(formData.value) : await createMaterialInbound(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteMaterialInbound(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.material-inbound-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
