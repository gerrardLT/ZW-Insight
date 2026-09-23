<template>
  <div class="cockpit-container">
    <!-- 顶部筛选 + 数据时间（§18 顶部 5%）
         ⚠ 与 §14 的差距如实标注：§14 要求 公司/区域/项目/项目经理/年度/月份/刷新 7 项
         + 6 个快捷筛选（高风险/亏损/资金紧张/利润下降/本月异常），当前仅实现年度 + 刷新（2/7），
         区域无数据源（拟用所属公司替代，见审计口径决策 8）。不得声称已对齐 §14。 -->
    <div class="cockpit-header">
      <div class="header-title">
        <h2>工程经营驾驶舱</h2>
        <span class="header-hint">30 秒看懂经营 → 3 分钟定位异常 → 10 分钟追到单据</span>
      </div>
      <div class="header-actions">
        <el-select v-model="yearFilter" style="width: 110px" @change="loadAll">
          <el-option v-for="y in yearOptions" :key="y" :label="`${y}年度`" :value="y" />
        </el-select>
        <el-button :loading="loading" @click="loadAll">
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
        <span class="update-time">数据更新 {{ updateTime }}</span>
      </div>
    </div>

    <!-- 核心指标卡 8 张（§4，15%）：经营结果 4 + 资金状态 4 -->
    <div v-loading="loading" class="metric-grid">
      <el-card v-for="card in metricCards" :key="card.label" shadow="never" class="metric-card"
        :class="{ 'metric-alert': card.alert }">
        <div class="metric-label">
          <span>{{ card.label }}</span>
          <el-tooltip v-if="card.tooltip" :content="card.tooltip" placement="top">
            <el-icon class="metric-help"><QuestionFilled /></el-icon>
          </el-tooltip>
        </div>
        <div class="metric-value" :class="card.valueClass">{{ card.value }}</div>
        <div class="metric-sub" :class="card.subClass">{{ card.sub }}</div>
      </el-card>
    </div>

    <!-- 四象限图表（§18，各 20%） -->
    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>预计利润趋势</span>
              <el-tag v-if="!hasSnapshot" type="info" size="small">
                暂无快照（每日 03:30 生成，可先触发风险扫描）
              </el-tag>
            </div>
          </template>
          <div ref="profitChartRef" class="chart-body"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>{{ cashMode === 'month' ? '现金流预测（滚动 6 个月）' : `未来 ${cashMode} 天资金预测` }}</span>
              <div class="header-tags">
                <!-- §9.2 要求 30/60/90 天三档；月度视图保留为趋势补充 -->
                <el-tag v-if="cashMode !== 'month' && dayForecast"
                  :type="Number(dayForecast.gap) > 0 ? (dayForecast.coverable ? 'warning' : 'danger') : 'success'"
                  size="small">
                  缺口 {{ formatWan(dayForecast.gap) }}·{{ Number(dayForecast.gap) > 0 ? (dayForecast.coverable ? '可覆盖' : '需筹资') : '无缺口' }}
                </el-tag>
                <el-tag v-if="cashMode === 'month' && !rollingData.length" type="info" size="small">暂无预测快照</el-tag>
                <el-radio-group v-model="cashMode" size="small" @change="onCashModeChange">
                  <el-radio-button label="month">月度</el-radio-button>
                  <el-radio-button :label="30">30天</el-radio-button>
                  <el-radio-button :label="60">60天</el-radio-button>
                  <el-radio-button :label="90">90天</el-radio-button>
                </el-radio-group>
              </div>
            </div>
          </template>
          <div ref="cashChartRef" class="chart-body"></div>
          <!-- 逾期堆积必须显式告知（V2026_63）：它已计入当月预计付款，
               但老板需知道其中多少是拖欠；图表不另加系列以免与净缺口配色混淆 -->
          <div v-if="cashMode === 'month' && currentMonthOverdue > 0" class="overdue-note"
            style="margin-top: var(--zw-space-xs); font-size: var(--zw-font-size-xs); color: var(--el-color-danger)">
            当月预计付款中含已逾期未付 <strong>{{ formatWan(currentMonthOverdue) }}</strong> 万元（构成项，不另计）
          </div>
          <!-- 天窗口下如实告知口径（§9.2 资金差额 vs §10.4 资金缺口不同） -->
          <div v-if="cashMode !== 'month' && dayForecast" class="overdue-note"
            style="margin-top: var(--zw-space-xs); font-size: var(--zw-font-size-xs); color: var(--el-text-color-secondary)">
            资金差额（回款−付款）<strong>{{ formatWan(dayForecast.netFlow) }}</strong> 万；
            资金缺口（付款−可用资金）<strong>{{ formatWan(dayForecast.gap) }}</strong> 万；
            其中已逾期 {{ formatWan(dayForecast.overdueUnpaid) }} 万
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="chart-row">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <span>项目经营健康度（异常优先）</span>
          </template>
          <div class="health-list">
            <el-empty v-if="!healthData.length" description="暂无在算项目" :image-size="60" />
            <div v-for="item in healthData" :key="item.projectId" class="health-item"
              @click="goProjectCost(item.projectId)">
              <span class="health-dot" :class="`dot-${item.health.toLowerCase()}`"></span>
              <div class="health-main">
                <div class="health-name">{{ item.projectName }}</div>
                <div class="health-metrics">
                  <span>预计利润 <b :class="profitClass(item.forecastProfit)">{{ formatWan(item.forecastProfit) }}</b></span>
                  <span>利润率 <b>{{ formatRate(item.profitRate) }}</b></span>
                  <span v-if="item.redCount || item.yellowCount" class="health-risk">
                    🔴{{ item.redCount }} 🟡{{ item.yellowCount }}
                  </span>
                </div>
                <div v-if="item.costBasis === 'FALLBACK_TOTAL_EXPENSE'" class="health-basis-warn">
                  成本口径：未建 CBS 成本账户，"预计"退化为已实现支出
                </div>
                <div v-if="item.topRisks?.length" class="health-top-risk">{{ item.topRisks[0] }}</div>
              </div>
              <el-icon class="health-arrow"><IconArrowRight /></el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <span>成本结构（完工预测）</span>
              <el-tag v-if="!costStructure.length" type="info" size="small">暂无成本账户数据</el-tag>
            </div>
          </template>
          <div ref="costChartRef" class="chart-body"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- TOP 风险 / 待办（§11，20%） -->
    <el-card shadow="never" class="risk-card">
      <template #header>
        <div class="card-header">
          <span>TOP 风险 · 待处理事项</span>
          <div>
            <el-tag type="danger" size="small">🔴 严重 {{ riskSummary.redCount }}</el-tag>
            <el-tag type="warning" size="small" class="tag-gap">🟡 关注 {{ riskSummary.yellowCount }}</el-tag>
            <el-button link type="primary" @click="$router.push('/cockpit/risk-center')">
              进入风险中心<el-icon><IconArrowRight /></el-icon>
            </el-button>
          </div>
        </div>
      </template>
      <el-empty v-if="!topRisks.length" description="当前无待处理风险" :image-size="60" />
      <div v-for="risk in topRisks" :key="risk.id" class="risk-item" @click="$router.push('/cockpit/risk-center')">
        <span class="risk-severity" :class="`sev-${risk.severity.toLowerCase()}`">
          {{ risk.severity === 'RED' ? '🔴' : risk.severity === 'YELLOW' ? '🟡' : '⚪' }}
        </span>
        <span class="risk-title">{{ risk.title }}</span>
        <span class="risk-impact">{{ formatWan(risk.impactAmount) }}</span>
        <el-button link type="primary">查看 →</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, QuestionFilled } from '@element-plus/icons-vue'
