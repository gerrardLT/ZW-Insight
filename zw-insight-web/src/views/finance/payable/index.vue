<template>
  <div class="payable-container">
    <!-- 账龄分析（对称应收：逾期项目排前，异常冒出来） -->
    <el-card shadow="never" class="aging-card">
      <template #header>
        <div class="card-header">
          <span>应付账龄分析</span>
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

    <!-- 应付台账（支出结算自动生成，付款申请自动核销；只读，事实源为单据） -->
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>应付台账</span>
          <el-alert type="info" :closable="false" class="ledger-tip"
            title="台账由支出结算审批自动生成、付款申请生效自动 FIFO 核销，不支持手工增删（保证与单据一致）" />
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
        <el-select v-model="query.contractCategory" placeholder="全部类别" clearable style="width: 150px; margin-left: var(--zw-space-sm)">
          <el-option v-for="(label, key) in CONTRACT_CATEGORY" :key="key" :label="label" :value="key" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px; margin-left: var(--zw-space-sm)">
          <el-option label="未结清" value="OPEN" />
          <el-option label="已结清" value="CLOSED" />
        </el-select>
        <el-button type="primary" style="margin-left: var(--zw-space-sm)" @click="handleSearch">查询</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border show-summary :summary-method="pageSummary">
        <el-table-column prop="projectName" label="项目" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || `项目#${row.projectId}` }}</template>
        </el-table-column>
        <el-table-column label="类别" width="140" align="center">
          <template #default="{ row }">{{ CONTRACT_CATEGORY[row.contractCategory] || row.contractCategory }}</template>
        </el-table-column>
        <el-table-column prop="supplierName" label="供应商/分包方" width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.supplierName || '—' }}</template>
        </el-table-column>
        <el-table-column label="来源" width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ sourceLabel(row.sourceType) }} #{{ row.sourceId }}</template>
        </el-table-column>
        <el-table-column label="应付金额" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.payableAmount) }}</template>
        </el-table-column>
        <el-table-column label="已核销" width="130" align="right">
          <template #default="{ row }">{{ formatAmount(row.paidAmount) }}</template>
        </el-table-column>
        <el-table-column label="未结余额" width="130" align="right">
          <template #default="{ row }">
            <span :class="{ 'overdue-amount': isOverdue(row as Payable) }">{{ formatAmount(balanceOf(row as Payable)) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="到期日" width="150" align="center">
          <template #default="{ row }">
            <span>{{ row.dueDate }}</span>
            <el-tag v-if="isOverdue(row as Payable)" :type="overdueTagType(overdueDays(row as Payable))" size="small" class="overdue-tag">
              逾期{{ overdueDays(row as Payable) }}天
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
      </el-table>
      <div class="sum-note">本页合计（非全量）</div>

      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        style="margin-top: var(--zw-space-md); justify-content: flex-end; display: flex"
        @size-change="loadData"
        @current-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getPayablePage, getPayableAging, type Payable, type PayableAging } from '@/api/payable'
import { getProjectList } from '@/api/project'
import { CONTRACT_CATEGORY } from '@/constants/biz-labels'

const route = useRoute()

interface ProjectOption { id: number; projectName: string }

const loading = ref(false)
const agingLoading = ref(false)
const tableData = ref<Payable[]>([])
const total = ref(0)
const aging = ref<PayableAging>({ totalOpen: 0, totalOverdue: 0, projects: [] })
const projectList = ref<ProjectOption[]>([])

const bucketDefs = [
  { key: 'NOT_DUE', label: '未到期' },
  { key: 'D0_30', label: '0-30 天' },
  { key: 'D31_60', label: '31-60 天' },
  { key: 'D61_90', label: '61-90 天' },
  { key: 'OVER_90', label: '90 天以上' }
]

const query = ref({ page: 1, size: 10, projectId: undefined as number | string | undefined, status: '', contractCategory: '' })

const SOURCE_LABELS: Record<string, string> = {
  SUBCONTRACT_SETTLEMENT: '分包结算',
  PURCHASE_SETTLEMENT: '采购结算',
  LABOR_SETTLEMENT: '劳务结算',
  MACHINE_SETTLEMENT: '机械结算',
  BACKFILL: '迁移回填'
}
function sourceLabel(t: string) {
  return SOURCE_LABELS[t] ?? t
}

function formatAmount(v: unknown): string {
  const n = Number(v)
  return Number.isFinite(n) ? n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) : '—'
}
function balanceOf(row: Payable): number {
  return Number(row.payableAmount || 0) - Number(row.paidAmount || 0)
}
function overdueDays(row: Payable): number {
  if (row.status !== 'OPEN' || !row.dueDate) return 0
  const days = Math.floor((Date.now() - new Date(row.dueDate).getTime()) / 86400000)
  return days > 0 ? days : 0
}
function isOverdue(row: Payable): boolean {
  return overdueDays(row) > 0
}
function overdueTagType(days: number): 'warning' | 'danger' {
  return days > 90 ? 'danger' : 'warning'
}

/** 本页合计（非全量）：应付金额/已核销/未结余额三列 */
function pageSummary({ columns, data }: { columns: Array<{ property?: string; label?: string }>; data: Payable[] }) {
  const sums: string[] = ['本页合计']
  columns.forEach((col, i) => {
    if (i === 0) return
    const prop = col.property || ''
    if (prop === 'payableAmount' || prop === 'paidAmount') {
      sums[i] = formatAmount(data.reduce((s, r) => s + Number((r as unknown as Record<string, unknown>)[prop] || 0), 0))
    } else if (col.label === '未结余额') {
      sums[i] = formatAmount(data.reduce((s, r) => s + balanceOf(r), 0))
    } else {
      sums[i] = ''
    }
  })
  return sums
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getPayablePage(query.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    ElMessage.error('应付台账加载失败，请重试')
  } finally {
    loading.value = false
  }
}

async function loadAging() {
  agingLoading.value = true
  try {
    const res: any = await getPayableAging(query.value.projectId)
    aging.value = res.data || { totalOpen: 0, totalOverdue: 0, projects: [] }
  } catch {
    // 账龄加载失败不阻断台账列表（独立失败独立提示的既有纪律）
  } finally {
    agingLoading.value = false
  }
}

function handleSearch() {
  query.value.page = 1
  loadData()
  loadAging()
}

function filterByProject(projectId: number) {
  query.value.projectId = projectId
  handleSearch()
}

async function searchProject(keyword?: string) {
  const res: any = await getProjectList({ projectName: keyword || '' })
  projectList.value = res.data || []
}

onMounted(() => {
  const qPid = route.query.projectId != null ? String(route.query.projectId).trim() : ''
  if (qPid && qPid !== '0') {
    query.value.projectId = qPid
  }
  searchProject()
  loadData()
  loadAging()
})
</script>

<style scoped>
.payable-container { padding: var(--zw-content-padding); }
.aging-card { margin-bottom: var(--zw-space-md); }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.aging-totals { display: flex; gap: var(--zw-space-sm); }
.filter-bar { display: flex; align-items: center; margin-bottom: var(--zw-space-md); }
.ledger-tip { padding: var(--zw-space-2xs) var(--zw-space-sm); }
.overdue-amount { color: var(--el-color-danger); font-weight: 600; }
.overdue-tag { margin-left: var(--zw-space-2xs); }
.sum-note { padding: var(--zw-space-2xs) var(--zw-space-sm); font-size: var(--zw-font-size-xs); color: var(--el-text-color-secondary); }
</style>
