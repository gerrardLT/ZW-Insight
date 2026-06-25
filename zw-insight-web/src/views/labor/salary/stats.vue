<template>
  <div class="salary-stats-container">
    <!-- 筛选栏 -->
    <el-card shadow="never" class="filter-card">
      <el-form :model="queryParams" inline>
        <el-form-item label="项目" required>
          <ProjectSelector v-model="queryParams.projectId" width="220px" @change="handleProjectChange" />
        </el-form-item>
        <el-form-item label="月份" required>
          <el-date-picker
            v-model="queryParams.month"
            type="month"
            value-format="YYYY-MM"
            placeholder="选择月份"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="班组">
          <el-input v-model="queryParams.teamName" placeholder="班组名称" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="工人姓名">
          <el-input v-model="queryParams.workerName" placeholder="工人姓名" clearable style="width: 130px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" :icon="Download" :disabled="!statsData" @click="handleExport">导出 Excel</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 同比环比数据卡片 -->
    <div v-if="compareData" class="compare-cards">
      <el-row :gutter="16">
        <el-col :span="8">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-label">本月薪资总额</div>
            <div class="stat-value">¥{{ formatAmount(compareData.currentAmount) }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-label">环比（较上月）</div>
            <div class="stat-value" :class="getRateClass(compareData.momRate)">
              <template v-if="compareData.momRate !== null">
                {{ compareData.momRate > 0 ? '+' : '' }}{{ compareData.momRate.toFixed(1) }}%
              </template>
              <template v-else>
                <span class="no-data-text">暂无数据</span>
              </template>
            </div>
            <div class="stat-sub">上月: ¥{{ formatAmount(compareData.previousMonthAmount) }}</div>
          </el-card>
        </el-col>
        <el-col :span="8">
          <el-card shadow="hover" class="stat-card">
            <div class="stat-label">同比（较去年同月）</div>
            <div class="stat-value" :class="getRateClass(compareData.yoyRate)">
              <template v-if="compareData.yoyRate !== null">
                {{ compareData.yoyRate > 0 ? '+' : '' }}{{ compareData.yoyRate.toFixed(1) }}%
              </template>
              <template v-else>
                <span class="no-data-text">暂无数据</span>
              </template>
            </div>
            <div class="stat-sub">去年同月: ¥{{ formatAmount(compareData.lastYearAmount) }}</div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 分类 Tab -->
    <el-card shadow="never" class="main-card">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="ALL" />
        <el-tab-pane label="自有劳务" name="FIXED" />
        <el-tab-pane label="零星用工" name="TEMPORARY" />
      </el-tabs>

      <!-- 汇总信息 -->
      <div v-if="statsData" class="summary-bar">
        <span>班组数: <strong>{{ statsData.teamCount }}</strong></span>
        <span>总人数: <strong>{{ statsData.totalHeadCount }}</strong></span>
        <span>应发合计: <strong>¥{{ formatAmount(statsData.totalPayable) }}</strong></span>
        <span>扣款合计: <strong>¥{{ formatAmount(statsData.totalDeduction) }}</strong></span>
        <span>实发合计: <strong>¥{{ formatAmount(statsData.totalActual) }}</strong></span>
      </div>

      <!-- 班组汇总表格 -->
      <el-table
        v-loading="loading"
        :data="filteredTeamList"
        border
        row-key="teamId"
        :expand-row-keys="expandedRows"
        @expand-change="handleExpandChange"
      >
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="detail-table-wrap">
              <el-table
                v-loading="row._detailLoading"
                :data="row._detailData || []"
                border
                size="small"
              >
                <el-table-column prop="workerName" label="姓名" width="100" />
                <el-table-column prop="idCardSuffix" label="身份证后四位" width="120" align="center" />
                <el-table-column prop="attendanceDays" label="出勤天数" width="100" align="center" />
                <el-table-column prop="overtimeHours" label="加班工时" width="100" align="center" />
                <el-table-column label="应发金额" width="120" align="right">
                  <template #default="{ row: detail }">¥{{ formatAmount(detail.payable) }}</template>
                </el-table-column>
                <el-table-column label="扣款金额" width="110" align="right">
                  <template #default="{ row: detail }">¥{{ formatAmount(detail.deduction) }}</template>
                </el-table-column>
                <el-table-column label="实发金额" width="120" align="right">
                  <template #default="{ row: detail }">¥{{ formatAmount(detail.actual) }}</template>
                </el-table-column>
              </el-table>
              <!-- 明细分页 -->
              <div v-if="row._detailTotal > 10" class="detail-pagination">
                <el-pagination
                  v-model:current-page="row._detailPage"
                  :page-size="10"
                  :total="row._detailTotal"
                  layout="total, prev, pager, next"
                  small
                  @current-change="(page: number) => loadTeamDetail(row, page)"
                />
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="teamName" label="班组名称" min-width="140" />
        <el-table-column prop="leaderName" label="班组长" width="100" />
        <el-table-column prop="headCount" label="人数" width="80" align="center" />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.orderType === 'FIXED' ? '' : 'warning'" size="small">
              {{ row.orderType === 'FIXED' ? '自有劳务' : '零星用工' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="应发总额" width="140" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.totalPayable) }}</template>
        </el-table-column>
        <el-table-column label="扣款合计" width="120" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.totalDeduction) }}</template>
        </el-table-column>
        <el-table-column label="实发总额" width="140" align="right">
          <template #default="{ row }">¥{{ formatAmount(row.totalActual) }}</template>
        </el-table-column>
      </el-table>

      <!-- 空数据状态 -->
      <el-empty
        v-if="!loading && searched && (!statsData || filteredTeamList.length === 0)"
        description="该月份暂无已审批的薪资数据"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import ProjectSelector from '@/components/ProjectSelector.vue'