import { IconArrowRight } from '@tabler/icons-vue'
import * as echarts from 'echarts'
import { useAppStore } from '@/stores/app'
import {
  getCockpitOverview,
  getCockpitProfitTrend,
  getProjectHealth,
  getRiskSummary,
  getRiskPage,
  scanRisks,
  type CockpitOverview,
  type ProjectHealth,
  type RiskRegister,
  type RiskSummary
} from '@/api/cockpit'
import { getRollingForecastPage, getForecastByDays, type FundRollingForecast, type DayForecast } from '@/api/fund-plan'
import { formatWan, toWan } from '@/utils/chart-format'
import { pickChartTheme, chartAxisStyle, chartTooltipStyle, chartSeriesLineStyle } from '@/constants/chart-theme'

const router = useRouter()
const appStore = useAppStore()

const loading = ref(false)
const updateTime = ref('—')
const yearFilter = ref(new Date().getFullYear())
const yearOptions = computed(() => {
  const current = new Date().getFullYear()
  return [current, current - 1, current - 2]
})

const overview = ref<CockpitOverview | null>(null)
const healthData = ref<ProjectHealth[]>([])
const rollingData = ref<FundRollingForecast[]>([])
// 当月逾期未付（rollingData 已按月份升序，首元素即当月）；
// 构成项：已含在当月 expectedPayments/netGap 内，仅用于告知，不可叠加
const currentMonthOverdue = computed(() => Number(rollingData.value[0]?.overdueUnpaid || 0))
const topRisks = ref<RiskRegister[]>([])
const costStructure = ref<{ category: string; amount: number }[]>([])
const hasSnapshot = ref(false)
const riskSummary = ref<RiskSummary>({
  redCount: 0, redImpact: 0, yellowCount: 0, yellowImpact: 0,
  infoCount: 0, infoImpact: 0, activeTotal: 0
})

