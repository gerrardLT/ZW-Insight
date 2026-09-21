<template>
  <div class="wage-account-container">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
      title="农民工工资专用账户（国务院令第724号）：人工费拨付周期不得超过1个月；专户资金专项用于支付农民工工资，不得挪用"
    />

    <el-card shadow="never">
      <div class="table-toolbar">
        <el-button @click="handleScan">合规巡检</el-button>
        <el-button type="primary" @click="handleAdd">开设专户</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="accountNo" label="专户账号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="bankName" label="开户银行" min-width="140" show-overflow-tooltip />
        <el-table-column prop="projectId" label="项目ID" width="140" show-overflow-tooltip />
        <el-table-column label="人工费预算" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.wageBudget) }}</template>
        </el-table-column>
        <el-table-column label="累计到账" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.totalReceived) }}</template>
        </el-table-column>
        <el-table-column label="累计代发" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.totalPaid) }}</template>
        </el-table-column>
        <el-table-column label="当前余额" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.currentBalance) }}</template>
        </el-table-column>
        <el-table-column label="到位率" width="90" align="center">
          <template #default="{ row }">
            <span :class="arrivalClass(row as WageSpecialAccount)">{{ arrivalText(row as WageSpecialAccount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="合规状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="complianceTag(row.complianceFlag)" size="small">{{ complianceLabel(row.complianceFlag) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近到账" width="110" align="center">
          <template #default="{ row }">{{ row.lastDepositDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ACTIVE'" link type="primary" @click="handleDeposit(row as WageSpecialAccount)">登记拨付</el-button>
            <el-button v-if="row.status === 'ACTIVE'" link type="success" @click="handleWagePayment(row as WageSpecialAccount)">代发工资</el-button>
            <el-button link type="info" @click="handleViewDeposits(row as WageSpecialAccount)">拨付记录</el-button>
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

    <el-dialog v-model="addDialogVisible" title="开设工资专户" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="项目ID" prop="projectId">
          <el-input-number v-model="formData.projectId" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="专户账号" prop="accountNo">
          <el-input v-model="formData.accountNo" maxlength="100" clearable />
        </el-form-item>
        <el-form-item label="专户户名">
          <el-input v-model="formData.accountName" maxlength="200" clearable />
        </el-form-item>
        <el-form-item label="开户银行" prop="bankName">
          <el-input v-model="formData.bankName" maxlength="200" clearable />
        </el-form-item>
        <el-form-item label="开户支行">
          <el-input v-model="formData.bankBranch" maxlength="200" clearable />
        </el-form-item>
        <el-form-item label="人工费预算">
          <el-input-number v-model="formData.wageBudget" :min="0" :precision="2" controls-position="right" style="width: 100%" placeholder="合同口径人工费总额" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="depositDialogVisible" title="登记人工费拨付到账" width="480px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="到账日期">
          <el-date-picker v-model="depositForm.depositDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="到账金额">
          <el-input-number v-model="depositForm.amount" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="拨付方">
          <el-input v-model="depositForm.payerName" placeholder="建设单位名称" maxlength="200" clearable />
        </el-form-item>
        <el-form-item label="银行凭证号">
          <el-input v-model="depositForm.voucherNo" maxlength="100" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="depositDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDeposit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="wagePayDialogVisible" title="登记总包代发工资" width="440px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="专户余额">
          <span>{{ formatAmount(currentRow?.currentBalance) }}</span>
        </el-form-item>
        <el-form-item label="代发金额">
          <el-input-number v-model="wagePayAmount" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="发放日期">
          <el-date-picker v-model="wagePayDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="wagePayDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitWagePayment">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="depositsDialogVisible" title="拨付记录" width="640px" destroy-on-close>
      <el-table :data="depositRecords" border>
        <el-table-column prop="depositDate" label="到账日期" width="110" align="center" />
        <el-table-column label="金额" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="payerName" label="拨付方" min-width="150" show-overflow-tooltip />
        <el-table-column prop="voucherNo" label="凭证号" width="140" show-overflow-tooltip />
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getWageAccountPage,
  saveWageAccount,
  recordWageDeposit,
  recordWagePayment,
  getWageDeposits,
  complianceScan,
  type WageSpecialAccount,
  type WageDeposit
} from '@/api/wage-account'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<WageSpecialAccount[]>([])
const total = ref(0)
const query = ref({ page: 1, size: 10 })
const addDialogVisible = ref(false)
const submitLoading = ref(false)
const depositDialogVisible = ref(false)
const wagePayDialogVisible = ref(false)
const depositsDialogVisible = ref(false)
const currentRow = ref<WageSpecialAccount | null>(null)
const depositRecords = ref<WageDeposit[]>([])

const formData = ref({
  projectId: undefined as number | undefined,
  accountNo: '',
  accountName: '',
  bankName: '',
  bankBranch: '',
  wageBudget: undefined as number | undefined
})

const depositForm = ref({ depositDate: '', amount: undefined as number | undefined, payerName: '', voucherNo: '' })
const wagePayAmount = ref(undefined as number | undefined)
const wagePayDate = ref('')

const formRules: FormRules = {
  projectId: [{ required: true, message: '请输入项目ID', trigger: 'blur' }],
  accountNo: [{ required: true, message: '请输入专户账号', trigger: 'blur' }],
  bankName: [{ required: true, message: '请输入开户银行', trigger: 'blur' }]
}

function formatAmount(value?: number) {
  return value != null ? `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` : '—'
}
function complianceLabel(flag: string) {
  return { COMPLIANT: '合规', INSUFFICIENT: '拨付不足', OVERDUE: '拨付逾期' }[flag] || flag
}
function complianceTag(flag: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { COMPLIANT: 'success', INSUFFICIENT: 'warning', OVERDUE: 'danger' }
  return map[flag] || 'info'
}
function arrivalText(row: WageSpecialAccount) {
  if (!row.wageBudget) return '—'
  return `${Math.round((row.totalReceived || 0) / row.wageBudget * 100)}%`
}
function arrivalClass(row: WageSpecialAccount) {
  if (!row.wageBudget) return ''
  const rate = (row.totalReceived || 0) / row.wageBudget
  return rate < 0.8 ? 'arrival-warn' : 'arrival-ok'
}

async function loadPage() {
  loading.value = true
  try {
    const res = await getWageAccountPage({ page: query.value.page, size: query.value.size })
    tableData.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  formData.value = { projectId: undefined, accountNo: '', accountName: '', bankName: '', bankBranch: '', wageBudget: undefined }
  addDialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await saveWageAccount(formData.value as WageSpecialAccount)
    ElMessage.success('专户已开设')
    addDialogVisible.value = false
    await loadPage()
  } finally {
    submitLoading.value = false
  }
}

function handleDeposit(row: WageSpecialAccount) {
  currentRow.value = row
  depositForm.value = { depositDate: new Date().toISOString().slice(0, 10), amount: undefined, payerName: '', voucherNo: '' }
  depositDialogVisible.value = true
}

async function submitDeposit() {
  if (!currentRow.value || !depositForm.value.amount) {
    ElMessage.warning('请填写到账日期与金额')
    return
  }
  await recordWageDeposit(currentRow.value.id as number, depositForm.value as WageDeposit)
  ElMessage.success('拨付到账已登记')
  depositDialogVisible.value = false
  await loadPage()
}

function handleWagePayment(row: WageSpecialAccount) {
  currentRow.value = row
  wagePayAmount.value = undefined
  wagePayDate.value = new Date().toISOString().slice(0, 10)
  wagePayDialogVisible.value = true
}

async function submitWagePayment() {
  if (!currentRow.value || !wagePayAmount.value) {
    ElMessage.warning('请填写代发金额')
    return
  }
  await recordWagePayment(currentRow.value.id as number, wagePayAmount.value, wagePayDate.value)
  ElMessage.success('代发工资已登记')
  wagePayDialogVisible.value = false
  await loadPage()
}

async function handleViewDeposits(row: WageSpecialAccount) {
  const res = await getWageDeposits(row.id as number)
  depositRecords.value = res.data.data || []
  depositsDialogVisible.value = true
}

async function handleScan() {
  const res = await complianceScan()
  const accounts = res.data.data || []
  const warnings = accounts.filter(a => a.complianceFlag !== 'COMPLIANT').length
  ElMessage.success(`巡检完成：${accounts.length} 个在用专户，${warnings} 个存在合规预警`)
  await loadPage()
}

onMounted(loadPage)
</script>

<style scoped>
.wage-account-container {
  padding: 16px;
}
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.arrival-warn {
  color: var(--el-color-warning);
  font-weight: 600;
}
.arrival-ok {
  color: var(--el-color-success);
}
</style>
