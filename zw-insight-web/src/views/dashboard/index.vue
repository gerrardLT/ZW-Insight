<template>
  <div class="dashboard-container">
    <!-- 欢迎区（石墨铭牌，去渐变） -->
    <div class="welcome-banner">
      <div class="welcome-text">
        <div class="welcome-eyebrow">Workbench Overview</div>
        <h2>{{ greeting }}，{{ userName }}</h2>
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
      <div class="stat-card card-corner-marked" v-for="item in statCards" :key="item.key">
        <div class="stat-icon-wrap" :style="{ background: item.bg, color: item.color }">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <span class="stat-label">{{ item.label }}</span>
          <span class="stat-value stat-number">{{ item.value }}</span>
        </div>
      </div>
    </div>

    <!-- 逾期风险告警卡（真实数据源：/v1/finance/retention/overdue，与质保金催办任务同口径；
         端点受 finance:view 类级权限保护，无权限角色不渲染、不请求） -->
    <el-card v-if="canViewFinance" shadow="never" class="overdue-card" data-testid="overdue-panel">
      <div class="hazard-divider overdue-divider"></div>
      <div class="overdue-head">
        <div class="overdue-title-wrap">
          <span class="overdue-title">质保金逾期风险</span>
          <span
            v-if="overdueStats.count > 0"
            class="status-badge-overdue status-badge-overdue--pulse"
            data-testid="overdue-badge"
          >{{ overdueStats.count }} 笔逾期</span>
          <span v-else-if="!overdueError" class="overdue-clear" data-testid="overdue-clear">无逾期</span>
        </div>
        <el-button link type="primary" @click="$router.push({ name: 'Retention' })">
          查看质保金<el-icon><IconArrowRight /></el-icon>
        </el-button>
      </div>
      <div v-if="overdueError" class="overdue-error" data-testid="overdue-error">
        <span>加载失败：{{ overdueError }}</span>
        <el-button size="small" @click="loadOverdue">重试</el-button>
      </div>
      <div v-else class="overdue-stats">
        <div class="overdue-stat">
          <span class="overdue-stat-label">逾期笔数</span>
          <span class="overdue-stat-value stat-number">{{ overdueStats.count }}</span>
        </div>
        <div class="overdue-stat">
          <span class="overdue-stat-label">逾期未返还金额(万)</span>
          <span class="overdue-stat-value stat-number">{{ formatWan(overdueStats.amount) }}</span>
        </div>
        <div class="overdue-stat">
          <span class="overdue-stat-label">最长逾期</span>
          <span class="overdue-stat-value stat-number">{{ overdueStats.maxDays }} 天</span>
        </div>
      </div>
    </el-card>

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
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import { IconArrowRight } from '@tabler/icons-vue'
import { getCompanyOverview } from '@/api/dashboard'
import { getOverdueRetention } from '@/api/finance'
import type { RetentionMoney } from '@/types/finance'
import { formatWan, toWan } from '@/utils/chart-format'
import { pickChartTheme, chartAxisStyle, chartTooltipStyle } from '@/constants/chart-theme'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { usePermission } from '@/composables/usePermission'

const userStore = useUserStore()
const appStore = useAppStore()
// 逾期卡数据源 /overdue 受 finance:view 类级权限保护：无权限角色不渲染卡片、不发请求，
// 避免全员首页对非财务角色必然 403 报错（超管 *:*:* 由 store 绕过）
const { hasPermission } = usePermission()
const canViewFinance = computed(() => hasPermission('finance:view'))
const stats = ref<any>({})
const pieChartRef = ref<HTMLElement>()
const barChartRef = ref<HTMLElement>()
let pieChart: echarts.ECharts | null = null
let barChart: echarts.ECharts | null = null
/** 最近一次成功的 company-overview 数据：主题切换时以缓存重建 option，不重复请求 */
let lastPieOverview: any = null
let lastBarOverview: any = null

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

// 字段与后端 company-overview 严格对齐（2026-08-17 真实浏览器实测修复：
// 原读 projectCount/contractAmount/receivedAmount/advanceAmount 与后端字段错位致卡片全 0）
const statCards = computed(() => [
  { key: 'project', label: '项目总数', value: stats.value.projectTotal || 0, icon: 'Briefcase', color: 'var(--zw-brand)', bg: 'var(--zw-brand-light)' },
  { key: 'contract', label: '合同总额(万)', value: formatWan(stats.value.totalContractAmount), icon: 'Notebook', color: 'var(--zw-success)', bg: 'var(--zw-success-light)' },
  { key: 'received', label: '已收款(万)', value: formatWan(stats.value.totalIncome), icon: 'WalletFilled', color: 'var(--zw-warning)', bg: 'var(--zw-warning-light)' },
  { key: 'advance', label: '垫资(万)', value: formatWan(stats.value.advanceFund), icon: 'Warning', color: 'var(--zw-danger)', bg: 'var(--zw-danger-light)' }
])

