<template>
  <div class="dashboard-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="stat-label">项目总数</span>
              <span class="stat-value">{{ stats.projectCount || 0 }}</span>
            </div>
            <el-icon class="stat-icon" style="color: #409eff"><Briefcase /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="stat-label">合同总额(万)</span>
              <span class="stat-value">{{ formatWan(stats.contractAmount) }}</span>
            </div>
            <el-icon class="stat-icon" style="color: #67c23a"><Notebook /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="stat-label">已收款(万)</span>
              <span class="stat-value">{{ formatWan(stats.receivedAmount) }}</span>
            </div>
            <el-icon class="stat-icon" style="color: #e6a23c"><WalletFilled /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="stat-label">垫资(万)</span>
              <span class="stat-value">{{ formatWan(stats.advanceAmount) }}</span>
            </div>
            <el-icon class="stat-icon" style="color: #f56c6c"><Warning /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>项目状态分布</span>
          </template>
          <div ref="pieChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>收支对比</span>
          </template>
          <div ref="barChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { getDashboardStats, getProjectStatusDistribution, getIncomeExpenseComparison } from '@/api/dashboard'

const stats = ref<any>({})
const pieChartRef = ref<HTMLElement>()
const barChartRef = ref<HTMLElement>()
let pieChart: echarts.ECharts | null = null
let barChart: echarts.ECharts | null = null

function formatWan(val: number) {
  if (!val && val !== 0) return '0'
  return (val / 10000).toFixed(1)
}

async function loadStats() {
  try {
    const res: any = await getDashboardStats()
    stats.value = res.data || {}
  } catch {
    // 接口未就绪时使用空数据
    stats.value = {}
  }
}

async function loadPieChart() {
  if (!pieChartRef.value) return
  pieChart = echarts.init(pieChartRef.value)

  try {
    const res: any = await getProjectStatusDistribution()
    const data = res.data || []
    pieChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {c}' },
        data
      }]
    })
  } catch {
    // 接口未就绪时显示空图表
    pieChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        data: [
          { name: '草稿', value: 0 },
          { name: '施工中', value: 0 },
          { name: '已竣工', value: 0 }
        ]
      }]
    })
  }
}

async function loadBarChart() {
  if (!barChartRef.value) return
  barChart = echarts.init(barChartRef.value)

  try {
    const res: any = await getIncomeExpenseComparison()
    const data = res.data || { months: [], income: [], expense: [] }
    barChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['收入', '支出'] },
      xAxis: { type: 'category', data: data.months || [] },
      yAxis: { type: 'value', axisLabel: { formatter: '{value} 万' } },
      series: [
        { name: '收入', type: 'bar', data: data.income || [], itemStyle: { color: '#67c23a' } },
        { name: '支出', type: 'bar', data: data.expense || [], itemStyle: { color: '#f56c6c' } }
      ]
    })
  } catch {
    barChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['收入', '支出'] },
      xAxis: { type: 'category', data: [] },
      yAxis: { type: 'value' },
      series: [
        { name: '收入', type: 'bar', data: [] },
        { name: '支出', type: 'bar', data: [] }
      ]
    })
  }
}

function handleResize() {
  pieChart?.resize()
  barChart?.resize()
}

onMounted(() => {
  loadStats()
  loadPieChart()
  loadBarChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  pieChart?.dispose()
  barChart?.dispose()
})
</script>

<style scoped>
.dashboard-container {
  padding: 16px;
}
.stat-row {
  margin-bottom: 16px;
}
.stat-card {
  cursor: default;
}
.stat-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.stat-info {
  display: flex;
  flex-direction: column;
}
.stat-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}
.stat-icon {
  font-size: 48px;
  opacity: 0.8;
}
.chart-row {
  margin-bottom: 16px;
}
.chart-box {
  height: 320px;
}
</style>
