<template>
  <div class="contract-container">
    <el-card shadow="never">
      <!-- 搜索区域 -->
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-select
            v-model="queryParams.projectId"
            placeholder="请选择项目"
            filterable
            remote
            :remote-method="searchProject"
            clearable
            style="width: 220px"
          >
            <el-option v-for="item in projectList" :key="item.id" :label="item.projectName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="审批中" value="SUBMITTED" />
            <el-option label="已生效" value="EFFECTIVE" />
            <el-option label="已结算" value="SETTLED" />
            <el-option label="已关闭" value="CLOSED" />
          </el-select>
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

      <!-- 操作栏 -->
      <div class="table-toolbar">
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增合同
        </el-button>
        <el-button @click="importVisible = true">批量导入</el-button>
        <el-button @click="exportVisible = true">导出</el-button>
      </div>

      <!-- 表格 -->
      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="contractCode" label="合同编号" width="140" />
        <el-table-column prop="projectName" label="所属项目" min-width="180" show-overflow-tooltip />
        <el-table-column prop="partyAName" label="甲方" width="150" show-overflow-tooltip />
        <el-table-column prop="contractAmount" label="合同金额" width="130" align="right">
          <template #default="{ row }">{{ formatMoney(row.contractAmount) }}</template>
        </el-table-column>
        <el-table-column prop="signingDate" label="签订日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="success" @click="handleSubmitContract(row)">提交</el-button>
            <el-button v-if="row.status === 'DRAFT'" link type="danger" @click="handleDelete(row)">删除</el-button>
            <PrintButton
              link
              :show-icon="false"
              business-type="CONTRACT"
              :business-data-id="row.id"
              :variables="row"
            />
          </template>
        </el-table-column>
      </el-table>

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

    <!-- 合同金额汇总面板 -->
    <stat-chart-panel
      ref="summaryPanelRef"
      class="stat-panel"
      title="合同金额汇总（按状态分布）"
      :fetch-data="fetchAmountSummary"
      :build-option="buildAmountSummaryOption"
      empty-text="暂无合同金额数据"
    />

    <batch-import-dialog
      v-model:visible="importVisible"
      module-code="CONTRACT"
      :project-id="queryParams.projectId"
      @success="loadData"
    />

    <async-export-dialog
      v-model:visible="exportVisible"
      module-code="CONTRACT"
      :params="queryParams.projectId ? { projectId: queryParams.projectId } : undefined"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getContractPage, deleteContract, submitContract, getContractAmountSummary } from '@/api/contract'
import { getProjectList } from '@/api/project'
import { toWan } from '@/utils/chart-format'
import PrintButton from '@/components/PrintButton.vue'
import BatchImportDialog from '@/components/BatchImportDialog.vue'
import AsyncExportDialog from '@/components/AsyncExportDialog.vue'
import StatChartPanel from '@/components/StatChartPanel.vue'

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const projectList = ref<any[]>([])
const importVisible = ref(false)
const exportVisible = ref(false)

const queryParams = ref({
  page: 1,
  size: 10,
  projectId: undefined as number | undefined,
  status: ''
})

const statusMap: Record<string, { label: string; type: string }> = {
  DRAFT: { label: '草稿', type: 'info' },
  SUBMITTED: { label: '审批中', type: 'warning' },
  EFFECTIVE: { label: '已生效', type: 'success' },
  SETTLED: { label: '已结算', type: 'primary' },
  CLOSED: { label: '已关闭', type: 'danger' }
}

function getStatusLabel(status: string) {
  return statusMap[status]?.label || status
}

function getStatusType(status: string) {
  return (statusMap[status]?.type || 'info') as any
}

function formatMoney(val: number) {
  if (!val && val !== 0) return '-'
  return val.toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

async function searchProject(query: string) {
  const res: any = await getProjectList({ projectName: query })
  projectList.value = res.data || []
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await getContractPage(queryParams.value)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  queryParams.value.page = 1
  loadData()
  summaryPanelRef.value?.reload()
}

function handleReset() {
  queryParams.value = { page: 1, size: 10, projectId: undefined, status: '' }
  loadData()
  summaryPanelRef.value?.reload()
}

function handleAdd() {
  router.push('/contract/create')
}

function handleEdit(row: any) {
  router.push(`/contract/edit/${row.id}`)
}

function handleView(row: any) {
  // 只读查看（2026-08-14 P0 修复盲点 3：原实现与 handleEdit 同跳编辑页，
  // 非草稿合同也可进编辑表单尝试保存）
  router.push(`/contract/edit/${row.id}?mode=view`)
}

async function handleSubmitContract(row: any) {
  await ElMessageBox.confirm('确定要提交该合同吗？', '提示', { type: 'warning' })
  await submitContract(row.id)
  ElMessage.success('提交成功')
  loadData()
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm('确定要删除该合同吗？', '提示', { type: 'warning' })
  await deleteContract(row.id)
  ElMessage.success('删除成功')
  loadData()
}

// ================= 合同金额汇总面板 =================
const summaryPanelRef = ref<InstanceType<typeof StatChartPanel>>()

async function fetchAmountSummary() {
  if (!queryParams.value.projectId) throw new Error('请先选择项目后查看合同金额汇总')
  const res: any = await getContractAmountSummary(queryParams.value.projectId)
  return res.data
}

function buildAmountSummaryOption(data: any) {
  const breakdown = data?.statusBreakdown || []
  if (!breakdown.length) return null
  return {
    color: ['#3370ff', '#00b42a', '#ff7d00', '#f53f3f', '#722ed1', '#38bdf8'],
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => `${p.name}<br/>合同金额：${toWan(Number(p.value))} 万元（${p.percent}%）`
    },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      itemStyle: { borderRadius: 8, borderWidth: 2 },
      label: { show: true, formatter: '{b}' },
      data: breakdown.map((s: any) => ({ name: getStatusLabel(s.status), value: Number(s.amount) || 0 }))
    }]
  }
}

onMounted(() => {
  loadData()
  searchProject('')
})
</script>

<style scoped>
.contract-container {
  padding: 16px;
}
.stat-panel {
  margin-top: 16px;
}
.table-toolbar {
  margin-bottom: 16px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
