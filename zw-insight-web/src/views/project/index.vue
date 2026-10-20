<template>
  <div class="project-container">
    <!-- S3.1 首屏：结论/异常优先，再进入筛选与明细 -->
    <WorkbenchSummaryCards
      :cards="workbenchCards"
      :loading="workbenchLoading"
      @select="handleWorkbenchCard"
    />
    <div v-if="workbenchError" class="workbench-error" data-testid="project-workbench-error">
      <span>项目摘要加载失败：{{ workbenchError }}</span>
      <el-button link type="primary" @click="loadWorkbench()">重试</el-button>
    </div>
    <div class="view-bar">
      <el-radio-group v-model="queryParams.view" size="small" @change="handleViewChange">
        <el-radio-button label="ALL">全部项目</el-radio-button>
        <el-radio-button label="MINE">我的项目</el-radio-button>
        <el-radio-button label="WARNING">预警项目</el-radio-button>
        <el-radio-button label="OVERDUE">逾期应收</el-radio-button>
      </el-radio-group>
      <el-button link type="primary" @click="router.push('/workflow/approval?tab=todo')">待我审批 →</el-button>
    </div>

    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目名称">
          <el-input v-model="queryParams.projectName" placeholder="请输入项目名称" clearable @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="(s, key) in PROJECT_STATUS" :key="key" :label="s.label" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目类型">
          <el-input v-model="queryParams.projectType" placeholder="请输入项目类型" clearable />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>搜索
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 项目组合看板 -->
    <stat-chart-panel
      class="filter-card"
      title="项目组合看板（状态×合同金额）"
      :fetch-data="fetchPortfolio"
      :build-option="buildPortfolioOption"
      empty-text="暂无项目数据"
    />

    <!-- 列表区 -->
    <el-card shadow="never" class="table-card">
      <ZwListToolbar
        :columns="projectColumns"
        :visible="columnVisible"
        @update:visible="setVisible"
        @reset="resetColumns"
        @refresh="loadData"
      >
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增项目
        </el-button>
        <el-button @click="importVisible = true">批量导入</el-button>
        <el-button type="danger" plain :disabled="!selectedRows.length" @click="handleBatchDelete">
          <el-icon><Delete /></el-icon>批量删除（{{ selectedRows.length }}）
        </el-button>
      </ZwListToolbar>
    
      <!-- 首屏骨架屏：loading 期间显示骨架，完成后渲染表格（感知性能优化 S1.4） -->
      <el-skeleton :loading="loading" :rows="5" animated>
        <el-table :data="tableData" @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="44" :selectable="isDraftRow" />
            <el-table-column v-if="columnVisible[0]" prop="projectCode" label="项目编号" width="140" />
            <el-table-column v-if="columnVisible[1]" prop="projectName" label="项目名称" min-width="200" show-overflow-tooltip />
            <el-table-column v-if="columnVisible[2]" prop="projectNature" label="项目性质" width="100" />
            <el-table-column v-if="columnVisible[3]" prop="projectType" label="项目类型" width="100" />
            <el-table-column v-if="columnVisible[4]" prop="ownerCompanyName" label="建设单位" width="150" show-overflow-tooltip />
            <el-table-column v-if="columnVisible[5]" prop="signingCompanyName" label="施工单位" width="150" show-overflow-tooltip />
            <el-table-column v-if="columnVisible[6]" label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ getStatusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column v-if="columnVisible[7]" label="健康度" width="90" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.health" :type="healthTagType(row.health)" size="small">{{ healthLabel(row.health) }}</el-tag>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column v-if="columnVisible[8]" label="风险" width="110" align="center">
              <template #default="{ row }">
                <span v-if="(row.redCount || 0) + (row.yellowCount || 0) > 0">
                  红 {{ row.redCount || 0 }} / 黄 {{ row.yellowCount || 0 }}
                </span>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column v-if="columnVisible[9]" label="逾期应收" width="150" align="right">
              <template #default="{ row }">
                <span v-if="Number(row.overdueReceivableAmount) > 0">
                  {{ formatWan(row.overdueReceivableAmount) }} 万 · {{ row.overdueReceivableCount || 0 }} 笔
                </span>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column v-if="columnVisible[10]" prop="createdAt" label="创建时间" width="170" />
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="handleView(row)">查看</el-button>
                <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="handleEdit(row)">编辑</el-button>
                <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmit(row)">提交</el-button>
                <el-button v-if="row.status === 'FILED'" link type="warning" @click="handleWithdraw(row)">撤回</el-button>
                <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="handleDelete(row)">删除</el-button>
                <el-button v-if="row.status === 'COMPLETED'" link type="warning" @click="handleClose(row)">结项</el-button>
              </template>
            </el-table-column>
          </el-table>
      </el-skeleton>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.page"
          v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <batch-import-dialog v-model:visible="importVisible" module-code="PROJECT" @success="loadData" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProjectPage, getProjectWorkbench, deleteProject, batchDeleteProjects, submitProject, withdrawProject, closeProject, getProjectCloseCheck, getProjectPortfolio } from '@/api/project'
