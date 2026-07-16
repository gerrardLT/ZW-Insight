<template>
  <div class="subcontract-settlement-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="分包合同">
          <SubcontractSelector v-model="queryParams.contractId" width="220px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="已审批" value="APPROVED" />
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
        <el-table-column prop="id" label="结算单ID" width="100" />
        <el-table-column prop="settlementAmount" label="本次结算金额(元)" width="160" align="right">
          <template #default="{ row }">{{ row.settlementAmount?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column prop="cumulativeSettlement" label="累计结算金额(元)" width="160" align="right">
          <template #default="{ row }">{{ row.cumulativeSettlement?.toLocaleString() }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : 'info'" size="small">{{ row.status === 'APPROVED' ? '已审批' : '草稿' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmitApply(row)">提交</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑结算单' : '新增结算单'" width="820px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="90px">
        <el-form-item label="项目" prop="projectId">
          <ProjectSelector v-model="formData.projectId" @change="handleProjectChange" />
        </el-form-item>
        <el-form-item label="分包合同" prop="contractId">
          <SubcontractSelector v-model="formData.contractId" :project-id="formData.projectId" />
        </el-form-item>
        <el-form-item label="结算明细" prop="details">
          <div style="width: 100%">
            <el-button type="primary" size="small" style="margin-bottom: 8px" @click="handleAddDetail">添加明细行</el-button>
            <el-table :data="formData.details" border size="small">
              <el-table-column label="工程项名称" min-width="160">
                <template #default="{ row }"><el-input v-model="row.itemName" placeholder="工程项名称" /></template>
              </el-table-column>
              <el-table-column label="单位" width="90">
                <template #default="{ row }"><el-input v-model="row.unit" placeholder="单位" /></template>
              </el-table-column>
              <el-table-column label="数量" width="120">
                <template #default="{ row }"><el-input-number v-model="row.quantity" :min="0" :precision="2" controls-position="right" style="width: 100%" /></template>
              </el-table-column>
              <el-table-column label="单价" width="120">
                <template #default="{ row }"><el-input-number v-model="row.unitPrice" :min="0" :precision="2" controls-position="right" style="width: 100%" /></template>
              </el-table-column>
              <el-table-column label="小计" width="120" align="right">
                <template #default="{ row }">{{ ((row.quantity || 0) * (row.unitPrice || 0)).toFixed(2) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="70" align="center">
                <template #default="{ $index }"><el-button link type="danger" @click="handleRemoveDetail($index)">删除</el-button></template>
              </el-table-column>
            </el-table>
            <div style="text-align: right; margin-top: 8px; font-weight: 600">合计：{{ totalAmount.toFixed(2) }} 元</div>
          </div>
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
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { getSubcontractSettlementPage, getSubcontractSettlementDetail, createSubcontractSettlement, updateSubcontractSettlement, deleteSubcontractSettlement, submitSubcontractSettlement } from '@/api/subcontract'
import ProjectSelector from '@/components/ProjectSelector.vue'
import SubcontractSelector from '@/components/SubcontractSelector.vue'

interface SettlementDetail {
  itemName: string
  unit: string
  quantity: number
  unitPrice: number
  remark: string
}

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const queryParams = ref({ page: 1, size: 10, contractId: undefined as number | undefined, status: '' })
const formData = ref({
  id: undefined as number | undefined,
  projectId: undefined as number | undefined,
  contractId: undefined as number | undefined,
  details: [] as SettlementDetail[]
})
const formRules = {
  projectId: [{ required: true, message: '请选择项目', trigger: 'change' }],
  contractId: [{ required: true, message: '请选择分包合同', trigger: 'change' }],
  details: [{ required: true, message: '请至少添加一条结算明细', trigger: 'change' }]
}

const totalAmount = computed(() => formData.value.details.reduce((sum, d) => sum + (d.quantity || 0) * (d.unitPrice || 0), 0))

async function loadData() {
  loading.value = true
  try {
    const res: any = await getSubcontractSettlementPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}
function handleSearch() { queryParams.value.page = 1; loadData() }
function handleReset() { queryParams.value = { page: 1, size: 10, contractId: undefined, status: '' }; loadData() }

function resetForm() {
  formData.value = { id: undefined, projectId: undefined, contractId: undefined, details: [] }
}
function handleProjectChange() { formData.value.contractId = undefined }
function handleAddDetail() { formData.value.details.push({ itemName: '', unit: '', quantity: 0, unitPrice: 0, remark: '' }) }
function handleRemoveDetail(index: number) { formData.value.details.splice(index, 1) }

function handleAdd() { isEdit.value = false; resetForm(); dialogVisible.value = true }

async function handleEdit(row: any) {
  isEdit.value = true
  resetForm()
  dialogVisible.value = true
  const res: any = await getSubcontractSettlementDetail(row.id)
  const data = res.data || {}
  formData.value = {
    id: data.id,
    projectId: data.projectId,
    contractId: data.contractId,
    details: (data.details || []).map((d: any) => ({
      itemName: d.itemName || '',
      unit: d.unit || '',
      quantity: Number(d.quantity) || 0,
      unitPrice: Number(d.unitPrice) || 0,
      remark: d.remark || ''
    }))
  }
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  if (!formData.value.details.length) { ElMessage.warning('请至少添加一条结算明细'); return }
  submitLoading.value = true
  try {
    const payload = {
      id: formData.value.id,
      projectId: formData.value.projectId,
      contractId: formData.value.contractId,
      details: formData.value.details.map((d, i) => ({ ...d, sortOrder: i + 1 }))
    }
    isEdit.value ? await updateSubcontractSettlement(payload) : await createSubcontractSettlement(payload)
    ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmitApply(row: any) {
  await ElMessageBox.confirm('确定要提交该结算单吗？', '提示', { type: 'warning' })
  await submitSubcontractSettlement(row.id)
  ElMessage.success('提交成功')
  loadData()
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await deleteSubcontractSettlement(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => { loadData() })
</script>

<style scoped>
.subcontract-settlement-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
