<template>
  <div class="fund-plan-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- ============ 年度预算 ============ -->
        <el-tab-pane label="年度预算" name="annual">
          <div class="table-toolbar">
            <el-button type="primary" @click="annualDialogVisible = true">编制年度预算</el-button>
          </div>
          <el-table :data="annualData" v-loading="annualLoading" border>
            <el-table-column prop="budgetYear" label="年度" width="90" align="center" />
            <el-table-column prop="projectId" label="项目ID（空=公司整体）" min-width="170" show-overflow-tooltip />
            <el-table-column label="预计收款" width="140" align="right">
              <template #default="{ row }">{{ formatAmount(row.incomePlan) }}</template>
            </el-table-column>
            <el-table-column label="预计付款" width="140" align="right">
              <template #default="{ row }">{{ formatAmount(row.expensePlan) }}</template>
            </el-table-column>
            <el-table-column label="净额（收-付）" width="140" align="right">
              <template #default="{ row }">
                <span :class="(row.incomePlan - row.expensePlan) < 0 ? 'gap-negative' : 'gap-positive'">
                  {{ formatAmount(row.incomePlan - row.expensePlan) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" align="center" />
            <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
          </el-table>
        </el-tab-pane>

        <!-- ============ 月度计划 ============ -->
        <el-tab-pane label="月度计划" name="monthly">
          <div class="table-toolbar">
            <el-button type="primary" @click="openMonthlyDialog">编制月度计划</el-button>
          </div>
          <el-table :data="monthlyData" v-loading="monthlyLoading" border>
            <el-table-column label="月份" width="110" align="center">
              <template #default="{ row }">{{ row.planYear }}-{{ String(row.planMonth).padStart(2, '0') }}</template>
            </el-table-column>
            <el-table-column prop="projectId" label="项目ID（空=公司整体）" min-width="170" show-overflow-tooltip />
            <el-table-column label="计划收款" width="130" align="right">
              <template #default="{ row }">{{ formatAmount(row.incomePlan) }}</template>
            </el-table-column>
            <el-table-column label="计划付款" width="130" align="right">
              <template #default="{ row }">{{ formatAmount(row.expensePlan) }}</template>
            </el-table-column>
            <el-table-column label="实际收款" width="130" align="right">
              <template #default="{ row }">{{ formatAmount(row.actualIncome) }}</template>
            </el-table-column>
            <el-table-column label="实际付款" width="130" align="right">
              <template #default="{ row }">{{ formatAmount(row.actualExpense) }}</template>
            </el-table-column>
            <el-table-column label="执行率" width="100" align="center">
              <template #default="{ row }">
                <span v-if="row.expensePlan">{{ Math.round((row.actualExpense || 0) / row.expensePlan * 100) }}%</span>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleFillActual(row as FundMonthlyPlan)">回填实际</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ============ 滚动预测 ============ -->
        <el-tab-pane label="滚动预测" name="rolling">
          <div class="table-toolbar">
            <el-button type="primary" @click="handleGenerate">生成未来6个月预测</el-button>
          </div>
          <el-table :data="rollingData" v-loading="rollingLoading" border>
            <el-table-column prop="forecastMonth" label="预测月份" width="110" align="center" />
            <el-table-column label="预计收款" width="140" align="right">
              <template #default="{ row }">{{ formatAmount(row.expectedReceipts) }}</template>
            </el-table-column>
            <el-table-column label="预计付款" width="140" align="right">
              <template #default="{ row }">{{ formatAmount(row.expectedPayments) }}</template>
            </el-table-column>
            <el-table-column label="净缺口（付-收）" width="150" align="right">
              <template #default="{ row }">
                <span :class="(row.netGap || 0) > 0 ? 'gap-negative' : 'gap-positive'">{{ formatAmount(row.netGap) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="风险等级" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="riskTag(row.riskLevel)" size="small">{{ riskLabel(row.riskLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="snapshotDate" label="快照日期" width="110" align="center" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="annualDialogVisible" title="编制年度预算" width="480px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="预算年度">
          <el-input-number v-model="annualForm.budgetYear" :min="2000" :max="2100" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="项目ID（选填）">
          <el-input-number v-model="annualForm.projectId" :min="1" controls-position="right" style="width: 100%" placeholder="留空为公司整体" />
        </el-form-item>
        <el-form-item label="预计收款计划">
          <el-input-number v-model="annualForm.incomePlan" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="预计付款计划">
          <el-input-number v-model="annualForm.expensePlan" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="annualForm.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="annualDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAnnual">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="monthlyDialogVisible" title="编制月度计划" width="640px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="计划年度">
          <el-input-number v-model="monthlyForm.planYear" :min="2000" :max="2100" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划月份">
          <el-input-number v-model="monthlyForm.planMonth" :min="1" :max="12" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="项目ID（选填）">
          <el-input-number v-model="monthlyForm.projectId" :min="1" controls-position="right" style="width: 100%" placeholder="留空为公司整体" />
        </el-form-item>
        <el-form-item label="当月预计收款">
          <el-input-number v-model="monthlyForm.incomePlan" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="当月预计付款">
          <el-input-number v-model="monthlyForm.expensePlan" :min="0" :precision="2" controls-position="right" style="width: 100%" />
        </el-form-item>
        <!-- 科目明细（V2026_58）：可选维护；维护后合计必须与总额一致（前后端双重校验，不静默） -->
        <el-divider content-position="left">科目明细（选填，按资金科目拆分付款计划）</el-divider>
        <el-form-item v-for="(d, idx) in monthlyDetails" :key="idx" :label="`付款科目 ${idx + 1}`">
          <div class="detail-row">
            <el-select v-model="d.categoryCode" filterable placeholder="选择科目" style="flex: 1">
              <el-option v-for="c in expenseCategories" :key="c.code" :label="`${c.name}（${c.code}）`" :value="c.code" />
            </el-select>
            <el-input-number v-model="d.amount" :min="0.01" :precision="2" controls-position="right" style="width: 180px" />
            <el-button link type="danger" @click="removeDetail(idx)">删除</el-button>
          </div>
        </el-form-item>
        <el-form-item label-width="110px">
          <el-button link type="primary" @click="addDetail">+ 添加科目明细</el-button>
          <span class="detail-sum" :class="{ 'detail-sum-error': detailSumMismatch }">
            明细合计 {{ formatAmount(detailSum) }} / 计划付款 {{ formatAmount(monthlyForm.expensePlan) }}
            <template v-if="detailSumMismatch">（不一致，提交将被拒绝）</template>
          </span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="monthlyDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="detailSumMismatch" @click="submitMonthly">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAnnualBudgetPage,
  saveAnnualBudget,
  getMonthlyPlanPage,
  saveMonthlyPlan,
  fillMonthlyActual,
  generateRollingForecast,
  getRollingForecastPage,
  type FundAnnualBudget,
  type FundMonthlyPlan,
  type FundPlanDetail,
  type FundRollingForecast
} from '@/api/fund-plan'
import { getEnabledCategories, type FundCategory } from '@/api/fund-category'

const activeTab = ref('annual')
const annualLoading = ref(false)
const monthlyLoading = ref(false)
const rollingLoading = ref(false)
const annualData = ref<FundAnnualBudget[]>([])
const monthlyData = ref<FundMonthlyPlan[]>([])
const rollingData = ref<FundRollingForecast[]>([])
const annualDialogVisible = ref(false)
const monthlyDialogVisible = ref(false)

const annualForm = ref({
  budgetYear: new Date().getFullYear(),
  projectId: undefined as number | undefined,
  incomePlan: undefined as number | undefined,
  expensePlan: undefined as number | undefined,
  remark: ''
})
const monthlyForm = ref({
  planYear: new Date().getFullYear(),
  planMonth: new Date().getMonth() + 1,
  projectId: undefined as number | undefined,
  incomePlan: undefined as number | undefined,
  expensePlan: undefined as number | undefined
})

// ================= 科目明细（V2026_58） =================
const monthlyDetails = ref<FundPlanDetail[]>([])
const expenseCategories = ref<FundCategory[]>([])

const detailSum = computed(() =>
  monthlyDetails.value.reduce((sum, d) => sum + (Number(d.amount) || 0), 0))
// 有明细时合计必须与计划付款总额一致（容差 0.01，与后端同口径）；无明细不校验（可选维护）
const detailSumMismatch = computed(() => {
  if (!monthlyDetails.value.length) return false
  const total = Number(monthlyForm.value.expensePlan) || 0
  return Math.abs(detailSum.value - total) > 0.01
})

function addDetail() {
  monthlyDetails.value.push({ direction: 'EXPENSE', categoryCode: '', amount: undefined as unknown as number })
}

function openMonthlyDialog() {
  // 重置明细编辑态，避免上次未提交的残留行被带入
  monthlyDetails.value = []
  monthlyDialogVisible.value = true
}

function removeDetail(idx: number) {
  monthlyDetails.value.splice(idx, 1)
}

async function loadExpenseCategories() {
  const res = await getEnabledCategories('EXPENSE')
  expenseCategories.value = res.data.data || []
}

function formatAmount(value?: number) {
  return value != null ? `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` : '—'
}
function riskLabel(level: string) {
  return { LOW: '低', MEDIUM: '中', HIGH: '高' }[level] || level
}
function riskTag(level: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger' }
  return map[level] || 'info'
}

async function loadAnnual() {
  annualLoading.value = true
  try {
    const res = await getAnnualBudgetPage({ page: 1, size: 50 })
    annualData.value = res.data.data?.records || []
  } finally {
    annualLoading.value = false
  }
}

async function loadMonthly() {
  monthlyLoading.value = true
  try {
    const res = await getMonthlyPlanPage({ page: 1, size: 50 })
    monthlyData.value = res.data.data?.records || []
  } finally {
    monthlyLoading.value = false
  }
}

async function loadRolling() {
  rollingLoading.value = true
  try {
    const res = await getRollingForecastPage({ page: 1, size: 24 })
    rollingData.value = res.data.data?.records || []
  } finally {
    rollingLoading.value = false
  }
}

async function submitAnnual() {
  if (annualForm.value.incomePlan == null || annualForm.value.expensePlan == null) {
    ElMessage.warning('请填写收支计划')
    return
  }
  await saveAnnualBudget(annualForm.value as FundAnnualBudget)
  ElMessage.success('年度预算已编制')
  annualDialogVisible.value = false
  await loadAnnual()
}

async function submitMonthly() {
  if (monthlyForm.value.incomePlan == null || monthlyForm.value.expensePlan == null) {
    ElMessage.warning('请填写月度收支计划')
    return
  }
  // 明细预检（不静默）：科目必选、金额必填、同科目不重复；合计一致性由 detailSumMismatch 门禁按钮拦截
  if (monthlyDetails.value.length) {
    const invalid = monthlyDetails.value.find(d => !d.categoryCode || d.amount == null)
    if (invalid) {
      ElMessage.warning('科目明细存在未选科目或未填金额的行')
      return
    }
    const codes = monthlyDetails.value.map(d => d.categoryCode)
    if (new Set(codes).size !== codes.length) {
      ElMessage.warning('同方向科目不可重复')
      return
    }
  }
  const payload: FundMonthlyPlan = {
    ...monthlyForm.value as FundMonthlyPlan,
    details: monthlyDetails.value.length ? monthlyDetails.value : undefined
  }
  await saveMonthlyPlan(payload)
  ElMessage.success('月度计划已编制')
  monthlyDialogVisible.value = false
  await loadMonthly()
}

async function handleFillActual(row: FundMonthlyPlan) {
  await ElMessageBox.confirm(
    `确认回填 ${row.planYear}-${row.planMonth} 实际收支？数据将从真实单据（回款登记+已审批付款申请）聚合`,
    '回填确认',
    { type: 'warning' }
  )
  await fillMonthlyActual(row.planYear, row.planMonth, row.projectId || undefined)
  ElMessage.success('实际收支已回填')
  await loadMonthly()
}

async function handleGenerate() {
  await generateRollingForecast(undefined, 6)
  ElMessage.success('滚动预测快照已生成')
  await loadRolling()
}

onMounted(() => {
  loadAnnual()
  loadMonthly()
  loadRolling()
  loadExpenseCategories()
})
</script>

<style scoped>
.fund-plan-container {
  padding: var(--zw-space-md);
}
.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: var(--zw-space-sm-md)
}
.gap-negative {
  color: var(--el-color-danger);
  font-weight: 600;
}
.gap-positive {
  color: var(--el-color-success);
}
.detail-row {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  width: 100%;
}
.detail-sum {
  margin-left: var(--zw-space-md);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
.detail-sum-error {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
