<template>
  <div class="security-bond-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="filter-bar">
          <el-select v-model="query.bondType" placeholder="保证金类型" clearable style="width: 160px" @change="loadPage">
            <el-option label="投标保证金" value="TENDER" />
            <el-option label="履约保证金" value="PERFORMANCE" />
            <el-option label="质量保证金" value="QUALITY" />
            <el-option label="农民工工资保证金" value="WAGE" />
          </el-select>
          <el-select v-model="query.refundStatus" placeholder="状态" clearable style="width: 140px; margin-left: 8px" @change="loadPage">
            <el-option label="已缴存" value="DEPOSITED" />
            <el-option label="退还申请中" value="REFUND_APPLY" />
            <el-option label="已退还" value="REFUNDED" />
            <el-option label="已动用" value="USED" />
          </el-select>
          <el-button style="margin-left: 8px" @click="loadExpiring">30天内到期</el-button>
        </div>
        <el-button type="primary" @click="handleAdd">新增保证金</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column label="类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.bondType)" size="small">{{ typeLabel(row.bondType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="projectId" label="项目ID" width="150" show-overflow-tooltip />
        <el-table-column label="金额" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="形式" width="110" align="center">
          <template #default="{ row }">{{ formLabel(row.bondForm) }}</template>
        </el-table-column>
        <el-table-column prop="depositDate" label="缴存日期" width="110" align="center" />
        <el-table-column prop="dueDate" label="到期日期" width="110" align="center">
          <template #default="{ row }">
            <span :class="{ 'bond-overdue': isOverdue(row as SecurityBond) }">{{ row.dueDate || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.refundStatus)" size="small">{{ statusLabel(row.refundStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.refundStatus === 'DEPOSITED' || row.refundStatus === 'USED'"
              link type="primary" @click="handleRefundApply(row as SecurityBond)">退还申请</el-button>
            <el-button
              v-if="row.refundStatus === 'REFUND_APPLY'"
              link type="success" @click="handleRefundConfirm(row as SecurityBond)">确认退还</el-button>
            <el-button
              v-if="row.bondType === 'WAGE' && row.refundStatus === 'DEPOSITED'"
              link type="warning" @click="handleMarkUsed(row as SecurityBond)">标记动用</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 12px; justify-content: flex-end"
        @current-change="loadPage"
      />
    </el-card>

    <el-dialog v-model="dialogVisible" title="新增保证金" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="项目ID" prop="projectId">
          <el-input-number v-model="formData.projectId" :min="1" controls-position="right" style="width: 100%" placeholder="选择所属项目" />
        </el-form-item>
        <el-form-item label="保证金类型" prop="bondType">
          <el-select v-model="formData.bondType" style="width: 100%">
            <el-option label="投标保证金（≤估算价2%）" value="TENDER" />
            <el-option label="履约保证金（≤合同额10%）" value="PERFORMANCE" />
            <el-option label="质量保证金（≤结算总额3%）" value="QUALITY" />
            <el-option label="农民工工资保证金（1%-3%）" value="WAGE" />
          </el-select>
        </el-form-item>
        <el-form-item label="保证金金额" prop="amount">
          <el-input-number v-model="formData.amount" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="合同/估算金额">
          <el-input-number v-model="formData.contractAmount" :min="0" :precision="2" controls-position="right" style="width: 100%" placeholder="比例校验基数（选填）" />
        </el-form-item>
        <el-form-item label="缴存日期" prop="depositDate">
          <el-date-picker v-model="formData.depositDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="到期日期">
          <el-date-picker v-model="formData.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="形式">
          <el-select v-model="formData.bondForm" style="width: 100%">
            <el-option label="现金" value="CASH" />
            <el-option label="银行保函" value="BANK_GUARANTEE" />
            <el-option label="保证保险" value="INSURANCE" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="formData.bondForm !== 'CASH'" label="担保文件" prop="guaranteeFile">
          <el-input v-model="formData.guaranteeFile" placeholder="保函/保险文件路径" clearable />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="formData.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="refundDialogVisible" title="确认退还" width="440px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="缴存金额">
          <span>{{ formatAmount(currentRow?.amount) }}</span>
        </el-form-item>
        <el-form-item label="退还金额">
          <el-input-number v-model="refundAmount" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="退还日期">
          <el-date-picker v-model="refundDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRefundConfirm">确认退还</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getSecurityBondPage,
  saveSecurityBond,
  refundApplyBond,
  refundConfirmBond,
  markBondUsed,
  getExpiringBonds,
  type SecurityBond
} from '@/api/security-bond'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<SecurityBond[]>([])
const total = ref(0)
const query = ref({ page: 1, size: 10, bondType: '', refundStatus: '' })
const dialogVisible = ref(false)
const submitLoading = ref(false)
const refundDialogVisible = ref(false)
const currentRow = ref<SecurityBond | null>(null)
const refundAmount = ref(0)
const refundDate = ref('')

const formData = ref({
  projectId: undefined as number | undefined,
  bondType: 'TENDER' as SecurityBond['bondType'],
  amount: undefined as number | undefined,
  contractAmount: undefined as number | undefined,
  depositDate: '',
  dueDate: '',
  bondForm: 'CASH' as SecurityBond['bondForm'],
  guaranteeFile: '',
  remark: ''
})

const formRules: FormRules = {
  projectId: [{ required: true, message: '请输入项目ID', trigger: 'blur' }],
  bondType: [{ required: true, message: '请选择保证金类型', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }],
  depositDate: [{ required: true, message: '请选择缴存日期', trigger: 'change' }],
  guaranteeFile: [{ required: true, message: '保函/保险形式必须上传担保文件', trigger: 'blur' }]
}

function typeLabel(type: string) {
  return { TENDER: '投标', PERFORMANCE: '履约', QUALITY: '质量', WAGE: '工资' }[type] || type
}
function typeTag(type: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { TENDER: 'primary', PERFORMANCE: 'warning', QUALITY: 'success', WAGE: 'danger' }
  return map[type] || 'info'
}
function statusLabel(status: string) {
  return { DEPOSITED: '已缴存', REFUND_APPLY: '退还申请中', REFUNDED: '已退还', USED: '已动用' }[status] || status
}
function statusTag(status: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { DEPOSITED: 'primary', REFUND_APPLY: 'warning', REFUNDED: 'success', USED: 'danger' }
  return map[status] || 'info'
}
function formLabel(form: string) {
  return { CASH: '现金', BANK_GUARANTEE: '银行保函', INSURANCE: '保证保险' }[form] || form
}
function formatAmount(value?: number) {
  return value != null ? `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` : '—'
}
function isOverdue(row: SecurityBond) {
  return !!row.dueDate && row.refundStatus !== 'REFUNDED' && new Date(row.dueDate) < new Date()
}

async function loadPage() {
  loading.value = true
  try {
    const res = await getSecurityBondPage({
      page: query.value.page,
      size: query.value.size,
      bondType: query.value.bondType || undefined,
      refundStatus: query.value.refundStatus || undefined
    })
    tableData.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadExpiring() {
  loading.value = true
  try {
    const res = await getExpiringBonds(30)
    tableData.value = res.data.data || []
    total.value = tableData.value.length
    ElMessage.info(`共 ${tableData.value.length} 笔保证金 30 天内到期`)
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  formData.value = {
    projectId: undefined,
    bondType: 'TENDER',
    amount: undefined,
    contractAmount: undefined,
    depositDate: '',
    dueDate: '',
    bondForm: 'CASH',
    guaranteeFile: '',
    remark: ''
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await saveSecurityBond(formData.value as SecurityBond)
    ElMessage.success('保证金已登记')
    dialogVisible.value = false
    await loadPage()
  } finally {
    submitLoading.value = false
  }
}

async function handleRefundApply(row: SecurityBond) {
  await ElMessageBox.confirm(`确认对「${typeLabel(row.bondType)}保证金」发起退还申请？`, '退还申请', { type: 'warning' })
  await refundApplyBond(row.id as number)
  ElMessage.success('退还申请已发起')
  await loadPage()
}

function handleRefundConfirm(row: SecurityBond) {
  currentRow.value = row
  refundAmount.value = row.amount
  refundDate.value = new Date().toISOString().slice(0, 10)
  refundDialogVisible.value = true
}

async function submitRefundConfirm() {
  if (!currentRow.value) return
  await refundConfirmBond(currentRow.value.id as number, refundAmount.value, refundDate.value)
  ElMessage.success('退还已确认')
  refundDialogVisible.value = false
  await loadPage()
}

async function handleMarkUsed(row: SecurityBond) {
  await ElMessageBox.confirm(
    '确认标记动用？按人社部〔2021〕65号规定，动用后须自使用之日起10个工作日内补足',
    '动用确认',
    { type: 'warning' }
  )
  await markBondUsed(row.id as number)
  ElMessage.success('已标记动用')
  await loadPage()
}

onMounted(loadPage)
</script>

<style scoped>
.security-bond-container {
  padding: 16px;
}
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.filter-bar {
  display: flex;
  align-items: center;
}
.bond-overdue {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
