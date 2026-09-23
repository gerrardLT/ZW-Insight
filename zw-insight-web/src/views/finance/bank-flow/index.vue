<template>
  <div class="bank-flow-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="filter-bar">
          <el-input v-model="query.accountId" placeholder="账户ID" clearable style="width: 120px" />
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px; margin-left: var(--zw-space-sm)"
          />
          <el-select v-model="query.reconciled" placeholder="勾稽状态" clearable style="width: 120px; margin-left: var(--zw-space-sm)">
            <el-option label="未勾稽" :value="0" />
            <el-option label="已勾稽" :value="1" />
          </el-select>
          <el-button type="primary" style="margin-left: var(--zw-space-sm)" @click="loadPage">查询</el-button>
        </div>
        <div>
          <el-button @click="balanceDialogVisible = true">余额登记</el-button>
          <el-button @click="importDialogVisible = true">导入流水</el-button>
          <el-button type="primary" @click="handleAddFlow">手工登记</el-button>
        </div>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="flowDate" label="交易日期" width="110" align="center" />
        <el-table-column label="方向" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'IN' ? 'success' : 'danger'" size="small">
              {{ row.direction === 'IN' ? '收入' : '支出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="140" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="transactionNo" label="流水号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="counterpartyName" label="对方单位" min-width="150" show-overflow-tooltip />
        <el-table-column prop="description" label="摘要" min-width="150" show-overflow-tooltip />
        <el-table-column label="勾稽" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.reconciled === 1 ? 'success' : 'info'" size="small">
              {{ row.reconciled === 1 ? '已勾稽' : '未勾稽' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.reconciled !== 1" link type="primary" @click="handleMatch(row as BankFlow)">勾稽</el-button>
            <el-button v-else link type="warning" @click="handleUnmatch(row as BankFlow)">取消勾稽</el-button>
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

    <!-- 手工登记流水 -->
    <el-dialog v-model="flowDialogVisible" title="手工登记流水" width="520px" destroy-on-close>
      <el-form ref="flowFormRef" :model="flowForm" :rules="flowRules" label-width="100px">
        <el-form-item label="账户ID" prop="accountId">
          <el-input-number v-model="flowForm.accountId" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="交易日期" prop="flowDate">
          <el-date-picker v-model="flowForm.flowDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="方向" prop="direction">
          <el-radio-group v-model="flowForm.direction">
            <el-radio value="IN">收入</el-radio>
            <el-radio value="OUT">支出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额" prop="amount">
          <el-input-number v-model="flowForm.amount" :min="0.01" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="流水号">
          <el-input v-model="flowForm.transactionNo" maxlength="100" clearable placeholder="选填，用于去重" />
        </el-form-item>
        <el-form-item label="对方单位">
          <el-input v-model="flowForm.counterpartyName" maxlength="200" clearable />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="flowForm.description" maxlength="500" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="flowDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFlow">确定</el-button>
      </template>
    </el-dialog>

    <!-- 余额登记 -->
    <el-dialog v-model="balanceDialogVisible" title="账户余额登记" width="460px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: var(--zw-space-sm-md)"
        title="余额来自网银对账单，作为资金日报头寸数据源；同日重复登记执行覆盖" />
      <el-form label-width="100px">
        <el-form-item label="账户ID">
          <el-input-number v-model="balanceForm.accountId" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="余额日期">
          <el-date-picker v-model="balanceForm.snapshotDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="账户余额">
          <el-input-number v-model="balanceForm.balance" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="balanceForm.remark" maxlength="500" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="balanceDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitBalance">确定</el-button>
      </template>
    </el-dialog>

    <!-- CSV 导入 -->
    <el-dialog v-model="importDialogVisible" title="导入银行流水（CSV 粘贴）" width="640px" destroy-on-close>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: var(--zw-space-sm-md)"
        title="从网银导出流水后按「日期,方向(IN/OUT),金额,流水号,对方单位,摘要」每行一条粘贴；按流水号去重，重复行自动跳过" />
      <el-form label-width="80px">
        <el-form-item label="账户ID">
          <el-input-number v-model="importAccountId" :min="1" controls-position="right" style="width: 200px" />
        </el-form-item>
        <el-form-item label="流水数据">
          <el-input v-model="importText" type="textarea" :rows="8"
            placeholder="2026-09-20,OUT,50000,TXN001,某供应商,材料款&#10;2026-09-20,IN,200000,TXN002,某建设单位,进度款" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitImport">解析并导入</el-button>
      </template>
    </el-dialog>

    <!-- 勾稽 -->
    <el-dialog v-model="matchDialogVisible" title="流水勾稽" width="440px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="流水金额">
          <span>{{ formatAmount(currentRow?.amount) }}（{{ currentRow?.direction === 'IN' ? '收入' : '支出' }}）</span>
        </el-form-item>
        <el-form-item label="匹配单据">
          <el-select v-model="matchForm.matchedType" style="width: 100%" :disabled="true">
            <el-option label="付款申请" value="PAYMENT_APPLY" />
            <el-option label="回款登记" value="PAYMENT_RECEIVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="单据ID">
          <el-input-number v-model="matchForm.matchedId" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="勾稽金额">
          <el-input-number
            v-model="matchForm.matchAmount"
            :min="0.01"
            :max="currentRow?.amount"
            :precision="2"
            controls-position="right"
            style="width: 100%"
            placeholder="留空按流水整笔金额勾稽"
          />
          <div class="match-hint">支持部分勾稽；付款申请累计勾稽足额后自动标记已支付（UNPAID→PAID）</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="matchDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMatch">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  getFlowPage,
  addFlow,
  importFlows,
  matchFlow,
  unmatchFlow,
  recordBalance,
  type BankFlow
} from '@/api/bank-flow'

const flowFormRef = ref<FormInstance>()
const loading = ref(false)
const tableData = ref<BankFlow[]>([])
const total = ref(0)
const query = ref<{ page: number; size: number; accountId?: number; reconciled?: number }>({ page: 1, size: 10 })
const dateRange = ref<[string, string] | null>(null)

const flowDialogVisible = ref(false)
const balanceDialogVisible = ref(false)
const importDialogVisible = ref(false)
const matchDialogVisible = ref(false)
const currentRow = ref<BankFlow | null>(null)

const flowForm = ref({
  accountId: undefined as number | undefined,
  flowDate: '',
  direction: 'OUT' as BankFlow['direction'],
  amount: undefined as number | undefined,
  transactionNo: '',
  counterpartyName: '',
  description: ''
})
const balanceForm = ref({
  accountId: undefined as number | undefined,
  snapshotDate: new Date().toISOString().slice(0, 10),
  balance: undefined as number | undefined,
  remark: ''
})
const importAccountId = ref<number | undefined>(undefined)
const importText = ref('')
const matchForm = ref({
  matchedType: 'PAYMENT_APPLY',
  matchedId: undefined as number | undefined,
  matchAmount: undefined as number | undefined
})

const flowRules: FormRules = {
  accountId: [{ required: true, message: '请输入账户ID', trigger: 'blur' }],
  flowDate: [{ required: true, message: '请选择交易日期', trigger: 'change' }],
  direction: [{ required: true, message: '请选择方向', trigger: 'change' }],
  amount: [{ required: true, message: '请输入金额', trigger: 'blur' }]
}

function formatAmount(value?: number) {
  return value != null ? `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` : '—'
}

async function loadPage() {
  loading.value = true
  try {
    const res = await getFlowPage({
      page: query.value.page,
      size: query.value.size,
      accountId: query.value.accountId || undefined,
      start: dateRange.value?.[0],
      end: dateRange.value?.[1],
      reconciled: query.value.reconciled
    })
    tableData.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleAddFlow() {
  flowForm.value = {
    accountId: undefined,
    flowDate: new Date().toISOString().slice(0, 10),
    direction: 'OUT',
    amount: undefined,
    transactionNo: '',
    counterpartyName: '',
    description: ''
  }
  flowDialogVisible.value = true
}

async function submitFlow() {
  await flowFormRef.value?.validate()
  await addFlow(flowForm.value as BankFlow)
  ElMessage.success('流水已登记')
  flowDialogVisible.value = false
  await loadPage()
}

async function submitBalance() {
  if (!balanceForm.value.accountId || balanceForm.value.balance == null) {
    ElMessage.warning('请填写账户ID与余额')
    return
  }
  await recordBalance(
    balanceForm.value.accountId,
    balanceForm.value.snapshotDate,
    balanceForm.value.balance,
    balanceForm.value.remark
  )
  ElMessage.success('余额已登记')
  balanceDialogVisible.value = false
}

/** 解析 CSV 文本为结构化流水（前端解析，失败行明确报错，不静默丢弃） */
function parseCsv(text: string): BankFlow[] {
  const lines = text.split(/\r?\n/).map(l => l.trim()).filter(l => l.length > 0)
  const flows: BankFlow[] = []
  lines.forEach((line, idx) => {
    const cols = line.split(',')
    if (cols.length < 3) {
      throw new Error(`第 ${idx + 1} 行格式错误（至少需 日期,方向,金额）：${line}`)
    }
    const direction = cols[1].trim().toUpperCase()
    if (direction !== 'IN' && direction !== 'OUT') {
      throw new Error(`第 ${idx + 1} 行方向非法（需 IN/OUT）：${cols[1]}`)
    }
    flows.push({
      accountId: importAccountId.value as number,
      flowDate: cols[0].trim(),
      direction: direction as BankFlow['direction'],
      amount: Number(cols[2]),
      transactionNo: cols[3]?.trim() || undefined,
      counterpartyName: cols[4]?.trim() || undefined,
      description: cols[5]?.trim() || undefined
    })
  })
  return flows
}

async function submitImport() {
  if (!importAccountId.value) {
    ElMessage.warning('请填写账户ID')
    return
  }
  let flows: BankFlow[]
  try {
    flows = parseCsv(importText.value)
  } catch (e) {
    ElMessage.error((e as Error).message)
    return
  }
  if (flows.length === 0) {
    ElMessage.warning('无有效流水数据')
    return
  }
  const res = await importFlows(flows)
  const r = res.data.data || {}
  ElMessage.success(`导入完成：新增 ${r.inserted ?? 0} 条，跳过重复 ${r.skipped ?? 0} 条`)
  importDialogVisible.value = false
  importText.value = ''
  await loadPage()
}

function handleMatch(row: BankFlow) {
  currentRow.value = row
  // 方向决定可匹配的单据类型：收入→回款，支出→付款
  matchForm.value = {
    matchedType: row.direction === 'IN' ? 'PAYMENT_RECEIVED' : 'PAYMENT_APPLY',
    matchedId: undefined,
    matchAmount: undefined
  }
  matchDialogVisible.value = true
}

async function submitMatch() {
  if (!currentRow.value || !matchForm.value.matchedId) {
    ElMessage.warning('请填写匹配单据ID')
    return
  }
  await matchFlow(
    currentRow.value.id as number,
    matchForm.value.matchedType,
    matchForm.value.matchedId,
    matchForm.value.matchAmount
  )
  ElMessage.success('勾稽成功')
  matchDialogVisible.value = false
  await loadPage()
}

async function handleUnmatch(row: BankFlow) {
  await unmatchFlow(row.id as number)
  ElMessage.success('已取消勾稽')
  await loadPage()
}

onMounted(loadPage)
</script>

<style scoped>
.bank-flow-container {
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
.match-hint {
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
  line-height: 1.5;
  margin-top: var(--zw-space-xs);
}
</style>