import BatchImportDialog from '@/components/BatchImportDialog.vue'
import StatChartPanel from '@/components/StatChartPanel.vue'
import ColumnSettingPopover from '@/components/ColumnSettingPopover.vue'
import ZwListToolbar from '@/components/ZwListToolbar.vue'
import WorkbenchSummaryCards from '@/components/WorkbenchSummaryCards.vue'
import { useColumnSetting } from '@/composables/useColumnSetting'
import { useRowNavigation } from '@/composables/useRowNavigation'
import { PROJECT_STATUS, labelOf, typeOf } from '@/constants/biz-labels'
import { formatWan, toWan } from '@/utils/chart-format'

// 列显隐配置（S2.1）：按 project-table 持久化 localStorage
const projectColumns = [
  { key: 'projectCode', label: '项目编号' },
  { key: 'projectName', label: '项目名称' },
  { key: 'projectNature', label: '项目性质' },
  { key: 'projectType', label: '项目类型' },
  { key: 'ownerCompanyName', label: '建设单位' },
  { key: 'signingCompanyName', label: '施工单位' },
  { key: 'status', label: '状态' },
  { key: 'health', label: '健康度' },
  { key: 'riskCount', label: '风险' },
  { key: 'overdueReceivable', label: '逾期应收' },
  { key: 'createdAt', label: '创建时间' },
]
const { visible: columnVisible, setVisible, reset: resetColumns } = useColumnSetting('project-table', projectColumns)

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const importVisible = ref(false)
const selectedRows = ref<any[]>([])
const workbench = ref<any>(null)
const workbenchLoading = ref(false)
const workbenchError = ref('')

type ProjectView = 'ALL' | 'MINE' | 'RISK' | 'WARNING' | 'OVERDUE'
const queryParams = ref<{
  page: number
  size: number
  projectName: string
  status: string
  projectType: string
  view: ProjectView
}>({
  page: 1,
  size: 10,
  projectName: '',
  status: '',
  projectType: '',
  view: 'ALL'
})

function getStatusLabel(status: string) {
  return labelOf(PROJECT_STATUS, status)
}

function getStatusType(status: string) {
  return typeOf(PROJECT_STATUS, status)
}

// 请求序号：翻页/切视图时丢弃迟到的旧响应，避免旧页数据覆盖当前页（聚合列被清空）
let listSeq = 0
let workbenchSeq = 0

async function loadData() {
  const seq = ++listSeq
  loading.value = true
  try {
    const res: any = await getProjectPage(queryParams.value)
    if (seq !== listSeq) return
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
    rowNav.reset() // 数据刷新时重置行导航状态
    // 摘要与当前页健康度独立加载：失败不阻断列表，由 workbenchError 提供重试
    void loadWorkbench(tableData.value.map((row: any) => String(row.id)).join(','))
  } catch (e: any) {
    ElMessage.error('加载项目列表失败：' + (e?.message || '接口异常'))
  } finally {
    loading.value = false
  }
}

/**
 * 项目台账首屏聚合（S3.1）：摘要卡 + 当前页项目健康度。
 * ids 为空时传 -1，后端返回全量摘要但不返回健康度行，避免一次拉全量项目。
 */