const profitChartRef = ref<HTMLElement>()
const cashChartRef = ref<HTMLElement>()
const costChartRef = ref<HTMLElement>()
let profitChart: echarts.ECharts | null = null
let cashChart: echarts.ECharts | null = null
let costChart: echarts.ECharts | null = null

// 现金流预测视图：月度滚动（趋势）/ 30、60、90 天窗口（§9.2 累计口径）
const cashMode = ref<'month' | 30 | 60 | 90>('month')
const dayForecast = ref<DayForecast | null>(null)

async function loadDayForecast() {
  if (cashMode.value === 'month') return
  try {
    const res: any = await getForecastByDays({ days: cashMode.value })
    dayForecast.value = res?.data || null
  } catch (e: any) {
    dayForecast.value = null
    ElMessage.error('加载天窗口资金预测失败：' + (e?.message || '接口异常'))
  }
}

async function onCashModeChange() {
  await loadDayForecast()
  renderCharts()
}

// ==================== 指标卡（当前值 + 目标/口径 + 风险态，§4） ====================
const metricCards = computed(() => {
  const o = overview.value
  if (!o) return []
  const profitNegative = Number(o.forecastProfit) < 0
  const gapPositive = Number(o.gap90Days) > 0
  return [
    {
      label: '合同收入', value: formatWan(o.contractIncome), sub: '预计利润计算基数',
      tooltip: '生效/已结算施工合同金额合计；无施工合同时回退项目合同额', subClass: ''
    },
    {
      label: '预计总成本', value: formatWan(o.forecastTotalCost), sub: 'CBS 完工预测（EAC）',
      tooltip: '成本账户完工预测合计；无成本账户时退化为已实现支出', subClass: '',
      alert: false
    },
    {
      label: '预计利润', value: formatWan(o.forecastProfit),
      sub: profitNegative ? '⚠ 预计亏损' : '合同收入 − 预计总成本',
      tooltip: '预计利润 = 合同收入 − 预计最终总成本（非已实现收支差）',
      valueClass: profitNegative ? 'value-danger' : '', subClass: profitNegative ? 'sub-danger' : '',
      alert: profitNegative
    },
    {
      label: '预计利润率', value: formatRate(o.forecastProfitRate), sub: '目标 ≥ 5%（低于即黄色预警）',
      tooltip: '预计利润 ÷ 合同收入', subClass: Number(o.forecastProfitRate) < 0.05 ? 'sub-warn' : ''
    },
    {
      label: '累计回款', value: formatWan(o.cumulativeReceived), sub: '项目收入账合计',
      tooltip: '回款登记审批通过回写的项目总收入', subClass: ''
    },
    {
      label: '累计支付', value: formatWan(o.cumulativePaid), sub: '审批口径（非现金口径）',
      tooltip: 'total_expense 为审批口径：付款申请审批通过即回写；现金支付状态见付款申请「支付状态」',
      subClass: ''
    },
    {
      label: '应收未收', value: formatWan(o.receivableOutstanding), sub: '台账 OPEN 余额合计',
      tooltip: '应收台账未结清余额（结算审批生成，回款 FIFO 核销）', subClass: ''
    },
    {
      label: '90天资金缺口', value: formatWan(o.gap90Days),
      sub: gapPositive
        ? `🔴 需安排资金（可用 ${formatWan(o.availableFund)}）`
        : `🟢 可用资金 ${formatWan(o.availableFund)} 可覆盖`,
      tooltip: '资金流转 §10.4：缺口 = 未来 3 个月预计支付 − 可用资金（正数=缺钱）。'
        + '可用资金 = 账户余额快照 + 窗口内预计回款；旧口径不减可用资金，会高估资金压力',
      valueClass: gapPositive ? 'value-danger' : 'value-success',
      subClass: gapPositive ? 'sub-danger' : 'sub-success', alert: gapPositive
    }
  ]
})

