<template>
  <div class="hr-statistics-container">
    <!-- 汇总卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="stat-label">在职总人数</span>
              <span class="stat-value">{{ overview.totalActive || 0 }}</span>
            </div>
            <el-icon class="stat-icon" style="color: #409eff"><User /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="stat-label">本月入职</span>
              <span class="stat-value">{{ overview.monthlyEntry || 0 }}</span>
            </div>
            <el-icon class="stat-icon" style="color: #67c23a"><CirclePlus /></el-icon>
          </div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-info">
              <span class="stat-label">本月离职</span>
              <span class="stat-value">{{ overview.monthlyResign || 0 }}</span>
            </div>
            <el-icon class="stat-icon" style="color: #f56c6c"><Remove /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 - 第一行 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>部门人数分布</span>
          </template>
          <div ref="deptChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>岗位人数分布</span>
          </template>
          <div ref="postChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区域 - 第二行 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>工龄段分布</span>
          </template>
          <div ref="seniorityChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span>入离职趋势（近12个月）</span>
          </template>
          <div ref="trendChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'
import { getHrStatisticsOverview } from '@/api/hr'

// 汇总数据
const overview = ref<any>({})

// 图表 DOM 引用
const deptChartRef = ref<HTMLElement>()
const postChartRef = ref<HTMLElement>()
const seniorityChartRef = ref<HTMLElement>()
const trendChartRef = ref<HTMLElement>()

// 图表实例
let deptChart: echarts.ECharts | null = null
let postChart: echarts.ECharts | null = null
let seniorityChart: echarts.ECharts | null = null
let trendChart: echarts.ECharts | null = null

async function loadData() {
  const res: any = await getHrStatisticsOverview()
  const data = res.data || {}
  overview.value = {
    totalActive: data.totalActive || 0,
    monthlyEntry: data.monthlyEntry || 0,
    monthlyResign: data.monthlyResign || 0
  }
  renderDeptChart(data.byDept || [])
  renderPostChart(data.byPost || [])
  renderSeniorityChart(data.bySeniority || [])
  renderTrendChart(data.monthlyTrend || [])
}

function renderDeptChart(byDept: Array<{ deptName: string; count: number }>) {
  if (!deptChartRef.value) return
  deptChart = echarts.init(deptChartRef.value)
  const names = byDept.map(item => item.deptName)
  const counts = byDept.map(item => item.count)
  deptChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: names, axisLabel: { rotate: names.length > 6 ? 30 : 0 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar',
      data: counts,
      itemStyle: { color: '#409eff', borderRadius: [4, 4, 0, 0] },
      barMaxWidth: 40
    }]
  })
}

function renderPostChart(byPost: Array<{ postName: string; count: number }>) {
  if (!postChartRef.value) return
  postChart = echarts.init(postChartRef.value)
  const data = byPost.map(item => ({ name: item.postName, value: item.count }))
  postChart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['35%', '65%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}: {c}' },
      data
    }]
  })
}

function renderSeniorityChart(bySeniority: Array<{ range: string; count: number }>) {
  if (!seniorityChartRef.value) return
  seniorityChart = echarts.init(seniorityChartRef.value)
  const names = bySeniority.map(item => item.range)
  const counts = bySeniority.map(item => item.count)
  seniorityChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: names },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar',
      data: counts,
      itemStyle: {
        color: (params: any) => {
          const colors = ['#67c23a', '#409eff', '#e6a23c', '#f56c6c']
          return colors[params.dataIndex % colors.length]
        },
        borderRadius: [4, 4, 0, 0]
      },
      barMaxWidth: 50
    }]
  })
}

function renderTrendChart(monthlyTrend: Array<{ month: string; entryCount: number; resignCount: number }>) {
  if (!trendChartRef.value) return
  trendChart = echarts.init(trendChartRef.value)
  const months = monthlyTrend.map(item => item.month)
  const entryData = monthlyTrend.map(item => item.entryCount)
  const resignData = monthlyTrend.map(item => item.resignCount)
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['入职', '离职'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: months, boundaryGap: false },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      {
        name: '入职',
        type: 'line',
        data: entryData,
        smooth: true,
        itemStyle: { color: '#67c23a' },
        areaStyle: { color: 'rgba(103, 194, 58, 0.1)' }
      },
      {
        name: '离职',
        type: 'line',
        data: resignData,
        smooth: true,
        itemStyle: { color: '#f56c6c' },
        areaStyle: { color: 'rgba(245, 108, 108, 0.1)' }
      }
    ]
  })
}

function handleResize() {
  deptChart?.resize()
  postChart?.resize()
  seniorityChart?.resize()
  trendChart?.resize()
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  deptChart?.dispose()
  postChart?.dispose()
  seniorityChart?.dispose()
  trendChart?.dispose()
})
</script>

<style scoped>
.hr-statistics-container {
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
