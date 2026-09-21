<template>
  <div class="financing-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="filter-bar">
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="loadPage">
            <el-option label="在借" value="ACTIVE" />
            <el-option label="已结清" value="SETTLED" />
            <el-option label="逾期" value="OVERDUE" />
          </el-select>
        </div>
        <el-button type="primary" @click="handleAdd">登记融资</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="contractNo" label="合同编号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="lenderName" label="出借方" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">{{ row.financingType === 'BANK_LOAN' ? '银行贷款' : '其他借款' }}</template>
        </el-table-column>
        <el-table-column label="本金" width="140" align="right">
          <template #default="{ row }">{{ formatAmount(row.principal) }}</template>
        </el-table-column>
        <el-table-column label="年利率" width="90" align="center">
          <template #default="{ row }">{{ (row.annualRate * 100).toFixed(2) }}%</template>
        </el-table-column>
        <el-table-column label="期限" width="80" align="center">
          <template #default="{ row }">{{ row.termMonths }}月</template>
        </el-table-column>
        <el-table-column label="还款方式" width="110" align="center">
          <template #default="{ row }">{{ methodLabel(row.repaymentMethod) }}</template>
        </el-table-column>
        <el-table-column label="计划总利息" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.totalInterest) }}</template>
        </el-table-column>
        <el-table-column label="已还本息" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.totalRepaid) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleViewPlan(row as BizFinancing)">还款计划</el-button>
            <el-button
              v-if="row.status !== 'SETTLED'"
              link type="success" @click="handleRepay(row as BizFinancing)">还款</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: var(--zw-space-sm-md); justify-content: flex-end"
        @current-change="loadPage"
      />
    </el-card>

    <el-dialog v-model="addDialogVisible" title="登记融资（自动生成还款计划）" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="110px">
        <el-form-item label="融资类型">
          <el-select v-model="formData.financingType" style="width: 100%">
            <el-option label="银行贷款" value="BANK_LOAN" />
            <el-option label="其他借款" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同编号" prop="contractNo">
          <el-input v-model="formData.contractNo" maxlength="100" clearable />
        </el-form-item>
        <el-form-item label="出借方" prop="lenderName">
          <el-input v-model="formData.lenderName" maxlength="200" clearable placeholder="银行/机构名称" />
        </el-form-item>
        <el-form-item label="借款本金" prop="principal">
          <el-input-number v-model="formData.principal" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="年利率" prop="annualRatePct">
          <el-input-number v-model="formData.annualRatePct" :min="0" :max="99" :precision="4" controls-position="right" style="width: 100%" />
          <span style="margin-left: var(--zw-space-xs)">%</span>
        </el-form-item>
        <el-form-item label="放款日期" prop="startDate">
          <el-date-picker v-model="formData.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="期限（月）" prop="termMonths">
          <el-input-number v-model="formData.termMonths" :min="1" :max="360" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="还款方式">
          <el-select v-model="formData.repaymentMethod" style="width: 100%">
            <el-option label="等额本息" value="EQUAL_INSTALLMENT" />
            <el-option label="等额本金" value="EQUAL_PRINCIPAL" />
            <el-option label="到期还本付息" value="BULLET" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="planDialogVisible" title="还款计划" width="760px" destroy-on-close>
      <el-table :data="repaymentPlan" border max-height="420">
        <el-table-column prop="periodNo" label="期数" width="70" align="center" />
        <el-table-column prop="dueDate" label="应还日期" width="110" align="center" />
        <el-table-column label="应还本金" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.principalDue) }}</template>
        </el-table-column>
        <el-table-column label="应还利息" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.interestDue) }}</template>
        </el-table-column>
        <el-table-column label="已还本金" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.principalPaid) }}</template>
        </el-table-column>
        <el-table-column label="已还利息" width="120" align="right">
          <template #default="{ row }">{{ formatAmount(row.interestPaid) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PAID' ? 'success' : row.status === 'PARTIAL' ? 'warning' : 'info'" size="small">
              {{ repaymentStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="repayDialogVisible" title="登记还款（按期核销，先息后本）" width="460px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="还款期数">
          <el-input-number v-model="repayForm.periodNo" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="实还利息">
          <el-input-number v-model="repayForm.interestPaid" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="实还本金">
          <el-input-number v-model="repayForm.principalPaid" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="实还日期">
          <el-date-picker v-model="repayForm.paidDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="repayDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRepay">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getFinancingPage,
  registerFinancing,
  getRepayments,
  recordRepayment,
  type BizFinancing,
  type BizFinancingRepayment
} from '@/api/financing'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<BizFinancing[]>([])
const total = ref(0)
const query = ref({ page: 1, size: 10, status: '' })
const addDialogVisible = ref(false)
const submitLoading = ref(false)
const planDialogVisible = ref(false)
const repayDialogVisible = ref(false)
const currentRow = ref<BizFinancing | null>(null)
const repaymentPlan = ref<BizFinancingRepayment[]>([])

const formData = ref({
  financingType: 'BANK_LOAN' as BizFinancing['financingType'],
  contractNo: '',
  lenderName: '',
  principal: undefined as number | undefined,
  annualRatePct: 4.5,
  startDate: '',
  termMonths: 12,
  repaymentMethod: 'EQUAL_INSTALLMENT' as BizFinancing['repaymentMethod']
})

const repayForm = ref({
  periodNo: 1,
  interestPaid: undefined as number | undefined,
  principalPaid: undefined as number | undefined,
  paidDate: ''
})

const formRules: FormRules = {
  contractNo: [{ required: true, message: '请输入合同编号', trigger: 'blur' }],
  lenderName: [{ required: true, message: '请输入出借方', trigger: 'blur' }],
  principal: [{ required: true, message: '请输入借款本金', trigger: 'blur' }],
  startDate: [{ required: true, message: '请选择放款日期', trigger: 'change' }],
  termMonths: [{ required: true, message: '请输入期限', trigger: 'blur' }]
}

function formatAmount(value?: number) {
  return value != null ? `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` : '—'
}
function methodLabel(method: string) {
  return { EQUAL_INSTALLMENT: '等额本息', EQUAL_PRINCIPAL: '等额本金', BULLET: '到期还本付息' }[method] || method
}
function statusLabel(status: string) {
  return { ACTIVE: '在借', SETTLED: '已结清', OVERDUE: '逾期' }[status] || status
}
function statusTag(status: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { ACTIVE: 'primary', SETTLED: 'success', OVERDUE: 'danger' }
  return map[status] || 'info'
}
function repaymentStatusLabel(status: string): string {
  const map: Record<string, string> = { PENDING: '待还', PARTIAL: '部分', PAID: '已清' }
  return map[status] || status
}

async function loadPage() {
  loading.value = true
  try {
    const res = await getFinancingPage({
      page: query.value.page,
      size: query.value.size,
      status: query.value.status || undefined
    })
    tableData.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  formData.value = {
    financingType: 'BANK_LOAN',
    contractNo: '',
    lenderName: '',
    principal: undefined,
    annualRatePct: 4.5,
    startDate: '',
    termMonths: 12,
    repaymentMethod: 'EQUAL_INSTALLMENT'
  }
  addDialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    // 年利率百分数 → 小数（后端口径 [0,1)）
    await registerFinancing({
      financingType: formData.value.financingType,
      contractNo: formData.value.contractNo,
      lenderName: formData.value.lenderName,
      principal: formData.value.principal as number,
      annualRate: formData.value.annualRatePct / 100,
      startDate: formData.value.startDate,
      termMonths: formData.value.termMonths,
      repaymentMethod: formData.value.repaymentMethod
    })
    ElMessage.success('融资已登记，还款计划已生成')
    addDialogVisible.value = false
    await loadPage()
  } finally {
    submitLoading.value = false
  }
}

async function handleViewPlan(row: BizFinancing) {
  const res = await getRepayments(row.id as number)
  repaymentPlan.value = res.data.data || []
  planDialogVisible.value = true
}

function handleRepay(row: BizFinancing) {
  currentRow.value = row
  repayForm.value = {
    periodNo: 1,
    interestPaid: undefined,
    principalPaid: undefined,
    paidDate: new Date().toISOString().slice(0, 10)
  }
  repayDialogVisible.value = true
}

async function submitRepay() {
  if (!currentRow.value) return
  if (!repayForm.value.interestPaid && !repayForm.value.principalPaid) {
    ElMessage.warning('请填写实还金额')
    return
  }
  await recordRepayment(
    currentRow.value.id as number,
    repayForm.value.periodNo,
    repayForm.value.interestPaid,
    repayForm.value.principalPaid,
    repayForm.value.paidDate
  )
  ElMessage.success('还款已登记')
  repayDialogVisible.value = false
  await loadPage()
}

onMounted(loadPage)
</script>

<style scoped>
.financing-container {
  padding: var(--zw-space-md);
}
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--zw-space-sm-md)
}
.filter-bar {
  display: flex;
  align-items: center;
}
</style>