// ================= 逾期风险告警卡（真实接口 /v1/finance/retention/overdue） =================
const overdueList = ref<RetentionMoney[]>([])
const overdueLoading = ref(false)
const overdueError = ref('')

/** 解析 yyyy-MM-dd 为本地零点（Date('yyyy-MM-dd') 按 UTC 解析，直接相减会差时区致天数少 1） */
function parseLocalDate(value: string): number {
  const [y, m, d] = value.slice(0, 10).split('-').map(Number)
  return new Date(y, m - 1, d).getTime()
}

/** 逾期汇总：笔数 / 未返还金额合计 / 最长逾期天数（均由后端返回的真实记录计算） */
const overdueStats = computed(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  let amount = 0
  let maxDays = 0
  for (const r of overdueList.value) {
    amount += Number(r.retentionAmount || 0) - Number(r.returnedAmount || 0)
    if (r.expireDate) {
      const days = Math.floor((today.getTime() - parseLocalDate(r.expireDate)) / 86400000)
      if (days > maxDays) maxDays = days
    }
  }
  return { count: overdueList.value.length, amount, maxDays }
})

async function loadOverdue() {
  overdueLoading.value = true
  overdueError.value = ''
  try {
    const res: any = await getOverdueRetention()
    overdueList.value = res.data || []
  } catch (e: any) {
    // 不静默：失败显式提示并展示重试入口
    overdueList.value = []
    overdueError.value = e?.message || '接口异常'
    ElMessage.error('加载逾期质保金失败：' + (e?.message || '接口异常'))
  } finally {
    overdueLoading.value = false
  }
}

// 项目状态英文枚举→中文（与项目列表 statusMap 同口径；未命中回退原值）
const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  FILED: '已报备',
  TENDERING: '招标中',
  WON: '已中标',
  CONSTRUCTION: '施工中',
  COMPLETED: '已竣工',
  CLOSING: '结项审批中',
  CLOSED: '已关闭'
}

async function loadStats() {
  try {
    const res: any = await getCompanyOverview()
    stats.value = res.data || {}
  } catch (e: any) {
    // 不静默处理：显式提示错误，同时置空避免页面卡死
    stats.value = {}
    ElMessage.error('加载统计数据失败：' + (e?.message || '接口异常'))
  }
}

/** 饼图 option：后端 statusDistribution Map<状态,数量> → [{name,value}]（纯函数，首渲与主题重绘共用） */
function buildPieOption(overview: any, theme: ReturnType<typeof pickChartTheme>) {
  // 状态键为英文枚举（与项目列表 statusMap 同口径），展示前映射为中文
  const dist = overview?.statusDistribution || {}
  const data = Object.entries(dist).map(([name, value]) => ({ name: STATUS_LABEL[name as string] || name, value: Number(value) || 0 }))
  return {
    color: theme.palette,
    tooltip: { trigger: 'item', ...chartTooltipStyle(theme) },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['45%', '70%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 2, borderColor: theme.surface.card, borderWidth: 2 },
      label: { show: true, formatter: '{b}: {c}' },
      data
    }]
  }
}

/** 饼图接口失败兜底空图（不静默：同时显式提示错误） */
function buildPieEmptyOption() {
  return {
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
  }
}

async function loadPieChart() {
  if (!pieChartRef.value) return
  pieChart = echarts.init(pieChartRef.value)

  try {
    const res: any = await getCompanyOverview()
    lastPieOverview = res.data || {}
    pieChart.setOption(buildPieOption(lastPieOverview, pickChartTheme(appStore.isDark)))
  } catch (e: any) {
    // 不静默处理：显式提示错误，同时展空图避免区域空白
    lastPieOverview = null
    ElMessage.error('加载项目状态分布失败：' + (e?.message || '接口异常'))
    pieChart.setOption(buildPieEmptyOption())
  }
}

/** 柱图 option：累计收入/支出（万元，真实数据；后端无月度收支端点） */
function buildBarOption(overview: any, theme: ReturnType<typeof pickChartTheme>) {
  const axisStyle = chartAxisStyle(theme)
  return {
    tooltip: { trigger: 'axis', valueFormatter: (v: any) => `${v} 万`, ...chartTooltipStyle(theme) },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: ['收入', '支出'], ...axisStyle },
    yAxis: { type: 'value', axisLabel: { formatter: '{value} 万', color: theme.axis.label }, axisLine: axisStyle.axisLine, splitLine: axisStyle.splitLine },
    series: [{
      name: '金额',
      type: 'bar',
      barMaxWidth: 48,
      label: { show: true, position: 'top', formatter: '{c} 万' },
      data: [
        { value: toWan(Number(overview?.totalIncome) || 0), itemStyle: { color: theme.highlight, borderRadius: [2, 2, 0, 0] } },
        { value: toWan(Number(overview?.totalExpense) || 0), itemStyle: { color: theme.semantic.danger, borderRadius: [2, 2, 0, 0] } }
      ]
    }]
  }
}