function formatRate(value?: number) {
  if (value == null) return '—'
  return `${(Number(value) * 100).toFixed(1)}%`
}

function profitClass(value?: number) {
  return Number(value) < 0 ? 'value-danger' : ''
}

function goProjectCost(projectId: number) {
  router.push({ path: '/project-cost-control', query: { projectId: String(projectId) } })
}

// ==================== 图表 ====================
type Theme = ReturnType<typeof pickChartTheme>

function buildProfitOption(theme: Theme) {
  const months = profitTrend.value.snapshots.map(s => s.snapshotMonth)
  return {
    tooltip: { trigger: 'axis', ...chartTooltipStyle(theme) },
    grid: { left: '3%', right: '4%', bottom: '8%', top: '18%', containLabel: true },
    xAxis: { type: 'category', data: months, ...chartAxisStyle(theme) },
    yAxis: { type: 'value', name: '万元', ...chartAxisStyle(theme) },
    series: [
      {
        name: '预计利润', type: 'line', smooth: true, connectNulls: false,
        data: profitTrend.value.snapshots.map(s => toWan(Number(s.forecastProfit) || 0)),
        itemStyle: { color: theme.highlight },
        markLine: {
          silent: true,
          data: [{ yAxis: 0, lineStyle: { color: theme.semantic.danger } }]
        }
      }
    ]
  }
}

