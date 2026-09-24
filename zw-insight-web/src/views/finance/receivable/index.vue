<template>
  <div class="receivable-container">
    <!-- 账龄分析（驾驶舱 V1 §10 回款风险：异常冒出来，逾期项目排前） -->
    <el-card shadow="never" class="aging-card">
      <template #header>
        <div class="card-header">
          <span>应收账龄分析</span>
          <div class="aging-totals">
            <el-tag type="info" size="small">未结清合计 {{ formatAmount(aging.totalOpen) }}</el-tag>
            <el-tag :type="Number(aging.totalOverdue) > 0 ? 'danger' : 'success'" size="small">
              逾期合计 {{ formatAmount(aging.totalOverdue) }}
            </el-tag>
          </div>
        </div>
      </template>
      <el-table :data="aging.projects" v-loading="agingLoading" border size="small">
        <el-table-column prop="projectName" label="项目" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || `项目#${row.projectId}` }}</template>
        </el-table-column>
        <el-table-column label="未结清余额" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.openBalance) }}</template>
        </el-table-column>
        <el-table-column label="逾期余额" width="130" align="right">
          <template #default="{ row }">
            <span :class="{ 'overdue-amount': Number(row.overdueBalance) > 0 }">{{ formatAmount(row.overdueBalance) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最长逾期" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.maxOverdueDays > 0" :type="overdueTagType(row.maxOverdueDays)" size="small">
              {{ row.maxOverdueDays }} 天
            </el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column v-for="b in bucketDefs" :key="b.key" :label="b.label" width="110" align="right">
          <template #default="{ row }">{{ formatAmount(row.buckets?.[b.key]) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="filterByProject(row.projectId)">查看台账</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 应收台账（结算审批自动生成，回款登记自动核销；只读，事实源为单据） -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>应收台账</span>
          <el-alert type="info" :closable="false" class="ledger-tip"
            title="台账由结算审批自动生成、回款登记自动 FIFO 核销，不支持手工增删（保证与单据一致）" />
        </div>
      </template>
      <div class="filter-bar">
        <el-select
          v-model="query.projectId"
          placeholder="全部项目"
          filterable
          remote
          :remote-method="searchProject"
          clearable
          style="width: 220px"
        >
          <el-option v-for="p in projectList" :key="p.id" :label="p.projectName" :value="p.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px; margin-left: var(--zw-space-sm)">
          <el-option label="未结清" value="OPEN" />
          <el-option label="已结清" value="CLOSED" />
        </el-select>
        <el-button type="primary" style="margin-left: var(--zw-space-sm)" @click="handleSearch">查询</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="projectName" label="项目" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || `项目#${row.projectId}` }}</template>
        </el-table-column>
        <el-table-column label="来源" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ sourceLabel(row.sourceType) }} #{{ row.sourceId }}</template>
        </el-table-column>
        <el-table-column label="应收金额" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.receivableAmount) }}</template>
        </el-table-column>
        <el-table-column label="已核销" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.writtenOffAmount) }}</template>
        </el-table-column>
        <el-table-column label="未结余额" width="130" align="right">
          <template #default="{ row }">
            <span :class="{ 'overdue-amount': isOverdue(row as Receivable) }">{{ formatAmount(balanceOf(row as Receivable)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="到期日" width="120" align="center">
          <template #default="{ row }">
            <span>{{ row.dueDate }}</span>
            <el-tag v-if="isOverdue(row as Receivable)" :type="overdueTagType(overdueDays(row as Receivable))" size="small" class="overdue-tag">
              逾期{{ overdueDays(row as Receivable) }}天
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'OPEN' ? 'warning' : 'success'" size="small">
              {{ row.status === 'OPEN' ? '未结清' : '已结清' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <!-- §10 下钻入口：八级链中五级需人工登记，故在台账行直接提供入口 -->
        <el-table-column label="操作" width="90" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDrill(row as Receivable)">
              下钻
            </el-button>
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

    <!-- §10 下钻 8 级链：项目 → 应收款 → 对应工程节点 → 应收日期 → 实际申请日期
         → 甲方审核状态 → 负责人 → 下一步动作。
         未登记的级显示「未登记」（不用默认词冒充），并可直接在本抽屉补登。 -->
    <el-drawer v-model="drillVisible" :title="`应收下钻·${drillData?.projectName || '未登记项目'}`"
      size="620px" append-to-body>
      <div v-loading="drillLoading" class="drill-panel">
        <template v-if="drillData">
          <!-- 汇总：余额与逾期（真实计算值，不估算） -->
          <div class="drill-summary">
            <span>应收 <b>{{ formatAmount(drillData.receivableAmount) }}</b></span>
            <span>已核销 <b>{{ formatAmount(drillData.writtenOffAmount) }}</b></span>
            <span>未结清 <b>{{ formatAmount(drillData.openBalance) }}</b></span>
            <el-tag v-if="drillData.overdue" type="danger" size="small">
              逾期 {{ drillData.overdueDays }} 天·{{ bucketLabel(drillData.agingBucket) }}
            </el-tag>
            <el-tag v-else type="success" size="small">未逾期</el-tag>
            <el-tag v-if="drillData.ownerStayDays != null" type="info" size="small">
              甲方停留 {{ drillData.ownerStayDays }} 天
            </el-tag>
          </div>

          <!-- 八级链 -->
          <div class="drill-chain">
            <div v-for="level in drillData.chain" :key="level.level" class="chain-item">
              <span class="chain-level">{{ level.level }}</span>
              <span class="chain-label">{{ level.label }}</span>
              <span class="chain-value" :class="{ 'is-unregistered': !level.registered }">
                {{ level.registered ? formatLevelValue(level) : '未登记' }}
              </span>
            </div>
          </div>

          <el-alert v-if="!drillData.complete" type="warning" :closable="false" show-icon
            class="drill-alert"
            :title="`八级链尚有 ${drillData.unregisteredCount} 级未登记（工程节点/申请日期/甲方审核状态/负责人/下一步动作系统内无来源单据，需人工补登）`" />

          <!-- 人工登记表单：工程节点无自动回填数据源（结算单无节点字段、产值报告与结算单无外键），
               故不预填推算值；留空即表示未登记 -->
          <el-divider content-position="left">补登下钻信息</el-divider>
          <el-form :model="drillForm" label-width="110px" size="small" class="drill-form">
            <el-form-item label="对应工程节点">
              <el-input v-model="drillForm.milestoneNode" maxlength="200" show-word-limit
                placeholder="如：主体结构封顶 / 第 3 期产值（留空=未登记）" />
            </el-form-item>
            <el-form-item label="实际申请日期">
              <el-date-picker v-model="drillForm.applyDate" type="date" value-format="YYYY-MM-DD"
                placeholder="向甲方提交结算/付款申请的日期" style="width: 100%" />
            </el-form-item>
            <el-form-item label="甲方审核状态">
              <el-select v-model="drillForm.ownerReviewStatus" placeholder="选择状态"
                style="width: 100%">
                <el-option v-for="opt in reviewStatusOptions" :key="opt.value"
                  :label="opt.label" :value="opt.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="甲方审核日期">
              <el-date-picker v-model="drillForm.ownerReviewDate" type="date" value-format="YYYY-MM-DD"
                placeholder="甲方完成审核的日期" style="width: 100%" />
            </el-form-item>
            <el-form-item label="负责人">
              <el-input v-model="drillForm.ownerName" maxlength="50"
                placeholder="催收负责人姓名（留空=未登记）" />
            </el-form-item>
            <el-form-item label="下一步动作">
              <el-input v-model="drillForm.nextAction" type="textarea" :rows="2" maxlength="500"
                show-word-limit placeholder="如：本周内找甲方财务对账（留空=未登记）" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="drillSaving" @click="saveDrill">保存登记</el-button>
              <span class="form-hint">
                文本框清空后保存 = 撤回该项登记；日期一经登记不可清空（只能改为正确日期）
              </span>
            </el-form-item>
          </el-form>
        </template>
        <el-empty v-else-if="!drillLoading" description="未获取到下钻数据" :image-size="60" />
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getReceivablePage, getReceivableAging, getReceivableDrill, updateReceivableDrill,
  type Receivable, type ReceivableAging, type AgingBucket,
  type ReceivableDrillChain, type ReceivableDrillLevel, type ReceivableDrillInfoRequest
} from '@/api/receivable'
import { getProjectList } from '@/api/project'

const bucketDefs: { key: AgingBucket; label: string }[] = [
  { key: 'NOT_DUE', label: '未到期' },
  { key: 'D0_30', label: '逾期0-30天' },
  { key: 'D31_60', label: '31-60天' },
  { key: 'D61_90', label: '61-90天' },
  { key: 'OVER_90', label: '90天以上' }
]

const loading = ref(false)
const agingLoading = ref(false)
const tableData = ref<Receivable[]>([])
const total = ref(0)
const aging = ref<ReceivableAging>({ totalOpen: 0, totalOverdue: 0, projects: [] })
const projectList = ref<any[]>([])
const query = ref<{ page: number; size: number; projectId?: number; status?: string }>({ page: 1, size: 10 })

function formatAmount(value?: number) {
  return value != null ? `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` : '—'
}

function sourceLabel(type: string) {
  return { SETTLEMENT: '项目结算', RETENTION: '质保金' }[type] || type
}

function balanceOf(row: Receivable) {
  return Number(row.receivableAmount || 0) - Number(row.writtenOffAmount || 0)
}

function overdueDays(row: Receivable) {
  if (!row.dueDate) return 0
  const due = new Date(row.dueDate).getTime()
  const days = Math.floor((Date.now() - due) / 86400000)
  return days > 0 ? days : 0
}

function isOverdue(row: Receivable) {
  return row.status === 'OPEN' && overdueDays(row) > 0
}

function overdueTagType(days: number): 'warning' | 'danger' {
  return days > 90 ? 'danger' : 'warning'
}

async function loadPage() {
  loading.value = true
  try {
    const res = await getReceivablePage({
      page: query.value.page,
      size: query.value.size,
      projectId: query.value.projectId || undefined,
      status: query.value.status || undefined
    })
    tableData.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadAging() {
  agingLoading.value = true
  try {
    const res = await getReceivableAging(query.value.projectId || undefined)
    aging.value = res.data.data || { totalOpen: 0, totalOverdue: 0, projects: [] }
  } finally {
    agingLoading.value = false
  }
}

function handleSearch() {
  query.value.page = 1
  loadPage()
  loadAging()
}

function filterByProject(projectId: number) {
  query.value.projectId = projectId
  handleSearch()
}

async function searchProject(keyword: string) {
  const res: any = await getProjectList({ projectName: keyword })
  projectList.value = res.data || []
}

// ==================== §10 下钻 8 级链（V2026_69）====================
const drillVisible = ref(false)
const drillLoading = ref(false)
const drillSaving = ref(false)
const drillData = ref<ReceivableDrillChain | null>(null)
const drillForm = ref<ReceivableDrillInfoRequest>({})

/**
 * 甲方审核状态可选项：值域与后端 BizReceivable.REVIEW_STATUSES 一致；
 * 额外提供「未登记（清空）」= 空串，保存后即撤回该项登记（并连带清审核日期）。
 */
const reviewStatusOptions = [
  { value: '', label: '未登记（清空）' },
  { value: 'SUBMITTED', label: '已提交甲方' },
  { value: 'UNDER_REVIEW', label: '甲方审核中' },
  { value: 'CONFIRMED', label: '甲方已确认' },
  { value: 'DISPUTED', label: '甲方有异议' }
]

function bucketLabel(bucket?: string | null): string {
  if (!bucket) return ''
  return bucketDefs.find(b => b.key === bucket)?.label || bucket
}

/** 八级链的值展示：应收款转万元，其余（日期/文本）原样呈现 */
function formatLevelValue(level: ReceivableDrillLevel): string {
  if (level.value == null) return '—'
  if (level.label === '应收款') return formatAmount(Number(level.value))
  return String(level.value)
}

async function openDrill(row: Receivable) {
  drillVisible.value = true
  drillLoading.value = true
  drillData.value = null
  drillForm.value = {}
  try {
    const res: any = await getReceivableDrill(Number(row.id))
    drillData.value = res?.data || null
    // 表单回填已登记值；未登记项留空（不预填推算值，也不把 null 当空串以外的东西）
    const info = drillData.value?.drillInfo || {}
    drillForm.value = {
      milestoneNode: info.milestoneNode ?? '',
      applyDate: info.applyDate ?? '',
      ownerReviewStatus: info.ownerReviewStatus ?? '',
      ownerReviewDate: info.ownerReviewDate ?? '',
      ownerId: info.ownerId ?? undefined,
      ownerName: info.ownerName ?? '',
      nextAction: info.nextAction ?? ''
    }
  } catch (e: any) {
    ElMessage.error('加载应收下钻链失败：' + (e?.message || '接口异常'))
  } finally {
    drillLoading.value = false
  }
}

/**
 * 保存登记。语义与后端一致：空串 = 清空该项（撤回登记）。
 * 日期为空串时不传（后端日期字段 null = 不修改，不支持清空）。
 */
async function saveDrill() {
  if (!drillData.value) return
  drillSaving.value = true
  try {
    const f = drillForm.value
    const payload: ReceivableDrillInfoRequest = {
      milestoneNode: f.milestoneNode ?? '',
      ownerReviewStatus: (f.ownerReviewStatus ?? '') as ReceivableDrillInfoRequest['ownerReviewStatus'],
      ownerName: f.ownerName ?? '',
      nextAction: f.nextAction ?? ''
    }
    // 日期只在有值时传（传空串会被后端当成非法日期格式）
    if (f.applyDate) payload.applyDate = f.applyDate
    if (f.ownerReviewDate) payload.ownerReviewDate = f.ownerReviewDate
    if (f.ownerId != null) payload.ownerId = f.ownerId

    await updateReceivableDrill(drillData.value.receivableId, payload)
    ElMessage.success('下钻信息已保存')
    // 重拉下钻链与台账（负责人/审核状态可能已在列表展示）
    const refreshed: any = await getReceivableDrill(drillData.value.receivableId)
    drillData.value = refreshed?.data || null
    await loadPage()
  } catch (e: any) {
    // 失败必须显式告知（包括非法审核状态的 400），不静默当作已保存
    ElMessage.error('保存下钻信息失败：' + (e?.message || '接口异常'))
  } finally {
    drillSaving.value = false
  }
}

onMounted(() => {
  loadPage()
  loadAging()
  searchProject('')
})
</script>

<style scoped>
.receivable-container {
  padding: var(--zw-space-md);
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-md);
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-sm);
  flex-wrap: wrap;
}
.aging-totals {
  display: flex;
  gap: var(--zw-space-sm);
}
.ledger-tip {
  padding: var(--zw-space-xs) var(--zw-space-sm);
}
.filter-bar {
  display: flex;
  align-items: center;
  margin-bottom: var(--zw-space-sm-md);
}
.overdue-amount {
  color: var(--el-color-danger);
  font-weight: 600;
}
.overdue-tag {
  margin-left: var(--zw-space-xs);
}

/* §10 下钻 8 级链抽屉 */
.drill-panel {
  min-height: 200px;
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-sm-md);
}
.drill-summary {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zw-space-sm-md);
  font-size: var(--zw-font-size-sm);
  color: var(--el-text-color-secondary);
}
.drill-summary b {
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
}
.drill-chain {
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-xs);
}
.chain-item {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  padding: var(--zw-space-xs) var(--zw-space-sm);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--zw-radius-sm);
}
.chain-level {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  border-radius: 50%;
  background-color: var(--el-fill-color);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
.chain-label {
  flex-shrink: 0;
  width: 110px;
  font-size: var(--zw-font-size-sm);
  color: var(--el-text-color-secondary);
}
.chain-value {
  flex: 1;
  min-width: 0;
  font-size: var(--zw-font-size-sm);
  font-variant-numeric: tabular-nums;
  word-break: break-all;
}
/* 未登记：置灰 + 斜体（与“已登记但有值”区分，不用默认词冒充） */
.chain-value.is-unregistered {
  color: var(--el-text-color-placeholder);
  font-style: italic;
}
.drill-alert { margin-top: var(--zw-space-xs); }
.form-hint {
  margin-left: var(--zw-space-sm);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
</style>