/** 柱图接口失败兜底空图（不静默：同时显式提示错误） */
function buildBarEmptyOption() {
  return {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: ['收入', '支出'] },
    yAxis: { type: 'value' },
    series: [{ name: '金额', type: 'bar', data: [] }]
  }
}

async function loadBarChart() {
  if (!barChartRef.value) return
  barChart = echarts.init(barChartRef.value)

  try {
    const res: any = await getCompanyOverview()
    lastBarOverview = res.data || {}
    barChart.setOption(buildBarOption(lastBarOverview, pickChartTheme(appStore.isDark)))
  } catch (e: any) {
    // 不静默处理：显式提示错误，同时展空图避免区域空白
    lastBarOverview = null
    ElMessage.error('加载收支对比失败：' + (e?.message || '接口异常'))
    barChart.setOption(buildBarEmptyOption())
  }
}

function handleResize() {
  pieChart?.resize()
  barChart?.resize()
}

// 主题切换即时重绘：以缓存的 overview 数据重建 option，不重复请求、不 dispose 重 init
//（与 StatChartPanel / project-dashboard / hr-statistics 的缓存重绘约定一致）
watch(() => appStore.isDark, () => {
  const theme = pickChartTheme(appStore.isDark)
  if (lastPieOverview && pieChart && !pieChart.isDisposed()) {
    pieChart.setOption(buildPieOption(lastPieOverview, theme), true)
  }
  if (lastBarOverview && barChart && !barChart.isDisposed()) {
    barChart.setOption(buildBarOption(lastBarOverview, theme), true)
  }
})

onMounted(() => {
  loadStats()
  if (canViewFinance.value) loadOverdue()
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

/* 欢迎区：石墨铭牌（无渐变无光斑，左侧橙定位条） */
.welcome-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 28px;
  margin-bottom: var(--zw-space-md);
  border-radius: var(--zw-radius-sm);
  background: var(--zw-bg-sidebar);
  border-left: 3px solid var(--zw-brand);
  color: #f2f3f1;
  overflow: hidden;
  position: relative;
}

/* 大写英文眉题（Display 层签名） */
.welcome-eyebrow {
  font-family: var(--zw-font-display);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: var(--zw-brand);
  margin-bottom: 6px;
}

.welcome-text h2 {
  font-size: var(--zw-font-size-2xl);
  font-weight: var(--zw-font-weight-bold);
  margin-bottom: 6px;
  letter-spacing: 0.02em;
}

.welcome-text p {
  font-size: var(--zw-font-size-base);
  color: #c6c9cc;
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
  background: rgba(255, 255, 255, 0.12);
  border-color: transparent;
  color: #f2f3f1;
}

.welcome-actions :deep(.el-button:not(.el-button--primary)):hover {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
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
  border: 1px solid var(--zw-border);
  border-radius: var(--zw-radius-sm);
  transition: border-color var(--zw-transition-fast);
}

/* 悬浮不浮动，仅边框高亮（直角纪律） */
.stat-card:hover {
  border-color: var(--zw-brand);
}

.stat-icon-wrap {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--zw-radius-xs);
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

/* ===== 逾期风险告警卡 ===== */
.overdue-card {
  margin-bottom: var(--zw-space-md);
  overflow: hidden;
}

.overdue-card :deep(.el-card__body) {
  padding: 0;
}

/* 顶部警示条纹（告警语言签名） */
.overdue-divider {
  height: 4px;
}

.overdue-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px 0;
}

.overdue-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.overdue-title {
  font-weight: var(--zw-font-weight-semibold);
  color: var(--zw-text-primary);
}

.overdue-clear {
  font-size: var(--zw-font-size-xs);
  color: var(--zw-success);
}

.overdue-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  padding: 12px 20px 18px;
}

.overdue-stat {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.overdue-stat-label {
  font-size: var(--zw-font-size-sm);
  color: var(--zw-text-tertiary);
}

.overdue-stat-value {
  font-size: 22px;
  font-weight: var(--zw-font-weight-bold);
  color: var(--zw-text-primary);
  line-height: 1.2;
}

.overdue-error {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 20px 18px;
  color: var(--zw-danger);
  font-size: var(--zw-font-size-sm);
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
