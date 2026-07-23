<template>
  <div class="dashboard-container">
    <!-- 欢迎区 -->
    <div class="welcome-banner">
      <div class="welcome-text">
        <h2>{{ greeting }}，{{ userName }} 👋</h2>
        <p>{{ todayText }}，祝您工作顺利。</p>
      </div>
      <div class="welcome-actions">
        <el-button type="primary" @click="$router.push('/project/create')">
          <el-icon><Plus /></el-icon>新建项目
        </el-button>
        <el-button @click="$router.push('/project-dashboard')">
          <el-icon><DataAnalysis /></el-icon>项目看板
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stat-grid">
      <div class="stat-card" v-for="item in statCards" :key="item.key">
        <div class="stat-icon-wrap" :style="{ background: item.bg, color: item.color }">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-label">{{ item.label }}</span>
          <span class="stat-value stat-number">{{ item.value }}</span>
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <el-row :gutter="16" class="chart-row">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span class="chart-title">项目状态分布</span>
          </template>
          <div ref="pieChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>
            <span class="chart-title">收支对比</span>
          </template>
          <div ref="barChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { getDashboardStats, getProjectStatusDistribution, getIncomeExpenseComparison } from '@/api/dashboard'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const stats = ref<any>({})
const pieChartRef = ref<HTMLElement>()
const barChartRef = ref<HTMLElement>()
let pieChart: echarts.ECharts | null = null
let barChart: echarts.ECharts | null = null

const userName = computed(
  () => userStore.userInfo?.realName || userStore.userInfo?.name || '管理员'
)

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() => {
  const d = new Date()
  const week = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日 ${week[d.getDay()]}`
})

function formatWan(val: number) {
  if (!val && val !== 0) return '0'
  return (val / 10000).toFixed(1)
}

const statCards = computed(() => [
  { key: 'project', label: '项目总数', value: stats.value.projectCount || 0, icon: 'Briefcase', color: '#3370ff', bg: 'rgba(51,112,255,0.12)' },
  { key: 'contract', label: '合同总额(万)', value: formatWan(stats.value.contractAmount), icon: 'Notebook', color: '#00b42a', bg: 'rgba(0,180,42,0.12)' },
  { key: 'received', label: '已收款(万)', value: formatWan(stats.value.receivedAmount), icon: 'WalletFilled', color: '#ff7d00', bg: 'rgba(255,125,0,0.12)' },
  { key: 'advance', label: '垫资(万)', value: formatWan(stats.value.advanceAmount), icon: 'Warning', color: '#f53f3f', bg: 'rgba(245,63,63,0.12)' }
])

async function loadStats() {
  try {
    const res: any = await getDashboardStats()
    stats.value = res.data || {}
  } catch (e: any) {
    // 不静默处理：显式提示错误，同时置空避免页面卡死
    stats.value = {}
    ElMessage.error('加载统计数据失败：' + (e?.message || '接口异常'))
  }
}

async function loadPieChart() {
  if (!pieChartRef.value) return
  pieChart = echarts.init(pieChartRef.value)

  try {
    const res: any = await getProjectStatusDistribution()
    const data = res.data || []
    pieChart.setOption({
      color: ['#3370ff', '#00b42a', '#ff7d00', '#f53f3f', '#722ed1', '#38bdf8'],
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: ['45%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: { borderRadius: 8, borderColor: 'var(--zw-bg-card)', borderWidth: 2 },
        label: { show: true, formatter: '{b}: {c}' },
        data
      }]
    })
  } catch (e: any) {
    // 不静默处理：显式提示错误，同时展空图避免区域空白
    ElMessage.error('加载项目状态分布失败：' + (e?.message || '接口异常'))
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
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: data.months || [] },
      yAxis: { type: 'value', axisLabel: { formatter: '{value} 万' } },
      series: [
        { name: '收入', type: 'bar', barMaxWidth: 28, itemStyle: { color: '#3370ff', borderRadius: [4, 4, 0, 0] }, data: data.income || [] },
        { name: '支出', type: 'bar', barMaxWidth: 28, itemStyle: { color: '#f76560', borderRadius: [4, 4, 0, 0] }, data: data.expense || [] }
      ]
    })
  } catch (e: any) {
    // 不静默处理：显式提示错误，同时展空图避免区域空白
    ElMessage.error('加载收支对比失败：' + (e?.message || '接口异常'))
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
  padding: var(--zw-content-padding);
}

/* 欢迎区 */
.welcome-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 28px;
  margin-bottom: var(--zw-space-md);
  border-radius: var(--zw-radius-lg);
  background: linear-gradient(120deg, #1e2a5e 0%, #3370ff 100%);
  color: #fff;
  overflow: hidden;
  position: relative;
}

.welcome-banner::after {
  content: '';
  position: absolute;
  right: -40px;
  top: -60px;
  width: 220px;
  height: 220px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
}

.welcome-text h2 {
  font-size: var(--zw-font-size-2xl);
  font-weight: var(--zw-font-weight-bold);
  margin-bottom: 6px;
}

.welcome-text p {
  font-size: var(--zw-font-size-base);
  color: rgba(255, 255, 255, 0.85);
}

.welcome-actions {
  display: flex;
  gap: var(--zw-space-sm);
  position: relative;
  z-index: 1;
}

.welcome-actions :deep(.el-button) {
  border: none;
}

.welcome-actions :deep(.el-button:not(.el-button--primary)) {
  background: rgba(255, 255, 255, 0.16);
  color: #fff;
}

.welcome-actions :deep(.el-button--primary) {
  background: #fff;
  color: var(--zw-brand);
}

/* 统计卡片 */
.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--zw-space-md);
  margin-bottom: var(--zw-space-md);
}

.stat-card {
  display: flex;
  align-items: center;
  gap: var(--zw-space-md);
  padding: 20px;
  background: var(--zw-bg-card);
  border-radius: var(--zw-radius-md);
  box-shadow: var(--zw-shadow-card);
  transition: transform var(--zw-transition-base), box-shadow var(--zw-transition-base);
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--zw-shadow-card-hover);
}

.stat-icon-wrap {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--zw-radius-md);
  font-size: 26px;
}

.stat-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-label {
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-tertiary);
}

.stat-value {
  font-size: 26px;
  font-weight: var(--zw-font-weight-bold);
  color: var(--zw-text-primary);
  line-height: 1.2;
}

/* 图表 */
.chart-row {
  margin-bottom: var(--zw-space-md);
}

.chart-title {
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
}

.chart-box {
  height: 320px;
}

@media (max-width: 1200px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