async function loadWorkbench(ids?: string) {
  const projectIds = ids ?? tableData.value.map((row: any) => String(row.id)).join(',')
  workbenchLoading.value = true
  workbenchError.value = ''
  try {
    const res: any = await getProjectWorkbench({ projectIds: projectIds || '-1' })
    workbench.value = res.data || null
    mergeWorkbenchRows(res.data?.projects || [])
  } catch (e: any) {
    workbenchError.value = e?.message || '接口异常'
  } finally {
    workbenchLoading.value = false
  }
}

/** 将健康度/风险/逾期应收按项目 ID 合并到当前页行；无匹配时清空旧值，避免翻页残留 */
function mergeWorkbenchRows(rows: any[]) {
  const byId = new Map(rows.map((row: any) => [String(row.projectId), row]))
  tableData.value = tableData.value.map((row: any) => {
    const extra = byId.get(String(row.id))
    return extra
      ? { ...row, ...extra }
      : {
          ...row,
          health: undefined,
          redCount: 0,
          yellowCount: 0,
          overdueReceivableAmount: 0,
          overdueReceivableCount: 0
        }
  })
}

const workbenchCards = computed(() => {
  const summary = workbench.value?.summary || {}
  return [
    { key: 'mine', label: '我的项目', value: Number(summary.mine || 0), sub: `全部 ${Number(summary.total || 0)} 个` },
    { key: 'construction', label: '在建项目', value: Number(summary.inConstruction || 0), sub: '施工中' },
    {
      key: 'warning',
      label: '预警项目',
      value: Number(summary.warning || 0),
      sub: `活跃风险 ${Number(summary.riskOpen || 0)} 项`,
      alert: Number(summary.warning || 0) > 0
    },
    {
      key: 'overdue',
      label: '逾期应收',
      value: Number(summary.overdueReceivableCount || 0),
      sub: `${formatWan(summary.overdueReceivableAmount)} 万`,
      alert: Number(summary.overdueReceivableCount || 0) > 0
    }
  ]
})

function handleViewChange() {
  queryParams.value.page = 1
  loadData()
}

function handleWorkbenchCard(card: { key: string }) {
  if (card.key === 'construction') {
    queryParams.value.view = 'ALL'
    queryParams.value.status = 'CONSTRUCTION'
  } else if (card.key === 'mine') {
    queryParams.value.view = 'MINE'
    queryParams.value.status = ''
  } else if (card.key === 'warning') {
    queryParams.value.view = 'WARNING'
    queryParams.value.status = ''
  } else if (card.key === 'overdue') {
    queryParams.value.view = 'OVERDUE'
    queryParams.value.status = ''
  } else {
    queryParams.value.view = 'ALL'
    queryParams.value.status = ''
  }
  queryParams.value.page = 1
  loadData()
}

function healthLabel(health: string) {
  if (health === 'RED') return '高风险'
  if (health === 'YELLOW') return '关注'
  return '正常'
}

function healthTagType(health: string) {
  if (health === 'RED') return 'danger'
  if (health === 'YELLOW') return 'warning'
  return 'success'
}

// 行级键盘导航：composable 化（3b 去重；Enter=查看详情）
const rowNav = useRowNavigation(tableData, (row) => handleView(row))

function handleSearch() {
  queryParams.value.page = 1
  loadData()
}

function handleReset() {
  queryParams.value = { page: 1, size: 10, projectName: '', status: '', projectType: '', view: 'ALL' }
  loadData()
}

function handleAdd() {
  router.push('/project/create')
}

function handleEdit(row: any) {
  router.push(`/project/edit/${row.id}`)
}

function handleView(row: any) {
  router.push(`/project/detail/${row.id}`)
}

async function handleSubmit(row: any) {
  await ElMessageBox.confirm('确定要提交该项目吗？提交后不可修改。', '提示', { type: 'warning' })
  await submitProject(row.id)
  ElMessage.success('提交成功')
  loadData()
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除该项目吗？', '提示', { type: 'warning' })
  await deleteProject(row.id)
  ElMessage.success('删除成功')
  loadData()
}

function handleSelectionChange(rows: any[]) {
  selectedRows.value = rows
}

