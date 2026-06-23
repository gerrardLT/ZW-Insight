<template>
  <div class="purchase-settlement-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="合同名称">
          <el-input v-model="queryParams.contractName" placeholder="关联合同" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="待结算" value="PENDING" />
            <el-option label="已结算" value="SETTLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">新增结算单</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="settlementNo" label="结算单号" width="150" />
        <el-table-column prop="contractName" label="关联合同" min-width="180" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" width="150" />
        <el-table-column prop="settlementAmount" label="结算金额(元)" width="140" align="right">
          <template #default="{ row }">{{ row.settlementAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="settlementDate" label="结算日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SETTLED' ? 'success' : 'warning'" size="small">
              {{ row.status === 'SETTLED' ? '已结算' : '待结算' }}
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑结算单' : '新增结算单'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="关联合同" prop="contractName">
          <el-input v-model="formData.contractName" />
        </el-form-item>
        <el-form-item label="供应商" prop="supplierName">
          <el-input v-model="formData.supplierName" />
        </el-form-item>
        <el-form-item label="结算金额" prop="settlementAmount">
          <el-input-number v-model="formData.settlementAmount" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结算日期">
          <el-date-picker v-model="formData.settlementDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" />
        </el-form-item>
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
import { getPurchaseSettlementPage, createPurchaseSettlement, updatePurchaseSettlement, deletePurchaseSettlement } from '@/api/purchase'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ pageNum: 1, pageSize: 10, contractName: '', status: '' })
const formData = ref({ id: undefined as number | undefined, contractName: '', supplierName: '', settlementAmount: 0, settlementDate: '', remark: '' })
const formRules = { contractName: [{ required: true, message: '请选择关联合同', trigger: 'blur' }], settlementAmount: [{ required: true, message: '请输入结算金额', trigger: 'blur' }] }

async function loadData() { loading.value = true; try { const res: any = await getPurchaseSettlementPage(queryParams.value); tableData.value = res.data?.records || []; total.value = res.data?.total || 0 } finally { loading.value = false } }
function handleSearch() { queryParams.value.pageNum = 1; loadData() }
function handleReset() { queryParams.value = { pageNum: 1, pageSize: 10, contractName: '', status: '' }; loadData() }
function handleAdd() { isEdit.value = false; formData.value = { id: undefined, contractName: '', supplierName: '', settlementAmount: 0, settlementDate: '', remark: '' }; dialogVisible.value = true }
function handleEdit(row: any) { isEdit.value = true; formData.value = { ...row }; dialogVisible.value = true }

async function handleFormSubmit() {
  await formRef.value?.validate(); submitLoading.value = true
  try { isEdit.value ? await updatePurchaseSettlement(formData.value) : await createPurchaseSettlement(formData.value); ElMessage.success(isEdit.value ? '更新成功' : '新增成功'); dialogVisible.value = false; loadData() } finally { submitLoading.value = false }
}

async function handleDelete(row: any) { await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' }); await deletePurchaseSettlement(row.id); ElMessage.success('删除成功'); loadData() }
onMounted(() => { loadData() })
</script>

<style scoped>
.purchase-settlement-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