import { getSalaryStats, getSalaryDetail, getSalaryCompare, exportSalaryExcel } from '@/api/labor'

// ==================== 类型定义 ====================
interface TeamSalaryVO {
  teamId: number
  teamName: string
  leaderName: string
  headCount: number
  totalPayable: number
  totalDeduction: number
  totalActual: number
  orderType: 'FIXED' | 'TEMPORARY'
  _detailLoading?: boolean
  _detailData?: any[]
  _detailPage?: number
  _detailTotal?: number
}

interface SalaryStatsSummary {
  projectId: number
  month: string
  teamCount: number
  totalHeadCount: number
  totalPayable: number
  totalDeduction: number
  totalActual: number
  fixedPayable: number
  temporaryPayable: number
  teamList: TeamSalaryVO[]
}

interface SalaryCompareVO {
  currentMonth: string
  currentAmount: number
  previousMonthAmount: number
  lastYearAmount: number
  momRate: number | null
  yoyRate: number | null
}

// ==================== 状态 ====================
const loading = ref(false)
const searched = ref(false)
const activeTab = ref('ALL')
const expandedRows = ref<number[]>([])

const queryParams = ref({
  projectId: undefined as number | undefined,
  month: '',
  teamName: '',
  workerName: ''
})

const statsData = ref<SalaryStatsSummary | null>(null)
const compareData = ref<SalaryCompareVO | null>(null)

// ==================== 计算属性 ====================
const filteredTeamList = computed(() => {
  if (!statsData.value) return []
  let list = statsData.value.teamList

  // 按 Tab 筛选
  if (activeTab.value === 'FIXED') {
    list = list.filter(t => t.orderType === 'FIXED')
  } else if (activeTab.value === 'TEMPORARY') {
    list = list.filter(t => t.orderType === 'TEMPORARY')
  }

  // 按班组名称筛选
  if (queryParams.value.teamName) {
    list = list.filter(t => t.teamName.includes(queryParams.value.teamName))
  }

  return list
})

// ==================== 方法 ====================
function formatAmount(val: number | undefined | null): string {
  if (val === undefined || val === null) return '0.00'
  return val.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

function getRateClass(rate: number | null): string {
  if (rate === null) return ''
  if (rate > 0) return 'rate-up'
  if (rate < 0) return 'rate-down'
  return ''
}

function handleProjectChange() {
  // 项目变更时清空已有数据
  statsData.value = null
  compareData.value = null
  searched.value = false
}

async function handleSearch() {
  if (!queryParams.value.projectId) {
    ElMessage.warning('请选择项目')
    return
  }
  if (!queryParams.value.month) {
    ElMessage.warning('请选择月份')
    return
  }

  loading.value = true
  searched.value = true
  expandedRows.value = []

  try {
    const [statsRes, compareRes] = await Promise.all([
      getSalaryStats({ projectId: queryParams.value.projectId, month: queryParams.value.month }),
      getSalaryCompare({ projectId: queryParams.value.projectId, month: queryParams.value.month })
    ]) as [any, any]

    statsData.value = statsRes.data || null
    compareData.value = compareRes.data || null
  } catch (e) {
    statsData.value = null
    compareData.value = null
  } finally {
    loading.value = false
  }
}

function handleReset() {
  queryParams.value = { projectId: undefined, month: '', teamName: '', workerName: '' }
  statsData.value = null
  compareData.value = null
  searched.value = false
  expandedRows.value = []
}

function handleTabChange() {
  expandedRows.value = []
}

async function handleExpandChange(row: TeamSalaryVO, expanded: boolean) {
  if (!expanded) {
    expandedRows.value = expandedRows.value.filter(id => id !== row.teamId)
    return
  }
  expandedRows.value.push(row.teamId)
  await loadTeamDetail(row, 1)
}

async function loadTeamDetail(row: TeamSalaryVO, page: number) {
  if (!queryParams.value.projectId || !queryParams.value.month) return

  row._detailLoading = true
  row._detailPage = page
  try {
    const res: any = await getSalaryDetail({
      projectId: queryParams.value.projectId,
      month: queryParams.value.month,
      teamId: row.teamId,
      page,
      size: 10
    })
    row._detailData = res.data?.records || []
    row._detailTotal = res.data?.total || 0
  } catch (e) {
    row._detailData = []
    row._detailTotal = 0
  } finally {
    row._detailLoading = false
  }
}

async function handleExport() {
  if (!queryParams.value.projectId || !queryParams.value.month) {
    ElMessage.warning('请先选择项目和月份')
    return
  }
  try {
    const blob = await exportSalaryExcel(queryParams.value.projectId, queryParams.value.month)
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `薪资统计_${queryParams.value.month}.xlsx`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败，请稍后重试')
  }
}
</script>

<style scoped>
.salary-stats-container {
  padding: 16px;
}

.filter-card {
  margin-bottom: 16px;
}

.compare-cards {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
  padding: 8px 0;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.stat-value.rate-up {
  color: #e6a23c;
}

.stat-value.rate-down {
  color: #67c23a;
}

.stat-value .no-data-text {
  font-size: 14px;
  color: #c0c4cc;
  font-weight: normal;
}

.stat-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.main-card {
  margin-bottom: 16px;
}

.summary-bar {
  display: flex;
  gap: 24px;
  padding: 12px 0;
  margin-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
  font-size: 14px;
  color: #606266;
}

.summary-bar strong {
  color: #303133;
}

.detail-table-wrap {
  padding: 12px 24px;
}

.detail-pagination {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}
</style>
