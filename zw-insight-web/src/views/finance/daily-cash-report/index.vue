<template>
  <div class="daily-cash-report-container">
    <!-- 顶部：日期选择 + 生成按钮 -->
    <el-card shadow="never">
      <div class="toolbar">
        <div class="date-picker">
          <span class="label">报告日期：</span>
          <el-date-picker v-model="reportDate" type="date" value-format="YYYY-MM-DD" :clearable="false" />
          <el-button type="primary" style="margin-left: 12px" @click="handleGenerate" :loading="generating">
            生成/刷新日报
          </el-button>
        </div>
        <div class="trend-switch">
          <span class="label">趋势天数：</span>
          <el-select v-model="trendDays" style="width: 100px" @change="loadTrend">
            <el-option :value="7" label="近7日" />
            <el-option :value="14" label="近14日" />
            <el-option :value="30" label="近30日" />
          </el-select>
        </div>
      </div>
    </el-card>

    <!-- 核心指标卡 -->
    <el-row :gutter="16" style="margin-top: 16px" v-loading="loading">
      <el-col :span="6">
        <el-card shadow="never" class="metric-card">
          <template #header><span>可用头寸（总余额）</span></template>
          <div class="metric-main">{{ formatAmount(report.totalBalance) }}</div>
          <div class="metric-sub">
            基本户 {{ formatAmount(report.basicBalance) }}
            <el-divider direction="vertical" />
            一般户 {{ formatAmount(report.generalBalance) }}
            <el-divider direction="vertical" />
            专户 {{ formatAmount(report.specialBalance) }}
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="metric-card">
          <template #header><span>当日净头寸</span></template>
          <div class="metric-main" :class="(report.netPosition || 0) < 0 ? 'metric-negative' : 'metric-positive'">
            {{ formatAmount(report.netPosition) }}
          </div>
          <div class="metric-sub">
            流入 {{ formatAmount(report.inflowAmount) }}
            <el-divider direction="vertical" />
            流出 {{ formatAmount(report.outflowAmount) }}
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="metric-card">
          <template #header><span>当日大额支出</span></template>
          <div class="metric-main" :class="(report.largeOutflowCount || 0) > 0 ? 'metric-negative' : ''">
            {{ report.largeOutflowCount ?? 0 }} 笔
          </div>
          <div class="metric-sub">
            金额 {{ formatAmount(report.largeOutflowAmount) }}（门槛 50 万）
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="metric-card">
          <template #header><span>统计账户数</span></template>
          <div class="metric-main">{{ report.accountCount ?? 0 }}</div>
          <div class="metric-sub">覆盖基本户/一般户/专户</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势折线 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>近 {{ trendDays }} 日资金头寸趋势</span></template>
      <div ref="chartRef" style="height: 320px"></div>
    </el-card>

    <!-- 大额支出明细 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>当日大额支出明细（下钻）</span></template>
      <el-table :data="largeOutflows" border>
        <el-table-column prop="flowDate" label="日期" width="110" align="center" />
        <el-table-column label="金额" width="150" align="right">
          <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="counterpartyName" label="对方单位" min-width="180" show-overflow-tooltip />
        <el-table-column prop="description" label="摘要" min-width="180" show-overflow-tooltip />
        <el-table-column label="勾稽状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.reconciled === 1 ? 'success' : 'info'" size="small">
              {{ row.reconciled === 1 ? '已勾稽' : '未勾稽' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  generateDailyReport,
  getDailyReport,
  getDailyReportTrend,
  getLargeOutflows,
  type DailyCashReport
} from '@/api/daily-cash-report'
import type { BankFlow } from '@/api/bank-flow'

const loading = ref(false)
const generating = ref(false)
const report = ref<Partial<DailyCashReport>>({})
const trendDays = ref(7)
const largeOutflows = ref<BankFlow[]>([])
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const reportDate = ref(new Date().toISOString().slice(0, 10))

function formatAmount(value?: number) {
  return value != null ? `¥${Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2 })}` : '—'
}

async function loadReport() {
  loading.value = true
  try {
    const res = await getDailyReport(reportDate.value)
    report.value = res.data.data || ({} as DailyCashReport)
  } catch {
    report.value = {} as DailyCashReport
  } finally {
    loading.value = false
  }
}

async function loadLargeOutflows() {
  const res = await getLargeOutflows(reportDate.value)
  largeOutflows.value = res.data.data || []
}

async function loadTrend() {
  const res = await getDailyReportTrend(trendDays.value)
  const list = res.data.data || []
  await nextTick()
  renderChart(list)
}

function renderChart(list: DailyCashReport[]) {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['总余额', '当日流入', '当日流出', '净头寸'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: list.map(r => r.reportDate) },
    yAxis: { type: 'value' },
    series: [
      { name: '总余额', type: 'line', smooth: true, data: list.map(r => r.totalBalance || 0) },
      { name: '当日流入', type: 'bar', data: list.map(r => r.inflowAmount || 0) },
      { name: '当日流出', type: 'bar', data: list.map(r => r.outflowAmount || 0) },
      { name: '净头寸', type: 'line', smooth: true, data: list.map(r => r.netPosition || 0) }
    ]
  })
}

async function handleGenerate() {
  generating.value = true
  try {
    await generateDailyReport(reportDate.value)
    ElMessage.success('日报已生成')
    await loadReport()
    await loadLargeOutflows()
    await loadTrend()
  } finally {
    generating.value = false
  }
}

function handleResize() {
  chart?.resize()
}

onMounted(async () => {
  await loadReport()
  await loadLargeOutflows()
  await loadTrend()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.daily-cash-report-container {
  padding: 16px;
}
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}
.date-picker,
.trend-switch {
  display: flex;
  align-items: center;
}
.label {
  margin-right: 8px;
  color: var(--el-text-color-secondary);
}
.metric-card :deep(.el-card__header) {
  padding: 12px 16px;
}
.metric-main {
  font-size: 26px;
  font-weight: 700;
  line-height: 1.4;
}
.metric-sub {
  margin-top: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.metric-positive {
  color: var(--el-color-success);
}
.metric-negative {
  color: var(--el-color-danger);
}
</style>
