<template>
  <div class="basedata-material-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="材料名称">
          <el-input v-model="queryParams.materialName" placeholder="材料名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="分类">
          <el-input v-model="queryParams.categoryName" placeholder="分类" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增材料</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="materialCode" label="材料编码" width="130" />
        <el-table-column prop="materialName" label="材料名称" min-width="160" />
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column prop="specification" label="规格型号" width="130" />
        <el-table-column prop="unit" label="计量单位" width="90" align="center" />
        <el-table-column prop="referencePrice" label="参考单价(元)" width="120" align="right" />
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑材料' : '新增材料'" width="550px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="材料名称" prop="materialName"><el-input v-model="formData.materialName" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="formData.categoryName" /></el-form-item>
        <el-form-item label="规格型号"><el-input v-model="formData.specification" /></el-form-item>
        <el-form-item label="计量单位"><el-input v-model="formData.unit" style="width: 120px" /></el-form-item>
        <el-form-item label="参考单价"><el-input-number v-model="formData.referencePrice" :min="0" :precision="2" style="width: 100%" /></el-form-item>
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
import { getMaterialDictPage, createMaterialDict, updateMaterialDict, deleteMaterialDict } from '@/api/basedata'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, materialName: '', categoryName: '' })
const formData = ref({ id: undefined as number | undefined, materialName: '', categoryName: '', specification: '', unit: '', referencePrice: 0 })
const formRules = { materialName: [{ required: true, message: '请输入材料名称', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getMaterialDictPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, materialName: '', categoryName: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, materialName: '', categoryName: '', specification: '', unit: '', referencePrice: 0 }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateMaterialDict(formData.value) : await createMaterialDict(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteMaterialDict(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.basedata-material-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
