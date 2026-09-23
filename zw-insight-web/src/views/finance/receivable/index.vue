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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getReceivablePage, getReceivableAging, type Receivable, type ReceivableAging, type AgingBucket } from '@/api/receivable'
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
</style>
