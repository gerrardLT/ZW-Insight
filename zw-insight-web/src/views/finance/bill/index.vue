<template>
  <div class="bill-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="filter-bar">
          <el-select v-model="query.direction" placeholder="方向" clearable style="width: 130px" @change="loadPage">
            <el-option label="应收票据" value="RECEIVABLE" />
            <el-option label="应付票据" value="PAYABLE" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px; margin-left: 8px" @change="loadPage">
            <el-option label="持有" value="HELD" />
            <el-option label="已背书" value="ENDORSED" />
            <el-option label="已贴现" value="DISCOUNTED" />
            <el-option label="已兑现" value="REDEEMED" />
            <el-option label="已兑付" value="PAID_OUT" />
          </el-select>
          <el-button style="margin-left: 8px" @click="loadExpiring">30天内到期</el-button>
        </div>
        <el-button type="primary" @click="handleAdd">登记票据</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="billNo" label="票据号码" min-width="150" show-overflow-tooltip />
        <el-table-column label="方向" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'RECEIVABLE' ? 'success' : 'warning'" size="small">
              {{ row.direction === 'RECEIVABLE' ? '应收' : '应付' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">{{ row.billType === 'BANK_ACCEPTANCE' ? '银行承兑' : '商业承兑' }}</template>
        </el-table-column>
        <el-table-column label="票面金额" width="140" align="right">
          <template #default="{ row }">{{ formatAmount(row.faceAmount) }}</template>
        </el-table-column>
        <el-table-column prop="issueDate" label="出票日" width="105" align="center" />
        <el-table-column prop="dueDate" label="到期日" width="105" align="center">
          <template #default="{ row }">
            <span :class="{ 'bill-overdue': isOverdue(row as BizBill) }">{{ row.dueDate }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="drawerName" label="出票人" min-width="130" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="贴现净额" width="130" align="right">
          <template #default="{ row }">
            <span v-if="row.discountNetAmount">{{ formatAmount(row.discountNetAmount) }}</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <template v-if="row.direction === 'RECEIVABLE' && row.status === 'HELD'">
              <el-button link type="primary" @click="handleEndorse(row as BizBill)">背书</el-button>
              <el-button link type="success" @click="handleDiscount(row as BizBill)">贴现</el-button>
              <el-button link type="warning" @click="handleRedeem(row as BizBill)">兑现</el-button>
            </template>
            <el-button
              v-if="row.direction === 'PAYABLE' && row.status === 'HELD'"
              link type="danger" @click="handlePayOut(row as BizBill)">兑付</el-button>
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

    <el-dialog v-model="addDialogVisible" title="登记票据" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="100px">
        <el-form-item label="票据号码" prop="billNo">
          <el-input v-model="formData.billNo" maxlength="100" clearable />
        </el-form-item>
        <el-form-item label="方向" prop="direction">
          <el-radio-group v-model="formData.direction">
            <el-radio value="RECEIVABLE">应收票据（收到）</el-radio>
            <el-radio value="PAYABLE">应付票据（开出）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="票据类型" prop="billType">
          <el-select v-model="formData.billType" style="width: 100%">
            <el-option label="银行承兑汇票" value="BANK_ACCEPTANCE" />
            <el-option label="商业承兑汇票" value="COMMERCIAL_ACCEPTANCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="票面金额" prop="faceAmount">
          <el-input-number v-model="formData.faceAmount" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="出票日期" prop="issueDate">
          <el-date-picker v-model="formData.issueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="到期日期" prop="dueDate">
          <el-date-picker v-model="formData.dueDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="出票人">
          <el-input v-model="formData.drawerName" maxlength="200" clearable />
        </el-form-item>
        <el-form-item label="收款人">
          <el-input v-model="formData.payeeName" maxlength="200" clearable />
        </el-form-item>
        <el-form-item label="承兑人">
          <el-input v-model="formData.acceptorName" maxlength="200" clearable placeholder="承兑银行/企业" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="endorseDialogVisible" title="背书转让" width="440px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="票面金额">
          <span>{{ formatAmount(currentRow?.faceAmount) }}</span>
        </el-form-item>
        <el-form-item label="被背书人">
          <el-input v-model="endorseeName" maxlength="200" clearable placeholder="转让给哪家单位" />
        </el-form-item>
        <el-form-item label="背书日期">
          <el-date-picker v-model="endorseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="endorseDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEndorse">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="discountDialogVisible" title="贴现变现" width="440px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
        title="贴现利息 = 票面金额 × 年贴现率 × 剩余天数 / 360（银行惯例）" />
      <el-form label-width="100px">
        <el-form-item label="票面金额">
          <span>{{ formatAmount(currentRow?.faceAmount) }}</span>
        </el-form-item>
        <el-form-item label="贴现年利率">
          <el-input-number v-model="discountRatePct" :min="0.01" :max="99" :precision="2" controls-position="right" style="width: 100%" />
          <span style="margin-left: 4px">%</span>
        </el-form-item>
        <el-form-item label="贴现日期">
          <el-date-picker v-model="discountDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="estimatedInterest" label="预估利息">
          <span style="color: var(--el-color-danger)">- {{ formatAmount(estimatedInterest) }}</span>
        </el-form-item>
        <el-form-item v-if="estimatedNet" label="预估净额">
          <span style="color: var(--el-color-success); font-weight: 600">{{ formatAmount(estimatedNet) }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="discountDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDiscount">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getBillPage,
  registerBill,
  endorseBill,
  discountBill,
  redeemBill,
  payOutBill,
  getExpiringBills,
  type BizBill
} from '@/api/bill'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<BizBill[]>([])
const total = ref(0)
const query = ref({ page: 1, size: 10, direction: '', status: '' })
const addDialogVisible = ref(false)
const submitLoading = ref(false)
const endorseDialogVisible = ref(false)
const discountDialogVisible = ref(false)
const currentRow = ref<BizBill | null>(null)
const endorseeName = ref('')
const endorseDate = ref('')
const discountRatePct = ref(4.8)
const discountDate = ref('')

const formData = ref({
  billNo: '',
  direction: 'RECEIVABLE' as BizBill['direction'],
  billType: 'BANK_ACCEPTANCE' as BizBill['billType'],
  faceAmount: undefined as number | undefined,
  issueDate: '',
  dueDate: '',
  drawerName: '',
  payeeName: '',
  acceptorName: ''
})

const formRules: FormRules = {
  billNo: [{ required: true, message: '请输入票据号码', trigger: 'blur' }],
  direction: [{ required: true, message: '请选择方向', trigger: 'change' }],
  billType: [{ required: true, message: '请选择票据类型', trigger: 'change' }],
  faceAmount: [{ required: true, message: '请输入票面金额', trigger: 'blur' }],
  issueDate: [{ required: true, message: '请选择出票日期', trigger: 'change' }],
  dueDate: [{ required: true, message: '请选择到期日期', trigger: 'change' }]
}

// 前端预估贴现（与后端公式一致：面值×率×剩余天数/360）
const estimatedInterest = computed(() => {
  if (!currentRow.value || !discountRatePct.value || !discountDate.value) return null
  const remaining = Math.round(
    (new Date(currentRow.value.dueDate).getTime() - new Date(discountDate.value).getTime()) / 86400000
  )
  if (remaining <= 0) return null
  const rate = discountRatePct.value / 100
  return Number((currentRow.value.faceAmount * rate * remaining / 360).toFixed(2))
})
const estimatedNet = computed(() => {
  if (!currentRow.value || estimatedInterest.value == null) return null
  return Number((currentRow.value.faceAmount - estimatedInterest.value).toFixed(2))
})

function formatAmount(value?: number) {
  return value != null ? `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` : '—'
}
function statusLabel(status: string) {
  return { HELD: '持有', ENDORSED: '已背书', DISCOUNTED: '已贴现', REDEEMED: '已兑现', PAID_OUT: '已兑付' }[status] || status
}
function statusTag(status: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { HELD: 'primary', ENDORSED: 'warning', DISCOUNTED: 'success', REDEEMED: 'success', PAID_OUT: 'info' }
  return map[status] || 'info'
}
function isOverdue(row: BizBill) {
  return row.status === 'HELD' && new Date(row.dueDate) < new Date()
}

async function loadPage() {
  loading.value = true
  try {
    const res = await getBillPage({
      page: query.value.page,
      size: query.value.size,
      direction: query.value.direction || undefined,
      status: query.value.status || undefined
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
    const res = await getExpiringBills(30)
    tableData.value = res.data.data || []
    total.value = tableData.value.length
    ElMessage.info(`共 ${tableData.value.length} 张票据 30 天内到期`)
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  formData.value = {
    billNo: '',
    direction: 'RECEIVABLE',
    billType: 'BANK_ACCEPTANCE',
    faceAmount: undefined,
    issueDate: '',
    dueDate: '',
    drawerName: '',
    payeeName: '',
    acceptorName: ''
  }
  addDialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    await registerBill(formData.value as BizBill)
    ElMessage.success('票据已登记')
    addDialogVisible.value = false
    await loadPage()
  } finally {
    submitLoading.value = false
  }
}

function handleEndorse(row: BizBill) {
  currentRow.value = row
  endorseeName.value = ''
  endorseDate.value = new Date().toISOString().slice(0, 10)
  endorseDialogVisible.value = true
}

async function submitEndorse() {
  if (!currentRow.value || !endorseeName.value) {
    ElMessage.warning('请填写被背书人')
    return
  }
  await endorseBill(currentRow.value.id as number, endorseeName.value, endorseDate.value)
  ElMessage.success('背书转让完成')
  endorseDialogVisible.value = false
  await loadPage()
}

function handleDiscount(row: BizBill) {
  currentRow.value = row
  discountRatePct.value = 4.8
  discountDate.value = new Date().toISOString().slice(0, 10)
  discountDialogVisible.value = true
}

async function submitDiscount() {
  if (!currentRow.value) return
  await discountBill(currentRow.value.id as number, discountRatePct.value / 100, discountDate.value)
  ElMessage.success('贴现完成')
  discountDialogVisible.value = false
  await loadPage()
}

async function handleRedeem(row: BizBill) {
  await ElMessageBox.confirm(`确认票据 ${row.billNo} 到期兑现？票面金额 ${formatAmount(row.faceAmount)}`, '兑现确认', { type: 'warning' })
  await redeemBill(row.id as number)
  ElMessage.success('已兑现')
  await loadPage()
}

async function handlePayOut(row: BizBill) {
  await ElMessageBox.confirm(`确认票据 ${row.billNo} 到期兑付？票面金额 ${formatAmount(row.faceAmount)}`, '兑付确认', { type: 'warning' })
  await payOutBill(row.id as number)
  ElMessage.success('已兑付')
  await loadPage()
}

onMounted(loadPage)
</script>

<style scoped>
.bill-container {
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
.bill-overdue {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