function buildCashOption(theme: Theme) {
  // 天窗口模式（§9.2）：三项对比柱状；缺口与覆盖状态由卡头 tag + 下方口径行呈现
  if (cashMode.value !== 'month' && dayForecast.value) {
    const d = dayForecast.value
    return {
      tooltip: { trigger: 'axis', ...chartTooltipStyle(theme) },
      grid: { left: '3%', right: '4%', bottom: '8%', top: '18%', containLabel: true },
      xAxis: {
        type: 'category',
        data: ['预计回款', '预计付款', '可用资金'],
        ...chartAxisStyle(theme)
      },
      yAxis: { type: 'value', name: '万元', ...chartAxisStyle(theme) },
      series: [{
        name: `未来 ${cashMode.value} 天`, type: 'bar', barMaxWidth: 56,
        data: [
          { value: toWan(Number(d.expectedReceipts) || 0), itemStyle: { color: theme.semantic.success } },
          { value: toWan(Number(d.expectedPayments) || 0), itemStyle: { color: theme.semantic.warning } },
          { value: toWan(Number(d.availableFund) || 0), itemStyle: { color: theme.highlight } }
        ],
        label: { show: true, position: 'top', formatter: '{c} 万', color: theme.text.secondary }
      }]
    }
  }
  const months = rollingData.value.map(r => r.forecastMonth)
  return {
    tooltip: { trigger: 'axis', ...chartTooltipStyle(theme) },
    legend: { bottom: 0, textStyle: { color: theme.text.secondary } },
    grid: { left: '3%', right: '4%', bottom: '14%', top: '14%', containLabel: true },
    xAxis: { type: 'category', data: months, ...chartAxisStyle(theme) },
    yAxis: { type: 'value', name: '万元', ...chartAxisStyle(theme) },
    series: [
      {
        name: '预计回款', type: 'line', smooth: true,
        data: rollingData.value.map(r => toWan(Number(r.expectedReceipts) || 0)),
        itemStyle: { color: theme.semantic.success },
        lineStyle: { type: chartSeriesLineStyle(0, theme) }
      },
      {
        name: '预计付款', type: 'line', smooth: true,
        data: rollingData.value.map(r => toWan(Number(r.expectedPayments) || 0)),
        itemStyle: { color: theme.semantic.warning },
        lineStyle: { type: chartSeriesLineStyle(1, theme) }
      },
      {
        name: '净缺口', type: 'bar', barMaxWidth: 28,
        data: rollingData.value.map(r => toWan(Number(r.netGap) || 0)),
        itemStyle: { color: theme.semantic.danger }
      }
    ]
  }
}

function buildCostOption(theme: Theme) {
  return {
    tooltip: { trigger: 'axis', ...chartTooltipStyle(theme) },
    grid: { left: '3%', right: '10%', bottom: '3%', top: '8%', containLabel: true },
    xAxis: { type: 'value', name: '万元', ...chartAxisStyle(theme) },
    yAxis: {
      type: 'category', inverse: true, ...chartAxisStyle(theme),
      data: costStructure.value.map(c => categoryLabel(c.category))
    },
    series: [{
      name: '完工预测', type: 'bar', barMaxWidth: 22,
      data: costStructure.value.map(c => toWan(c.amount)),
      itemStyle: { color: theme.highlight },
      label: { show: true, position: 'right', formatter: '{c} 万', color: theme.text.secondary }
    }]
  }
}

/** 成本类别编码 → 中文（与后端 CBS costCategory 值域一致） */
function categoryLabel(category: string) {
  const map: Record<string, string> = {
    MATERIAL: '材料', LABOR: '人工', MACHINE: '机械', SUBCONTRACT: '分包',
    INDIRECT: '间接费', OTHER: '其他'
  }
  return map[category] || category
}

function emptyOption(theme: Theme, text: string) {
  return {
    title: {
      text, left: 'center', top: 'middle',
      textStyle: { color: theme.text.secondary, fontSize: 13, fontWeight: 'normal' }
    }
  }
}

// ==================== 数据加载（真实接口，失败显式提示不静默） ====================
const profitTrend = ref<{ snapshots: any[]; realized: any }>({ snapshots: [], realized: null })

async function loadOverview() {
  try {
    const res: any = await getCockpitOverview()
    overview.value = res.data || null
  } catch (e: any) {
    ElMessage.error('加载经营总览失败：' + (e?.message || '接口异常'))
  }
}

async function loadProfitTrend() {
  try {
    const res: any = await getCockpitProfitTrend({ months: 6, year: yearFilter.value })
    profitTrend.value = res.data || { snapshots: [], realized: null }
    hasSnapshot.value = (profitTrend.value.snapshots || []).length > 0
    // 成本结构取最新公司级快照的类别分解（真实快照数据，无快照则不渲染）
    const latest = [...(profitTrend.value.snapshots || [])].reverse()
      .find((s: any) => s.categoryBreakdown)
    costStructure.value = latest ? parseBreakdown(latest.categoryBreakdown) : []
  } catch (e: any) {
    hasSnapshot.value = false
    costStructure.value = []
    ElMessage.error('加载利润趋势失败：' + (e?.message || '接口异常'))
  }
}

