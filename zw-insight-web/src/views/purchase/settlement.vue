<template>
  <div class="purchase-settlement-container">
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="关联合同">
          <el-select v-model="queryParams.contractId" placeholder="全部合同" clearable filterable style="width: 220px" @change="handleSearch">
            <el-option v-for="c in contractOptions" :key="c.id" :label="c.contractName || c.contractCode" :value="c.id" />
          </el-select>
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
        <el-table-column prop="settlementNo" label="结算单号" width="160" />
        <el-table-column prop="contractName" label="关联合同" min-width="160" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" width="140" show-overflow-tooltip />
        <el-table-column prop="inboundCode" label="关联入库单" width="150" />
        <el-table-column prop="inboundAmount" label="入库金额(元)" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.inboundAmount) }}</template>
        </el-table-column>
        <el-table-column prop="settlementAmount" label="结算金额(元)" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.settlementAmount) }}</template>
        </el-table-column>
        <el-table-column prop="settlementDate" label="结算日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'APPROVED' ? 'success' : 'warning'" size="small">
              {{ row.status === 'APPROVED' ? '已审批' : '草稿' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'DRAFT'">
              <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
              <el-button link type="success" @click="handleSubmit(row)">提交</el-button>
              <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
            </template>
            <span v-else style="color: #909399">已审批</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.size" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next, jumper" @size-change="loadData" @current-change="loadData" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑结算单' : '新增结算单'" width="600px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="关联合同" prop="contractId">
          <el-select v-model="formData.contractId" placeholder="请选择采购合同" filterable :disabled="isEdit" style="width: 100%" @change="handleContractChange">
            <el-option v-for="c in contractOptions" :key="c.id" :label="c.contractName || c.contractCode" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联入库单" prop="inboundId">
          <el-select v-model="formData.inboundId" placeholder="请选择已审批入库单" :disabled="isEdit || !formData.contractId" style="width: 100%" @change="handleInboundChange">
            <el-option v-for="i in inboundOptions" :key="i.id" :label="`${i.inboundCode}（¥${formatAmount(i.totalAmount)}）`" :value="i.id" />
          </el-select>
          <div v-if="!isEdit && formData.contractId && inboundOptions.length === 0" class="empty-tip">该合同暂无可结算的已审批入库单</div>
        </el-form-item>
        <el-form-item label="入库金额">
          <el-input :value="formatAmount(formData.inboundAmount)" disabled>
            <template #prepend>¥</template>
          </el-input>
        </el-form-item>
        <el-form-item label="本次结算金额" prop="settlementAmount">
          <el-input-number v-model="formData.settlementAmount" :min="0" :max="formData.inboundAmount || undefined" :precision="2" style="width: 100%" />
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
import {
  getPurchaseSettlementPage,
  createPurchaseSettlement,
  updatePurchaseSettlement,
  deletePurchaseSettlement,
  submitPurchaseSettlement,
  getPurchaseContractPage,
  getAvailableInbounds
} from '@/api/purchase'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitLoading = ref(false)
const isEdit = ref(false)

const contractOptions = ref<any[]>([])
const inboundOptions = ref<any[]>([])

const queryParams = ref({ page: 1, size: 10, contractId: undefined as number | undefined, status: '' })
const defaultForm = () => ({ id: undefined as number | undefined, contractId: undefined as number | undefined, inboundId: undefined as number | undefined, inboundAmount: 0, settlementAmount: 0, settlementDate: '', remark: '' })
const formData = ref(defaultForm())
const formRules = {
  contractId: [{ required: true, message: '请选择关联合同', trigger: 'change' }],
  inboundId: [{ required: true, message: '请选择关联入库单', trigger: 'change' }],
  settlementAmount: [{ required: true, message: '请输入结算金额', trigger: 'blur' }]
}

function formatAmount(v: any) {
  const n = Number(v || 0)
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadContracts() {
  const res: any = await getPurchaseContractPage({ page: 1, size: 1000 })
  contractOptions.value = res.data?.records || []
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getPurchaseSettlementPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}
function handleSearch() { queryParams.value.page = 1; loadData() }
function handleReset() { queryParams.value = { page: 1, size: 10, contractId: undefined, status: '' }; loadData() }

function handleAdd() {
  isEdit.value = false
  formData.value = defaultForm()
  inboundOptions.value = []
  dialogVisible.value = true
}
function handleEdit(row: any) {
  isEdit.value = true
  formData.value = { id: row.id, contractId: row.contractId, inboundId: row.inboundId, inboundAmount: row.inboundAmount, settlementAmount: row.settlementAmount, settlementDate: row.settlementDate, remark: row.remark }
  dialogVisible.value = true
}

async function handleContractChange() {
  formData.value.inboundId = undefined
  formData.value.inboundAmount = 0
  inboundOptions.value = []
  if (!formData.value.contractId) return
  const res: any = await getAvailableInbounds(formData.value.contractId)
  inboundOptions.value = res.data || []
}
function handleInboundChange() {
  const inbound = inboundOptions.value.find(i => i.id === formData.value.inboundId)
  formData.value.inboundAmount = inbound ? Number(inbound.totalAmount || 0) : 0
}

async function handleFormSubmit() {
  await formRef.value?.validate()
  if (formData.value.settlementAmount > (formData.value.inboundAmount || 0)) {
    ElMessage.warning('结算金额不能大于入库金额')
    return
  }
  submitLoading.value = true
  try {
    if (isEdit.value) {
      await updatePurchaseSettlement(formData.value)
    } else {
      await createPurchaseSettlement(formData.value)
    }
    ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
    dialogVisible.value = false
    loadData()
  } finally {
    submitLoading.value = false
  }
}

async function handleSubmit(row: any) {
  await ElMessageBox.confirm('确定提交该结算单进入审批流程吗？', '提示', { type: 'warning' })
  await submitPurchaseSettlement(row.id)
  ElMessage.success('已提交审批')
  loadData()
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除吗？', '提示', { type: 'warning' })
  await deletePurchaseSettlement(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(() => { loadContracts(); loadData() })
</script>

<style scoped>
.purchase-settlement-container { padding: 16px; }
.table-toolbar { margin-bottom: 16px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.empty-tip { color: #e6a23c; font-size: 12px; line-height: 1.5; margin-top: 4px; }
</style>
