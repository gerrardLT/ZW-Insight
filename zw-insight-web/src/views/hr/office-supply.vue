<template>
  <div class="office-supply-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="物品名称">
          <el-input v-model="queryParams.supplyName" placeholder="物品名称" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增办公用品</el-button>
      </div>

      <!-- 2026-10-10：本页原先按「领用申请」建模（申请单号/申请人/申请日期/数量），但对接的是
           /v1/hr/office-supply 办公用品主数据 CRUD，表 biz_office_supply 并无这些列，且表单绑定的
           itemName 非实体字段 → supply_name(NOT NULL) 恒为空，新增必报 ERROR 1364。现按实体
           BizOfficeSupply 的真实字段重建本页；领用/出入库走 /v1/hr/office-supply/in-out，本页不涉及。 -->
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="supplyName" label="物品名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="categoryName" label="分类" width="130" />
        <el-table-column prop="specification" label="规格" width="130" />
        <el-table-column prop="unit" label="单位" width="80" align="center" />
        <el-table-column prop="stockQuantity" label="库存数量" width="100" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'info' : 'success'" size="small">
              {{ row.status === 0 ? '停用' : '启用' }}
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑办公用品' : '新增办公用品'" width="500px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="物品名称" prop="supplyName"><el-input v-model="formData.supplyName" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="formData.categoryName" /></el-form-item>
        <el-form-item label="规格"><el-input v-model="formData.specification" /></el-form-item>
        <el-form-item label="单位"><el-input v-model="formData.unit" /></el-form-item>
        <el-form-item label="库存数量"><el-input-number v-model="formData.stockQuantity" :min="0" style="width: 100%" /></el-form-item>
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

const queryParams = ref({ pageNum: 1, pageSize: 10, supplyName: '' })
const formData = ref({ id: undefined as number | undefined, supplyName: '', categoryName: '', specification: '', unit: '', stockQuantity: 0 })
const formRules = { supplyName: [{ required: true, message: '请输入物品名称', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getOfficeSupplyPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, supplyName: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, supplyName: '', categoryName: '', specification: '', unit: '', stockQuantity: 0 }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }
async function handleFormSubmit() { await formRef.value?.validate(); submitLoading.value = true; try { isEdit.value ? await updateOfficeSupply(formData.value) : await createOfficeSupply(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false } }
async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deleteOfficeSupply(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.office-supply-container { padding: var(--zw-space-md); }
.table-toolbar { margin-bottom: var(--zw-space-md); }
.pagination-wrap { margin-top: var(--zw-space-md); display: flex; justify-content: flex-end; }
</style>
