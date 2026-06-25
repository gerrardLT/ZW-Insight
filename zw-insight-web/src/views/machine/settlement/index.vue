<template>
  <div class="machine-settlement-container">
    <!-- 费用总览卡片 -->
    <el-row :gutter="16" class="summary-row">
      <el-col :span="8">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">已结算总额</div>
          <div class="summary-value">¥ {{ summary.totalSettledAmount?.toLocaleString() || '0' }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">已付款金额</div>
          <div class="summary-value">¥ {{ summary.totalPaidAmount?.toLocaleString() || '0' }}</div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="summary-card">
          <div class="summary-label">未付款金额</div>
          <div class="summary-value warning">¥ {{ summary.unpaidAmount?.toLocaleString() || '0' }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 列表区域 -->
    <el-card shadow="never">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目">
          <el-select
            v-model="queryParams.projectId"
            placeholder="请选择项目"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="item in projectList"
              :key="item.id"
              :label="item.projectName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 120px">
            <el-option label="草稿" :value="0" />
            <el-option label="审批中" :value="1" />
            <el-option label="已审批" :value="2" />
            <el-option label="已驳回" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="结算周期">
          <el-date-picker
            v-model="queryParams.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <div class="table-toolbar">
        <el-button type="primary" @click="handleCreate">新建结算单</el-button>
      </div>

      <el-table :data="tableData" v-loading="loading" border>
        <el-table-column prop="settlementCode" label="结算编号" width="160" />
        <el-table-column label="结算周期" width="200">
          <template #default="{ row }">
            {{ row.periodStart }} ~ {{ row.periodEnd }}
          </template>
        </el-table-column>
        <el-table-column prop="totalAmount" label="结算金额(元)" width="140" align="right">
          <template #default="{ row }">
            {{ row.totalAmount?.toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">查看</el-button>
            <el-button
              v-if="row.status === 0 || row.status === 3"
              link
              type="warning"
              @click="handleSubmit(row)"
            >
              提交审批
            </el-button>
            <el-button link type="success" @click="handleExport(row)">导出</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getMachineSettlementPage,
  submitMachineSettlement,
  getMachineSettlementSummary,
  exportMachineSettlement
} from '@/api/machine'
import { getProjectList } from '@/api/project'

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const projectList = ref<any[]>([])

const summary = ref({
  totalSettledAmount: 0,
  totalPaidAmount: 0,
  unpaidAmount: 0,
  settlementCount: 0
})

const queryParams = ref({
  pageNum: 1,
  pageSize: 10,
  projectId: undefined as number | undefined,
  status: undefined as number | undefined,
  dateRange: null as string[] | null
})

type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

const statusMap: Record<number, { label: string; type: TagType }> = {
  0: { label: '草稿', type: 'info' },
  1: { label: '审批中', type: 'primary' },
  2: { label: '已审批', type: 'success' },
  3: { label: '已驳回', type: 'danger' }
}

function statusLabel(status: number) {
  return statusMap[status]?.label || '未知'
}

function statusTagType(status: number): TagType {
  return statusMap[status]?.type || 'info'
}

async function loadData() {
  loading.value = true
  try {
    const params: any = {
      pageNum: queryParams.value.pageNum,
      pageSize: queryParams.value.pageSize,
      projectId: queryParams.value.projectId,
      status: queryParams.value.status
    }
    if (queryParams.value.dateRange && queryParams.value.dateRange.length === 2) {
      params.periodStart = queryParams.value.dateRange[0]
      params.periodEnd = queryParams.value.dateRange[1]
    }
    const res: any = await getMachineSettlementPage(params)
    tableData.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

async function loadSummary() {
  try {
    const params: any = {}
    if (queryParams.value.projectId) {
      params.projectId = queryParams.value.projectId
    }
    const res: any = await getMachineSettlementSummary(params)
    summary.value = res.data || {}
  } catch {
    // summary 加载失败不阻断页面
  }
}

async function loadProjects() {
  try {
    const res: any = await getProjectList()
    projectList.value = res.data || []
  } catch {
    // 项目列表加载失败不阻断页面
  }
}

function handleSearch() {
  queryParams.value.pageNum = 1
  loadData()
  loadSummary()
}

function handleReset() {
  queryParams.value = {
    pageNum: 1,
    pageSize: 10,
    projectId: undefined,
    status: undefined,
    dateRange: null
  }
  loadData()
  loadSummary()
}

function handleCreate() {
  router.push('/machine/settlement/create')
}

function handleView(row: any) {
  router.push(`/machine/settlement/detail/${row.id}`)
}

async function handleSubmit(row: any) {
  await ElMessageBox.confirm('确定要提交审批吗？提交后将进入审批流程。', '提交审批', { type: 'warning' })
  await submitMachineSettlement(row.id)
  ElMessage.success('提交成功')
  loadData()
}

async function handleExport(row: any) {
  try {
    const res: any = await exportMachineSettlement(row.id)
    const blob = new Blob([res], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `机械结算单_${row.settlementCode}.xlsx`
    link.click()
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('导出失败')
  }
}

onMounted(() => {
  loadProjects()
  loadData()
  loadSummary()
})
</script>

<style scoped>
.machine-settlement-container {
  padding: 16px;
}
.summary-row {
  margin-bottom: 16px;
}
.summary-card {
  text-align: center;
}
.summary-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}
.summary-value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}
.summary-value.warning {
  color: #e6a23c;
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