function parseBreakdown(json: string): { category: string; amount: number }[] {
  try {
    const obj = JSON.parse(json)
    return Object.entries(obj)
      .map(([category, amount]) => ({ category, amount: Number(amount) || 0 }))
      .filter(c => c.amount > 0)
      .sort((a, b) => b.amount - a.amount)
  } catch {
    // 解析失败如实返回空（不伪造结构），由卡片提示"暂无成本账户数据"
    return []
  }
}

async function loadRolling() {
  try {
    const res: any = await getRollingForecastPage({ page: 1, size: 6 })
    const records: FundRollingForecast[] = res.data?.records || []
    // 后端按月倒序返回，图表需按月升序
    rollingData.value = [...records].reverse()
  } catch (e: any) {
    rollingData.value = []
    ElMessage.error('加载现金流预测失败：' + (e?.message || '接口异常'))
  }
}

async function loadHealth() {
  try {
    const res: any = await getProjectHealth()
    healthData.value = res.data || []
  } catch (e: any) {
    healthData.value = []
    ElMessage.error('加载项目健康度失败：' + (e?.message || '接口异常'))
  }
}

async function loadRisks() {
  try {
    const [summaryRes, pageRes]: any[] = await Promise.all([
      getRiskSummary(),
      getRiskPage({ page: 1, size: 5, handleStatus: 'OPEN' })
    ])
    riskSummary.value = summaryRes.data || riskSummary.value
    topRisks.value = pageRes.data?.records || []
    // 无待处理风险时补查 PROCESSING，保证"待老板处理"区不空白（仍是真实数据）
    if (!topRisks.value.length) {
      const processingRes: any = await getRiskPage({ page: 1, size: 5, handleStatus: 'PROCESSING' })
      topRisks.value = processingRes.data?.records || []
    }
  } catch (e: any) {
    ElMessage.error('加载风险数据失败：' + (e?.message || '接口异常'))
  }
}

function renderCharts() {
  const theme = pickChartTheme(appStore.isDark)
  if (profitChartRef.value) {
    if (!profitChart || profitChart.isDisposed()) profitChart = echarts.init(profitChartRef.value)
    profitChart.setOption(
      hasSnapshot.value ? buildProfitOption(theme) : emptyOption(theme, '暂无预计利润快照'), true)
  }
  if (cashChartRef.value) {
    if (!cashChart || cashChart.isDisposed()) cashChart = echarts.init(cashChartRef.value)
    // 数据源随视图切换：月度看滚动快照，天窗口看 forecastByDays
    const hasCashData = cashMode.value === 'month'
      ? rollingData.value.length > 0
      : !!dayForecast.value
    cashChart.setOption(
      hasCashData
        ? buildCashOption(theme)
        : emptyOption(theme, cashMode.value === 'month'
          ? '暂无滚动预测快照'
          : '暂无天窗口预测数据（需存在已批未付单据或应收台账）'),
      true)
  }
  if (costChartRef.value) {
    if (!costChart || costChart.isDisposed()) costChart = echarts.init(costChartRef.value)
    costChart.setOption(
      costStructure.value.length ? buildCostOption(theme) : emptyOption(theme, '暂无成本账户数据'), true)
  }
}

async function loadAll() {
  loading.value = true
  try {
    await Promise.all([loadOverview(), loadProfitTrend(), loadRolling(), loadHealth(), loadRisks()])
    updateTime.value = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
    renderCharts()
  } finally {
    loading.value = false
  }
}

/**
 * 首屏若风险台账为空（新环境/未跑定时任务），触发一次真实扫描。
 * 不伪造风险数据，扫描结果由 7 条规则基于真实单据产生。
 */
async function ensureRisksScanned() {
  if (riskSummary.value.activeTotal > 0) return
  try {
    const res: any = await scanRisks()
    if (res.data?.failedRules?.length) {
      ElMessage.warning(`风险扫描完成，但 ${res.data.failedRules.length} 条规则执行失败：${res.data.failedRules.join('、')}`)
    }
    await loadRisks()
    await loadHealth()
  } catch (e: any) {
    // 扫描失败不阻断首屏（风险区显示空态），但必须显式告知，不静默
    ElMessage.warning('风险扫描未执行：' + (e?.message || '接口异常'))
  }
}

