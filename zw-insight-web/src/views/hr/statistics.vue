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
            <el-icon class="stat-icon stat-icon--info"><User /></el-icon>
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
            <el-icon class="stat-icon stat-icon--success"><CirclePlus /></el-icon>
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
            <el-icon class="stat-icon stat-icon--danger"><Remove /></el-icon>
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
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { getHrStatisticsOverview } from '@/api/hr'
import { useAppStore } from '@/stores/app'
import { pickChartTheme, applyChartTheme } from '@/constants/chart-theme'

const appStore = useAppStore()

// 汇总数据（原始响应缓存：主题切换时不重复请求，直接重绘）
const overview = ref<any>({})
let lastStatData: any = null

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
  try {
    const res: any = await getHrStatisticsOverview()
    const data = res.data || {}
    overview.value = {
      totalActive: data.totalActive || 0,
      monthlyEntry: data.monthlyEntry || 0,
      monthlyResign: data.monthlyResign || 0
    }
    lastStatData = data
    renderAll(data)
  } catch (e: any) {
    // C-20-7 修复（2026-08-14 P2 补测）：不静默处理，接口失败显式提示
    //（对齐 dashboard/index.vue loadStats 范式）
    ElMessage.error('加载人事统计数据失败：' + (e?.message || '接口异常'))
  }
}

function renderAll(data: any) {
  renderDeptChart(data.byDept || [])
  renderPostChart(data.byPost || [])
  renderSeniorityChart(data.bySeniority || [])
  renderTrendChart(data.monthlyTrend || [])
}

// 主题切换即时重绘（canvas 颜色固化，需以新主题重建 option）
watch(() => appStore.isDark, () => {
  if (lastStatData) renderAll(lastStatData)
})

/** 容器已有实例则复用，避免主题切换重绘时重复 init */
function ensureChart(current: echarts.ECharts | null, el: HTMLElement | undefined): echarts.ECharts | null {
  if (!el) return current
  if (current && !current.isDisposed()) return current
  return echarts.init(el)
}

function renderDeptChart(byDept: Array<{ deptName: string; count: number }>) {
  deptChart = ensureChart(deptChart, deptChartRef.value)
  if (!deptChart) return
  const theme = pickChartTheme(appStore.isDark)
  const names = byDept.map(item => item.deptName)
  const counts = byDept.map(item => item.count)
  deptChart.setOption(applyChartTheme({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: names, axisLabel: { rotate: names.length > 6 ? 30 : 0 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar',
      data: counts,
      itemStyle: { color: theme.highlight, borderRadius: [2, 2, 0, 0] },
      barMaxWidth: 40
    }]
  }, theme), true)
}

function renderPostChart(byPost: Array<{ postName: string; count: number }>) {
  postChart = ensureChart(postChart, postChartRef.value)
  if (!postChart) return
  const theme = pickChartTheme(appStore.isDark)
  const data = byPost.map(item => ({ name: item.postName, value: item.count }))
  postChart.setOption(applyChartTheme({
    tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie',
      radius: ['35%', '65%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 2, borderColor: theme.surface.card, borderWidth: 2 },
      label: { show: true, formatter: '{b}: {c}', color: theme.text.secondary },
      data
    }]
  }, theme), true)
}

function renderSeniorityChart(bySeniority: Array<{ range: string; count: number }>) {
  seniorityChart = ensureChart(seniorityChart, seniorityChartRef.value)
  if (!seniorityChart) return
  const theme = pickChartTheme(appStore.isDark)
  const names = bySeniority.map(item => item.range)
  const counts = bySeniority.map(item => item.count)
  const s = theme.semantic
  seniorityChart.setOption(applyChartTheme({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: names },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar',
      data: counts,
      itemStyle: {
        color: (params: any) => {
          const colors = [s.success, s.info, s.warning, s.danger]
          return colors[params.dataIndex % colors.length]
        },
        borderRadius: [2, 2, 0, 0]
      },
      barMaxWidth: 50
    }]
  }, theme), true)
}

function renderTrendChart(monthlyTrend: Array<{ month: string; entryCount: number; resignCount: number }>) {
  trendChart = ensureChart(trendChart, trendChartRef.value)
  if (!trendChart) return
  const theme = pickChartTheme(appStore.isDark)
  const months = monthlyTrend.map(item => item.month)
  const entryData = monthlyTrend.map(item => item.entryCount)
  const resignData = monthlyTrend.map(item => item.resignCount)
  trendChart.setOption(applyChartTheme({
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
        itemStyle: { color: theme.semantic.success },
        areaStyle: { opacity: 0.1 }
      },
      {
        name: '离职',
        type: 'line',
        data: resignData,
        smooth: true,
        itemStyle: { color: theme.semantic.danger },
        areaStyle: { opacity: 0.1 }
      }
    ]
  }, theme), true)
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
  color: var(--zw-text-secondary);
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: var(--zw-text-primary);
}
.stat-icon {
  font-size: 48px;
  opacity: 0.8;
}
.stat-icon--info {
  color: var(--zw-info);
}
.stat-icon--success {
  color: var(--zw-success);
}
.stat-icon--danger {
  color: var(--zw-danger);
}
.chart-row {
  margin-bottom: 16px;
}
.chart-box {
  height: 320px;
}
</style>
