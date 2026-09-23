<template>
  <div class="risk-center-container">
    <!-- 分级汇总（§11：数量 + 影响金额） -->
    <div v-loading="summaryLoading" class="severity-grid">
      <el-card v-for="s in severityCards" :key="s.key" shadow="never" class="severity-card"
        :class="[`sev-card-${s.key}`, { active: query.severity === s.severity }]"
        @click="toggleSeverity(s.severity)">
        <div class="sev-label">{{ s.icon }} {{ s.label }}</div>
        <div class="sev-count">{{ s.count }}<span class="sev-unit">项</span></div>
        <div class="sev-impact">影响金额 {{ formatWan(s.impact) }}</div>
      </el-card>
      <el-card shadow="never" class="severity-card sev-card-action">
        <div class="sev-label">待处理合计</div>
        <div class="sev-count">{{ summary.activeTotal }}<span class="sev-unit">项</span></div>
        <el-button link type="primary" :loading="scanning" @click="handleScan">
          重新扫描
        </el-button>
      </el-card>
    </div>

    <!-- 风险台账（老板待处理事项中心） -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>风险台账</span>
          <div class="filter-bar">
            <el-select v-model="query.handleStatus" placeholder="处理状态" clearable style="width: 120px"
              @change="handleSearch">
              <el-option label="待处理" value="OPEN" />
              <el-option label="处理中" value="PROCESSING" />
              <el-option label="已解决" value="RESOLVED" />
              <el-option label="已忽略" value="IGNORED" />
            </el-select>
            <el-select v-model="query.riskType" placeholder="风险类型" clearable style="width: 150px"
              @change="handleSearch">
              <el-option v-for="(label, value) in RISK_TYPE_LABELS" :key="value" :label="label" :value="value" />
            </el-select>
            <el-select v-model="query.projectId" placeholder="全部项目" filterable remote clearable
              :remote-method="searchProject" style="width: 200px" @change="handleSearch">
              <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
            </el-select>
          </div>
        </div>
      </template>

      <el-table :data="riskList" v-loading="listLoading" border>
        <el-table-column label="级别" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="severityTag(row.severity)" size="small" :effect="row.severity === 'RED' ? 'dark' : 'light'">
              {{ severityLabel(row.severity) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发生了什么" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row as RiskRegister)">
              {{ row.title }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="影响金额" width="130" align="right">
          <template #default="{ row }">
            <span class="impact-amount">{{ formatWan(row.impactAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">{{ RISK_TYPE_LABELS[row.riskType] || row.riskType }}</template>
        </el-table-column>
        <el-table-column label="责任人" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.ownerName">{{ row.ownerName }}</span>
            <el-tag v-else type="info" size="small">未指定</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="handleStatusTag(row.handleStatus)" size="small">
              {{ HANDLE_STATUS_LABELS[row.handleStatus] || row.handleStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row as RiskRegister)">详情</el-button>
            <el-button v-if="row.handleStatus === 'OPEN'" link type="warning"
              @click="handleAction(row as RiskRegister, 'PROCESSING')">认领</el-button>
            <el-button v-if="row.handleStatus !== 'RESOLVED'" link type="success"
              @click="handleAction(row as RiskRegister, 'RESOLVED')">解决</el-button>
            <el-button v-else link type="info" @click="handleAction(row as RiskRegister, 'OPEN')">重开</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        class="pagination-wrap"
        @size-change="loadList"
        @current-change="loadList"
      />
    </el-card>

    <!-- 风险详情（§12：六要素 + 穿透跳转） -->
    <el-drawer v-model="detailVisible" title="风险详情" size="520px">
      <div v-if="current" class="risk-detail">
        <div class="detail-head">
          <el-tag :type="severityTag(current.severity)" :effect="current.severity === 'RED' ? 'dark' : 'light'">
            {{ severityLabel(current.severity) }}
          </el-tag>
          <span class="detail-type">{{ RISK_TYPE_LABELS[current.riskType] || current.riskType }}</span>
        </div>
        <h3 class="detail-title">{{ current.title }}</h3>

        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="项目">
            {{ current.projectName || (current.projectId ? `项目#${current.projectId}` : '公司整体') }}
          </el-descriptions-item>
          <el-descriptions-item label="影响多少钱">
            <span class="impact-amount">{{ formatWan(current.impactAmount) }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="为什么发生">
            <div v-if="reasonRows.length" class="reason-list">
              <div v-for="r in reasonRows" :key="r.key" class="reason-row">
                <span class="reason-key">{{ r.key }}</span>
                <span class="reason-value">{{ r.value }}</span>
              </div>
            </div>
            <span v-else class="empty-text">规则未提供结构化归因</span>
          </el-descriptions-item>
          <el-descriptions-item label="谁负责">
            {{ current.ownerName || '未指定（项目未配置项目经理）' }}
          </el-descriptions-item>
          <el-descriptions-item label="下一步动作">
            {{ current.nextAction || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="当前处理状态">
            <el-tag :type="handleStatusTag(current.handleStatus)" size="small">
              {{ HANDLE_STATUS_LABELS[current.handleStatus] || current.handleStatus }}
            </el-tag>
            <span v-if="current.handleNote" class="handle-note">{{ current.handleNote }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="判定依据">
            <code class="rule-params">{{ current.ruleParams || '—' }}</code>
          </el-descriptions-item>
          <el-descriptions-item label="最近扫描确认">
            {{ current.lastScanAt || '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 单据穿透（§13：任何数字可追溯到业务单据） -->
        <div class="drill-section">
          <div class="drill-title">单据穿透</div>
          <div class="drill-actions">
            <el-button v-for="d in drillActions" :key="d.label" size="small" @click="d.go()">
              {{ d.label }}
            </el-button>
            <span v-if="!drillActions.length" class="empty-text">该风险类型暂无穿透映射</span>
          </div>
        </div>

        <div class="detail-actions">
          <el-button v-if="current.handleStatus === 'OPEN'" type="warning"
            @click="handleAction(current, 'PROCESSING')">认领处理</el-button>
          <el-button v-if="current.handleStatus !== 'RESOLVED'" type="success"
            @click="handleAction(current, 'RESOLVED')">标记已解决</el-button>
          <el-button v-if="current.handleStatus !== 'IGNORED'" @click="handleIgnore(current)">忽略（消音）</el-button>
          <el-button v-if="current.handleStatus === 'RESOLVED'" @click="handleAction(current, 'OPEN')">重开</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRiskSummary, getRiskPage, handleRisk, scanRisks,
  type RiskRegister, type RiskSummary
} from '@/api/cockpit'
import { getProjectList } from '@/api/project'
import { formatWan } from '@/utils/chart-format'

const router = useRouter()

/** 风险类型中文（与后端 RiskRule.riskType() 值域一致） */
const RISK_TYPE_LABELS: Record<string, string> = {
  PROFIT_LOSS: '利润风险',
  BUDGET_OVER: '超预算',
  FUND_GAP: '资金缺口',
  RECEIVABLE_OVERDUE: '应收逾期',
  RETENTION_OVERDUE: '质保金逾期',
  WAGE_COMPLIANCE: '工资专户合规',
  ENTERTAINMENT_ANOMALY: '招待费异常'
}

const HANDLE_STATUS_LABELS: Record<string, string> = {
  OPEN: '待处理', PROCESSING: '处理中', RESOLVED: '已解决', IGNORED: '已忽略'
}

const summaryLoading = ref(false)
const listLoading = ref(false)
const scanning = ref(false)
const summary = ref<RiskSummary>({
  redCount: 0, redImpact: 0, yellowCount: 0, yellowImpact: 0,
  infoCount: 0, infoImpact: 0, activeTotal: 0
})
const riskList = ref<RiskRegister[]>([])
const total = ref(0)
const projectList = ref<any[]>([])
const query = ref<{
  page: number; size: number; severity?: string; handleStatus?: string;
  riskType?: string; projectId?: number
}>({ page: 1, size: 20 })

const detailVisible = ref(false)
const current = ref<RiskRegister | null>(null)

const severityCards = computed(() => [
  { key: 'red', icon: '🔴', label: '严重', severity: 'RED', count: summary.value.redCount, impact: summary.value.redImpact },
  { key: 'yellow', icon: '🟡', label: '关注', severity: 'YELLOW', count: summary.value.yellowCount, impact: summary.value.yellowImpact },
  { key: 'info', icon: '⚪', label: '提醒', severity: 'INFO', count: summary.value.infoCount, impact: summary.value.infoImpact }
])

function severityLabel(severity: string) {
  return { RED: '严重', YELLOW: '关注', INFO: '提醒' }[severity] || severity
}

function severityTag(severity: string): 'danger' | 'warning' | 'info' {
  const map: Record<string, 'danger' | 'warning' | 'info'> = { RED: 'danger', YELLOW: 'warning', INFO: 'info' }
  return map[severity] || 'info'
}

function handleStatusTag(status: string): 'danger' | 'warning' | 'success' | 'info' {
  const map: Record<string, 'danger' | 'warning' | 'success' | 'info'> = {
    OPEN: 'danger', PROCESSING: 'warning', RESOLVED: 'success', IGNORED: 'info'
  }
  return map[status] || 'info'
}

/** 结构化归因（reasonDetail JSON）转为可读键值行；解析失败如实展示原文 */
const reasonRows = computed(() => {
  const raw = current.value?.reasonDetail
  if (!raw) return []
  try {
    const obj = JSON.parse(raw)
    if (Array.isArray(obj)) {
      return obj.map((item, idx) => ({ key: `明细 ${idx + 1}`, value: JSON.stringify(item) }))
    }
    return Object.entries(obj).map(([key, value]) => ({ key: REASON_LABELS[key] || key, value: String(value) }))
  } catch {
    return [{ key: '原始归因', value: raw }]
  }
})

const REASON_LABELS: Record<string, string> = {
  contractIncome: '合同收入', forecastTotalCost: '预计总成本', profitRate: '利润率',
  costBasis: '成本口径', category: '成本类别', actual: '实际发生', budget: '预算',
  rate: '执行率', month: '月份', expectedPayments: '预计付款', expectedReceipts: '预计收款',
  netGap: '净缺口', forecastRiskLevel: '预测风险级', totalAmount: '累计金额',
  totalCount: '笔数', maxSingleAmount: '单次最高', noReasonCount: '无事由笔数',
  noHostCount: '无对象笔数', noPreApprovalCount: '无事前审批笔数', noInvoiceCount: '发票不完整笔数',
  sameDayMultiCount: '同人同日多笔', frequentHandlerCount: '高频经办人数',
  receivableId: '应收ID', dueDate: '到期日', balance: '未结余额', overdueDays: '逾期天数',
  retentionId: '质保金ID', expireDate: '到期日', retentionAmount: '质保金额',
  returnedAmount: '已退金额', accountId: '专户ID', accountNo: '账号',
  complianceFlag: '合规状态', wageBudget: '工资预算', totalReceived: '已到账', totalPaid: '已发放'
}

/**
 * 单据穿透路由映射（§13）：按 bizRefType 跳到对应业务页面并带上定位参数。
 * 未登记的类型不伪造跳转（显示"暂无穿透映射"）。
 */
const drillActions = computed(() => {
  const r = current.value
  if (!r) return []
  const actions: { label: string; go: () => void }[] = []
  if (r.projectId) {
    actions.push({
      label: '项目成本主线',
      go: () => router.push({ path: '/project-cost-control', query: { projectId: String(r.projectId) } })
    })
    actions.push({
      label: '项目看板',
      go: () => router.push({ path: '/project-dashboard', query: { projectId: String(r.projectId) } })
    })
  }
  switch (r.bizRefType) {
    case 'RECEIVABLE':
      actions.push({ label: '应收台账', go: () => router.push({ path: '/finance/receivable' }) })
      break
    case 'FORECAST':
      actions.push({ label: '资金计划·滚动预测', go: () => router.push({ path: '/finance/fund-plan' }) })
      break
    case 'RETENTION':
      actions.push({ label: '质保金台账', go: () => router.push({ path: '/finance/retention' }) })
      break
    case 'WAGE_ACCOUNT':
      actions.push({ label: '工资专户', go: () => router.push({ path: '/finance/wage-account' }) })
      break
    case 'ENTERTAINMENT':
      actions.push({ label: '项目报销', go: () => router.push({ path: '/finance/project-reimbursement' }) })
      break
    default:
      break
  }
  return actions
})

async function loadSummary() {
  summaryLoading.value = true
  try {
    const res: any = await getRiskSummary(query.value.projectId)
    summary.value = res.data || summary.value
  } catch (e: any) {
    ElMessage.error('加载风险汇总失败：' + (e?.message || '接口异常'))
  } finally {
    summaryLoading.value = false
  }
}

async function loadList() {
  listLoading.value = true
  try {
    const res: any = await getRiskPage({
      page: query.value.page,
      size: query.value.size,
      severity: query.value.severity,
      handleStatus: query.value.handleStatus,
      riskType: query.value.riskType,
      projectId: query.value.projectId
    })
    riskList.value = res.data?.records || []
    total.value = Number(res.data?.total) || 0
  } catch (e: any) {
    riskList.value = []
    ElMessage.error('加载风险台账失败：' + (e?.message || '接口异常'))
  } finally {
    listLoading.value = false
  }
}

function handleSearch() {
  query.value.page = 1
  loadList()
  loadSummary()
}

function toggleSeverity(severity: string) {
  query.value.severity = query.value.severity === severity ? undefined : severity
  handleSearch()
}

async function handleScan() {
  scanning.value = true
  try {
    const res: any = await scanRisks()
    const d = res.data || {}
    if (d.failedRules?.length) {
      // 规则失败必须显式暴露（不静默降级为"扫描成功"）
      ElMessage.warning(`扫描完成，但 ${d.failedRules.length} 条规则失败：${d.failedRules.join('、')}`)
    } else {
      ElMessage.success(`扫描完成：命中 ${d.findings ?? 0} 项（新增 ${d.inserted ?? 0}，更新 ${d.updated ?? 0}，自动关闭 ${d.resolved ?? 0}）`)
    }
    await Promise.all([loadSummary(), loadList()])
  } catch (e: any) {
    ElMessage.error('风险扫描失败：' + (e?.message || '接口异常'))
  } finally {
    scanning.value = false
  }
}

function openDetail(row: RiskRegister) {
  current.value = row
  detailVisible.value = true
}

async function handleAction(row: RiskRegister, action: string, note?: string) {
  try {
    await handleRisk(row.id, action, note)
    ElMessage.success(`已置为「${HANDLE_STATUS_LABELS[action] || action}」`)
    detailVisible.value = false
    await Promise.all([loadSummary(), loadList()])
  } catch (e: any) {
    ElMessage.error('处理失败：' + (e?.message || '接口异常'))
  }
}

async function handleIgnore(row: RiskRegister) {
  const { value } = await ElMessageBox.prompt(
    '忽略后该风险不再自动重开（除非规则判级升高）。请填写忽略原因（留痕可审计）：',
    '忽略风险', { inputPlaceholder: '如：已与甲方确认延后收款', inputValidator: (v: string) => (v && v.trim() ? true : '忽略原因不能为空') })
  await handleAction(row, 'IGNORED', value)
}

async function searchProject(keyword: string) {
  const res: any = await getProjectList({ projectName: keyword })
  projectList.value = res.data || []
}

onMounted(() => {
  loadSummary()
  loadList()
  searchProject('')
})
</script>

<style scoped>
.risk-center-container {
  padding: var(--zw-content-padding);
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-md);
}
.severity-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--zw-space-sm-md);
}
@media (max-width: 900px) {
  .severity-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
.severity-card {
  cursor: pointer;
  border-left: 3px solid var(--el-border-color);
  transition: border-color var(--zw-duration-base);
}
.severity-card.active { border-left-color: var(--el-color-primary); }
.sev-card-red { border-left-color: var(--el-color-danger); }
.sev-card-yellow { border-left-color: var(--el-color-warning); }
.sev-card-info { border-left-color: var(--el-color-info); }
.sev-card-action { cursor: default; border-left-color: var(--el-color-primary); }
.sev-label {
  font-size: var(--zw-font-size-sm);
  color: var(--el-text-color-secondary);
}
.sev-count {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xl);
  font-weight: var(--zw-font-weight-semibold);
  font-variant-numeric: tabular-nums;
}
.sev-unit {
  margin-left: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  font-weight: normal;
  color: var(--el-text-color-secondary);
}
.sev-impact {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-sm);
  flex-wrap: wrap;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  flex-wrap: wrap;
}
.impact-amount {
  font-variant-numeric: tabular-nums;
  font-weight: var(--zw-font-weight-semibold);
  color: var(--el-color-danger);
}
.pagination-wrap {
  margin-top: var(--zw-space-sm-md);
  justify-content: flex-end;
}
.risk-detail { display: flex; flex-direction: column; gap: var(--zw-space-sm-md); }
.detail-head { display: flex; align-items: center; gap: var(--zw-space-sm); }
.detail-type { font-size: var(--zw-font-size-sm); color: var(--el-text-color-secondary); }
.detail-title {
  margin: 0;
  font-size: var(--zw-font-size-md);
  font-weight: var(--zw-font-weight-semibold);
  line-height: 1.5;
}
.reason-list { display: flex; flex-direction: column; gap: var(--zw-space-xs); }
.reason-row { display: flex; gap: var(--zw-space-sm); font-size: var(--zw-font-size-xs); }
.reason-key { color: var(--el-text-color-secondary); min-width: 90px; }
.reason-value { font-variant-numeric: tabular-nums; }
.handle-note {
  margin-left: var(--zw-space-sm);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
.rule-params {
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-regular);
  word-break: break-all;
}
.empty-text { font-size: var(--zw-font-size-xs); color: var(--el-text-color-placeholder); }
.drill-section {
  padding: var(--zw-space-sm);
  background-color: var(--el-fill-color-lighter);
  border-radius: var(--zw-radius-sm);
}
.drill-title {
  font-size: var(--zw-font-size-sm);
  font-weight: var(--zw-font-weight-semibold);
  margin-bottom: var(--zw-space-xs);
}
.drill-actions { display: flex; flex-wrap: wrap; gap: var(--zw-space-sm); }
.detail-actions { display: flex; flex-wrap: wrap; gap: var(--zw-space-sm); }
</style>