function handleResize() {
  profitChart?.resize()
  cashChart?.resize()
  costChart?.resize()
}

watch(() => appStore.isDark, () => renderCharts())

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await loadAll()
  await ensureRisksScanned()
  renderCharts()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  profitChart?.dispose()
  cashChart?.dispose()
  costChart?.dispose()
  profitChart = null
  cashChart = null
  costChart = null
})
</script>

<style scoped>
.cockpit-container {
  padding: var(--zw-content-padding);
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-md);
}
.cockpit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: var(--zw-space-sm);
}
.header-title h2 {
  margin: 0;
  font-size: var(--zw-font-size-xl);
  font-weight: var(--zw-font-weight-semibold);
}
.header-hint {
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
}
.update-time {
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}

/* 8 卡网格（§4：经营结果 4 + 资金状态 4） */
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--zw-space-sm-md);
}
@media (max-width: 1200px) {
  .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 640px) {
  .metric-grid { grid-template-columns: 1fr; }
}
.metric-card {
  border-left: 3px solid var(--el-color-primary);
}
.metric-card.metric-alert {
  border-left-color: var(--el-color-danger);
  background-color: var(--el-color-danger-light-9);
}
.metric-label {
  display: flex;
  align-items: center;
  gap: var(--zw-space-xs);
  font-size: var(--zw-font-size-sm);
  color: var(--el-text-color-secondary);
}
.metric-help { cursor: help; }
.metric-value {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xl);
  font-weight: var(--zw-font-weight-semibold);
  font-variant-numeric: tabular-nums;
}
.metric-sub {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
.value-danger { color: var(--el-color-danger); }
.value-success { color: var(--el-color-success); }
.sub-danger { color: var(--el-color-danger); font-weight: var(--zw-font-weight-semibold); }
.sub-warn { color: var(--el-color-warning); }
.sub-success { color: var(--el-color-success); }

.chart-row { width: 100%; }
.chart-card { height: 100%; }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zw-space-sm);
}
.header-tags {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  flex-wrap: wrap;
}
.chart-body { height: 260px; }

/* 项目健康度列表（§5.2：异常项目排前） */
.health-list {
  max-height: 260px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--zw-space-sm);
}
.health-item {
  display: flex;
  align-items: flex-start;
  gap: var(--zw-space-sm);
  padding: var(--zw-space-sm);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--zw-radius-sm);
  cursor: pointer;
  transition: background-color var(--zw-duration-base);
}
.health-item:hover { background-color: var(--el-fill-color-light); }
.health-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-top: var(--zw-space-xs);
  flex-shrink: 0;
}
.dot-red { background-color: var(--el-color-danger); }
.dot-yellow { background-color: var(--el-color-warning); }
.dot-green { background-color: var(--el-color-success); }
.health-main { flex: 1; min-width: 0; }
.health-name { font-weight: var(--zw-font-weight-semibold); }
.health-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zw-space-sm-md);
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-secondary);
}
.health-risk { color: var(--el-color-danger); }
.health-basis-warn {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-color-warning);
}
.health-top-risk {
  margin-top: var(--zw-space-xs);
  font-size: var(--zw-font-size-xs);
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.health-arrow { color: var(--el-text-color-placeholder); }

/* TOP 风险（§11：老板待处理事项） */
.risk-card :deep(.el-card__body) { padding-top: var(--zw-space-sm); }
.risk-item {
  display: flex;
  align-items: center;
  gap: var(--zw-space-sm);
  padding: var(--zw-space-sm) 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
}
.risk-item:last-child { border-bottom: none; }
.risk-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--zw-font-size-sm);
}
.risk-impact {
  font-variant-numeric: tabular-nums;
  font-weight: var(--zw-font-weight-semibold);
  color: var(--el-color-danger);
}
.tag-gap { margin: 0 var(--zw-space-sm); }
</style>