/** 活跃态（非终态、非审批中间态）允许编辑，修改关键字段落台账（A5） */
function isEditable(status: string) {
  return !['CLOSED', 'LOST', 'TERMINATED', 'CLOSING', 'TERMINATING'].includes(status)
}

async function handleWithdraw(row: any) {
  await ElMessageBox.confirm('确定撤回立项吗？项目将回退为草稿供编辑。', '撤回立项', { type: 'warning' })
  await withdrawProject(row.id)
  ElMessage.success('撤回成功')
  loadData()
}

/** 仅草稿状态的行可勾选（与后端“仅 DRAFT 可删”校验对齐） */
function isDraftRow(row: any) {
  return row.status === 'DRAFT'
}

/** 批量删除草稿项目（后端整体单事务，任一失败全部回滚） */
async function handleBatchDelete() {
  const rows = selectedRows.value
  if (!rows.length) return
  await ElMessageBox.confirm(`确定批量删除选中的 ${rows.length} 个草稿项目吗？删除后不可恢复。`, '提示', { type: 'warning' })
  await batchDeleteProjects(rows.map((r) => r.id))
  ElMessage.success('批量删除成功')
  selectedRows.value = []
  loadData()
}

async function handleClose(row: any) {
  // 先预检结项条件
  const res: any = await getProjectCloseCheck(row.id)
  const result = res.data || {}
  if (!result.allPassed) {
    const reasons = (result.failedReasons || []).join('；') || '结项条件不满足'
    ElMessageBox.alert(reasons, '无法结项', { type: 'warning' })
    return
  }
  await ElMessageBox.confirm('确定发起项目结项审批吗？提交后项目进入结项审批中，审批通过后自动关闭。', '提示', { type: 'warning' })
  await closeProject(row.id)
  ElMessage.success('结项审批已发起')
  loadData()
}

onMounted(() => {
  rowNav.install()
  loadData()
})

// 卸载时移除全局键盘监听（防内存泄漏，S2.2）
onBeforeUnmount(() => {
  rowNav.uninstall()
})

// ================= 项目组合看板 =================
async function fetchPortfolio() {
  const res: any = await getProjectPortfolio()
  return res.data
}

function buildPortfolioOption(data: any) {
  if (!data?.statusList?.length) return null
  return {
    title: {
      text: `项目总数：${data.totalProjectCount ?? 0}　合同总额：${toWan(Number(data.totalContractAmount) || 0)} 万元`,
      left: 'center', top: 0, textStyle: { fontSize: 13 }
    },
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => {
        const item = data.statusList[p.dataIndex]
        return `${p.name}<br/>项目数：${item.count}<br/>合同金额：${toWan(Number(item.contractAmount) || 0)} 万元<br/>预算金额：${toWan(Number(item.budgetAmount) || 0)} 万元<br/>累计产值：${toWan(Number(item.cumulativeOutput) || 0)} 万元`
      }
    },
    legend: { bottom: 0 },
    series: [{
      name: '合同金额分布', type: 'pie', radius: ['35%', '60%'], center: ['50%', '55%'],
      label: { formatter: '{b}: {d}%' },
      data: data.statusList.map((s: any) => ({ name: getStatusLabel(s.status), value: toWan(Number(s.contractAmount) || 0), count: s.count }))
    }]
  }
}
</script>

<style scoped>
.project-container {
  padding: var(--zw-content-padding);
}

.filter-card {
  margin-bottom: var(--zw-space-md);
}

.view-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-sm);
  margin-bottom: var(--zw-space-sm);
}

.workbench-error {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  margin-bottom: var(--zw-space-sm);
  padding: var(--zw-space-xs) var(--zw-space-sm);
  border: 1px solid var(--zw-danger);
  border-radius: var(--zw-radius-sm);
  color: var(--zw-danger);
  font-size: var(--zw-font-size-sm);
  background: var(--zw-danger-light);
}

.filter-card :deep(.el-form--inline) {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zw-space-xs) var(--zw-space-sm);
}

.filter-card :deep(.el-form-item) {
  margin-bottom: 0;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-bottom: var(--zw-space-md);
}

.pagination-wrap {
  margin-top: var(--zw-space-md);
  display: flex;
  justify-content: flex-end;
}
</style>
