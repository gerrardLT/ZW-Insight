<template>
  <div class="balance-reconciliation-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="filter-bar">
          <el-input v-model="query.accountId" placeholder="账户ID" clearable style="width: 120px" />
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 240px; margin-left: var(--zw-space-sm)"
          />
          <el-button type="primary" style="margin-left: var(--zw-space-sm)" @click="loadPage">查询</el-button>
        </div>
        <el-button type="primary" @click="handleAdd">生成调节表</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="reconciliationDate" label="调节基准日" width="120" align="center" />
        <el-table-column prop="accountId" label="账户ID" width="140" show-overflow-tooltip />
        <el-table-column label="银行对账单余额" width="150" align="right">
          <template #default="{ row }">{{ formatAmount(row.bankStatementBalance) }}</template>
        </el-table-column>
        <el-table-column label="企业账面余额" width="150" align="right">
          <template #default="{ row }">{{ formatAmount(row.bookBalance) }}</template>
        </el-table-column>
        <el-table-column label="调节后银行余额" width="150" align="right">
          <template #default="{ row }">
            <span class="adjusted">{{ formatAmount(row.adjustedBankBalance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="调节后账面余额" width="150" align="right">
          <template #default="{ row }">
            <span class="adjusted">{{ formatAmount(row.adjustedBookBalance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="是否调平" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.balanced === 1 ? 'success' : 'danger'" size="small">
              {{ row.balanced === 1 ? '已调平' : '未调平' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleDetail(row as BalanceReconciliation)">详情</el-button>
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

    <!-- 生成调节表 -->
    <el-dialog v-model="dialogVisible" title="生成银行存款余额调节表" width="620px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: var(--zw-space-sm-md)"
        title="调节后银行余额 = 银行对账单 + 银行收企业未收 - 银行付企业未付；调节后账面余额 = 企业账面 + 企业已收银行未收 - 企业已付银行未付。两者相等即调平。" />
      <el-form ref="formRef" :model="formData" :rules="formRules" label-width="160px">
        <el-form-item label="账户ID" prop="accountId">
          <el-input-number v-model="formData.accountId" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="调节基准日" prop="reconciliationDate">
          <el-date-picker v-model="formData.reconciliationDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="银行对账单余额" prop="bankStatementBalance">
          <el-input-number v-model="formData.bankStatementBalance" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="企业账面余额" prop="bookBalance">
          <el-input-number v-model="formData.bookBalance" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-divider content-position="left">未达账项（企业已记账、银行未记账）</el-divider>
        <el-form-item label="企业已收银行未收">
          <el-input-number v-model="formData.enterpriseDepositBankNot" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="企业已付银行未付">
          <el-input-number v-model="formData.enterprisePaymentBankNot" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-divider content-position="left">未达账项（银行已记账、企业未记账）</el-divider>
        <el-form-item label="银行收企业未收">
          <el-input-number v-model="formData.bankDepositEnterpriseNot" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="银行付企业未付">
          <el-input-number v-model="formData.bankPaymentEnterpriseNot" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 详情 -->
    <el-dialog v-model="detailDialogVisible" title="调节表详情" width="560px" destroy-on-close>
      <el-descriptions :column="1" border v-if="detail">
        <el-descriptions-item label="调节基准日">{{ detail.reconciliationDate }}</el-descriptions-item>
        <el-descriptions-item label="银行对账单余额">{{ formatAmount(detail.bankStatementBalance) }}</el-descriptions-item>
        <el-descriptions-item label="企业账面余额">{{ formatAmount(detail.bookBalance) }}</el-descriptions-item>
        <el-descriptions-item label="企业已收银行未收">{{ formatAmount(detail.enterpriseDepositBankNot) }}</el-descriptions-item>
        <el-descriptions-item label="企业已付银行未付">{{ formatAmount(detail.enterprisePaymentBankNot) }}</el-descriptions-item>
        <el-descriptions-item label="银行收企业未收">{{ formatAmount(detail.bankDepositEnterpriseNot) }}</el-descriptions-item>
        <el-descriptions-item label="银行付企业未付">{{ formatAmount(detail.bankPaymentEnterpriseNot) }}</el-descriptions-item>
        <el-descriptions-item label="调节后银行余额">
          <span class="adjusted">{{ formatAmount(detail.adjustedBankBalance) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="调节后账面余额">
          <span class="adjusted">{{ formatAmount(detail.adjustedBookBalance) }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="是否调平">
          <el-tag :type="detail.balanced === 1 ? 'success' : 'danger'" size="small">
            {{ detail.balanced === 1 ? '已调平' : '未调平' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getReconciliationPage,
  saveReconciliation,
  getReconciliationDetail,
  type BalanceReconciliation
} from '@/api/balance-reconciliation'

const formRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<BalanceReconciliation[]>([])
const total = ref(0)
const query = ref<{ page: number; size: number; accountId?: number }>({ page: 1, size: 10 })
const dateRange = ref<[string, string] | null>(null)
const dialogVisible = ref(false)
const detailDialogVisible = ref(false)
const submitLoading = ref(false)
const detail = ref<BalanceReconciliation | null>(null)

const formData = ref({
  accountId: undefined as number | undefined,
  reconciliationDate: new Date().toISOString().slice(0, 10),
  bankStatementBalance: undefined as number | undefined,
  bookBalance: undefined as number | undefined,
  enterpriseDepositBankNot: 0,
  enterprisePaymentBankNot: 0,
  bankDepositEnterpriseNot: 0,
  bankPaymentEnterpriseNot: 0
})

const formRules: FormRules = {
  accountId: [{ required: true, message: '请输入账户ID', trigger: 'blur' }],
  reconciliationDate: [{ required: true, message: '请选择调节基准日', trigger: 'change' }],
  bankStatementBalance: [{ required: true, message: '请输入银行对账单余额', trigger: 'blur' }],
  bookBalance: [{ required: true, message: '请输入企业账面余额', trigger: 'blur' }]
}

function formatAmount(value?: number) {
  return value != null ? `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` : '—'
}

async function loadPage() {
  loading.value = true
  try {
    const res = await getReconciliationPage({
      page: query.value.page,
      size: query.value.size,
      accountId: query.value.accountId || undefined,
      start: dateRange.value?.[0],
      end: dateRange.value?.[1]
    })
    tableData.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleAdd() {
  formData.value = {
    accountId: undefined,
    reconciliationDate: new Date().toISOString().slice(0, 10),
    bankStatementBalance: undefined,
    bookBalance: undefined,
    enterpriseDepositBankNot: 0,
    enterprisePaymentBankNot: 0,
    bankDepositEnterpriseNot: 0,
    bankPaymentEnterpriseNot: 0
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  await formRef.value?.validate()
  submitLoading.value = true
  try {
    const res = await saveReconciliation(formData.value as BalanceReconciliation)
    const saved = res.data.data
    if (saved && saved.balanced === 1) {
      ElMessage.success('调节表已生成，双向余额已调平')
    } else {
      ElMessage.warning('调节表已生成，但双向余额未调平，请核对未达账项')
    }
    dialogVisible.value = false
    await loadPage()
  } finally {
    submitLoading.value = false
  }
}

async function handleDetail(row: BalanceReconciliation) {
  const res = await getReconciliationDetail(row.id as number)
  detail.value = res.data.data
  detailDialogVisible.value = true
}

onMounted(loadPage)
</script>

<style scoped>
.balance-reconciliation-container {
  padding: var(--zw-space-md);
}
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--zw-space-sm-md);
  flex-wrap: wrap;
  gap: var(--zw-space-sm);
}
.filter-bar {
  display: flex;
  align-items: center;
}
.adjusted {
  font-weight: 600;
  color: var(--el-color-primary);
}
</style>
